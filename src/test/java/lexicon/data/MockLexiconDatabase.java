package lexicon.data;

import lexicon.object.Player;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock database implementation for unit testing
 * Player management only - media files in MockMediaDatabase
 */
public class MockLexiconDatabase implements ILexiconDatabase {
    
    private final Map<Integer, Player> players = new ConcurrentHashMap<>();
    private int nextPlayerId = 1;
    
    @Override
    public int getNextPlayerId() {
        return nextPlayerId++;
    }
    
    @Override
    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }
    
    @Override
    public Player getPlayer(int playerId) {
        return players.get(playerId);
    }
    
    @Override
    public Player getPlayerByUsername(String username) {
        return players.values().stream()
            .filter(p -> p.getUsername().equals(username))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public Player getPlayerByEmail(String email) {
        return players.values().stream()
            .filter(p -> email.equals(p.getEmail()))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public Collection<Player> getAllPlayers() {
        return new ArrayList<>(players.values());
    }
    
    @Override
    public void updatePlayer(Player player) {
        players.put(player.getId(), player);
    }
    
    @Override
    public void deletePlayer(int playerId) {
        players.remove(playerId);
    }
    
    @Override
    public void updatePlayerLastLogin(int playerId, LocalDateTime lastLoginDate) {
        Player player = players.get(playerId);
        if (player != null) {
            player.setLastLoginDate(lastLoginDate);
        }
    }
    
    @Override
    public boolean playerExists(String username) {
        return getPlayerByUsername(username) != null;
    }
    
    @Override
    public boolean emailExists(String email) {
        return getPlayerByEmail(email) != null;
    }
    
    // Utility method for tests
    public void clear() {
        players.clear();
        nextPlayerId = 1;
    }
}
