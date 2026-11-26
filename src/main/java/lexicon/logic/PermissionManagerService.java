package lexicon.logic;

import lexicon.object.Permission;
import lexicon.object.Role;

import java.util.Map;
import java.util.Set;

/**
 * The single place in the stack that decides what a player may do.
 *
 * <p>Controllers in Lexicon, Alchemy (through the SSO token) and the GTW voice bridge (through
 * the login and SSO responses) all ask this service; none of them decide on their own.
 */
public interface PermissionManagerService {

    Role getRole(int userId);

    Set<Permission> getPermissions(int userId);

    boolean hasPermission(int userId, Permission permission);

    /**
     * {@code {role, permissions}} ready to merge into an auth response. Permissions are the enum
     * names, sorted, so clients can do a plain "contains" check.
     */
    Map<String, Object> describe(int userId);

    /**
     * {@code userId}'s role and permissions for a trusted service (the GTW bridge).
     * {@code status} 200, or 404 when the player does not exist.
     */
    Map<String, Object> lookup(int userId);

    /**
     * {@code targetId}'s role and permissions as seen by {@code actorId}: players may see their
     * own, admins may see anyone's. Same {@code status} contract as {@link #setRole}.
     */
    Map<String, Object> viewPermissions(int actorId, int targetId);

    /**
     * Change {@code targetId}'s role on behalf of {@code actorId}.
     *
     * @return {@code {status, success, message, role?}}: 200 on success, 400 for an unknown role,
     *         403 when the actor lacks {@link Permission#MANAGE_PERMISSIONS}, 404 when the target
     *         does not exist, 409 when the change would leave no admin at all
     */
    Map<String, Object> setRole(int actorId, int targetId, String role);

    /**
     * Promote the usernames listed in {@code ADMIN_BOOTSTRAP_USERNAMES} to {@link Role#ADMIN}.
     * Only promotes; never demotes. Safe to run on every start.
     *
     * @return how many players were promoted this run
     */
    int bootstrapAdmins();
}
