package lexicon.utils;

/**
 * Single source for the HSQLDB JDBC URL used by the classes that open JDBC
 * connections directly (bypassing Spring DI): the ~13 HSQL*Database
 * repositories, DatabaseInitializer, and the standalone
 * lexicon.scripts.CreatePlaylistTables script.
 *
 * Resolution order:
 *   1. JVM system property "DATABASE_URL" (e.g. -DDATABASE_URL=...)
 *   2. process environment variable DATABASE_URL
 *   3. the literal default below
 *
 * When the app boots through Spring, {@link lexicon.config.DatabaseUrlEnvironmentPostProcessor}
 * resolves DATABASE_URL from the layered config (module .env < root .env <
 * real env var, via spring.config.import in application.properties) and
 * copies it into system property (1) before any bean is constructed - so a
 * value that exists only in a layered .env file still reaches these classes.
 * A real env var already satisfies (2) on its own without any Spring
 * involvement, which is what keeps this working for CreatePlaylistTables,
 * whose main() never starts Spring at all.
 */
public final class DatabaseConfig {

    /** Keep the literal default in exactly this one place. */
    private static final String DEFAULT_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";

    private DatabaseConfig() {
    }

    public static String url() {
        String systemProperty = System.getProperty("DATABASE_URL");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String env = System.getenv("DATABASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_URL;
    }
}
