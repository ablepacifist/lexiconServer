package lexicon.api;

import lexicon.logic.PrinterMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrinterMonitorControllerTest {
    @Test
    void streamsAndDisconnectsUpstream() throws IOException {
        PrinterMonitorService monitor = mock(PrinterMonitorService.class);
        HttpURLConnection upstream = mock(HttpURLConnection.class);
        when(monitor.openCamera()).thenReturn(upstream);
        when(upstream.getContentType()).thenReturn("multipart/x-mixed-replace; boundary=frame");
        when(upstream.getInputStream()).thenReturn(new ByteArrayInputStream("--frame\r\npicture".getBytes()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PrinterMonitorController(monitor).camera(response);
        assertEquals("multipart/x-mixed-replace; boundary=frame", response.getContentType());
        assertEquals("--frame\r\npicture", response.getContentAsString());
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
        verify(upstream).disconnect();
    }

    @Test
    void cameraFailureIsReported() throws IOException {
        PrinterMonitorService monitor = mock(PrinterMonitorService.class);
        when(monitor.openCamera()).thenThrow(new IOException("offline"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PrinterMonitorController(monitor).camera(response);
        assertEquals(503, response.getStatus());
    }

    @Test
    void disconnectsUpstreamWhenStreamFails() throws IOException {
        PrinterMonitorService monitor = mock(PrinterMonitorService.class);
        HttpURLConnection upstream = mock(HttpURLConnection.class);
        InputStream input = mock(InputStream.class);
        when(monitor.openCamera()).thenReturn(upstream);
        when(upstream.getContentType()).thenReturn("multipart/x-mixed-replace; boundary=frame");
        when(upstream.getInputStream()).thenReturn(input);
        when(input.read(any(byte[].class))).thenThrow(new IOException("client or upstream disconnected"));
        new PrinterMonitorController(monitor).camera(new MockHttpServletResponse());
        verify(input).close();
        verify(upstream).disconnect();
    }
}
