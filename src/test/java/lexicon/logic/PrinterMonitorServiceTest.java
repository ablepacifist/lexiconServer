package lexicon.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PrinterMonitorServiceTest {
    private HttpServer server;
    private PrinterMonitorService monitor;
    private final AtomicInteger printerCode = new AtomicInteger(200);
    private final AtomicInteger jobCode = new AtomicInteger(200);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/job", exchange -> {
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("X-Api-Key"));
            reply(exchange, jobCode.get(), "application/json",
                    "{\"state\":\"Printing\",\"job\":{\"file\":{\"name\":\"cube.gcode\"}},"
                            + "\"progress\":{\"completion\":42.5,\"printTime\":60,\"printTimeLeft\":120}}");
        });
        server.createContext("/api/printer", exchange -> reply(exchange, printerCode.get(), "application/json",
                "{\"temperature\":{\"tool0\":{\"actual\":215,\"target\":220},\"bed\":{\"actual\":60,\"target\":60}}}"));
        server.createContext("/stream", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            reply(exchange, 200, "multipart/x-mixed-replace; boundary=frame", "--frame\r\nframe-data\r\n");
        });
        server.createContext("/not-stream", exchange -> reply(exchange, 200, "text/plain", "not a stream"));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        monitor = new PrinterMonitorService(base, base + "/stream", "test-key", new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void normalizesStatus() throws IOException {
        Map<String, Object> status = monitor.status();
        assertEquals("Printing", status.get("state"));
        assertEquals(true, status.get("connected"));
        assertEquals("cube.gcode", status.get("jobName"));
        assertEquals(42.5, status.get("progress"));
        assertEquals(60, status.get("elapsedSeconds"));
        assertEquals(120, status.get("remainingSeconds"));
        assertEquals(215, status.get("toolActual"));
        assertEquals(60, status.get("bedTarget"));
    }

    @Test
    void disconnectedPrinterRetainsJobState() throws IOException {
        printerCode.set(409);
        Map<String, Object> status = monitor.status();
        assertEquals(false, status.get("connected"));
        assertEquals("cube.gcode", status.get("jobName"));
        assertNull(status.get("toolActual"));
    }

    @Test
    void rejectsBadCredentialsAndUnconfiguredStatus() {
        jobCode.set(403);
        assertTrue(assertThrows(PrinterMonitorService.MonitorException.class, () -> monitor.status())
                .getMessage().contains("rejected"));
        PrinterMonitorService unconfigured = new PrinterMonitorService("", "", "", new ObjectMapper());
        assertTrue(assertThrows(PrinterMonitorService.MonitorException.class, () -> unconfigured.status())
                .getMessage().contains("not configured"));
    }

    @Test
    void rejectsUpstreamFailureAndInvalidCameraContent() {
        jobCode.set(500);
        assertTrue(assertThrows(PrinterMonitorService.MonitorException.class, () -> monitor.status())
                .getMessage().contains("unavailable"));
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        PrinterMonitorService badCamera = new PrinterMonitorService(base, base + "/not-stream", "test-key", new ObjectMapper());
        assertThrows(PrinterMonitorService.MonitorException.class, badCamera::openCamera);
    }

    @Test
    void forwardsCameraWithoutSendingApiKey() throws IOException {
        HttpURLConnection stream = monitor.openCamera();
        try {
            assertEquals("multipart/x-mixed-replace; boundary=frame", stream.getContentType());
            assertTrue(new String(stream.getInputStream().readAllBytes(), StandardCharsets.UTF_8).contains("frame-data"));
        } finally {
            stream.disconnect();
        }
    }

    private static void reply(HttpExchange exchange, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
