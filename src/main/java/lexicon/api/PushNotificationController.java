package lexicon.api;

import lexicon.logic.PushNotificationService;
import lexicon.object.PushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*")
public class PushNotificationController {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationController.class);

    @Autowired
    private PushNotificationService pushService;

    /**
     * Returns the VAPID public key for the frontend to create push subscriptions.
     */
    @GetMapping("/vapid-key")
    public ResponseEntity<Map<String, String>> getVapidKey() {
        String key = pushService.getVapidPublicKey();
        if (key == null || key.isEmpty()) {
            return ResponseEntity.status(503).body(Map.of("error", "Push notifications not configured"));
        }
        return ResponseEntity.ok(Map.of("publicKey", key));
    }

    /**
     * Register a push subscription for a user.
     * Body: { userId, endpoint, keys: { p256dh, auth }, userAgent }
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody Map<String, Object> body) {
        Integer userId = (Integer) body.get("userId");
        String endpoint = (String) body.get("endpoint");

        @SuppressWarnings("unchecked")
        Map<String, String> keys = (Map<String, String>) body.get("keys");
        String userAgent = (String) body.get("userAgent");

        if (userId == null || endpoint == null || keys == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: userId, endpoint, keys"));
        }

        String p256dh = keys.get("p256dh");
        String auth = keys.get("auth");

        if (p256dh == null || auth == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing keys.p256dh or keys.auth"));
        }

        PushSubscription sub = new PushSubscription(userId, endpoint, p256dh, auth, userAgent);
        pushService.subscribe(sub);

        return ResponseEntity.ok(Map.of("success", true, "message", "Push subscription registered"));
    }

    /**
     * Remove a push subscription by endpoint.
     * Body: { endpoint }
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing endpoint"));
        }

        pushService.unsubscribe(endpoint);
        return ResponseEntity.ok(Map.of("success", true, "message", "Push subscription removed"));
    }

    /**
     * Send a push notification to a specific user.
     * Called by the bridge or internal services.
     * Body: { userId, title, body, data (optional), url (optional) }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody Map<String, Object> body) {
        Integer userId = (Integer) body.get("userId");
        String title = (String) body.get("title");
        String notifBody = (String) body.get("body");

        if (userId == null || title == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: userId, title"));
        }

        // Build the JSON payload for the service worker
        String url = (String) body.get("url");
        String payload = pushService.buildPayload(title, notifBody, url, body.get("data"));

        int sent = pushService.sendToUser(userId, payload);
        return ResponseEntity.ok(Map.of("success", true, "sent", sent));
    }

    /**
     * Send a push notification to multiple users.
     * Body: { userIds: [1, 2, 3], title, body, url (optional), data (optional) }
     */
    @PostMapping("/send-bulk")
    public ResponseEntity<Map<String, Object>> sendBulk(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> userIds = (List<Integer>) body.get("userIds");
        String title = (String) body.get("title");
        String notifBody = (String) body.get("body");

        if (userIds == null || userIds.isEmpty() || title == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: userIds, title"));
        }

        String url = (String) body.get("url");
        String payload = pushService.buildPayload(title, notifBody, url, body.get("data"));

        int sent = pushService.sendToUsers(userIds, payload);
        return ResponseEntity.ok(Map.of("success", true, "sent", sent));
    }
}
