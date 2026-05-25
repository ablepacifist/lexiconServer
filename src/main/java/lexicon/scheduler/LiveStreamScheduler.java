package lexicon.scheduler;

import lexicon.object.LiveStreamState;
import lexicon.service.LiveStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Background scheduler for live stream auto-advance
 * Checks periodically if current media has finished and advances to next
 */
@Component
public class LiveStreamScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(LiveStreamScheduler.class);
    
    @Autowired
    private LiveStreamService liveStreamService;
    
    // Track last known state per channel to avoid redundant broadcasts
    private final ConcurrentHashMap<String, Integer> lastBroadcastMediaId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastBroadcastStartTime = new ConcurrentHashMap<>();
    
    /**
     * Check every 5 seconds if media needs to advance
     * This handles auto-advancing when media finishes playing
     * DISABLED: Cannot use actual duration since media duration is not extracted from files yet
     * TODO: Extract duration from video/audio files when uploading
     */
    // @Scheduled(fixedRate = 5000)
    public void checkAndAdvanceStream() {
        try {
            liveStreamService.checkAndAdvanceIfNeeded("music");
            liveStreamService.checkAndAdvanceIfNeeded("video");
        } catch (Exception e) {
            logger.error("Error in live stream scheduler: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send heartbeat to all SSE connections every 30 seconds.
     * Prevents Cloudflare (100s idle timeout) and proxies from dropping the connection.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            liveStreamService.sendHeartbeatToAll();
        } catch (Exception e) {
            logger.debug("Heartbeat error: {}", e.getMessage());
        }
    }

    /**
     * Broadcast periodic state updates to keep clients in sync.
     * Only broadcasts if state has actually changed since last broadcast.
     */
    @Scheduled(fixedRate = 10000)
    public void broadcastStateSync() {
        try {
            broadcastIfChanged("music");
            broadcastIfChanged("video");
        } catch (Exception e) {
            logger.debug("Error broadcasting state sync: {}", e.getMessage());
        }
    }
    
    private void broadcastIfChanged(String channel) {
        try {
            LiveStreamState state = liveStreamService.getStreamState(channel);
            if (state == null) return;
            
            Integer lastMediaId = lastBroadcastMediaId.get(channel);
            Long lastStart = lastBroadcastStartTime.get(channel);
            
            int currentMediaId = state.getCurrentMediaId();
            long currentStart = state.getCurrentStartTime() != null
                    ? state.getCurrentStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : 0;
            
            // Only broadcast if media or start time changed
            if (lastMediaId == null || lastMediaId != currentMediaId ||
                lastStart == null || lastStart != currentStart) {
                liveStreamService.broadcastStateChange(channel);
                lastBroadcastMediaId.put(channel, currentMediaId);
                lastBroadcastStartTime.put(channel, currentStart);
            }
        } catch (Exception e) {
            // Silently ignore — next cycle will retry
        }
    }
}
