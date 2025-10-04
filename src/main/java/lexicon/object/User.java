package lexicon.object;

public class User {
    private int id;
    private String username;
    private String password;
    private String email;
    private String displayName;
    
    public User() {}
    
    public User(int id, String username, String password, String email, String displayName) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.displayName = displayName;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', displayName='" + displayName + "'}";
    }
}