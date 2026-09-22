package lexicon.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Makes the layered DATABASE_URL (module .env &lt; root .env &lt; real env
 * var, per {@code spring.config.import} in application.properties) visible
 * to the handful of classes that open JDBC connections directly with
 * System.getProperty/getenv instead of Spring DI - see
 * {@link lexicon.utils.DatabaseConfig}.
 *
 * EnvironmentPostProcessor implementations with no explicit Ordered run at
 * Ordered.LOWEST_PRECEDENCE, i.e. after Spring Boot's own
 * ConfigDataEnvironmentPostProcessor (Ordered.HIGHEST_PRECEDENCE + 10) has
 * already loaded application.properties and processed spring.config.import.
 * So by the time this runs, environment.getProperty("DATABASE_URL") already
 * reflects the fully layered value - and this still runs well before the
 * ApplicationContext is refreshed, i.e. before any @Repository/@Component
 * (HSQL*Database, DatabaseInitializer) is constructed. That is what "early
 * enough" means here; nothing later in startup would still be in time.
 *
 * Registered via META-INF/spring.factories - EnvironmentPostProcessor is
 * discovered through the classic SpringFactoriesLoader mechanism even on
 * Boot 3.1 (it runs before there is an ApplicationContext, so it predates
 * the AutoConfiguration.imports mechanism that replaced spring.factories
 * for auto-configuration classes).
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // A real system property (e.g. -DDATABASE_URL=...) already wins over
        // everything else; never clobber it.
        if (System.getProperty("DATABASE_URL") != null) {
            return;
        }
        String resolved = environment.getProperty("DATABASE_URL");
        if (resolved != null && !resolved.isBlank()) {
            System.setProperty("DATABASE_URL", resolved);
        }
    }
}
