package lexicon.logic;

import lexicon.data.INotificationDatabase;
import lexicon.object.Notification;
import lexicon.object.NotificationPrefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notification business logic: persistence, live SSE delivery, and OS push
 * fan-out. This is the ONLY layer that touches the notification data store,
 * holds the SSE emitter registry, and applies preference / self-exclusion
 * rules. Controllers must only delegate here.
 */
@Service
public class NotificationManager implements NotificationManagerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);

    private final INotificationDatabase db;
    private final PushNotificationService pushService;

    // userId -> that user's open SSE connections
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // Async executor so push fan-out never blocks the request thread
    private final ExecutorService pushExecutor = Executors.newFixedThreadPool(2);

    @Autowired
    public NotificationManager(INotificationDatabase db, PushNotificationService pushService) {
        this.db = db;
        this.pushService = pushService;
    }

    @Override
    public long create(Notification n) {
        if (n.getCreatedAt() == null) n.setCreatedAt(LocalDateTime.now());
        long id = db.insert(n);
        n.setId(id);

        deliverSse(n);
        pushExecutor.submit(() -> {
            try {
                deliverPush(n);
            } catch (Exception e) {
                log.warn("Push delivery failed for notification {}: {}", id, e.getMessage());
            }
        });
        return id;
    }

    /** Send the notification over SSE to every eligible connected user. */
    private void deliverSse(Notification n) {
        for (Map.Entry<Integer, CopyOnWriteArrayList<SseEmitter>> entry : userEmitters.entrySet()) {
            int userId = entry.getKey();
            if (!isEligible(n, userId)) continue;

            CopyOnWriteArrayList<SseEmitter> emitters = entry.getValue();
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("notification").data(n));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) emitters.removeAll(dead);
        }
    }

    /** Send an OS/browser push to eligible users who opted in. */
    private void deliverPush(Notification n) {
        if (Boolean.FALSE.equals(n.getDeliverPush())) return; // producer already routed push
        if (!pushService.isEnabled()) return;
        String payload = pushService.buildPayload(
                n.getTitle(),
                n.getBody(),
                n.getLink() != null ? n.getLink() : "/lexicon-dashboard",
                null);

        if (n.getTargetUserId() != null) {
            NotificationPrefs prefs = db.getPrefs(n.getTargetUserId());
            if (prefs.isEnablePush() && prefs.isTypeEnabled(n.getType()) && !isActor(n, n.getTargetUserId())) {
                pushService.sendToUser(n.getTargetUserId(), payload);
            }
            return;
        }
        // Broadcast: fan out to everyone who wants push for this type.
        for (NotificationPrefs prefs : db.pushRecipientsForType(n.getType())) {
            if (isActor(n, prefs.getUserId())) continue;
            pushService.sendToUser(prefs.getUserId(), payload);
        }
    }

    /** Whether an in-app (SSE) notification should reach this user. */
    private boolean isEligible(Notification n, int userId) {
        if (n.getTargetUserId() != null && n.getTargetUserId() != userId) return false; // directed elsewhere
        if (isActor(n, userId)) return false; // don't notify yourself
        return db.getPrefs(userId).isTypeEnabled(n.getType());
    }

    private boolean isActor(Notification n, int userId) {
        return n.getFromUserId() != null && n.getFromUserId() == userId;
    }

    @Override
    public List<Notification> list(int userId, int limit, Long beforeId) {
        if (limit <= 0 || limit > 200) limit = 50;
        return db.listForUser(userId, limit, beforeId);
    }

    @Override
    public int unreadCount(int userId) {
        return db.unreadCount(userId);
    }

    @Override
    public void markAllRead(int userId) {
        db.markAllRead(userId);
    }

    @Override
    public NotificationPrefs getPrefs(int userId) {
        return db.getPrefs(userId);
    }

    @Override
    public void updatePrefs(NotificationPrefs prefs) {
        db.updatePrefs(prefs);
    }

    @Override
    public SseEmitter subscribe(int userId) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 min
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            emitter.complete();
        });
        emitter.onError((e) -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("connected"));
            Map<String, Object> init = new HashMap<>();
            init.put("unreadCount", db.unreadCount(userId));
            emitter.send(SseEmitter.event().name("init").data(init));
        } catch (Exception e) {
            removeEmitter(userId, emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void removeEmitter(int userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) emitters.remove(emitter);
    }

    @Override
    public void notifyNowPlaying(String trackTitle, String link) {
        if (trackTitle == null || trackTitle.isBlank()) return;
        // Music changes every track and is opt-in — skip entirely if nobody wants it.
        if (!hasSubscribersForType("music")) return;

        Notification n = new Notification();
        n.setType("music");
        n.setTitle("🎵 Now Playing");
        n.setBody(trackTitle);
        n.setSource("lexicon");
        n.setLink(link);
        create(n);
    }

    /** True if any connected user has the type enabled, or anyone opted into push for it. */
    private boolean hasSubscribersForType(String type) {
        for (Map.Entry<Integer, CopyOnWriteArrayList<SseEmitter>> entry : userEmitters.entrySet()) {
            CopyOnWriteArrayList<SseEmitter> emitters = entry.getValue();
            if (emitters != null && !emitters.isEmpty() && db.getPrefs(entry.getKey()).isTypeEnabled(type)) {
                return true;
            }
        }
        return !db.pushRecipientsForType(type).isEmpty();
    }

    /** Keep SSE connections alive through idle-timeout proxies (e.g. Cloudflare). */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeats() {
        for (CopyOnWriteArrayList<SseEmitter> emitters : userEmitters.values()) {
            if (emitters == null || emitters.isEmpty()) continue;
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) emitters.removeAll(dead);
        }
    }
}
