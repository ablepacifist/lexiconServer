package lexicon.api;

import lexicon.logic.UserAdminManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The admin "Users &amp; storage" page. Thin by design: every decision (who may, what is shown,
 * what may be deleted) is in {@link UserAdminManagerService}; this only works out who is calling.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminManagerService userAdmin;

    public UserAdminController(UserAdminManagerService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> users(@RequestParam(required = false) String sort) {
        Integer actor = callerId();
        return actor == null ? signIn() : respond(userAdmin.listUsers(actor, sort));
    }

    @GetMapping("/uploads")
    public ResponseEntity<Map<String, Object>> uploads(@RequestParam(required = false) String sort,
                                                       @RequestParam(required = false) Integer ownerId) {
        Integer actor = callerId();
        return actor == null ? signIn() : respond(userAdmin.listUploads(actor, sort, ownerId));
    }

    @DeleteMapping("/uploads/{kind}/{id}")
    public ResponseEntity<Map<String, Object>> deleteUpload(@PathVariable String kind, @PathVariable long id) {
        Integer actor = callerId();
        return actor == null ? signIn() : respond(userAdmin.deleteUpload(actor, kind, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable int id,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Integer actor = callerId();
        Object confirm = body == null ? null : body.get("confirmUsername");
        return actor == null ? signIn() : respond(userAdmin.deleteUser(actor, id, confirm == null ? null : confirm.toString()));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, Object>> setPassword(@PathVariable int id,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        Integer actor = callerId();
        Object password = body == null ? null : body.get("password");
        return actor == null ? signIn() : respond(userAdmin.setPassword(actor, id, password == null ? null : password.toString()));
    }

    @GetMapping("/storage-check")
    public ResponseEntity<Map<String, Object>> storageCheck() {
        Integer actor = callerId();
        return actor == null ? signIn() : respond(userAdmin.storageCheck(actor));
    }

    @PostMapping("/storage-check/clean")
    public ResponseEntity<Map<String, Object>> cleanStorage(@RequestBody(required = false) Map<String, Object> body) {
        Integer actor = callerId();
        if (actor == null) return signIn();
        Object raw = body == null ? null : body.get("paths");
        java.util.List<String> paths = raw instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList() : java.util.List.of();
        return respond(userAdmin.cleanStorage(actor, paths));
    }

    /** The signed-in player's id; null for anonymous callers and for the bridge's service login. */
    private static Integer callerId() {
        LexiconPrincipal p = LexiconPrincipal.current();
        return (p == null || p.isService()) ? null : p.getUserId();
    }

    private static ResponseEntity<Map<String, Object>> signIn() {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "Sign in first."));
    }

    private static ResponseEntity<Map<String, Object>> respond(Map<String, Object> result) {
        return ResponseEntity.status((int) result.get("status")).body(result);
    }
}
