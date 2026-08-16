package lexicon.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/app")
@CrossOrigin(origins = "*")
public class AppVersionController {

    @Value("${app.update.version-code:1}")
    private long versionCode;

    @Value("${app.update.version-name:0.1.0}")
    private String versionName;

    @Value("${app.update.download-url:https://api.alex-dyakin.com/api/app/download/latest}")
    private String downloadUrl;

    @Value("${app.update.critical:false}")
    private boolean critical;

    @Value("${app.update.changelog:}")
    private String changelog;

    @Value("${app.update.sha256:}")
    private String sha256;

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> latestVersion() {
        Map<String, Object> body = new HashMap<>();
        body.put("versionCode", versionCode);
        body.put("versionName", versionName);
        body.put("downloadUrl", downloadUrl);
        body.put("critical", critical);
        body.put("changelog", changelog);
        if (sha256 != null && !sha256.isBlank()) {
            body.put("sha256", sha256);
        }
        return ResponseEntity.ok(body);
    }
}
