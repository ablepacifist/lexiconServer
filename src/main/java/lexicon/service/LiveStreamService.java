package lexicon.service;

import lexicon.data.ILiveStreamDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.object.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for managing the live stream
 */
@Service
public class LiveStreamService {
    
    @Autowired
    private ILiveStreamDatabase liveStreamDb;
    
    @Autowired
    private IMediaDatabase mediaDb;
    
    @Autowired
    private IPlaylistDatabase playlistDb;
    
    private final Random random = new Random();
    
    // Thread-safe list of SSE emitters for real-time updates
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    // Executor for async SSE broadcasts (don't block request threads!)
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(2);
    
    // Track if stream has been initialized
    private volatile boolean initialized = false;
    
    // Cache for eligible media to avoid expensive queries on every skip
    private volatile List<MediaFile> cachedEligibleMedia = null;
    private volatile long lastMediaCacheTime = 0;
    private static final long MEDIA_CACHE_TTL = 60000; // 1 minute cache
    
    /**
    * Initialize stream on startup - runs asynchronously to not block server startup
     */
    @jakarta.annotation.PostConstruct
    public void initializeStream() {
        // Run initialization in background thread
        new Thread(() -> {
            try {
                System.out.println("Checking live stream state...");
                LiveStreamState state = liveStreamDb.getStreamState();
                if (state.getCurrentMediaId() == 0) {
                    System.out.println("No media playing, selecting first media...");
                    selectAndPlayNextMedia();
                    System.out.println("Live stream initialized with first media");
                } else {
                    System.out.println("Live stream already has media ID: " + state.getCurrentMediaId());
                }
                initialized = true;
            } catch (Exception e) {
                System.err.println("Error initializing stream: " + e.getMessage());
                e.printStackTrace();
                initialized = true; // Mark as initialized even if failed, to not block
            }
        }, "LiveStream-Init").start();
    }
    
    /**
     * Get current stream state with all info
     */
    public LiveStreamState getStreamState() {
        return liveStreamDb.getStreamState();
    }
    
    /**
     * Get the queue
     */
    public List<LiveStreamQueue> getQueue() {
        return liveStreamDb.getQueueItemsLightweight();
    }
    
    /**
     * Add media to the queue - OPTIMIZED
     * Returns lightweight queue item without full media
     */
    public LiveStreamQueue addToQueue(int userId, int mediaFileId) {
        // Verify media exists and is video or music - we need metadata check here
        MediaFile media = mediaDb.getMediaFile(mediaFileId);
        if (media == null) {
            throw new IllegalArgumentException("Media file not found");
        }
        
        if (media.getMediaType() != MediaType.VIDEO && media.getMediaType() != MediaType.MUSIC) {
            throw new IllegalArgumentException("Only videos and music can be added to live stream");
        }
        
        // Check if media is public, belongs to user, or is in a public playlist
        if (!media.isPublic() && media.getUploadedBy() != userId) {
            // Check if the media is in any public playlist
            if (!isMediaInPublicPlaylist(mediaFileId)) {
                throw new SecurityException("Cannot add private media that doesn't belong to you or isn't in a public playlist");
            }
        }
        
        int queueId = liveStreamDb.addToQueue(mediaFileId, userId);
        broadcastQueueChange();
        
        // Return lightweight queue item - don't reload entire queue!
        LiveStreamQueue result = new LiveStreamQueue();
        result.setId(queueId);
        result.setMediaFileId(mediaFileId);
        result.setAddedBy(userId);
        result.setStatus(LiveStreamQueue.QueueStatus.QUEUED);
        
        // Create lightweight media for response
        MediaFile lightMedia = new MediaFile();
        lightMedia.setId(mediaFileId);
        lightMedia.setTitle(media.getTitle());
        lightMedia.setOriginalFilename(media.getOriginalFilename());
        result.setMediaFile(lightMedia);
        
        return result;
    }
    
