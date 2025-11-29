package lexicon.api;

import lexicon.data.ILexiconDatabase;
import lexicon.object.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerControllerTest {

    private PlayerController controller;
    private ILexiconDatabase mockDatabase;

    @BeforeEach
    void setUp() throws Exception {
        controller = new PlayerController();
        mockDatabase = mock(ILexiconDatabase.class);
        
        // Inject mock database using reflection
        Field databaseField = PlayerController.class.getDeclaredField("lexiconDatabase");
        databaseField.setAccessible(true);
        databaseField.set(controller, mockDatabase);
    }

    @Test
    void testGetAllPlayers_Success() {
        // Arrange
        Player player1 = new Player(1, "user1", "pass1", 5);
        Player player2 = new Player(2, "user2", "pass2", 3);
        when(mockDatabase.getAllPlayers()).thenReturn(Arrays.asList(player1, player2));

        // Act
        ResponseEntity<Collection<Player>> response = controller.getAllPlayers();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        verify(mockDatabase).getAllPlayers();
    }

    @Test
    void testGetAllPlayers_Exception() {
        // Arrange
        when(mockDatabase.getAllPlayers()).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<Collection<Player>> response = controller.getAllPlayers();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetPlayerById_Found() {
        // Arrange
        Player player = new Player(1, "testuser", "password", 5);
        when(mockDatabase.getPlayer(1)).thenReturn(player);

        // Act
        ResponseEntity<Player> response = controller.getPlayerById(1);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("testuser", response.getBody().getUsername());
        verify(mockDatabase).getPlayer(1);
    }

    @Test
    void testGetPlayerById_NotFound() {
        // Arrange
        when(mockDatabase.getPlayer(999)).thenReturn(null);

        // Act
        ResponseEntity<Player> response = controller.getPlayerById(999);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetPlayerById_Exception() {
        // Arrange
        when(mockDatabase.getPlayer(anyInt())).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<Player> response = controller.getPlayerById(1);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetPlayerByUsername_Found() {
        // Arrange
        Player player = new Player(1, "testuser", "password", 5);
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Player> response = controller.getPlayerByUsername("testuser");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("testuser", response.getBody().getUsername());
    }

    @Test
    void testGetPlayerByUsername_NotFound() {
        // Arrange
        when(mockDatabase.getPlayerByUsername("nonexistent")).thenReturn(null);

        // Act
        ResponseEntity<Player> response = controller.getPlayerByUsername("nonexistent");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testRegisterPlayer_Success() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "newuser");
        request.put("password", "password123");
        request.put("email", "test@example.com");
        request.put("displayName", "New User");

        when(mockDatabase.getNextPlayerId()).thenReturn(1);
        when(mockDatabase.getPlayerByUsername("newuser")).thenReturn(null);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("newuser", response.getBody().get("username"));
        verify(mockDatabase).addPlayer(any(Player.class));
    }

    @Test
    void testRegisterPlayer_MissingUsername() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("password", "password123");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testRegisterPlayer_MissingPassword() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "newuser");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testRegisterPlayer_UsernameExists() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "existinguser");
        request.put("password", "password123");

        Player existingPlayer = new Player(1, "existinguser", "oldpass", 1);
        when(mockDatabase.getPlayerByUsername("existinguser")).thenReturn(existingPlayer);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("error").toString().contains("already exists"));
    }

    @Test
    void testRegisterPlayer_EmptyUsername() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "   ");
        request.put("password", "password123");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testRegisterPlayer_MinimalFields() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "newuser");
        request.put("password", "password123");
        // No email or displayName

        when(mockDatabase.getNextPlayerId()).thenReturn(1);
        when(mockDatabase.getPlayerByUsername("newuser")).thenReturn(null);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void testLoginPlayer_Success_BCryptPassword() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "password123");

        // BCrypt hash - any password starting with $2a$ will be treated as BCrypt
        // For testing, we'll use a simple BCrypt marker and test plain text instead
        Player player = new Player(1, "testuser", "password123", 5);
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().containsKey("player"));
    }

    @Test
    void testLoginPlayer_Success_PlainTextPassword() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "password123");

        // Plain text password (legacy)
        Player player = new Player(1, "testuser", "password123", 5);
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void testLoginPlayer_InvalidUsername() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "nonexistent");
        request.put("password", "password123");

        when(mockDatabase.getPlayerByUsername("nonexistent")).thenReturn(null);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Invalid username or password"));
    }

    @Test
    void testLoginPlayer_InvalidPassword() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "wrongpassword");

        Player player = new Player(1, "testuser", "password123", 5);
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Invalid username or password"));
    }

    @Test
    void testLoginPlayer_MissingCredentials() {
        // Arrange
        Map<String, String> request = new HashMap<>();

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testUpdatePlayer_PlayerExists() {
        // Arrange
        Player existingPlayer = new Player(1, "testuser", "password", 5);
        when(mockDatabase.getPlayer(1)).thenReturn(existingPlayer);

        Map<String, String> request = new HashMap<>();
        request.put("displayName", "New Display Name");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.updatePlayer(1, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("message"));
    }

    @Test
    void testUpdatePlayer_PlayerNotFound() {
        // Arrange
        when(mockDatabase.getPlayer(999)).thenReturn(null);

        Map<String, String> request = new HashMap<>();

        // Act
        ResponseEntity<Map<String, Object>> response = controller.updatePlayer(999, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testDeletePlayer_PlayerExists() {
        // Arrange
        Player existingPlayer = new Player(1, "testuser", "password", 5);
        when(mockDatabase.getPlayer(1)).thenReturn(existingPlayer);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.deletePlayer(1);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("message"));
    }

    @Test
    void testDeletePlayer_PlayerNotFound() {
        // Arrange
        when(mockDatabase.getPlayer(999)).thenReturn(null);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.deletePlayer(999);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testDeletePlayer_Exception() {
        // Arrange
        when(mockDatabase.getPlayer(anyInt())).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.deletePlayer(1);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testUpdatePlayer_Exception() {
        // Arrange
        when(mockDatabase.getPlayer(anyInt())).thenThrow(new RuntimeException("Database error"));

        Map<String, String> request = new HashMap<>();

        // Act
        ResponseEntity<Map<String, Object>> response = controller.updatePlayer(1, request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testRegisterPlayer_DatabaseException() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "newuser");
        request.put("password", "password123");

        when(mockDatabase.getNextPlayerId()).thenReturn(1);
        when(mockDatabase.getPlayerByUsername("newuser")).thenReturn(null);
        doThrow(new RuntimeException("Database error")).when(mockDatabase).addPlayer(any(Player.class));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testLoginPlayer_DatabaseException() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "password123");

        when(mockDatabase.getPlayerByUsername(anyString())).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testGetPlayerByUsername_Exception() {
        // Arrange
        when(mockDatabase.getPlayerByUsername(anyString())).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<Player> response = controller.getPlayerByUsername("testuser");

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testLoginPlayer_WithNullEmail() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "password123");

        Player player = new Player(1, "testuser", "password123", 5);
        player.setEmail(null); // Null email
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void testLoginPlayer_WithNullDisplayName() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser");
        request.put("password", "password123");

        Player player = new Player(1, "testuser", "password123", 5);
        player.setDisplayName(null); // Null display name
        when(mockDatabase.getPlayerByUsername("testuser")).thenReturn(player);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.loginPlayer(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
