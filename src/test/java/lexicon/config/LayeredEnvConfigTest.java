package lexicon.config;

import lexicon.utils.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the layered destination-config contract (module .env &lt; root
 * .env &lt; real process env var, per {@code spring.config.import} in
 * application.properties) using the exact same Spring mechanism the app
 * uses at runtime - {@code optional:file:<path>[.properties]} imports -
 * against real temp files, without ever starting the web server or
 * touching the real database.
 *
 * Safety: {@link MinimalConfig} is a bare {@code @Configuration} with no
 * {@code @ComponentScan}/{@code @SpringBootApplication}/
 * {@code @EnableAutoConfiguration}, so booting it never scans into
 * lexicon.data/lexicon.api - no {@code @Repository} (which would open a
 * JDBC connection) or {@code @RestController}/web server is ever created.
 * Only {@link DatabaseUrlEnvironmentPostProcessor} (registered globally via
 * META-INF/spring.factories) runs, which is pure System property/Environment
 * bookkeeping.
 *
 * Not run as part of the normal suite in the live project - committed
 * nowhere; exists only to verify this change in the scratchpad copy.
 */
class LayeredEnvConfigTest {

    @Configuration
    static class MinimalConfig {
    }

    @TempDir
    Path tempDir;

    private String savedDatabaseUrlSystemProperty;

    @BeforeEach
    void saveSystemProperty() {
        savedDatabaseUrlSystemProperty = System.getProperty("DATABASE_URL");
        System.clearProperty("DATABASE_URL");
    }

    @AfterEach
    void restoreSystemProperty() {
        if (savedDatabaseUrlSystemProperty != null) {
            System.setProperty("DATABASE_URL", savedDatabaseUrlSystemProperty);
        } else {
            System.clearProperty("DATABASE_URL");
        }
    }

    private Path writeEnvFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Forward-slash path (Windows gives backslashes from Path.toString()) - see test (d). */
    private static String forwardSlashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String importValue(Path modulePathOrNull, Path masterPathOrNull) {
        String modulePart = modulePathOrNull == null
                ? "optional:file:" + tempMissingPath() + "[.properties]"
                : "optional:file:" + forwardSlashes(modulePathOrNull) + "[.properties]";
        String masterPart = masterPathOrNull == null
                ? "optional:file:" + tempMissingPath() + "[.properties]"
                : "optional:file:" + forwardSlashes(masterPathOrNull) + "[.properties]";
        return modulePart + "," + masterPart;
    }

    private static String tempMissingPath() {
        return "C:/does/not/exist/" + System.nanoTime() + "/missing.env";
    }

    private ConfigurableApplicationContext bootMinimal(String springConfigImport) {
        return bootMinimal(springConfigImport, null);
    }

    private ConfigurableApplicationContext bootMinimal(String springConfigImport, ConfigurableEnvironment env) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(MinimalConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=" + springConfigImport);
        if (env != null) {
            builder.environment(env);
        }
        return builder.run();
    }

    // (a) later import wins: root/master .env overrides the module's own .env
    @Test
    void laterImportOverridesEarlier() throws IOException {
        Path moduleEnv = writeEnvFile("module.env", "TEST_LAYER_KEY=module-value");
        Path masterEnv = writeEnvFile("master.env", "TEST_LAYER_KEY=master-value");

        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(moduleEnv, masterEnv))) {
            assertEquals("master-value", ctx.getEnvironment().getProperty("TEST_LAYER_KEY"));
        }
    }

    // (b) real OS env vars still beat both files. A real Spring Boot process
    // reads OS env vars from the "systemEnvironment" PropertySource; we
    // substitute it with an equivalent in-memory source rather than
    // mutating the JVM's actual (immutable) environment map, which
    // exercises the identical precedence rule Spring applies at runtime.
    @Test
    void osEnvVarBeatsBothLayeredFiles() throws IOException {
        Path moduleEnv = writeEnvFile("module2.env", "TEST_LAYER_KEY=module-value");
        Path masterEnv = writeEnvFile("master2.env", "TEST_LAYER_KEY=master-value");

        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("TEST_LAYER_KEY", "real-env-var-value")));

        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(moduleEnv, masterEnv), env)) {
            assertEquals("real-env-var-value", ctx.getEnvironment().getProperty("TEST_LAYER_KEY"));
        }
    }

    // (c) does not break when neither file exists
    @Test
    void missingFilesDoNotBreakStartup() {
        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(null, null))) {
            assertNull(ctx.getEnvironment().getProperty("TEST_LAYER_KEY"));
        }
    }

    // (d) Windows paths with forward slashes survive. @TempDir yields a
    // Windows path with backslashes (e.g. C:\Users\...\Temp\junitNNNN); we
    // convert it to forward slashes (as the real ./.env and
    // ${MASTER_ENV_FILE:../.env} values must, per the contract's "forward
    // slashes in paths" rule) and confirm Spring still resolves the file.
    @Test
    void windowsPathWithForwardSlashesResolves() throws IOException {
        Path masterEnv = writeEnvFile("master3.env", "TEST_LAYER_KEY=forward-slash-value");
        String forwardSlashPath = forwardSlashes(masterEnv);
        assumeWindowsStyleAbsolutePath(forwardSlashPath);

        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(null, masterEnv))) {
            assertEquals("forward-slash-value", ctx.getEnvironment().getProperty("TEST_LAYER_KEY"));
        }
    }

    private static void assumeWindowsStyleAbsolutePath(String path) {
        // Documents the intent rather than skipping silently: on a non-Windows
        // CI box this would just be an ordinary absolute path, and the same
        // assertion still holds either way.
        if (path.contains("\\")) {
            throw new AssertionError("expected forward slashes only, got: " + path);
        }
    }

    // End-to-end: the layered DATABASE_URL specifically reaches
    // lexicon.utils.DatabaseConfig (used outside Spring DI) via
    // DatabaseUrlEnvironmentPostProcessor.
    @Test
    void environmentPostProcessorExposesLayeredDatabaseUrlToDatabaseConfig() throws IOException {
        Path moduleEnv = writeEnvFile("module4.env", "DATABASE_URL=jdbc:hsqldb:hsql://module-host:9002/mydb");
        Path masterEnv = writeEnvFile("master4.env", "DATABASE_URL=jdbc:hsqldb:hsql://master-host:9002/mydb");

        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(moduleEnv, masterEnv))) {
            assertEquals("jdbc:hsqldb:hsql://master-host:9002/mydb", System.getProperty("DATABASE_URL"));
            assertEquals("jdbc:hsqldb:hsql://master-host:9002/mydb", DatabaseConfig.url());
        }
    }

    // The post processor must never clobber a real -DDATABASE_URL=... that
    // was already set before Spring started.
    @Test
    void environmentPostProcessorNeverOverridesAnExplicitSystemProperty() throws IOException {
        System.setProperty("DATABASE_URL", "jdbc:hsqldb:hsql://explicit-sysprop-host:9002/mydb");
        Path masterEnv = writeEnvFile("master5.env", "DATABASE_URL=jdbc:hsqldb:hsql://master-host:9002/mydb");

        try (ConfigurableApplicationContext ctx = bootMinimal(importValue(null, masterEnv))) {
            assertEquals("jdbc:hsqldb:hsql://explicit-sysprop-host:9002/mydb", System.getProperty("DATABASE_URL"));
        }
    }
}
