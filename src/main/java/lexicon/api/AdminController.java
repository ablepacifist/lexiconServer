package lexicon.api;

import lexicon.logic.PermissionManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Role and permission management. Thin by design: who may do what is decided entirely in
 * {@link PermissionManagerService}; this only works out who is calling.
 */
@RestController
@RequestMapping("/api/admin/players")
public class AdminController {

    private final PermissionManagerService permissionManagerService;

    public AdminController(PermissionManagerService permissionManagerService) {
        this.permissionManagerService = permissionManagerService;
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> permissions(@PathVariable int id) {
        Integer actor = callerId();
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Sign in first."));
        }
        return respond(permissionManagerService.viewPermissions(actor, id));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> setRole(@PathVariable int id, @RequestBody Map<String, Object> body) {
        Integer actor = callerId();
        if (actor == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Sign in first."));
        }
        return respond(permissionManagerService.setRole(actor, id, String.valueOf(body.get("role"))));
    }

    /** The signed-in player's id; null for anonymous callers and for the bridge's service login. */
    private static Integer callerId() {
        LexiconPrincipal p = LexiconPrincipal.current();
        return (p == null || p.isService()) ? null : p.getUserId();
    }

    private static ResponseEntity<Map<String, Object>> respond(Map<String, Object> result) {
        return ResponseEntity.status((int) result.get("status")).body(result);
    }
}
