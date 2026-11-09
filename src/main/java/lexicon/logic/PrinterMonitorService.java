package lexicon.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PrinterMonitorService {
    private final String octoprintUrl;
    private final String cameraUrl;
    private final String apiKey;
    private final ObjectMapper mapper;

    public PrinterMonitorService(@Value("${printer.octoprint-url:}") String octoprintUrl,
                                 @Value("${printer.camera-url:}") String cameraUrl,
                                 @Value("${printer.api-key:}") String apiKey,
                                 ObjectMapper mapper) {
        this.octoprintUrl = octoprintUrl;
        this.cameraUrl = cameraUrl;
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    public Map<String, Object> status() throws IOException {
        if (octoprintUrl.isBlank() || apiKey.isBlank()) {
            throw new MonitorException("Printer status is not configured.");
        }
        JsonNode job = getJson(octoprintUrl.replaceAll("/+$", "") + "/api/job", false);
        JsonNode printer = getJson(octoprintUrl.replaceAll("/+$", "") + "/api/printer", true);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", text(job.path("state")));
        status.put("connected", printer != null);
        JsonNode file = job.path("job").path("file");
        String display = text(file.path("display"));
        status.put("jobName", display != null ? display : text(file.path("name")));
        status.put("progress", number(job.path("progress").path("completion")));
        status.put("elapsedSeconds", number(job.path("progress").path("printTime")));
        status.put("remainingSeconds", number(job.path("progress").path("printTimeLeft")));
        JsonNode temperature = printer == null ? mapper.missingNode() : printer.path("temperature");
        status.put("toolActual", number(temperature.path("tool0").path("actual")));
        status.put("toolTarget", number(temperature.path("tool0").path("target")));
        status.put("bedActual", number(temperature.path("bed").path("actual")));
        status.put("bedTarget", number(temperature.path("bed").path("target")));
        return status;
    }

    public HttpURLConnection openCamera() throws IOException {
        if (cameraUrl.isBlank()) {
            throw new MonitorException("Printer camera is not configured.");
        }
        HttpURLConnection connection = open(cameraUrl);
        try {
            int code = connection.getResponseCode();
            String type = connection.getContentType();
            if (code != 200 || type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/x-mixed-replace")
                    || !type.toLowerCase(java.util.Locale.ROOT).contains("boundary=")) {
                throw new MonitorException("Printer camera is unavailable.");
            }
            return connection;
        } catch (IOException | RuntimeException e) {
            connection.disconnect();
            throw e;
        }
    }

    private JsonNode getJson(String url, boolean allowDisconnected) throws IOException {
        HttpURLConnection connection = open(url);
        connection.setRequestProperty("X-Api-Key", apiKey);
        try {
            int code = connection.getResponseCode();
            if (allowDisconnected && code == 409) return null;
            if (code == 401 || code == 403) throw new MonitorException("OctoPrint rejected the configured API key.");
            if (code != 200) throw new MonitorException("OctoPrint status is unavailable.");
            try (InputStream input = connection.getInputStream()) {
                byte[] body = input.readNBytes(1024 * 1024 + 1);
                if (body.length > 1024 * 1024) throw new MonitorException("OctoPrint status response is too large.");
                JsonNode result = mapper.readTree(new String(body, StandardCharsets.UTF_8));
                if (result == null || !result.isObject()) throw new MonitorException("OctoPrint returned invalid status.");
                return result;
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid URL");
            }
        } catch (IllegalArgumentException e) {
            throw new MonitorException("Printer service URL is invalid.");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    private static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static Number number(JsonNode node) {
        return node.isNumber() ? node.numberValue() : null;
    }

    public static class MonitorException extends IOException {
        public MonitorException(String message) {
            super(message);
        }
    }
}
