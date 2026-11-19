package lexicon.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The app-update settings, re-read from the shared {@code .env} rather than frozen at startup.
 *
 * <p>These used to be {@code @Value} fields on the controller, which Spring resolves once when
 * the bean is created. Publishing a new APK therefore needed a full restart of the stack before
 * the new version was served — and the publish pipeline ran that restart <em>through</em> the
 * Cloudflare tunnel it was connected over, so restarting the tunnel killed the deploy's own SSH
 * session and the job failed every time even when the publish had worked.
 *
 * <p>Reading from Spring's {@code Environment} instead would not have helped: property sources
 * are resolved at startup too, so a rewritten {@code .env} is invisible to it. This reads the
 * file itself, and only when it has actually changed — the modified time and size are checked
 * first, so a steady stream of requests costs two stat calls, not a parse.
 *
 * <p>Values still fall back to the Spring-resolved property, so behaviour is unchanged when the
 * file is missing, unreadable, or has no entry for a key — which is the case in tests and in any
 * deployment that configures the server through real environment variables instead.
 */
@Component
public class AppUpdateSettings {

    private static final Logger log = LoggerFactory.getLogger(AppUpdateSettings.class);

    /** Mirrors the lookup order in {@code spring.config.import}. */
    private final List<Path> candidates;

    private Map<String, String> cached = Map.of();
    private Path cachedFrom;
    private long cachedModified = -1L;
    private long cachedSize = -1L;

    public AppUpdateSettings(@Value("${MASTER_ENV_FILE:../.env}") String masterEnvFile) {
        this.candidates = List.of(Paths.get(".env"), Paths.get(masterEnvFile));
    }

    /**
     * The value for {@code key} from the live {@code .env}, or {@code fallback} when the file
     * has no entry for it.
     */
    public String get(String key, String fallback) {
        String value = current().get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public long getLong(String key, long fallback) {
        String raw = current().get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // A non-numeric version code is a publish mistake, not a reason to stop serving.
            log.warn("{} in .env is not a number ('{}'); using {}", key, raw, fallback);
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String raw = current().get(key);
        return (raw == null || raw.isBlank()) ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private synchronized Map<String, String> current() {
        for (Path candidate : candidates) {
            if (!Files.isReadable(candidate)) {
                continue;
            }
            try {
                long modified = Files.getLastModifiedTime(candidate).toMillis();
                long size = Files.size(candidate);
                boolean unchanged = candidate.equals(cachedFrom)
                        && modified == cachedModified
                        && size == cachedSize;
                if (!unchanged) {
                    cached = parse(candidate);
                    cachedFrom = candidate;
                    cachedModified = modified;
                    cachedSize = size;
                }
                return cached;
            } catch (IOException e) {
                log.debug("Could not read {}: {}", candidate, e.toString());
            }
        }
        return cached;
    }

    private static Map<String, String> parse(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            // The file is written by hand and by scripts, so both quoting styles turn up.
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}