    /**
     * Remove item from queue (only if user added it) - OPTIMIZED
     */
    public boolean removeFromQueue(int queueId, int userId) {
        // Use lightweight query
        List<LiveStreamQueue> items = liveStreamDb.getQueueItemsLightweight();
        LiveStreamQueue item = items.stream()
            .filter(i -> i.getId() == queueId)
            .findFirst()
            .orElse(null);
        
        if (item == null) {
            return false;
        }
        
        // Only allow user who added it to remove it
        if (item.getAddedBy() != userId) {
            throw new SecurityException("Can only remove items you added");
        }
        
        // Don't allow removing currently playing item
        if (item.getStatus() == LiveStreamQueue.QueueStatus.PLAYING) {
            throw new IllegalStateException("Cannot remove currently playing item, use skip instead");
        }
        
        boolean removed = liveStreamDb.removeFromQueue(queueId);
        if (removed) {
            broadcastQueueChange();
        }
        return removed;
    }
    
    /**
     * Vote to skip current media
     * With threshold=1, immediately skips
     * OPTIMIZED: Uses fast getCurrentPlayingQueueId() instead of loading all queue items
     */
    public boolean voteSkip(int userId) {
        long start = System.currentTimeMillis();
        System.out.println("[SKIP] Starting skip vote for user " + userId);
        
        // FAST: Get just the queue ID without loading all items
        Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId();
        System.out.println("[SKIP] Got current queue ID in " + (System.currentTimeMillis() - start) + "ms");
        
        if (currentQueueId == null) {
            System.out.println("[SKIP] No currently playing item");
            return false;
        }
        
        // Add vote
        long voteStart = System.currentTimeMillis();
        boolean voteAdded = liveStreamDb.addSkipVote(currentQueueId, userId);
        System.out.println("[SKIP] Added vote in " + (System.currentTimeMillis() - voteStart) + "ms, result: " + voteAdded);
        
        if (!voteAdded) {
            System.out.println("[SKIP] Vote not added (already voted)");
            return false; // Already voted
        }
        
        // Check if threshold reached (threshold = 1, so immediately skip)
        int voteCount = liveStreamDb.getSkipVoteCount(currentQueueId);
        LiveStreamState state = liveStreamDb.getStreamState();
        System.out.println("[SKIP] Vote count: " + voteCount + ", threshold: " + state.getRequiredSkipVotes());
        
        if (voteCount >= state.getRequiredSkipVotes()) {
            // Skip to next media - pass the queue ID we already have
            long skipStart = System.currentTimeMillis();
            skipToNextFast(currentQueueId);
            System.out.println("[SKIP] skipToNextFast() took " + (System.currentTimeMillis() - skipStart) + "ms");
            
            long broadcastStart = System.currentTimeMillis();
            broadcastStateChange();
            broadcastLightStateChange();
            broadcastQueueChange();
            System.out.println("[SKIP] Broadcasts submitted in " + (System.currentTimeMillis() - broadcastStart) + "ms");
            System.out.println("[SKIP] Total time: " + (System.currentTimeMillis() - start) + "ms");
            return true;
        }
        
        System.out.println("[SKIP] Total time (no skip): " + (System.currentTimeMillis() - start) + "ms");
        return false; // Vote added but not enough to skip
    }
    
    /**
     * Skip to next media immediately (legacy method that queries for current)
     */
    public void skipToNext() {
        Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId();
        if (currentQueueId != null) {
            skipToNextFast(currentQueueId);
        } else {
            // No current item, just select next
            selectAndPlayNextMediaFast();
        }
    }
    
    // Lock object for mediaEnded to prevent race conditions from multiple clients
    private final Object mediaEndedLock = new Object();
    private volatile long lastMediaEndedTime = 0;

