package lexicon.logic;

import lexicon.object.User;
import java.util.Collection;

/**
 * Service interface for user management
 * Following the same pattern as Alchemy's service layer
 */
public interface UserManagerService {
    
    /**
     * Register a new user
     */
    User registerUser(String username, String password, String email, String displayName);
    
    /**
     * Authenticate a user with username and password
     */
    User authenticateUser(String username, String password);
    
    /**
     * Get user by ID
     */
    User getUserById(int userId);
    
    /**
     * Get user by username
     */
    User getUserByUsername(String username);
    
    /**
     * Get all users
     */
    Collection<User> getAllUsers();
    
    /**
     * Update user information
     */
    boolean updateUser(User user);
    
    /**
     * Delete a user
     */
    boolean deleteUser(int userId);
    
    /**
     * Check if username is available
     */
    boolean isUsernameAvailable(String username);
    
    /**
     * Check if email is available
     */
    boolean isEmailAvailable(String email);
}