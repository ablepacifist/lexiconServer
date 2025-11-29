package lexicon.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Initializes the database schema for Lexicon media files
 * Runs automatically on application startup
 */
@Component
public class DatabaseInitializer {
    
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";
    
    @PostConstruct
    public void initializeSchema() {
        System.out.println("Initializing Lexicon media database schema...");
        try {
            // Read schema.sql from resources
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String sql;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }
            
            // Execute schema creation
            try (Connection conn = DriverManager.getConnection(DATABASE_URL, "SA", "");
                 Statement stmt = conn.createStatement()) {
                
                // Split by semicolon and execute each statement
                String[] statements = sql.split(";");
                for (String statement : statements) {
                    statement = statement.trim();
                    if (!statement.isEmpty() && !statement.startsWith("--")) {
                        stmt.execute(statement);
                    }
                }
                
                System.out.println("✓ Lexicon media database schema initialized successfully");
            }
            
        } catch (Exception e) {
            System.err.println("⚠ Warning: Could not initialize database schema: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - let the application start anyway
        }
    }
}