    /**
     * Called when the frontend reports that the current media has ended
     * Marks the current item as COMPLETED and advances to next
     * Synchronized to prevent duplicate calls from multiple clients
     */
    public void mediaEnded() {
        synchronized (mediaEndedLock) {
            long now = System.currentTimeMillis();
            
            // Debounce: ignore calls within 3 seconds of last successful call
            if (now - lastMediaEndedTime < 3000) {
                System.out.println("[MEDIA_ENDED] Ignoring duplicate call (within 3s of last call)");
                return;
            }
            
            long start = now;
            System.out.println("[MEDIA_ENDED] Media ended, advancing to next...");
            
            Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId();
            if (currentQueueId != null) {
                // Mark as completed (not skipped)
                liveStreamDb.updateQueueStatus(currentQueueId, LiveStreamQueue.QueueStatus.COMPLETED);
                liveStreamDb.clearSkipVotesForItem(currentQueueId);
                System.out.println("[MEDIA_ENDED] Marked queue item " + currentQueueId + " as COMPLETED");
            }
            
            // Play next
            selectAndPlayNextMediaFast();
            
            // Update last call time
            lastMediaEndedTime = System.currentTimeMillis();
            
            // Broadcast changes
            broadcastStateChange();
            broadcastLightStateChange();
            broadcastQueueChange();
            
            System.out.println("[MEDIA_ENDED] Total time: " + (System.currentTimeMillis() - start) + "ms");
        }
    }
    
    /**
     * Skip to next media immediately - FAST version with known queue ID
     */
    private void skipToNextFast(int currentQueueId) {
        // Mark current as skipped
        liveStreamDb.updateQueueStatus(currentQueueId, LiveStreamQueue.QueueStatus.SKIPPED);
        liveStreamDb.clearSkipVotesForItem(currentQueueId);
        
        // Play next
        selectAndPlayNextMediaFast();
    }
    
    /**
     * Select and play the next media file - FAST version using lightweight queries
     * Picks from queue, or random public video/music if queue empty
     */
    private void selectAndPlayNextMediaFast() {
        // Use lightweight query - no full media file loading!
        List<LiveStreamQueue> items = liveStreamDb.getQueueItemsLightweight();
        
        // Filter out completed/skipped, get next queued item
        LiveStreamQueue nextItem = items.stream()
            .filter(i -> i.getStatus() == LiveStreamQueue.QueueStatus.QUEUED)
            .findFirst()
            .orElse(null);
        
        if (nextItem != null) {
            // Play from queue
            liveStreamDb.updateQueueStatus(nextItem.getId(), LiveStreamQueue.QueueStatus.PLAYING);
            liveStreamDb.setCurrentMedia(nextItem.getMediaFileId(), 0);
            liveStreamDb.clearSkipVotesForItem(nextItem.getId());
            broadcastStateChange();
            broadcastLightStateChange();
            broadcastQueueChange();
        } else {
            // Queue is empty, pick random public video or music
            int randomMediaId = selectRandomMedia();
            if (randomMediaId > 0) {
                // Add to queue and mark as playing
                int queueId = liveStreamDb.addToQueue(randomMediaId, 0); // System user = 0
                liveStreamDb.updateQueueStatus(queueId, LiveStreamQueue.QueueStatus.PLAYING);
                liveStreamDb.setCurrentMedia(randomMediaId, 0);
                broadcastStateChange();
                broadcastLightStateChange();
                broadcastQueueChange();
            }
        }
    }
    
    /**
     * Select and play the next media file (legacy - uses old method)
     * Picks from queue, or random public video/music if queue empty
     */
    private void selectAndPlayNextMedia() {
        selectAndPlayNextMediaFast();
    }
    
    /**
     * Select a random video or music file from public media and public playlists
     * Prioritizes videos over music (70% video, 30% music)
     * Includes items from public playlists
     */
    private int selectRandomMedia() {
        long now = System.currentTimeMillis();
        
        // Use cached media if available and not stale
        List<MediaFile> eligibleMedia;
        if (cachedEligibleMedia != null && (now - lastMediaCacheTime) < MEDIA_CACHE_TTL) {
            eligibleMedia = cachedEligibleMedia;
            System.out.println("Using cached media list (" + eligibleMedia.size() + " items)");
        } else {
            // Rebuild cache - this is slow but only happens once per minute
            eligibleMedia = rebuildMediaCache();
        }
        
        if (eligibleMedia == null || eligibleMedia.isEmpty()) {
            return 0;
        }
        
        // Separate videos and music for weighted selection
        List<MediaFile> videos = eligibleMedia.stream()
            .filter(m -> m.getMediaType() == MediaType.VIDEO)
            .collect(Collectors.toList());
        
        List<MediaFile> music = eligibleMedia.stream()
            .filter(m -> m.getMediaType() == MediaType.MUSIC)
            .collect(Collectors.toList());
        
        // 70% chance to pick video, 30% chance to pick music
        boolean pickVideo = random.nextInt(100) < 70;
        
        if (pickVideo && !videos.isEmpty()) {
            return videos.get(random.nextInt(videos.size())).getId();
        } else if (!music.isEmpty()) {
            return music.get(random.nextInt(music.size())).getId();
        } else if (!videos.isEmpty()) {
            return videos.get(random.nextInt(videos.size())).getId();
        }
        
        return 0;
    }
    
