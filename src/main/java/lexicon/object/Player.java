package lexicon.object;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Unified Player class for both Alchemy and Lexicon systems
 * This is a simplified version without alchemy-specific fields (inventory, knowledgeBook)
 * but includes all user account fields needed for unified authentication
 */
public class Player implements Serializable {

    private final int id; // Unique identifier for the player
    private final String username; // Player's username
    private final String password; // Player's password
    private int level; // Player level (for alchemy game compatibility)
    
    // User profile fields for unified system
    private String email; // Email address for account recovery and communication
    private String displayName; // Friendly display name (can be different from username)
    private LocalDateTime registrationDate; // When the account was created
    private LocalDateTime lastLoginDate; // When the user last logged in

    // Constructor with all fields
    public Player(int id, String username, String password, int level, String email, String displayName, 
                  LocalDateTime registrationDate, LocalDateTime lastLoginDate) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.level = level;
        this.email = email;
        this.displayName = displayName;
        this.registrationDate = registrationDate;
        this.lastLoginDate = lastLoginDate;
    }
    
    // Constructor with level but default user profile fields
    public Player(int id, String username, String password, int level) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.level = level;
        this.email = null; // Can be set later
        this.displayName = username; // Default to username
        this.registrationDate = LocalDateTime.now();
        this.lastLoginDate = null; // Set on first login
    }
    
    // Constructor with default level
    public Player(int id, String username, String password) {
        this(id, username, password, 1);
    }
    
    // Default constructor for frameworks
    public Player() {
        this.id = 0;
        this.username = null;
        this.password = null;
        this.level = 1;
    }
    
    // Getters
    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getLevel() {
        return level;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }

    public LocalDateTime getLastLoginDate() {
        return lastLoginDate;
    }

    // Setters for mutable fields
    public void setLevel(int level) {
        this.level = level;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }

    public void setLastLoginDate(LocalDateTime lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    // Alchemy game compatibility methods
    public boolean levelUp() {
        if (level < 10) {
            level++;
            return true;
        }
        return false;  // Already at max level.
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", level=" + level +
                ", registrationDate=" + registrationDate +
                ", lastLoginDate=" + lastLoginDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player)) return false;
        Player player = (Player) o;
        return id == player.id &&
                level == player.level &&
                Objects.equals(username, player.username) &&
                Objects.equals(password, player.password) &&
                Objects.equals(email, player.email) &&
                Objects.equals(displayName, player.displayName) &&
                Objects.equals(registrationDate, player.registrationDate) &&
                Objects.equals(lastLoginDate, player.lastLoginDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, password, level, email, displayName, registrationDate, lastLoginDate);
    }
}