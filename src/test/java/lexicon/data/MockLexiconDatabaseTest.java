package lexicon.data;

import lexicon.object.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Unit tests for MockLexiconDatabase
 * Tests player database operations only
 */
class MockLexiconDatabaseTest {
    
    private MockLexiconDatabase database;
    
    @BeforeEach
    void setUp() {
        database = new MockLexiconDatabase();
    }
    
    // ========== Player Management Tests ==========
    
    @Test
    void testAddAndGetPlayer() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "testuser", "password123", 1,
                                   "test@example.com", "Test User",
                                   LocalDateTime.now(), null);
        database.addPlayer(player);
        
        Player retrieved = database.getPlayer(id);
        assertNotNull(retrieved);
        assertEquals("testuser", retrieved.getUsername());
        assertEquals("test@example.com", retrieved.getEmail());
    }
    
    @Test
    void testGetPlayerByUsername() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "john_doe", "pass", 1,
                                   "john@example.com", "John",
                                   LocalDateTime.now(), null);
        database.addPlayer(player);
        
        Player found = database.getPlayerByUsername("john_doe");
        assertNotNull(found);
        assertEquals(id, found.getId());
    }
    
    @Test
    void testGetPlayerByEmail() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "jane", "pass", 1,
                                   "jane@example.com", "Jane",
                                   LocalDateTime.now(), null);
        database.addPlayer(player);
        
        Player found = database.getPlayerByEmail("jane@example.com");
        assertNotNull(found);
        assertEquals("jane", found.getUsername());
    }
    
    @Test
    void testPlayerExists() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "existinguser", "pass", 1);
        database.addPlayer(player);
        
        assertTrue(database.playerExists("existinguser"));
        assertFalse(database.playerExists("nonexistent"));
    }
    
    @Test
    void testEmailExists() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "user", "pass", 1,
                                   "existing@email.com", "User",
                                   LocalDateTime.now(), null);
        database.addPlayer(player);
        
        assertTrue(database.emailExists("existing@email.com"));
        assertFalse(database.emailExists("nonexistent@email.com"));
    }
    
    @Test
    void testUpdatePlayer() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "user", "oldpass", 1);
        database.addPlayer(player);
        
        player.setDisplayName("Updated Name");
        database.updatePlayer(player);
        
        Player updated = database.getPlayer(id);
        assertEquals("Updated Name", updated.getDisplayName());
    }
    
    @Test
    void testDeletePlayer() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "tobedeleted", "pass", 1);
        database.addPlayer(player);
        
        assertNotNull(database.getPlayer(id));
        database.deletePlayer(id);
        assertNull(database.getPlayer(id));
    }
    
    @Test
    void testUpdatePlayerLastLogin() {
        int id = database.getNextPlayerId();
        Player player = new Player(id, "user", "pass", 1);
        database.addPlayer(player);
        
        LocalDateTime loginTime = LocalDateTime.now();
        database.updatePlayerLastLogin(id, loginTime);
        
        Player updated = database.getPlayer(id);
        assertEquals(loginTime, updated.getLastLoginDate());
    }
    
    @Test
    void testGetAllPlayers() {
        int id1 = database.getNextPlayerId();
        int id2 = database.getNextPlayerId();
        database.addPlayer(new Player(id1, "user1", "pass", 1));
        database.addPlayer(new Player(id2, "user2", "pass", 1));
        
        Collection<Player> all = database.getAllPlayers();
        assertEquals(2, all.size());
    }
    
}
