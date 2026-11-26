package lexicon.data;

import lexicon.object.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Role CRUD against the private in-memory test database (see TestDatabaseBootstrap). */
class HSQLLexiconDatabaseRoleTest {

    private HSQLLexiconDatabase db;
    private int id;

    @BeforeEach
    void setUp() {
        db = new HSQLLexiconDatabase();
        db.ensureRoleColumn(); // must be harmless when the column already exists
        id = db.getNextPlayerId();
        db.addPlayer(new Player(id, "role_test_" + System.nanoTime(), "x", 1));
    }

    @AfterEach
    void tearDown() {
        db.deletePlayer(id);
    }

    @Test
    void newPlayersDefaultToUser() {
        assertEquals("USER", db.getPlayerRole(id));
    }

    @Test
    void setRoleIsReadBackAndCounted() {
        int before = db.countPlayersWithRole("ADMIN");
        db.setPlayerRole(id, "ADMIN");
        assertEquals("ADMIN", db.getPlayerRole(id));
        assertEquals(before + 1, db.countPlayersWithRole("ADMIN"));
    }

    @Test
    void updatePlayerLeavesRoleAlone() {
        db.setPlayerRole(id, "ADMIN");
        Player p = db.getPlayer(id);
        p.setLevel(5);
        db.updatePlayer(p);
        assertEquals("ADMIN", db.getPlayerRole(id));
    }

    @Test
    void missingPlayerHasNoRole() {
        assertNull(db.getPlayerRole(-42));
    }
}
