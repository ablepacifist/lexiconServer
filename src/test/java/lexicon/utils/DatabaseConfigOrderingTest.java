package lexicon.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves lexicon.utils.DatabaseConfig's ordering: system property
 * "DATABASE_URL" beats the env var, which beats the one literal default -
 * without opening any JDBC connection (this class never touches
 * java.sql.*).
 *
 * "beats the env var" is proven by forking a real child JVM with
 * ProcessBuilder.environment() rather than mutating this process's own
 * (immutable) environment map - no reflection, no new dependency.
 */
class DatabaseConfigOrderingTest {

    private static final String DEFAULT_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";

    private String savedSystemProperty;

    @BeforeEach
    void save() {
        savedSystemProperty = System.getProperty("DATABASE_URL");
        System.clearProperty("DATABASE_URL");
    }

    @AfterEach
    void restore() {
        if (savedSystemProperty != null) {
            System.setProperty("DATABASE_URL", savedSystemProperty);
        } else {
            System.clearProperty("DATABASE_URL");
        }
    }

    @Test
    void systemPropertyWinsOverEverything() {
        System.setProperty("DATABASE_URL", "jdbc:hsqldb:hsql://sysprop-host:1111/sysdb");
        assertEquals("jdbc:hsqldb:hsql://sysprop-host:1111/sysdb", DatabaseConfig.url());
    }

    @Test
    void envVarWinsOverLiteralDefault_inFreshProcessWithNoSystemProperty() throws IOException, InterruptedException {
        String output = runProbe("jdbc:hsqldb:hsql://env-host:2222/envdb");
        assertEquals("jdbc:hsqldb:hsql://env-host:2222/envdb", output);
    }

    @Test
    void literalDefaultUsed_inFreshProcessWithNeitherOverrideSet() throws IOException, InterruptedException {
        String output = runProbe(null);
        assertEquals(DEFAULT_URL, output);
    }

    /** Forks `java -cp <this test classpath> lexicon.utils.DatabaseConfigProbeMain`. */
    private String runProbe(String envVarValueOrNull) throws IOException, InterruptedException {
        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin"
                + java.io.File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classpath, DatabaseConfigProbeMain.class.getName());
        pb.environment().remove("DATABASE_URL"); // start clean regardless of the host machine's own env
        if (envVarValueOrNull != null) {
            pb.environment().put("DATABASE_URL", envVarValueOrNull);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "probe subprocess failed (exit " + exitCode + "):\n" + output);
        return output.trim();
    }
}
