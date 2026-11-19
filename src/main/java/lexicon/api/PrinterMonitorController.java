package lexicon.api;

import jakarta.servlet.http.HttpServletResponse;
import lexicon.logic.PrinterMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;

@RestController
@RequestMapping("/api/printer-monitor")
public class PrinterMonitorController {
    private static final Logger log = LoggerFactory.getLogger(PrinterMonitorController.class);
    private final PrinterMonitorService monitor;

    public PrinterMonitorController(PrinterMonitorService monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(monitor.status());
        } catch (IOException e) {
            log.warn("Printer status unavailable: {}", e.toString());
            String message = e instanceof PrinterMonitorService.MonitorException
                    ? e.getMessage() : "Could not reach OctoPrint.";
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Cache-Control", "no-store").body(Map.of("error", message));
        }
    }

    @GetMapping("/camera")
    public void camera(HttpServletResponse response) throws IOException {
        HttpURLConnection upstream;
        try {
            upstream = monitor.openCamera();
        } catch (IOException e) {
            log.warn("Printer camera unavailable: {}", e.toString());
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Printer camera unavailable");
            return;
        }
        try {
            response.setContentType(upstream.getContentType());
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("X-Accel-Buffering", "no");
            try (InputStream input = upstream.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, count);
                    response.getOutputStream().flush();
                }
            }
        } catch (IOException e) {
            log.info("Printer camera stream ended: {}", e.toString());
        } finally {
            upstream.disconnect();
        }
    }
}
