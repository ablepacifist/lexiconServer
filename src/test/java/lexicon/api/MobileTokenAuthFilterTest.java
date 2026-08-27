package lexicon.api;

import lexicon.data.HSQLMobileTokenDatabase;
import lexicon.logic.MobileTokenManager;
import lexicon.logic.MobileTokenService;
import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import lexicon.object.Player;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level tests for the Android app's bearer-token auth path.
 *
 * Wires the real {@link MobileTokenAuthFilter} in front of the real
 * {@link AuthController} via standalone MockMvc, with the player lookup mocked
 * and tokens stored in an in-memory HSQLDB — so this exercises the actual
 * request path without a running server or the shared database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobileTokenAuthFilterTest {

    private static final String TEST_DB_URL = "jdbc:hsqldb:mem:mobilefiltertest";
    private static final int USER_ID = 777;
    private static final String USERNAME = "mobiletester";

    private String previousDbUrl;
    private MockMvc mvc;
    private HSQLMobileTokenDatabase database;
    private MobileTokenService mobileTokenService;
    private PlayerManagerService playerManagerService;

    @BeforeAll
    void setup() {
        previousDbUrl = System.getProperty("database.url");
        System.setProperty("database.url", TEST_DB_URL);

        database = new HSQLMobileTokenDatabase();
        database.initializeSchema();
        mobileTokenService = new MobileTokenManager(database);

        Player player = new Player(USER_ID, USERNAME, "irrelevant", 3);
        player.setEmail("mobile@lexicon.local");
        player.setDisplayName("Mobile Tester");

        playerManagerService = mock(PlayerManagerService.class);
        when(playerManagerService.getPlayerById(USER_ID)).thenReturn(player);
        when(playerManagerService.authenticatePlayer(eq(USERNAME), anyString())).thenReturn(player);

        RememberMeManagerService rememberMe = mock(RememberMeManagerService.class);
        when(rememberMe.validateAndRotate(any())).thenReturn(null);

        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "playerManagerService", playerManagerService);
        ReflectionTestUtils.setField(controller, "rememberMeManagerService", rememberMe);
        ReflectionTestUtils.setField(controller, "mobileTokenService", mobileTokenService);

        MobileTokenAuthFilter filter =
                new MobileTokenAuthFilter(mobileTokenService, playerManagerService);

        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(filter).build();
    }

    @AfterAll
    void restore() {
        if (previousDbUrl == null) {
            System.clearProperty("database.url");
        } else {
            System.setProperty("database.url", previousDbUrl);
        }
    }

    @BeforeEach
    void clean() {
        mobileTokenService.clearTokens(USER_ID);
    }

    private String loginAsMobile() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"pw\",\"platform\":\"mobile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileToken").exists())
                .andReturn().getResponse().getContentAsString();

        int start = body.indexOf("\"mobileToken\":\"") + "\"mobileToken\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    void mobileLoginIssuesABearerToken() throws Exception {
        String token = loginAsMobile();
        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isBlank());
        Assertions.assertEquals(USER_ID, mobileTokenService.validateAndRotate(token).userId());
    }

    @Test
    void webLoginIsUnchangedAndIssuesNoToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.mobileToken").doesNotExist());
    }

    @Test
    void bearerTokenAuthenticatesWithNoCookieOrSession() throws Exception {
        String token = loginAsMobile();

        // No session, no cookie — exactly what the Android WebView sends
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void bearerTokenIsReusableAcrossRequests() throws Exception {
        String token = loginAsMobile();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(USER_ID));
        }
    }

    @Test
    void garbageBearerTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutAuthIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatedTokenIsReturnedOnTheResponseHeader() throws Exception {
        // Seed a token inside the renewal window (90d lifetime, renews under 45d left)
        String oldRaw = "nearly-expired-token-for-filter-test";
        database.storeToken(USER_ID, sha256(oldRaw), LocalDateTime.now().plusDays(5));

        String rotated = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldRaw))
                .andExpect(status().isOk())
                .andExpect(header().exists(MobileTokenAuthFilter.REFRESHED_TOKEN_HEADER))
                .andReturn().getResponse().getHeader(MobileTokenAuthFilter.REFRESHED_TOKEN_HEADER);

        Assertions.assertNotNull(rotated);
        Assertions.assertNotEquals(oldRaw, rotated);

        // The replacement works and the old one is dead
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + rotated))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldRaw))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void freshTokenDoesNotSetTheRotationHeader() throws Exception {
        String token = loginAsMobile();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(MobileTokenAuthFilter.REFRESHED_TOKEN_HEADER));
    }

    @Test
    void deletedUserGetsTheirTokensRevoked() throws Exception {
        String token = loginAsMobile();
        when(playerManagerService.getPlayerById(anyInt())).thenReturn(null);
        try {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            Assertions.assertNull(mobileTokenService.validateAndRotate(token),
                    "tokens for a deleted user must be cleared");
        } finally {
            when(playerManagerService.getPlayerById(USER_ID))
                    .thenReturn(new Player(USER_ID, USERNAME, "irrelevant", 3));
        }
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