    private List<MediaFile> rebuildMediaCache() {
        List<MediaFile> eligibleMedia = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();
        
        try {
            System.out.println("Rebuilding media cache...");
            
            // 1. Get all public media files (video and music)
            List<MediaFile> publicMedia = mediaDb.getAllPublicMediaFiles();
            for (MediaFile media : publicMedia) {
                if ((media.getMediaType() == MediaType.VIDEO || media.getMediaType() == MediaType.MUSIC) 
                    && !addedIds.contains(media.getId())) {
                    eligibleMedia.add(media);
                    addedIds.add(media.getId());
                }
            }
            
            // 2. Get media from all public playlists (includes private media in public playlists)
            List<Playlist> publicPlaylists = playlistDb.getPublicPlaylists();
            for (Playlist playlist : publicPlaylists) {
                Playlist fullPlaylist = playlistDb.getPlaylistWithItems(playlist.getId());
                if (fullPlaylist != null && fullPlaylist.getItems() != null) {
                    for (PlaylistItem item : fullPlaylist.getItems()) {
                        MediaFile media = item.getMediaFile();
                        if (media != null && !addedIds.contains(media.getId())) {
                            if (media.getMediaType() == MediaType.VIDEO || media.getMediaType() == MediaType.MUSIC) {
                                eligibleMedia.add(media);
                                addedIds.add(media.getId());
                            }
                        }
                    }
                }
            }
            
            cachedEligibleMedia = eligibleMedia;
            lastMediaCacheTime = System.currentTimeMillis();
            System.out.println("Media cache rebuilt: " + eligibleMedia.size() + " eligible items");
            
        } catch (Exception e) {
            System.err.println("Error building media cache: " + e.getMessage());
            if (cachedEligibleMedia != null) {
                return cachedEligibleMedia; // Return stale cache on error
            }
        }
        
        return eligibleMedia;
    }
    
