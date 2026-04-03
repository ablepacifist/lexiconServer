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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for managing the live stream with per-channel support.
 * Each channel ("music" or "video") has independent state, queue, and SSE connections.
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
    
    // Per-channel SSE emitters
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> channelEmitters = new ConcurrentHashMap<>();
    
    // Executor for async SSE broadcasts
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(4);
    
    // Per-channel media cache
    private final ConcurrentHashMap<String, List<MediaFile>> channelMediaCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> channelCacheTime = new ConcurrentHashMap<>();
    private static final long MEDIA_CACHE_TTL = 60000; // 1 minute
    
    // Per-channel mediaEnded debounce
    private final ConcurrentHashMap<String, Long> lastMediaEndedTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> mediaEndedLocks = new ConcurrentHashMap<>();
    
    @jakarta.annotation.PostConstruct
    public void initializeStream() {
        // Initialize emitter lists for both channels
        channelEmitters.put("music", new CopyOnWriteArrayList<>());
        channelEmitters.put("video", new CopyOnWriteArrayList<>());
        mediaEndedLocks.put("music", new Object());
        mediaEndedLocks.put("video", new Object());
        
        // Run initialization in background thread
        new Thread(() -> {
            try {
                System.out.println("Initializing live stream channels...");
                for (String channel : new String[]{"music", "video"}) {
                    LiveStreamState state = liveStreamDb.getStreamState(channel);
                    if (state.getCurrentMediaId() == 0) {
                        System.out.println("No media playing on " + channel + " channel, selecting first...");
                        selectAndPlayNextMediaFast(channel);
                        System.out.println(channel + " channel initialized");
                    } else {
                        System.out.println(channel + " channel already has media ID: " + state.getCurrentMediaId());
                    }
                }
            } catch (Exception e) {
                System.err.println("Error initializing streams: " + e.getMessage());
                e.printStackTrace();
            }
        }, "LiveStream-Init").start();
    }
    
    // ===== State & Queue =====
    
    public LiveStreamState getStreamState(String channel) {
        return liveStreamDb.getStreamState(channel);
    }
    
    public List<LiveStreamQueue> getQueue(String channel) {
        return liveStreamDb.getQueueItemsLightweight(channel);
    }
    
    public LiveStreamQueue addToQueue(String channel, int userId, int mediaFileId) {
        MediaFile media = mediaDb.getMediaFile(mediaFileId);
        if (media == null) {
            throw new IllegalArgumentException("Media file not found");
        }
        
        // Enforce channel media type: music channel only accepts MUSIC, video only accepts VIDEO
        if (channel.equals("music") && media.getMediaType() != MediaType.MUSIC) {
            throw new IllegalArgumentException("Only music files can be added to the music stream");
        }
        if (channel.equals("video") && media.getMediaType() != MediaType.VIDEO) {
            throw new IllegalArgumentException("Only video files can be added to the video stream");
        }
        
        // Check access
        if (!media.isPublic() && media.getUploadedBy() != userId) {
            if (!isMediaInPublicPlaylist(mediaFileId)) {
                throw new SecurityException("Cannot add private media that doesn't belong to you or isn't in a public playlist");
            }
        }
        
        int queueId = liveStreamDb.addToQueue(channel, mediaFileId, userId);
        broadcastQueueChange(channel);
        
        LiveStreamQueue result = new LiveStreamQueue();
        result.setId(queueId);
        result.setChannel(channel);
        result.setMediaFileId(mediaFileId);
        result.setAddedBy(userId);
        result.setStatus(LiveStreamQueue.QueueStatus.QUEUED);
        
        MediaFile lightMedia = new MediaFile();
        lightMedia.setId(mediaFileId);
        lightMedia.setTitle(media.getTitle());
        lightMedia.setOriginalFilename(media.getOriginalFilename());
        result.setMediaFile(lightMedia);
        
        return result;
    }
    
    public int addPlaylistToQueue(String channel, int userId, int playlistId) {
        Playlist playlist = playlistDb.getPlaylistWithItems(playlistId);
        if (playlist == null) {
            throw new IllegalArgumentException("Playlist not found");
        }
        if (!playlist.isPublic() && playlist.getCreatedBy() != userId) {
            throw new SecurityException("Cannot queue a private playlist you don't own");
        }
        
        String expectedType = channel.equals("music") ? "MUSIC" : "VIDEO";
        if (playlist.getMediaType() == null || !expectedType.equals(playlist.getMediaType().name())) {
            throw new IllegalArgumentException("Playlist type " + playlist.getMediaType() + " doesn't match " + channel + " channel");
        }
        
        int added = 0;
        for (PlaylistItem item : playlist.getItems()) {
            try {
                liveStreamDb.addToQueue(channel, item.getMediaFileId(), userId);
                added++;
            } catch (Exception e) {
                // Skip items that fail (e.g. deleted media)
            }
        }
        
        if (added > 0) {
            broadcastQueueChange(channel);
        }
        return added;
    }
    
    public boolean removeFromQueue(String channel, int queueId, int userId) {
        List<LiveStreamQueue> items = liveStreamDb.getQueueItemsLightweight(channel);
        LiveStreamQueue item = items.stream()
            .filter(i -> i.getId() == queueId)
            .findFirst()
            .orElse(null);
        
        if (item == null) {
            return false;
        }
        
        if (item.getAddedBy() != userId) {
            throw new SecurityException("Can only remove items you added");
        }
        
        if (item.getStatus() == LiveStreamQueue.QueueStatus.PLAYING) {
            throw new IllegalStateException("Cannot remove currently playing item, use skip instead");
        }
        
        boolean removed = liveStreamDb.removeFromQueue(channel, queueId);
        if (removed) {
            broadcastQueueChange(channel);
        }
        return removed;
    }
    
    // ===== Skip =====
    
    public boolean voteSkip(String channel, int userId) {
        long start = System.currentTimeMillis();
        System.out.println("[SKIP:" + channel + "] Starting skip vote for user " + userId);
        
        Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId(channel);
        if (currentQueueId == null) {
            System.out.println("[SKIP:" + channel + "] No currently playing item");
            return false;
        }
        
        boolean voteAdded = liveStreamDb.addSkipVote(currentQueueId, userId);
        if (!voteAdded) {
            return false;
        }
        
        int voteCount = liveStreamDb.getSkipVoteCount(currentQueueId);
        LiveStreamState state = liveStreamDb.getStreamState(channel);
        
        if (voteCount >= state.getRequiredSkipVotes()) {
            skipToNextFast(channel, currentQueueId);
            broadcastStateChange(channel);
            broadcastQueueChange(channel);
            System.out.println("[SKIP:" + channel + "] Total time: " + (System.currentTimeMillis() - start) + "ms");
            return true;
        }
        
        return false;
    }
    
    public void skipToNext(String channel) {
        Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId(channel);
        if (currentQueueId != null) {
            skipToNextFast(channel, currentQueueId);
        } else {
            selectAndPlayNextMediaFast(channel);
        }
    }
    
    public void mediaEnded(String channel) {
        Object lock = mediaEndedLocks.computeIfAbsent(channel, k -> new Object());
        
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Long lastTime = lastMediaEndedTime.get(channel);
            
            if (lastTime != null && now - lastTime < 3000) {
                System.out.println("[MEDIA_ENDED:" + channel + "] Ignoring duplicate call (within 3s)");
                return;
            }
            
            System.out.println("[MEDIA_ENDED:" + channel + "] Media ended, advancing...");
            
            Integer currentQueueId = liveStreamDb.getCurrentPlayingQueueId(channel);
            if (currentQueueId != null) {
                liveStreamDb.updateQueueStatus(currentQueueId, LiveStreamQueue.QueueStatus.COMPLETED);
                liveStreamDb.clearSkipVotesForItem(currentQueueId);
            }
            
            selectAndPlayNextMediaFast(channel);
            lastMediaEndedTime.put(channel, System.currentTimeMillis());
            
            broadcastStateChange(channel);
            broadcastQueueChange(channel);
        }
    }
    
    // ===== Internal =====
    
    private void skipToNextFast(String channel, int currentQueueId) {
        liveStreamDb.updateQueueStatus(currentQueueId, LiveStreamQueue.QueueStatus.SKIPPED);
        liveStreamDb.clearSkipVotesForItem(currentQueueId);
        selectAndPlayNextMediaFast(channel);
    }
    
    private void selectAndPlayNextMediaFast(String channel) {
        List<LiveStreamQueue> items = liveStreamDb.getQueueItemsLightweight(channel);
        
        // Mark all currently PLAYING items as COMPLETED to prevent stale PLAYING entries
        items.stream()
            .filter(i -> i.getStatus() == LiveStreamQueue.QueueStatus.PLAYING)
            .forEach(i -> {
                liveStreamDb.updateQueueStatus(i.getId(), LiveStreamQueue.QueueStatus.COMPLETED);
                liveStreamDb.clearSkipVotesForItem(i.getId());
            });
        
        LiveStreamQueue nextItem = items.stream()
            .filter(i -> i.getStatus() == LiveStreamQueue.QueueStatus.QUEUED)
            .findFirst()
            .orElse(null);
        
        if (nextItem != null) {
            liveStreamDb.updateQueueStatus(nextItem.getId(), LiveStreamQueue.QueueStatus.PLAYING);
            liveStreamDb.setCurrentMedia(channel, nextItem.getMediaFileId(), 0);
            liveStreamDb.clearSkipVotesForItem(nextItem.getId());
            broadcastStateChange(channel);
            broadcastQueueChange(channel);
        } else {
            // Queue empty: pick random media of the correct type for this channel
            int randomMediaId = selectRandomMedia(channel);
            if (randomMediaId > 0) {
                int queueId = liveStreamDb.addToQueue(channel, randomMediaId, 0);
                liveStreamDb.updateQueueStatus(queueId, LiveStreamQueue.QueueStatus.PLAYING);
                liveStreamDb.setCurrentMedia(channel, randomMediaId, 0);
                broadcastStateChange(channel);
                broadcastQueueChange(channel);
            }
        }
    }
    
    /**
     * Select random media filtered by channel type.
     * Music channel only picks MUSIC, video channel only picks VIDEO.
     */
    private int selectRandomMedia(String channel) {
        List<MediaFile> eligibleMedia = getEligibleMedia(channel);
        
        if (eligibleMedia == null || eligibleMedia.isEmpty()) {
            return 0;
        }
        
        // Channel already filters by type, so just pick randomly
        return eligibleMedia.get(random.nextInt(eligibleMedia.size())).getId();
    }
    
    private List<MediaFile> rebuildMediaCache(String channel) {
        List<MediaFile> eligibleMedia = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();
        
        // Determine target type based on channel
        MediaType targetType = channel.equals("music") ? MediaType.MUSIC : MediaType.VIDEO;
        
        try {
            // 1. Get all public media files of this type
            List<MediaFile> publicMedia = mediaDb.getAllPublicMediaFiles();
            for (MediaFile media : publicMedia) {
                if (media.getMediaType() == targetType && !addedIds.contains(media.getId())) {
                    eligibleMedia.add(media);
                    addedIds.add(media.getId());
                }
            }
            
            // 2. Get media from public playlists (single bulk query instead of N+1)
            List<MediaFile> playlistMedia = playlistDb.getMediaInPublicPlaylists();
            for (MediaFile media : playlistMedia) {
                if (media.getMediaType() == targetType && !addedIds.contains(media.getId())) {
                    eligibleMedia.add(media);
                    addedIds.add(media.getId());
                }
            }
            
            channelMediaCache.put(channel, eligibleMedia);
            channelCacheTime.put(channel, System.currentTimeMillis());
            System.out.println("Media cache rebuilt for " + channel + ": " + eligibleMedia.size() + " items");
            
        } catch (Exception e) {
            System.err.println("Error building media cache for " + channel + ": " + e.getMessage());
            List<MediaFile> stale = channelMediaCache.get(channel);
            if (stale != null) {
                return stale;
            }
        }
        
        return eligibleMedia;
    }
    
    private boolean isMediaInPublicPlaylist(int mediaFileId) {
        try {
            return playlistDb.isMediaInAnyPublicPlaylist(mediaFileId);
        } catch (Exception e) {
            System.err.println("Error checking public playlists: " + e.getMessage());
        }
        return false;
    }
    
    public List<MediaFile> getEligibleMedia(String channel) {
        long now = System.currentTimeMillis();
        Long cacheTime = channelCacheTime.get(channel);
        List<MediaFile> cached = channelMediaCache.get(channel);
        
        if (cached != null && cacheTime != null && (now - cacheTime) < MEDIA_CACHE_TTL) {
            return cached;
        }
        return rebuildMediaCache(channel);
    }
    
    public void checkAndAdvanceIfNeeded(String channel) {
        LiveStreamState state = liveStreamDb.getStreamState(channel);
        if (state.getCurrentMediaId() == 0) {
            selectAndPlayNextMediaFast(channel);
        }
    }
    
    // ===== SSE Management =====
    
    public void registerEmitter(String channel, SseEmitter emitter) {
        channelEmitters.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }
    
    public void unregisterEmitter(String channel, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = channelEmitters.get(channel);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
    
    private void sendUpdateToClients(String channel, String eventType, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = channelEmitters.get(channel);
        if (emitters == null || emitters.isEmpty()) return;
        
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", eventType);
        eventData.put("data", data);
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(eventData));
            } catch (IllegalStateException e) {
                deadEmitters.add(emitter);
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }
    
    public void broadcastStateChange(String channel) {
        broadcastExecutor.submit(() -> {
            try {
                LiveStreamState state = liveStreamDb.getStreamState(channel);
                
                Map<String, Object> stateData = new HashMap<>();
                stateData.put("id", state.getId());
                stateData.put("channel", channel);
                stateData.put("currentMediaId", state.getCurrentMediaId());
                stateData.put("currentMedia", state.getCurrentMedia());
                stateData.put("currentStartTime", state.getCurrentStartTime());
                stateData.put("currentPositionMs", state.getCurrentPositionMs());
                stateData.put("totalSkipVotes", state.getTotalSkipVotes());
                stateData.put("requiredSkipVotes", state.getRequiredSkipVotes());
                stateData.put("timestamp", System.currentTimeMillis());
                
                sendUpdateToClients(channel, "state-update", stateData);
            } catch (Exception e) {
                System.err.println("Error broadcasting state change for " + channel + ": " + e.getMessage());
            }
        });
    }
    
    public void broadcastQueueChange(String channel) {
        broadcastExecutor.submit(() -> {
            try {
                List<LiveStreamQueue> fullQueue = liveStreamDb.getQueueItemsLightweight(channel);
                
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
                
                sendUpdateToClients(channel, "queue-update", minimalQueue);
            } catch (Exception e) {
                System.err.println("Error broadcasting queue change for " + channel + ": " + e.getMessage());
            }
        });
    }
}
