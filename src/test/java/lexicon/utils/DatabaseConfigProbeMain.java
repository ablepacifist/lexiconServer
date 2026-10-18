package lexicon.utils;

/**
 * Test-only helper: prints {@link DatabaseConfig#url()} to stdout so
 * {@code DatabaseConfigOrderingTest} can fork a fresh JVM with a real OS
 * environment variable set (via ProcessBuilder.environment()) and observe
 * the result - the only reliable, non-reflective way to test real env var
 * precedence, since the current process's own environment is immutable.
 * Not used by the application; lives in src/test only.
 */
public final class DatabaseConfigProbeMain {
    private DatabaseConfigProbeMain() {
    }

    public static void main(String[] args) {
        System.out.println(DatabaseConfig.url());
    }
}
