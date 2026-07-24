package lexicon.logic;

import lexicon.object.Notification;
import lexicon.object.NotificationPrefs;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Service interface for the notification system. This is the only layer the
 * controller (and other services) may call — all business logic, the SSE
 * emitter registry, and data access live behind this interface.
 */
public interface NotificationManagerService {

    /**
     * Persist a notification and deliver it live (SSE) + via OS push to the
     * appropriate recipients, respecting each user's preferences and excluding
     * the actor.
     * @return the generated notification id
     */
    long create(Notification n);

    /** History for a user (targeted + broadcast), newest first. */
    List<Notification> list(int userId, int limit, Long beforeId);

    /** Unread count for a user. */
    int unreadCount(int userId);

    /** Mark all of a user's notifications read. */
    void markAllRead(int userId);

    /** Get a user's preferences (creating defaults on first access). */
    NotificationPrefs getPrefs(int userId);

    /** Update a user's preferences. */
    void updatePrefs(NotificationPrefs prefs);

    /** Open an SSE stream for a user; registers the emitter and sends init. */
    SseEmitter subscribe(int userId);

    /**
     * Emit a "music now playing" broadcast notification. Because music tracks
     * change frequently and the category is opt-in, this is a no-op (nothing is
     * persisted) unless at least one user is actually subscribed to music.
     */
    void notifyNowPlaying(String trackTitle, String link);
}
