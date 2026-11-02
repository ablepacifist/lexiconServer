package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link PlayerController}, which delegates everything to {@link PlayerManagerService}. */
class PlayerControllerTest {

    private PlayerController controller;
    private PlayerManagerService playerManager;

    @BeforeEach
    void setUp() {
        controller = new PlayerController();
        playerManager = mock(PlayerManagerService.class);
        ReflectionTestUtils.setField(controller, "playerManagerService", playerManager);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(int id) {
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.user(id, "user" + id));
    }

    // ------------------------------------------------------------------ read

    @Test
    void testGetAllPlayers_Success() {
        Player player1 = new Player(1, "user1", "pass1", 5);
        Player player2 = new Player(2, "user2", "pass2", 3);
        when(playerManager.getAllPlayers()).thenReturn(Arrays.asList(player1, player2));

        ResponseEntity<Collection<Player>> response = controller.getAllPlayers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        verify(playerManager).getAllPlayers();
    }

    @Test
    void testGetAllPlayers_Exception() {
        when(playerManager.getAllPlayers()).thenThrow(new RuntimeException("Database error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getAllPlayers().getStatusCode());
    }

    @Test
    void testGetPlayerById_Found() {
        Player player = new Player(1, "user1", "pass1", 5);
        when(playerManager.getPlayerById(1)).thenReturn(player);

        ResponseEntity<Player> response = controller.getPlayerById(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(player, response.getBody());
    }

    @Test
    void testGetPlayerById_NotFound() {
        when(playerManager.getPlayerById(999)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.getPlayerById(999).getStatusCode());
    }

    @Test
    void testGetPlayerById_Exception() {
        when(playerManager.getPlayerById(anyInt())).thenThrow(new RuntimeException("Database error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getPlayerById(1).getStatusCode());
    }

    @Test
    void testGetPlayerByUsername_Found() {
        Player player = new Player(1, "testuser", "pass", 5);
        when(playerManager.getPlayerByUsername("testuser")).thenReturn(player);

        ResponseEntity<Player> response = controller.getPlayerByUsername("testuser");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(player, response.getBody());
    }

    @Test
    void testGetPlayerByUsername_NotFound() {
        when(playerManager.getPlayerByUsername("nobody")).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.getPlayerByUsername("nobody").getStatusCode());
    }

    @Test
    void testGetPlayerByUsername_Exception() {
        when(playerManager.getPlayerByUsername(anyString())).thenThrow(new RuntimeException("Database error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getPlayerByUsername("x").getStatusCode());
    }

    // -------------------------------------------------------------- register

    @Test
    void testRegisterPlayer_Success() {
        Player created = new Player(10, "newuser", "hash", 1);
        when(playerManager.registerPlayer("newuser", "password123", "new@example.com", "New User")).thenReturn(created);
        Map<String, String> request = new HashMap<>();
        request.put("username", "newuser");
        request.put("password", "password123");
        request.put("email", "new@example.com");
        request.put("displayName", "New User");

        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals(10, response.getBody().get("playerId"));
        assertEquals("newuser", response.getBody().get("username"));
    }

    @Test
    void testRegisterPlayer_MissingUsername() {
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(Map.of("password", "pw"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Username and password are required", response.getBody().get("error"));
        verifyNoInteractions(playerManager);
    }

    @Test
    void testRegisterPlayer_MissingPassword() {
        ResponseEntity<Map<String, Object>> response = controller.registerPlayer(Map.of("username", "someone"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(playerManager);
    }

    @Test
    void testRegisterPlayer_EmptyUsername() {
        Map<String, String> request = Map.of("username", "   ", "password", "pw");

        assertEquals(HttpStatus.BAD_REQUEST, controller.registerPlayer(request).getStatusCode());
        verifyNoInteractions(playerManager);
    }

    @Test
    void testRegisterPlayer_UsernameExists() {
        when(playerManager.registerPlayer(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        ResponseEntity<Map<String, Object>> response =
                controller.registerPlayer(Map.of("username", "taken", "password", "pw"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Username already exists", response.getBody().get("error"));
    }

    @Test
    void testRegisterPlayer_MinimalFields() {
        when(playerManager.registerPlayer(eq("minimal"), eq("pw"), eq("minimal@lexicon.local"), isNull()))
                .thenReturn(new Player(11, "minimal", "hash", 1));

        ResponseEntity<Map<String, Object>> response =
                controller.registerPlayer(Map.of("username", "minimal", "password", "pw"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(playerManager).registerPlayer("minimal", "pw", "minimal@lexicon.local", null);
    }

    @Test
    void testRegisterPlayer_DatabaseException() {
        when(playerManager.registerPlayer(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<Map<String, Object>> response =
                controller.registerPlayer(Map.of("username", "u", "password", "pw"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----------------------------------------------------------------- login

    @Test
    void testLoginPlayer_Success_BCryptPassword() {
        Player player = new Player(1, "alice", "$2a$10$hashhashhashhashhashhash", 4);
        player.setEmail("alice@example.com");
        player.setDisplayName("Alice");
        when(playerManager.authenticatePlayer("alice", "secret")).thenReturn(player);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "alice", "password", "secret"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().get("player");
        assertEquals("Alice", body.get("displayName"));
        assertEquals("alice@example.com", body.get("email"));
        assertFalse(body.containsKey("password"), "the password must never be part of a login response");
    }

    @Test
    void testLoginPlayer_Success_PlainTextPassword() {
        // Upgrading a legacy plain-text password to a hash is PlayerManager's job (see PlayerManagerTest);
        // the controller only sees an authenticated player.
        Player player = new Player(2, "legacy", "oldplain", 1);
        when(playerManager.authenticatePlayer("legacy", "oldplain")).thenReturn(player);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "legacy", "password", "oldplain"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testLoginPlayer_InvalidUsername() {
        when(playerManager.authenticatePlayer("ghost", "pw")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "ghost", "password", "pw"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid username or password", response.getBody().get("error"));
    }

    @Test
    void testLoginPlayer_InvalidPassword() {
        when(playerManager.authenticatePlayer("alice", "wrong")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "alice", "password", "wrong"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testLoginPlayer_MissingCredentials() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.loginPlayer(Map.of("username", "alice")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.loginPlayer(Map.of("password", "pw")).getStatusCode());
        verifyNoInteractions(playerManager);
    }

    @Test
    void testLoginPlayer_DatabaseException() {
        when(playerManager.authenticatePlayer(anyString(), anyString())).thenThrow(new RuntimeException("Database error"));

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "alice", "password", "pw"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testLoginPlayer_WithNullEmail() {
        Player player = new Player(3, "noemail", "hash", 2);
        player.setDisplayName("No Email");
        when(playerManager.authenticatePlayer("noemail", "pw")).thenReturn(player);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "noemail", "password", "pw"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().get("player");
        assertEquals("", body.get("email"));
    }

    @Test
    void testLoginPlayer_WithNullDisplayName() {
        Player player = new Player(4, "nodisplay", "hash", 2);
        player.setEmail("n@example.com");
        when(playerManager.authenticatePlayer("nodisplay", "pw")).thenReturn(player);

        ResponseEntity<Map<String, Object>> response =
                controller.loginPlayer(Map.of("username", "nodisplay", "password", "pw"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().get("player");
        assertEquals("nodisplay", body.get("displayName"), "falls back to the username");
    }

    // ---------------------------------------------------------------- update

    @Test
    void testUpdatePlayer_PlayerExists() {
        when(playerManager.getPlayerById(1)).thenReturn(new Player(1, "user1", "pass1", 5));

        ResponseEntity<Map<String, Object>> response = controller.updatePlayer(1, Map.of("displayName", "New"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("playerId"));
    }

    @Test
    void testUpdatePlayer_PlayerNotFound() {
        when(playerManager.getPlayerById(999)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.updatePlayer(999, Map.of()).getStatusCode());
    }

    @Test
    void testUpdatePlayer_Exception() {
        when(playerManager.getPlayerById(anyInt())).thenThrow(new RuntimeException("Database error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.updatePlayer(1, Map.of()).getStatusCode());
    }

    // ---------------------------------------------------------------- delete

    @Test
    void testDeletePlayer_PlayerExists() {
        signInAs(1);
        when(playerManager.deletePlayer(1)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.deletePlayer(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("playerId"));
        verify(playerManager).deletePlayer(1);
    }

    @Test
    void testDeletePlayer_PlayerNotFound() {
        signInAs(999);
        when(playerManager.deletePlayer(999)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, controller.deletePlayer(999).getStatusCode());
    }

    @Test
    void testDeletePlayer_Exception() {
        signInAs(1);
        when(playerManager.deletePlayer(anyInt())).thenThrow(new RuntimeException("Database error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.deletePlayer(1).getStatusCode());
    }

    @Test
    void testDeletePlayer_OnlyTheOwnerMayDelete() {
        signInAs(2);

        assertEquals(HttpStatus.FORBIDDEN, controller.deletePlayer(1).getStatusCode());
        verify(playerManager, never()).deletePlayer(anyInt());
    }
}
