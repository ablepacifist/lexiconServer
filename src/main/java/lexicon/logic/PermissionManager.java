package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.object.Permission;
import lexicon.object.Player;
import lexicon.object.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionManager implements PermissionManagerService {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private final ILexiconDatabase database;
    private final List<String> bootstrapUsernames;

    @Autowired
    public PermissionManager(ILexiconDatabase database,
                             @Value("${ADMIN_BOOTSTRAP_USERNAMES:}") String bootstrapUsernames) {
        this.database = database;
        this.bootstrapUsernames = Arrays.stream(bootstrapUsernames == null ? new String[0] : bootstrapUsernames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public Role getRole(int userId) {
        return Role.fromStored(database.getPlayerRole(userId));
    }

    @Override
    public Set<Permission> getPermissions(int userId) {
        return getRole(userId).permissions();
    }

    @Override
    public boolean hasPermission(int userId, Permission permission) {
        return getPermissions(userId).contains(permission);
    }

    @Override
    public Map<String, Object> describe(int userId) {
        Role role = getRole(userId);
        Map<String, Object> out = new HashMap<>();
        out.put("role", role.name());
        out.put("permissions", role.permissions().stream().map(Enum::name).sorted().toList());
        return out;
    }

    @Override
    public Map<String, Object> lookup(int userId) {
        if (database.getPlayerRole(userId) == null) {
            return result(404, false, "Player not found.");
        }
        Map<String, Object> ok = result(200, true, "OK");
        ok.put("userId", userId);
        ok.putAll(describe(userId));
        return ok;
    }

    @Override
    public Map<String, Object> viewPermissions(int actorId, int targetId) {
        if (actorId != targetId && !hasPermission(actorId, Permission.MANAGE_PERMISSIONS)) {
            return result(403, false, "Only an admin can see other players' permissions.");
        }
        if (database.getPlayerRole(targetId) == null) {
            return result(404, false, "Player not found.");
        }
        Map<String, Object> ok = result(200, true, "OK");
        ok.put("userId", targetId);
        ok.putAll(describe(targetId));
        return ok;
    }

    @Override
    public synchronized Map<String, Object> setRole(int actorId, int targetId, String roleName) {
        if (!hasPermission(actorId, Permission.MANAGE_PERMISSIONS)) {
            return result(403, false, "Only an admin can change roles.");
        }
        Role newRole;
        try {
            newRole = Role.valueOf(String.valueOf(roleName).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return result(400, false, "Unknown role: " + roleName);
        }
        String current = database.getPlayerRole(targetId);
        if (current == null) {
            return result(404, false, "Player not found.");
        }
        Role currentRole = Role.fromStored(current);
        if (currentRole == Role.ADMIN && newRole != Role.ADMIN
                && database.countPlayersWithRole(Role.ADMIN.name()) <= 1) {
            return result(409, false, "That would leave no admin. Make someone else an admin first.");
        }
        if (currentRole != newRole) {
            database.setPlayerRole(targetId, newRole.name());
            log.info("Player {} changed player {}'s role from {} to {}", actorId, targetId, currentRole, newRole);
        }
        Map<String, Object> ok = result(200, true, "Role updated.");
        ok.putAll(describe(targetId));
        return ok;
    }

    /** Runs once the app is up, after the data layer has added the ROLE column. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            bootstrapAdmins();
        } catch (RuntimeException e) {
            // Never stop the server over this; existing roles keep working.
            log.error("Admin bootstrap failed: {}", e.getMessage());
        }
    }

    @Override
    public int bootstrapAdmins() {
        int promoted = 0;
        for (String username : bootstrapUsernames) {
            Player player = database.getPlayerByUsername(username);
            if (player == null) {
                log.warn("ADMIN_BOOTSTRAP_USERNAMES lists '{}' but no such player exists", username);
                continue;
            }
            if (getRole(player.getId()) != Role.ADMIN) {
                database.setPlayerRole(player.getId(), Role.ADMIN.name());
                log.info("Promoted '{}' to ADMIN from ADMIN_BOOTSTRAP_USERNAMES", username);
                promoted++;
            }
        }
        return promoted;
    }

    private static Map<String, Object> result(int status, boolean success, String message) {
        Map<String, Object> out = new HashMap<>();
        out.put("status", status);
        out.put("success", success);
        out.put("message", message);
        return out;
    }
}
