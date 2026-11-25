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
    private final Map<Integer, String> roles = new ConcurrentHashMap<>();
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
    
    private final Map<Integer, LocalDateTime> lastActive = new ConcurrentHashMap<>();

    @Override
    public boolean deletePlayerAccount(int playerId) {
        lastActive.remove(playerId);
        roles.remove(playerId);
        return players.remove(playerId) != null;
    }

    @Override
    public void updatePlayerLastActive(int playerId, LocalDateTime lastActiveDate) {
        if (players.containsKey(playerId)) lastActive.put(playerId, lastActiveDate);
    }

    @Override
    public List<lexicon.object.PlayerActivity> getPlayerActivity() {
        List<lexicon.object.PlayerActivity> rows = new ArrayList<>();
        players.values().stream().sorted(Comparator.comparingInt(Player::getId)).forEach(p ->
                rows.add(new lexicon.object.PlayerActivity(p.getId(), p.getUsername(), roles.getOrDefault(p.getId(), "USER"),
                        p.getRegistrationDate(), p.getLastLoginDate(), lastActive.get(p.getId()))));
        return rows;
    }

    @Override
    public void updatePlayerPassword(int playerId, String hashedPassword) {
        Player old = players.get(playerId);
        if (old != null) {
            players.put(playerId, new Player(old.getId(), old.getUsername(), hashedPassword, old.getLevel(),
                    old.getEmail(), old.getDisplayName(), old.getRegistrationDate(), old.getLastLoginDate()));
        }
    }

    @Override
    public String getPlayerRole(int playerId) {
        return players.containsKey(playerId) ? roles.getOrDefault(playerId, "USER") : null;
    }

    @Override
    public void setPlayerRole(int playerId, String role) {
        if (players.containsKey(playerId)) {
            roles.put(playerId, role);
        }
    }

    @Override
    public int countPlayersWithRole(String role) {
        return (int) players.keySet().stream()
                .filter(id -> role.equals(roles.getOrDefault(id, "USER")))
                .count();
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
        roles.clear();
        nextPlayerId = 1;
    }
}
