package lexicon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Talks to qBittorrent's WebUI API on this machine.
 *
 * qBittorrent is bound to the Proton VPN tunnel address (split tunnel, "Include only"
 * mode), and Proton deliberately has no machine-wide kill switch here — a full tunnel would
 * take the website, the APIs and RustDesk down with it. The binding is what keeps the real
 * IP off the trackers, so {@link #health()} refuses to report healthy unless qBittorrent is
 * actually bound to the live tunnel address. Nothing in this class ever launches
 * qBittorrent: it must be started by hand, after the VPN is up.
 *
 * The WebUI listens on loopback only and is never proxied, so the credentials below never
 * leave this machine.
 */
@Service
public class QBittorrentClient {

    /** Long enough for a slow local answer; the API is on loopback. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String baseUrl;
    private final String username;
    private final String password;

    /**
     * The session cookie from /auth/login, as the whole {@code name=value} pair. Re-fetched
     * whenever the API answers 403. The name is not fixed: qBittorrent 5.1 renamed it from
     * {@code SID} to {@code QBT_SID_<port>} so that instances on different ports can share
     * a browser, so whatever it hands back is what gets sent again.
     */
    private volatile String sessionCookie;

    /**
     * qBittorrent 5 renamed the pause/resume vocabulary (WebAPI 2.11): "paused" became
     * "stopped" on add, and torrents/resume|pause became torrents/start|stop. Resolved once
     * from the running instance rather than assumed, so this works against either.
     */
    private volatile Boolean modernVocabulary;

    public QBittorrentClient(
            @Value("${qbittorrent.url:}") String baseUrl,
            @Value("${qbittorrent.username:}") String username,
            @Value("${qbittorrent.password:}") String password) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    /** False when no WebUI credentials are configured; the feature then refuses to run. */
    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

    // ── health ───────────────────────────────────────────────────────────────────────

    /**
     * Whether a torrent may be queued right now.
     *
     * @param tunnelAddress the live VPN tunnel address, or null/blank if there is no tunnel
     */
    public Health health(String tunnelAddress) {
        if (!isConfigured()) {
            return new Health(false, false, "", "qBittorrent is not configured on the server.");
        }
        String boundAddress;
        try {
            boundAddress = preferences().path("current_network_interface_address").asText("");
            if (boundAddress.isBlank()) {
                boundAddress = preferences().path("current_interface_address").asText("");
            }
        } catch (Exception e) {
            return new Health(false, false, "",
                    "qBittorrent isn't running. Start it at the desktop, after Proton VPN is connected.");
        }
        if (tunnelAddress == null || tunnelAddress.isBlank()) {
            return new Health(true, false, boundAddress,
                    "Proton VPN isn't connected, so downloading would expose this machine's real IP.");
        }
        if (!boundAddress.equals(tunnelAddress)) {
            return new Health(true, false, boundAddress,
                    "qBittorrent is bound to " + (boundAddress.isBlank() ? "no interface" : boundAddress)
                            + " but the VPN tunnel is " + tunnelAddress
                            + ". Restart qBittorrent so it binds to the tunnel.");
        }
        return new Health(true, true, boundAddress, "");
    }

    /** qBittorrent's reachability and whether it is bound to the VPN tunnel. */
    public record Health(boolean up, boolean boundToTunnel, String boundAddress, String message) {
        public boolean usable() {
            return up && boundToTunnel;
        }
    }

    // ── torrents ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a .torrent file, stopped, so file priorities can be set before anything is
     * written to disk.
     *
     * @param tag a unique tag used to find the torrent afterwards: the add endpoint answers
     *            only "Ok." and never returns the hash, and tagging avoids having to parse
     *            bencode to compute it ourselves
     */
    public void addTorrentFile(byte[] torrent, String filename, String savePath, String tag)
            throws IOException, InterruptedException {
        String boundary = "lexicon" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : addFields(savePath, tag).entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"torrents\"; filename=\""
                + filename.replace("\"", "") + "\"\r\nContent-Type: application/x-bittorrent\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(torrent);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        String answer = send(request("/api/v2/torrents/add")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
        requireOk(answer);
    }

    /** Adds a magnet link or the URL of a .torrent file, stopped. See {@link #addTorrentFile}. */
    public void addTorrentUrl(String urlOrMagnet, String savePath, String tag)
            throws IOException, InterruptedException {
        Map<String, String> fields = addFields(savePath, tag);
        fields.put("urls", urlOrMagnet);
        requireOk(postForm("/api/v2/torrents/add", fields));
    }

    private Map<String, String> addFields(String savePath, String tag) throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("savepath", savePath);
        fields.put("category", CATEGORY);
        fields.put("tags", tag);
        // Added stopped on purpose: the unwanted files are deselected before it ever starts,
        // so nothing outside the allowlist is written to disk even briefly.
        fields.put(modernVocabulary() ? "stopped" : "paused", "true");
        return fields;
    }

    /** Torrents carrying the given tag. Empty until qBittorrent has registered the add. */
    public List<TorrentInfo> byTag(String tag) throws IOException, InterruptedException {
        return parseInfo(send(request("/api/v2/torrents/info?tag=" + encode(tag)).GET()));
    }

    public List<TorrentInfo> byHashes(List<String> hashes) throws IOException, InterruptedException {
        if (hashes.isEmpty()) {
            return List.of();
        }
        String joined = String.join("|", hashes);
        return parseInfo(send(request("/api/v2/torrents/info?hashes=" + encode(joined)).GET()));
    }

    public List<TorrentFile> files(String hash) throws IOException, InterruptedException {
        JsonNode array = JSON.readTree(send(request("/api/v2/torrents/files?hash=" + encode(hash)).GET()));
        List<TorrentFile> files = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);
            // Older builds omit "index" and rely on array order.
            files.add(new TorrentFile(
                    node.hasNonNull("index") ? node.get("index").asInt() : i,
                    node.path("name").asText(""),
                    node.path("size").asLong(0),
                    node.path("priority").asInt(1),
                    node.path("progress").asDouble(0)));
        }
        return files;
    }

    /** Priority 0 means "do not download": the file is never fetched or written. */
    public void setFilePriority(String hash, List<Integer> fileIndexes, int priority)
            throws IOException, InterruptedException {
        if (fileIndexes.isEmpty()) {
            return;
        }
        requireOk(postForm("/api/v2/torrents/filePrio", Map.of(
                "hash", hash,
                "id", fileIndexes.stream().map(String::valueOf).collect(Collectors.joining("|")),
                "priority", String.valueOf(priority))));
    }

    public void start(String hash) throws IOException, InterruptedException {
        String path = modernVocabulary() ? "/api/v2/torrents/start" : "/api/v2/torrents/resume";
        requireOk(postForm(path, Map.of("hashes", hash)));
    }

    /** Removes the torrent, optionally with everything it downloaded. */
    public void delete(String hash, boolean deleteFiles) throws IOException, InterruptedException {
        requireOk(postForm("/api/v2/torrents/delete", Map.of(
                "hashes", hash,
                "deleteFiles", String.valueOf(deleteFiles))));
    }

    /**
     * Idempotent. Returns whether the category is now known to exist, so the caller can try
     * again later rather than assuming a category it never managed to create: on this
     * machine qBittorrent is usually started well after the server.
     */
    public boolean ensureCategory(String savePath) {
        try {
            postForm("/api/v2/torrents/createCategory", Map.of("category", CATEGORY, "savePath", savePath));
            return true;
        } catch (IOException e) {
            // A category that already exists answers 409, which is just as good. Anything
            // else — qBittorrent down, credentials wrong — is worth retrying.
            return e.getMessage() != null && e.getMessage().contains("409");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public JsonNode preferences() throws IOException, InterruptedException {
        return JSON.readTree(send(request("/api/v2/app/preferences").GET()));
    }

    // ── records ──────────────────────────────────────────────────────────────────────

    public record TorrentInfo(
            String hash, String name, String state, double progress, long size,
            long downloadSpeed, long eta, String tags, String savePath, long completedOn) {

        /** qBittorrent reports every seeding/complete variant differently; treat them alike. */
        public boolean complete() {
            return progress >= 1.0d || state.startsWith("seeding") || state.startsWith("stalledUP")
                    || state.equals("uploading") || state.equals("pausedUP") || state.equals("stoppedUP")
                    || state.equals("queuedUP") || state.equals("forcedUP") || state.equals("checkingUP");
        }

        /** Waiting for a magnet's metadata: the file list is not known yet. */
        public boolean awaitingMetadata() {
            return state.equals("metaDL") || state.equals("downloadingMetadata");
        }

        public boolean errored() {
            return state.equals("error") || state.equals("missingFiles");
        }

        /** A broken interface binding shows up here, which is the VPN having moved. */
        public boolean stalled() {
            return state.equals("stalledDL");
        }
    }

    public record TorrentFile(int index, String name, long size, int priority, double progress) {
        public boolean wanted() {
            return priority > 0;
        }

        public boolean complete() {
            return progress >= 1.0d;
        }
    }

    // ── plumbing ─────────────────────────────────────────────────────────────────────

    public static final String CATEGORY = "lexicon";

    private boolean modernVocabulary() throws IOException, InterruptedException {
        Boolean known = modernVocabulary;
        if (known != null) {
            return known;
        }
        String version = send(request("/api/v2/app/webapiVersion").GET()).trim();
        boolean modern = compareVersions(version, "2.11") >= 0;
        modernVocabulary = modern;
        return modern;
    }

    /** Numeric, component-wise, so "2.9" sorts below "2.11" rather than above it. */
    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parseOrZero(a[i]) : 0;
            int y = i < b.length ? parseOrZero(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parseOrZero(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<TorrentInfo> parseInfo(String body) throws IOException {
        JsonNode array = JSON.readTree(body);
        List<TorrentInfo> torrents = new ArrayList<>();
        for (JsonNode node : array) {
            torrents.add(new TorrentInfo(
                    node.path("hash").asText(""),
                    node.path("name").asText(""),
                    node.path("state").asText(""),
                    node.path("progress").asDouble(0),
                    node.path("size").asLong(0),
                    node.path("dlspeed").asLong(0),
                    node.path("eta").asLong(0),
                    node.path("tags").asText(""),
                    node.path("save_path").asText(""),
                    node.path("completion_on").asLong(0)));
        }
        return torrents;
    }

    private HttpRequest.Builder request(String path) {
        // No session cookie here on purpose: the builder is handed to send(), which may have
        // to log in first, and a cookie read at this point would be the stale one (or none
        // at all on the first call after startup).
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT);
    }

    /**
     * Stamps the current session onto the request. Called immediately before each attempt,
     * never when the builder is created: the first call after startup has no session yet,
     * and a retry after a 403 needs the cookie the re-login just produced. setHeader rather
     * than header so a second attempt replaces the cookie instead of sending both.
     */
    private HttpRequest.Builder withSession(HttpRequest.Builder builder) {
        String cookie = sessionCookie;
        return cookie == null ? builder : builder.setHeader("Cookie", cookie);
    }

    private String postForm(String path, Map<String, String> fields) throws IOException, InterruptedException {
        String body = fields.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return send(request(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /**
     * Sends a request, logging in first when there is no session and once more if the
     * session has expired (qBittorrent answers 403 for that).
     */
    private String send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("qBittorrent credentials are not configured on the server");
        }
        if (sessionCookie == null) {
            login();
        }
        HttpResponse<String> response =
                http.send(withSession(builder).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403) {
            login();
            response = http.send(withSession(builder).build(), HttpResponse.BodyHandlers.ofString());
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("qBittorrent answered " + response.statusCode()
                    + (response.body().isBlank() ? "" : ": " + response.body().trim()));
        }
        return response.body();
    }

    private void login() throws IOException, InterruptedException {
        HttpRequest login = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v2/auth/login"))
                .timeout(REQUEST_TIMEOUT)
                // qBittorrent checks the Referer against its own host unless that check is
                // disabled; sending it keeps CSRF protection on.
                .header("Referer", baseUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "username=" + encode(username) + "&password=" + encode(password)))
                .build();
        HttpResponse<String> response = http.send(login, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body().trim();
        // Older builds answer 200 "Ok."; 5.3 (WebAPI 2.16) answers 204 with no body at all.
        // A refusal is "Fails." with a 200, so the body is what decides, not the status.
        if (response.statusCode() / 100 != 2 || body.toLowerCase().startsWith("fail")) {
            throw new IOException("qBittorrent rejected the WebUI credentials (" + response.statusCode() + ")");
        }
        sessionCookie = response.headers().allValues("set-cookie").stream()
                .map(value -> value.split(";", 2)[0].trim())
                .filter(pair -> pair.startsWith("SID=") || pair.startsWith("QBT_SID"))
                // A logout clears the cookie by sending an empty value; that is not a session.
                .filter(pair -> pair.indexOf('=') > 0 && pair.length() > pair.indexOf('=') + 1)
                .findFirst()
                .orElseThrow(() -> new IOException("qBittorrent did not return a session cookie"));
    }

    /** The API answers "Ok." or "Fails." in a 200 body, so a 2xx alone proves nothing. */
    private void requireOk(String body) throws IOException {
        if (body != null && body.trim().equalsIgnoreCase("Fails.")) {
            throw new IOException("qBittorrent refused the request");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
