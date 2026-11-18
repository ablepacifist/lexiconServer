package lexicon.api;

import lexicon.config.AppUpdateSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * What the Android app polls to find out whether there is a newer APK.
 *
 * <p>The values come from {@link AppUpdateSettings}, which re-reads the shared {@code .env} when
 * it changes, rather than from {@code @Value} fields resolved once at startup. That is what lets
 * the publish pipeline write a new version and have it served immediately, instead of needing a
 * full stack restart it could not run without killing its own SSH tunnel.
 *
 * <p>The injected properties remain as the fallback, so a deployment configured through real
 * environment variables rather than a {@code .env} file behaves exactly as before.
 */
@RestController
@RequestMapping("/api/app")
@CrossOrigin(origins = "*")
public class AppVersionController {

    private final AppUpdateSettings settings;

    @Value("${app.update.version-code:1}")
    private long versionCode;

    @Value("${app.update.version-name:0.1.0}")
    private String versionName;

    @Value("${app.update.download-url}")
    private String downloadUrl;

    @Value("${app.update.critical:false}")
    private boolean critical;

    @Value("${app.update.changelog:}")
    private String changelog;

    @Value("${app.update.sha256:}")
    private String sha256;

    public AppVersionController(AppUpdateSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> latestVersion() {
        Map<String, Object> body = new HashMap<>();
        body.put("versionCode", settings.getLong("APP_UPDATE_VERSION_CODE", versionCode));
        body.put("versionName", settings.get("APP_UPDATE_VERSION_NAME", versionName));
        body.put("downloadUrl", settings.get("APP_UPDATE_DOWNLOAD_URL", downloadUrl));
        body.put("critical", settings.getBoolean("APP_UPDATE_CRITICAL", critical));
        body.put("changelog", settings.get("APP_UPDATE_CHANGELOG", changelog));

        String hash = settings.get("APP_UPDATE_SHA256", sha256);
        if (hash != null && !hash.isBlank()) {
            body.put("sha256", hash);
        }
        return ResponseEntity.ok(body);
    }
}
