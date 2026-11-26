package lexicon.data;

import lexicon.object.Player;
import lexicon.object.PlayerActivity;
import lexicon.utils.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Activity dates and account deletion, against the private in-memory test database only. */
class HSQLLexiconDatabaseActivityTest {

    private HSQLLexiconDatabase db;
    private int id;

    @BeforeEach
    void setUp() {
        db = new HSQLLexiconDatabase();
        db.ensureActivityColumns();
        db.ensureActivityColumns(); // a second run must be harmless
        id = db.getNextPlayerId();
        db.addPlayer(new Player(id, "activity_test_" + System.nanoTime(), "x", 1));
    }

    @AfterEach
    void tearDown() {
        db.deletePlayerAccount(id);
    }

    private PlayerActivity row() {
        return db.getPlayerActivity().stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    }

    @Test
    void activityStartsEmptyAndIsReadBack() {
        assertNull(row().lastActiveDate());
        LocalDateTime when = LocalDateTime.of(2026, 11, 20, 9, 30);
        db.updatePlayerLastActive(id, when);
        assertEquals(when, row().lastActiveDate());
        assertEquals("USER", row().role());
    }

    @Test
    void lastLoginNowHasAColumnToLandIn() {
        LocalDateTime when = LocalDateTime.of(2026, 11, 1, 8, 0);
        db.updatePlayerLastLogin(id, when);
        assertEquals(when, row().lastLoginDate());
    }

    @Test
    void deletingAnAccountAlsoClearsTheAlchemyRowsThatWouldBlockIt() throws Exception {
        try (Connection c = DriverManager.getConnection(DatabaseConfig.url(), "SA", DatabaseConfig.password());
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS PUBLIC.INVENTORY (player_id INTEGER NOT NULL, ingredient_id INTEGER NOT NULL, "
                    + "quantity INTEGER, PRIMARY KEY (player_id, ingredient_id), FOREIGN KEY (player_id) REFERENCES PUBLIC.PLAYERS(id))");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO PUBLIC.INVENTORY VALUES (?, 1, 3)")) {
                p.setInt(1, id);
                p.executeUpdate();
            }
        }

        db.deletePlayer(id); // the old delete: blocked by the foreign key, fails quietly
        assertTrue(db.getPlayerActivity().stream().anyMatch(a -> a.id() == id), "old delete could not remove a player who had played");

        assertTrue(db.deletePlayerAccount(id));
        assertFalse(db.getPlayerActivity().stream().anyMatch(a -> a.id() == id));
        try (Connection c = DriverManager.getConnection(DatabaseConfig.url(), "SA", DatabaseConfig.password());
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM PUBLIC.INVENTORY WHERE player_id = ?")) {
            p.setInt(1, id);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    void deletingAMissingAccountReportsFalse() {
        assertFalse(db.deletePlayerAccount(-12345));
    }
}
