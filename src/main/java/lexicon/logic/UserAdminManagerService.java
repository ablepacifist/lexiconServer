package lexicon.logic;

import java.util.Map;

/**
 * The admin "Users &amp; storage" page: find abandoned accounts and the uploads taking up space,
 * and clean them up. Every method needs {@link lexicon.object.Permission#MANAGE_USERS}.
 *
 * Results are display-ready (dates, "N days ago", sizes, warning levels, sort order, what may
 * be deleted) so the website only shows them. Each result carries an HTTP-style "status".
 */
public interface UserAdminManagerService {

    /** Accounts with their last activity and upload totals. sort: inactive | storage | newest | name. */
    Map<String, Object> listUsers(int actorId, String sort);

    /** Media uploads and chat images. sort: oldest | newest | largest. ownerId narrows to one account. */
    Map<String, Object> listUploads(int actorId, String sort, Integer ownerId);

    /** Delete one upload (kind "media" or "chat") from disk and the database. */
    Map<String, Object> deleteUpload(int actorId, String kind, long uploadId);

    /**
     * Delete an account with everything it uploaded. {@code confirmUsername} must match the
     * account's username. Refuses the caller's own account and any admin account.
     */
    Map<String, Object> deleteUser(int actorId, int targetId, String confirmUsername);

    /** Give an account a new password (at least 8 characters) and sign it out everywhere. */
    Map<String, Object> setPassword(int actorId, int targetId, String newPassword);

    /**
     * Compare the storage drive with the database: files in the media and chat folders that no
     * row points at (wasted space), and rows whose file is missing. Read-only.
     */
    Map<String, Object> storageCheck(int actorId);

    /**
     * Delete the given unreferenced files. The check is run again first and only paths that are
     * still unreferenced (and not changed in the last day) are deleted.
     */
    Map<String, Object> cleanStorage(int actorId, java.util.List<String> paths);
}
