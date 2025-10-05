package lexicon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Configuration class for database initialization
 */
@Configuration
public class DatabaseConfig {
    
    @Value("${database.url:jdbc:hsqldb:hsql://localhost:9002/mydb}")
    private String databaseUrl;
    
    /**
     * Initialize the database schema for lexicon if needed
     */
    @EventListener(ContextRefreshedEvent.class)
    public void initializeDatabase() {
        try {
            // Try to connect and create tables if they don't exist
            Connection conn = DriverManager.getConnection(databaseUrl, "SA", "");
            Statement stmt = conn.createStatement();
            
            // Create media_files table if it doesn't exist
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS media_files (
                    id INTEGER PRIMARY KEY,
                    filename VARCHAR(255) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    content_type VARCHAR(100) NOT NULL,
                    file_size BIGINT NOT NULL,
                    file_path VARCHAR(500) NOT NULL,
                    uploaded_by INTEGER NOT NULL,
                    upload_date TIMESTAMP NOT NULL,
                    title VARCHAR(200),
                    description TEXT,
                    is_public BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (uploaded_by) REFERENCES players(id)
                )
            """);
            
            // Add indexes
            try {
                stmt.execute("CREATE INDEX idx_media_files_uploaded_by ON media_files(uploaded_by)");
            } catch (Exception e) {
                // Index might already exist
            }
            
            try {
                stmt.execute("CREATE INDEX idx_media_files_public ON media_files(is_public)");
            } catch (Exception e) {
                // Index might already exist
            }
            
            try {
                stmt.execute("CREATE INDEX idx_media_files_upload_date ON media_files(upload_date)");
            } catch (Exception e) {
                // Index might already exist
            }
            
            // Try to add new columns to players table (they might already exist)
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN email VARCHAR(100)");
            } catch (Exception e) {
                // Column might already exist
            }
            
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN display_name VARCHAR(100)");
            } catch (Exception e) {
                // Column might already exist
            }
            
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN registration_date TIMESTAMP");
            } catch (Exception e) {
                // Column might already exist
            }
            
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN last_login_date TIMESTAMP");
            } catch (Exception e) {
                // Column might already exist
            }
            
            // Update existing players to have default values for new columns
            stmt.execute("UPDATE players SET display_name = username WHERE display_name IS NULL");
            stmt.execute("UPDATE players SET registration_date = CURRENT_TIMESTAMP WHERE registration_date IS NULL");
            
            stmt.close();
            conn.close();
            
            System.out.println("Lexicon database initialization completed successfully");
            
        } catch (Exception e) {
            System.err.println("Database initialization error (this might be expected if database isn't ready yet): " + e.getMessage());
        }
    }
}
