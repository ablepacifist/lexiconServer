package lexicon.logic;

import lexicon.data.HSQLMobileTokenDatabase;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the mobile bearer-token lifecycle used by the Android app.
 *
 * Runs against an in-memory HSQLDB rather than the shared database, so it never
 * touches live data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobileTokenManagerTest {

    private static final String TEST_DB_URL = "jdbc:hsqldb:mem:mobiletokentest";
    private static final int TEST_USER_ID = 4242;

    private String previousDbUrl;
    private HSQLMobileTokenDatabase database;
    private MobileTokenManager manager;

    @BeforeAll
    void setup() {
        previousDbUrl = System.getProperty("database.url");
        System.setProperty("database.url", TEST_DB_URL);

        database = new HSQLMobileTokenDatabase();
        database.initializeSchema();
        manager = new MobileTokenManager(database);
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
        manager.clearTokens(TEST_USER_ID);
    }

    @Test
    void createdTokenAuthenticatesItsOwner() {
        String raw = manager.createToken(TEST_USER_ID);

        assertNotNull(raw);
        assertFalse(raw.isBlank());

        MobileTokenService.MobileTokenResult result = manager.validateAndRotate(raw);

        assertNotNull(result, "a freshly issued token must validate");
        assertEquals(TEST_USER_ID, result.userId());
    }

    @Test
    void tokenIsHashedAtRest() throws Exception {
        String raw = manager.createToken(TEST_USER_ID);

        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "SA", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT token_hash FROM mobile_tokens WHERE user_id = " + TEST_USER_ID)) {
            assertTrue(rs.next(), "token row should exist");
            String stored = rs.getString("token_hash");
            assertNotEquals(raw, stored, "raw token must never be stored");
            assertEquals(sha256(raw), stored);
        }
    }

    @Test
    void freshTokenIsReusableAndNotRotatedYet() {
        String raw = manager.createToken(TEST_USER_ID);

        // The app presents the same token on every request — it must keep working,
        // and must not rotate while it is nowhere near expiry (that would race
        // concurrent requests).
        for (int i = 0; i < 5; i++) {
            MobileTokenService.MobileTokenResult result = manager.validateAndRotate(raw);
            assertNotNull(result, "token must still validate on use #" + (i + 1));
            assertEquals(TEST_USER_ID, result.userId());
            assertNull(result.newRawToken(), "should not rotate a fresh token");
        }
    }

    @Test
    void garbageTokenIsRejected() {
        assertNull(manager.validateAndRotate("not-a-real-token"));
        assertNull(manager.validateAndRotate(""));
        assertNull(manager.validateAndRotate(null));
    }

    @Test
    void expiredTokenIsRejectedAndDiscarded() {
        String raw = "expired-raw-token";
        database.storeToken(TEST_USER_ID, sha256(raw), LocalDateTime.now().minusDays(1));

        assertNull(manager.validateAndRotate(raw), "expired token must not authenticate");
        assertNull(database.findByTokenHash(sha256(raw)), "expired token should be cleared out");
    }

    @Test
    void tokenNearingExpiryRotatesAndRetiresTheOldOne() {
        String oldRaw = "about-to-expire-raw-token";
        // Inside the renewal window (lifetime 90d, renew with <45d remaining)
        database.storeToken(TEST_USER_ID, sha256(oldRaw), LocalDateTime.now().plusDays(10));

        MobileTokenService.MobileTokenResult result = manager.validateAndRotate(oldRaw);

        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.userId());
        assertNotNull(result.newRawToken(), "token past the renewal threshold must rotate");
        assertNotEquals(oldRaw, result.newRawToken());

        assertNull(manager.validateAndRotate(oldRaw), "the old raw token must stop working");
        MobileTokenService.MobileTokenResult viaNew = manager.validateAndRotate(result.newRawToken());
        assertNotNull(viaNew, "the replacement token must work");
        assertEquals(TEST_USER_ID, viaNew.userId());
    }

    @Test
    void clearTokensEndsTheSession() {
        String raw = manager.createToken(TEST_USER_ID);
        assertNotNull(manager.validateAndRotate(raw));

        manager.clearTokens(TEST_USER_ID);

        assertNull(manager.validateAndRotate(raw), "logout must invalidate the bearer token");
    }

    @Test
    void tokensAreUniquePerIssue() {
        String a = manager.createToken(TEST_USER_ID);
        String b = manager.createToken(TEST_USER_ID);
        assertNotEquals(a, b);
        assertEquals(TEST_USER_ID, manager.validateAndRotate(a).userId());
        assertEquals(TEST_USER_ID, manager.validateAndRotate(b).userId());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
