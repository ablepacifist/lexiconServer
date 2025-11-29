package lexicon.object;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void testFullConstructor() {
        // Arrange
        LocalDateTime regDate = LocalDateTime.now();
        LocalDateTime loginDate = LocalDateTime.now().plusDays(1);

        // Act
        Player player = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            loginDate
        );

        // Assert
        assertEquals(1, player.getId());
        assertEquals("testuser", player.getUsername());
        assertEquals("password123", player.getPassword());
        assertEquals(5, player.getLevel());
        assertEquals("test@example.com", player.getEmail());
        assertEquals("Test User", player.getDisplayName());
        assertEquals(regDate, player.getRegistrationDate());
        assertEquals(loginDate, player.getLastLoginDate());
    }

    @Test
    void testConstructorWithLevel() {
        // Act
        Player player = new Player(1, "testuser", "password123", 5);

        // Assert
        assertEquals(1, player.getId());
        assertEquals("testuser", player.getUsername());
        assertEquals("password123", player.getPassword());
        assertEquals(5, player.getLevel());
        assertNull(player.getEmail());
        assertEquals("testuser", player.getDisplayName()); // Defaults to username
        assertNotNull(player.getRegistrationDate());
        assertNull(player.getLastLoginDate());
    }

    @Test
    void testConstructorWithDefaultLevel() {
        // Act
        Player player = new Player(1, "testuser", "password123");

        // Assert
        assertEquals(1, player.getId());
        assertEquals("testuser", player.getUsername());
        assertEquals("password123", player.getPassword());
        assertEquals(1, player.getLevel()); // Default level
    }

    @Test
    void testDefaultConstructor() {
        // Act
        Player player = new Player();

        // Assert
        assertEquals(0, player.getId());
        assertNull(player.getUsername());
        assertNull(player.getPassword());
        assertEquals(1, player.getLevel());
    }

    @Test
    void testSetLevel() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 1);

        // Act
        player.setLevel(5);

        // Assert
        assertEquals(5, player.getLevel());
    }

    @Test
    void testSetEmail() {
        // Arrange
        Player player = new Player(1, "testuser", "password123");

        // Act
        player.setEmail("newemail@example.com");

        // Assert
        assertEquals("newemail@example.com", player.getEmail());
    }

    @Test
    void testSetDisplayName() {
        // Arrange
        Player player = new Player(1, "testuser", "password123");

        // Act
        player.setDisplayName("New Display Name");

        // Assert
        assertEquals("New Display Name", player.getDisplayName());
    }

    @Test
    void testSetRegistrationDate() {
        // Arrange
        Player player = new Player(1, "testuser", "password123");
        LocalDateTime newDate = LocalDateTime.of(2024, 1, 1, 12, 0);

        // Act
        player.setRegistrationDate(newDate);

        // Assert
        assertEquals(newDate, player.getRegistrationDate());
    }

    @Test
    void testSetLastLoginDate() {
        // Arrange
        Player player = new Player(1, "testuser", "password123");
        LocalDateTime loginDate = LocalDateTime.now();

        // Act
        player.setLastLoginDate(loginDate);

        // Assert
        assertEquals(loginDate, player.getLastLoginDate());
    }

    @Test
    void testLevelUp_Success() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 5);

        // Act
        boolean result = player.levelUp();

        // Assert
        assertTrue(result);
        assertEquals(6, player.getLevel());
    }

    @Test
    void testLevelUp_MultipleTimesSuccess() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 8);

        // Act & Assert
        assertTrue(player.levelUp()); // 8 -> 9
        assertEquals(9, player.getLevel());
        
        assertTrue(player.levelUp()); // 9 -> 10
        assertEquals(10, player.getLevel());
    }

    @Test
    void testLevelUp_AtMaxLevel() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 10);

        // Act
        boolean result = player.levelUp();

        // Assert
        assertFalse(result);
        assertEquals(10, player.getLevel()); // Level should stay at 10
    }

    @Test
    void testToString() {
        // Arrange
        LocalDateTime regDate = LocalDateTime.of(2024, 1, 1, 12, 0);
        Player player = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            null
        );

        // Act
        String result = player.toString();

        // Assert
        assertTrue(result.contains("id=1"));
        assertTrue(result.contains("username='testuser'"));
        assertTrue(result.contains("displayName='Test User'"));
        assertTrue(result.contains("email='test@example.com'"));
        assertTrue(result.contains("level=5"));
    }

    @Test
    void testEquals_SameObject() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 5);

        // Act & Assert
        assertEquals(player, player);
    }

    @Test
    void testEquals_EqualPlayers() {
        // Arrange
        LocalDateTime regDate = LocalDateTime.of(2024, 1, 1, 12, 0);
        Player player1 = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            null
        );
        Player player2 = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            null
        );

        // Act & Assert
        assertEquals(player1, player2);
    }

    @Test
    void testEquals_DifferentPlayers() {
        // Arrange
        Player player1 = new Player(1, "testuser1", "password123", 5);
        Player player2 = new Player(2, "testuser2", "password456", 3);

        // Act & Assert
        assertNotEquals(player1, player2);
    }

    @Test
    void testEquals_DifferentId() {
        // Arrange
        Player player1 = new Player(1, "testuser", "password123", 5);
        Player player2 = new Player(2, "testuser", "password123", 5);

        // Act & Assert
        assertNotEquals(player1, player2);
    }

    @Test
    void testEquals_DifferentLevel() {
        // Arrange
        Player player1 = new Player(1, "testuser", "password123", 5);
        Player player2 = new Player(1, "testuser", "password123", 3);

        // Act & Assert
        assertNotEquals(player1, player2);
    }

    @Test
    void testEquals_Null() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 5);

        // Act & Assert
        assertNotEquals(player, null);
    }

    @Test
    void testEquals_DifferentClass() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 5);
        String notAPlayer = "not a player";

        // Act & Assert
        assertNotEquals(player, notAPlayer);
    }

    @Test
    void testHashCode_EqualPlayers() {
        // Arrange
        LocalDateTime regDate = LocalDateTime.of(2024, 1, 1, 12, 0);
        Player player1 = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            null
        );
        Player player2 = new Player(
            1,
            "testuser",
            "password123",
            5,
            "test@example.com",
            "Test User",
            regDate,
            null
        );

        // Act & Assert
        assertEquals(player1.hashCode(), player2.hashCode());
    }

    @Test
    void testHashCode_DifferentPlayers() {
        // Arrange
        Player player1 = new Player(1, "testuser1", "password123", 5);
        Player player2 = new Player(2, "testuser2", "password456", 3);

        // Act & Assert
        assertNotEquals(player1.hashCode(), player2.hashCode());
    }

    @Test
    void testHashCode_Consistency() {
        // Arrange
        Player player = new Player(1, "testuser", "password123", 5);

        // Act
        int hashCode1 = player.hashCode();
        int hashCode2 = player.hashCode();

        // Assert
        assertEquals(hashCode1, hashCode2);
    }
}
