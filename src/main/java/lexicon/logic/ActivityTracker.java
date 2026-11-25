package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Writes a player's last-active time at most once per {@link #WRITE_INTERVAL_MS}, so a busy
 * client (the app polls) costs one small UPDATE every few minutes, not one per request.
 */
@Service
public class ActivityTracker implements ActivityTrackerService {

    static final long WRITE_INTERVAL_MS = 5 * 60 * 1000L;

    private final ILexiconDatabase database;
    private final LongSupplier clock;
    private final Map<Integer, Long> lastWritten = new ConcurrentHashMap<>();

    @Autowired
    public ActivityTracker(ILexiconDatabase database) {
        this(database, System::currentTimeMillis);
    }

    ActivityTracker(ILexiconDatabase database, LongSupplier clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public void recordActivity(int playerId) {
        if (playerId <= 0) return;
        long now = clock.getAsLong();
        Long previous = lastWritten.get(playerId);
        if (previous != null && now - previous < WRITE_INTERVAL_MS) return;
        lastWritten.put(playerId, now);
        try {
            database.updatePlayerLastActive(playerId, LocalDateTime.now());
        } catch (Exception e) {
            // Best effort: activity tracking must never fail a request.
            System.err.println("Could not record activity for player id " + playerId + ": " + e.getMessage());
        }
    }

    /** Forget a deleted player, so the map does not keep them. */
    void forget(int playerId) {
        lastWritten.remove(playerId);
    }
}
