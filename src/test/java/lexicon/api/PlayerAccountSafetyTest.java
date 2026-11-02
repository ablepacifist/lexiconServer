package lexicon.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Player JSON never carries the password, and an account can only be deleted by its owner. */
class PlayerAccountSafetyTest {

    private PlayerManagerService players;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        players = mock(PlayerManagerService.class);
        PlayerController controller = new PlayerController();
        ReflectionTestUtils.setField(controller, "playerManagerService", players);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(players.deletePlayer(anyInt())).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void playerJsonHasNoPasswordField() throws Exception {
        Player p = new Player(5, "tester", "$2a$10$abcdefghijklmnopqrstuv", 3);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(p);
        assertFalse(json.toLowerCase().contains("password"), json);
        assertTrue(json.contains("tester"));
    }

    @Test
    void ownerCanDeleteOwnAccount() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.user(5, "tester"));
        mvc.perform(delete("/api/players/5")).andExpect(status().isOk());
        verify(players).deletePlayer(5);
    }

    @Test
    void otherPlayerCannotDeleteSomeoneElse() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.user(6, "other"));
        mvc.perform(delete("/api/players/5")).andExpect(status().isForbidden());
        verify(players, never()).deletePlayer(anyInt());
    }

    @Test
    void serviceAndAnonymousCannotDelete() throws Exception {
        mvc.perform(delete("/api/players/5")).andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.service());
        mvc.perform(delete("/api/players/5")).andExpect(status().isForbidden());
        verify(players, never()).deletePlayer(anyInt());
    }
}
