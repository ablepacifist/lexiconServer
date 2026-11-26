package lexicon.api;

import lexicon.logic.PermissionManagerService;
import lexicon.logic.SsoTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Alchemy and the GTW bridge learn a caller's permissions from validate-token. */
class SsoControllerPermissionsTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SsoTokenService tokens = mock(SsoTokenService.class);
        when(tokens.validateToken("good")).thenReturn(new SsoTokenService.SsoValidationResult(1, "zx", null));
        PermissionManagerService permissions = mock(PermissionManagerService.class);
        when(permissions.describe(1)).thenReturn(Map.of("role", "ADMIN",
                "permissions", List.of("HOLDFAST_ADVANCE_TIME", "VOICE_ADMIN")));
        mvc = MockMvcBuilders.standaloneSetup(new SsoController(tokens, permissions)).build();
    }

    @Test
    void validTokenCarriesRoleAndPermissions() throws Exception {
        mvc.perform(post("/api/auth/sso/validate-token").contentType("application/json").content("{\"token\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("zx"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.permissions[0]").value("HOLDFAST_ADVANCE_TIME"));
    }

    @Test
    void invalidTokenIsStill401() throws Exception {
        mvc.perform(post("/api/auth/sso/validate-token").contentType("application/json").content("{\"token\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }
}
