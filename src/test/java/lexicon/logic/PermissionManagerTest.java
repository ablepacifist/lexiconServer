package lexicon.logic;

import lexicon.data.MockLexiconDatabase;
import lexicon.object.Permission;
import lexicon.object.Player;
import lexicon.object.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PermissionManagerTest {

    private MockLexiconDatabase db;
    private PermissionManager manager;
    private int zx;
    private int player;

    @BeforeEach
    void setUp() {
        db = new MockLexiconDatabase();
        zx = add("zx");
        player = add("someone");
        manager = new PermissionManager(db, " zx , ghost ");
    }

    private int add(String username) {
        int id = db.getNextPlayerId();
        db.addPlayer(new Player(id, username, "x", 1));
        return id;
    }

    @Test
    void everyoneStartsAsUserWithNoPermissions() {
        assertEquals(Role.USER, manager.getRole(player));
        assertEquals(EnumSet.of(Permission.GAMESERVER_START, Permission.GAMESERVER_REQUEST_RESTART), manager.getPermissions(player));
        assertFalse(manager.hasPermission(player, Permission.GAMESERVER_ADMIN));
        assertFalse(manager.hasPermission(player, Permission.HOLDFAST_ADVANCE_TIME));
    }

    @Test
    void bootstrapPromotesListedPlayersOnceAndSkipsUnknownNames() {
        assertEquals(1, manager.bootstrapAdmins());
        assertEquals(0, manager.bootstrapAdmins(), "second run must be a no-op");
        assertEquals(Role.ADMIN, manager.getRole(zx));
        assertEquals(Role.USER, manager.getRole(player));
    }

    @Test
    void bootstrapNeverDemotes() {
        db.setPlayerRole(player, "ADMIN");
        manager.bootstrapAdmins();
        assertEquals(Role.ADMIN, manager.getRole(player));
    }

    @Test
    void adminHoldsEveryPermission() {
        manager.bootstrapAdmins();
        for (Permission p : Permission.values()) {
            assertTrue(manager.hasPermission(zx, p), p.name());
        }
    }

    @Test
    void describeGivesSortedPermissionNames() {
        manager.bootstrapAdmins();
        Map<String, Object> d = manager.describe(zx);
        assertEquals("ADMIN", d.get("role"));
        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) d.get("permissions");
        assertTrue(perms.contains("HOLDFAST_ADVANCE_TIME"));
        assertTrue(perms.contains("VOICE_ADMIN"));
        assertEquals(perms.stream().sorted().toList(), perms);
        assertEquals(List.of("GAMESERVER_REQUEST_RESTART", "GAMESERVER_START"), manager.describe(player).get("permissions"));
    }

    @Test
    void unknownStoredRoleIsTreatedAsUser() {
        db.setPlayerRole(player, "SUPREME_OVERLORD");
        assertEquals(Role.USER, manager.getRole(player));
    }

    @Test
    void nonAdminCannotChangeRoles() {
        Map<String, Object> r = manager.setRole(player, player, "ADMIN");
        assertEquals(403, r.get("status"));
        assertEquals(Role.USER, manager.getRole(player));
    }

    @Test
    void adminCanPromoteAndDemote() {
        manager.bootstrapAdmins();
        assertEquals(200, manager.setRole(zx, player, "admin").get("status"));
        assertEquals(Role.ADMIN, manager.getRole(player));
        assertEquals(200, manager.setRole(zx, player, "USER").get("status"));
        assertEquals(Role.USER, manager.getRole(player));
    }

    @Test
    void lastAdminCannotBeRemoved() {
        manager.bootstrapAdmins();
        Map<String, Object> r = manager.setRole(zx, zx, "USER");
        assertEquals(409, r.get("status"));
        assertEquals(Role.ADMIN, manager.getRole(zx));
    }

    @Test
    void badRoleAndMissingPlayerAreRejected() {
        manager.bootstrapAdmins();
        assertEquals(400, manager.setRole(zx, player, "WIZARD").get("status"));
        assertEquals(404, manager.setRole(zx, 9999, "ADMIN").get("status"));
    }

    @Test
    void playersSeeOnlyTheirOwnPermissions() {
        manager.bootstrapAdmins();
        assertEquals(200, manager.viewPermissions(player, player).get("status"));
        assertEquals(403, manager.viewPermissions(player, zx).get("status"));
        assertEquals(200, manager.viewPermissions(zx, player).get("status"));
        assertEquals(404, manager.viewPermissions(zx, 9999).get("status"));
    }

    @Test
    void emptyBootstrapListPromotesNobody() {
        PermissionManager none = new PermissionManager(db, "");
        assertEquals(0, none.bootstrapAdmins());
        PermissionManager nul = new PermissionManager(db, null);
        assertEquals(0, nul.bootstrapAdmins());
    }
}
