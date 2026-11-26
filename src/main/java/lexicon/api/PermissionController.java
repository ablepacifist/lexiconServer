package lexicon.api;

import lexicon.logic.PermissionManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * "What may player N do?" for trusted services. Bridge-only (X-Service-Token), see
 * {@link LexiconSecurityConfig}: the GTW voice bridge calls this when it resumes a session,
 * so voice admin always follows the player's Lexicon role.
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionManagerService permissionManagerService;

    public PermissionController(PermissionManagerService permissionManagerService) {
        this.permissionManagerService = permissionManagerService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> lookup(@PathVariable int userId) {
        Map<String, Object> result = permissionManagerService.lookup(userId);
        return ResponseEntity.status((int) result.get("status")).body(result);
    }
}
