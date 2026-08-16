package lexicon.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/api/app/download")
@CrossOrigin(origins = "*")
public class AppUpdateDownloadController {

    @Value("${app.update.apk-path:./releases/lexicon-latest.apk}")
    private String apkPath;

    @GetMapping("/latest")
    public ResponseEntity<Resource> downloadLatestApk() {
        File apk = new File(apkPath).getAbsoluteFile();
        if (!apk.exists() || !apk.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = new FileSystemResource(apk);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
            .contentLength(apk.length())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lexicon-latest.apk")
            .body(resource);
    }
}
