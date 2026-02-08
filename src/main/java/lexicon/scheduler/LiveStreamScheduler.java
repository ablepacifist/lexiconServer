package lexicon.scheduler;

import lexicon.service.LiveStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler for live stream auto-advance
 * Checks periodically if current media has finished and advances to next
 */
@Component
public class LiveStreamScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(LiveStreamScheduler.class);
    
    @Autowired
    private LiveStreamService liveStreamService;
    
    /**
     * Check every 5 seconds if media needs to advance
     * This handles auto-advancing when media finishes playing
     * DISABLED: Cannot use actual duration since media duration is not extracted from files yet
     * TODO: Extract duration from video/audio files when uploading
     */
    // @Scheduled(fixedRate = 5000)
    public void checkAndAdvanceStream() {
        try {
            liveStreamService.checkAndAdvanceIfNeeded();
        } catch (Exception e) {
            logger.error("Error in live stream scheduler: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast periodic state updates to keep clients in sync
     * Every 10 seconds, send current position to all connected clients
     */
    @Scheduled(fixedRate = 10000)
    public void broadcastStateSync() {
        try {
            liveStreamService.broadcastStateChange();
        } catch (Exception e) {
            logger.debug("Error broadcasting state sync: {}", e.getMessage());
        }
    }
}
