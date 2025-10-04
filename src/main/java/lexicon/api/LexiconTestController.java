package lexicon.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Basic controller for Lexicon API testing
 * Following the same pattern as Alchemy's controllers
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LexiconTestController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "service", "Lexicon API",
            "message", "Lexicon backend is running!"
        ));
    }

    @GetMapping("/info")
    public ResponseEntity<?> info() {
        return ResponseEntity.ok(Map.of(
            "service", "Lexicon Media Sharing API",
            "version", "1.0.0",
            "description", "Personal video and audio sharing platform",
            "features", new String[]{
                "User authentication",
                "Media file upload",
                "Video sharing",
                "Audio sharing",
                "Public/private media"
            }
        ));
    }
}