    /**
     * Check if a specific media file is in any public playlist
     */
    private boolean isMediaInPublicPlaylist(int mediaFileId) {
        try {
            List<Playlist> publicPlaylists = playlistDb.getPublicPlaylists();
            for (Playlist playlist : publicPlaylists) {
                List<PlaylistItem> items = playlistDb.getPlaylistItems(playlist.getId());
                if (items != null) {
                    for (PlaylistItem item : items) {
                        if (item.getMediaFileId() == mediaFileId) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking public playlists: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Get all media eligible for the livestream queue
     * Includes: public media + private media in public playlists
     * Filtered to VIDEO and MUSIC types only
     */
    public List<MediaFile> getEligibleMedia() {
        long now = System.currentTimeMillis();
        if (cachedEligibleMedia != null && (now - lastMediaCacheTime) < MEDIA_CACHE_TTL) {
            return cachedEligibleMedia;
        }
        return rebuildMediaCache();
    }
    
    /**
     * Check if current media has finished playing
     * Called by background scheduler
     * DISABLED: Auto-advance causes issues without accurate media duration
     * Use manual skip instead
     */
    public void checkAndAdvanceIfNeeded() {
        LiveStreamState state = liveStreamDb.getStreamState();
        
        // Only ensure something is playing if nothing is playing
        // Don't auto-advance based on duration since we don't have accurate durations
        if (state.getCurrentMediaId() == 0) {
            selectAndPlayNextMedia();
            return;
        }
        
        // DISABLED: Do not auto-advance based on estimated duration
        // This was causing media to skip after 3 minutes regardless of actual duration
        // Users must manually skip if they want to change media
    }
    
    /**
     * Register an SSE emitter for real-time updates
     */
    public void registerEmitter(SseEmitter emitter) {
        emitters.add(emitter);
    }
    
    /**
     * Unregister an SSE emitter
     */
    public void unregisterEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }
    
    /**
     * Send update to all connected clients
     */
    private void sendUpdateToClients(String eventType, Object data) {
        // Send to clients asynchronously to avoid blocking the request thread
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", eventType);
        eventData.put("data", data);
        
        for (SseEmitter emitter : emitters) {
            // Send async - don't block on slow/dead connections
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(eventData));
            } catch (IllegalStateException e) {
                // Emitter already completed/closed
                deadEmitters.add(emitter);
            } catch (Exception e) {
                // IO exception or timeout - mark as dead
                deadEmitters.add(emitter);
            }
        }
        
        // Clean up dead emitters immediately
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            System.out.println("Removed " + deadEmitters.size() + " dead SSE connections");
        }
    }
    
    /**
     * Broadcast state change to all clients
     * OPTIMIZED: Don't include full MediaFile to avoid loading 100+ MB files
     */
    public void broadcastStateChange() {
        // Execute async so we don't block the request thread
        broadcastExecutor.submit(() -> {
            try {
                LiveStreamState state = liveStreamDb.getStreamState();
                
                // Create lightweight state without full media file data
                Map<String, Object> lightState = new HashMap<>();
                lightState.put("id", state.getId());
                lightState.put("currentMediaId", state.getCurrentMediaId());
                lightState.put("currentStartTime", state.getCurrentStartTime());
                lightState.put("currentPositionMs", state.getCurrentPositionMs());
                lightState.put("totalSkipVotes", state.getTotalSkipVotes());
                lightState.put("requiredSkipVotes", state.getRequiredSkipVotes());
                
                sendUpdateToClients("state-update", lightState);
            } catch (Exception e) {
                System.err.println("Error broadcasting state change: " + e.getMessage());
            }
        });
    }
    
    /**
     * Broadcast queue change to all clients - send currently playing item + next items
     * OPTIMIZED: Uses lightweight queue query
     */
    public void broadcastQueueChange() {
        // Execute async so we don't block the request thread
        broadcastExecutor.submit(() -> {
            try {
                // Use lightweight query - no full media loading!
                List<LiveStreamQueue> fullQueue = liveStreamDb.getQueueItemsLightweight();
                
                // Send currently playing + next 10 items
                List<LiveStreamQueue> minimalQueue = new ArrayList<>();
                if (fullQueue != null && !fullQueue.isEmpty()) {
                    int playingIndex = -1;
                    for (int i = 0; i < fullQueue.size(); i++) {
                        if (fullQueue.get(i).getStatus() == LiveStreamQueue.QueueStatus.PLAYING) {
                            playingIndex = i;
                            break;
                        }
                    }
                    
                    int startIdx = Math.max(0, playingIndex == -1 ? 0 : playingIndex);
                    int endIdx = Math.min(fullQueue.size(), startIdx + 11);
                    minimalQueue = fullQueue.subList(startIdx, endIdx);
                }
                
                // Send array directly - frontend expects array, not wrapper object
                sendUpdateToClients("queue-update", minimalQueue);
            } catch (Exception e) {
                System.err.println("Error broadcasting queue change: " + e.getMessage());
            }
        });
    }

    /**
     * Broadcast lightweight state update (for /light/updates endpoint)
     * Only sends current media without queue data
     */
    public void broadcastLightStateChange() {
        LiveStreamState state = liveStreamDb.getStreamState();
        
        // Send minimal state - just media and playback info
        Map<String, Object> lightState = new HashMap<>();
        lightState.put("currentMediaId", state.getCurrentMediaId());
        lightState.put("currentMedia", state.getCurrentMedia());
        lightState.put("currentStartTime", state.getCurrentStartTime());
        lightState.put("currentPositionMs", state.getCurrentPositionMs());
        lightState.put("totalSkipVotes", state.getTotalSkipVotes());
        lightState.put("timestamp", System.currentTimeMillis());
        
        sendUpdateToClients("state-update-light", lightState);
    }
}

