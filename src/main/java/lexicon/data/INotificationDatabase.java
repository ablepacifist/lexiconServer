package lexicon.data;

import lexicon.object.Notification;
import lexicon.object.NotificationPrefs;

import java.util.List;

/**
 * Database operations for notifications and per-user notification preferences.
 * Only the service layer (NotificationManager) should call this.
 */
public interface INotificationDatabase {

    /** Insert a notification; returns generated id. */
    long insert(Notification n);

    /**
     * List notifications visible to a user (targeted to them OR broadcast),
     * newest first, excluding ones they sent themselves.
     * @param beforeId only return rows with id < beforeId (null for latest page)
     */
    List<Notification> listForUser(int userId, int limit, Long beforeId);

    /** Count unread notifications for a user (created after their lastReadAt). */
    int unreadCount(int userId);

    /** Get a user's prefs, creating a default row on first access. */
    NotificationPrefs getPrefs(int userId);

    /** Update a user's category/push preferences (does not touch lastReadAt). */
    void updatePrefs(NotificationPrefs prefs);

    /** Set lastReadAt = now for a user (marks everything read). */
    void markAllRead(int userId);

    /**
     * Prefs rows for users who should receive an OS push for the given type
     * (category enabled AND enablePush true). Used for broadcast push fan-out.
     */
    List<NotificationPrefs> pushRecipientsForType(String type);
}
