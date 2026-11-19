package lexicon.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of this class is that a published APK is served without restarting the server, so
 * the test that matters is {@link #picksUpAChangedFileWithoutRestarting()}.
 */
class AppUpdateSettingsTest {

    @TempDir
    Path tmp;

    private AppUpdateSettings settingsFor(Path envFile) {
        return new AppUpdateSettings(envFile.toString());
    }

    private void write(Path envFile, String body) throws IOException {
        Files.writeString(envFile, body);
        // Force a distinct mtime: the cache is keyed on it, and a test can rewrite a file
        // inside the filesystem's timestamp granularity.
        Files.setLastModifiedTime(envFile, FileTime.fromMillis(System.currentTimeMillis()));
    }

    @Test
    void readsValuesFromTheEnvFile() throws IOException {
        Path env = tmp.resolve(".env");
        write(env, """
                # a comment
                APP_UPDATE_VERSION_CODE=8
                APP_UPDATE_VERSION_NAME=0.7.1
                APP_UPDATE_CRITICAL=true
                """);

        AppUpdateSettings settings = settingsFor(env);
        assertEquals(8L, settings.getLong("APP_UPDATE_VERSION_CODE", 1L));
        assertEquals("0.7.1", settings.get("APP_UPDATE_VERSION_NAME", "0.1.0"));
        assertTrue(settings.getBoolean("APP_UPDATE_CRITICAL", false));
    }

    @Test
    void picksUpAChangedFileWithoutRestarting() throws IOException {
        Path env = tmp.resolve(".env");
        write(env, "APP_UPDATE_VERSION_CODE=7\nAPP_UPDATE_VERSION_NAME=0.6.0\n");

        AppUpdateSettings settings = settingsFor(env);
        assertEquals(7L, settings.getLong("APP_UPDATE_VERSION_CODE", 1L));

        // What the publish pipeline does to the file.
        write(env, "APP_UPDATE_VERSION_CODE=8\nAPP_UPDATE_VERSION_NAME=0.7.1\n");

        assertEquals(8L, settings.getLong("APP_UPDATE_VERSION_CODE", 1L),
                "a rewritten .env must be visible without recreating the bean");
        assertEquals("0.7.1", settings.get("APP_UPDATE_VERSION_NAME", "0.1.0"));
    }

    @Test
    void fallsBackWhenTheFileIsMissing() {
        AppUpdateSettings settings = settingsFor(tmp.resolve("does-not-exist.env"));
        assertEquals(42L, settings.getLong("APP_UPDATE_VERSION_CODE", 42L));
        assertEquals("0.1.0", settings.get("APP_UPDATE_VERSION_NAME", "0.1.0"));
        assertFalse(settings.getBoolean("APP_UPDATE_CRITICAL", false));
    }

    @Test
    void fallsBackWhenTheKeyIsAbsentOrBlank() throws IOException {
        Path env = tmp.resolve(".env");
        write(env, "SOMETHING_ELSE=1\nAPP_UPDATE_VERSION_NAME=\n");

        AppUpdateSettings settings = settingsFor(env);
        assertEquals(5L, settings.getLong("APP_UPDATE_VERSION_CODE", 5L));
        assertEquals("0.1.0", settings.get("APP_UPDATE_VERSION_NAME", "0.1.0"));
    }

    @Test
    void stripsQuotesAndKeepsServingOnABadVersionCode() throws IOException {
        Path env = tmp.resolve(".env");
        // "0.7.1" is exactly the mistake that broke the last publish: a version *name* typed
        // into the version *code*. It must not take the endpoint down.
        write(env, """
                APP_UPDATE_VERSION_CODE=0.7.1
                APP_UPDATE_VERSION_NAME="0.7.1"
                APP_UPDATE_CHANGELOG='quoted the other way'
                """);

        AppUpdateSettings settings = settingsFor(env);
        assertEquals(7L, settings.getLong("APP_UPDATE_VERSION_CODE", 7L),
                "a non-numeric version code falls back instead of throwing");
        assertEquals("0.7.1", settings.get("APP_UPDATE_VERSION_NAME", "x"));
        assertEquals("quoted the other way", settings.get("APP_UPDATE_CHANGELOG", ""));
    }
}
