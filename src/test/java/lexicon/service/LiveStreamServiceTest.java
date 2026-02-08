package lexicon.service;

import lexicon.data.ILiveStreamDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiveStreamService
 * Tests business logic for queue management, skipping, and media selection
 * Updated to use FAST lightweight methods (getQueueItemsLightweight, getCurrentPlayingQueueId)
 */
class LiveStreamServiceTest {
    
    @Mock
    private ILiveStreamDatabase liveStreamDb;
    
    @Mock
    private IMediaDatabase mediaDb;
    
    private LiveStreamService liveStreamService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        liveStreamService = new LiveStreamService();
        
        // Inject mocks via reflection
        try {
            var dbField = LiveStreamService.class.getDeclaredField("liveStreamDb");
            dbField.setAccessible(true);
            dbField.set(liveStreamService, liveStreamDb);
            
            var mediaField = LiveStreamService.class.getDeclaredField("mediaDb");
            mediaField.setAccessible(true);
            mediaField.set(liveStreamService, mediaDb);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }
    }
    
    @Test
    void testGetStreamState_ReturnsCurrentState() {
        LiveStreamState mockState = new LiveStreamState();
        mockState.setCurrentMediaId(10);
        mockState.setCurrentPositionMs(5000);
        
        when(liveStreamDb.getStreamState()).thenReturn(mockState);
        
        LiveStreamState result = liveStreamService.getStreamState();
        
        assertEquals(10, result.getCurrentMediaId());
        assertEquals(5000, result.getCurrentPositionMs());
    }
    
    @Test
    void testGetQueue_ReturnsQueueItemsLightweight() {
        List<LiveStreamQueue> mockQueue = new ArrayList<>();
        LiveStreamQueue item1 = new LiveStreamQueue(1, 100, 5, 1);
        LiveStreamQueue item2 = new LiveStreamQueue(2, 101, 6, 2);
        mockQueue.add(item1);
        mockQueue.add(item2);
        
        // Uses lightweight method now
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(mockQueue);
        
        List<LiveStreamQueue> result = liveStreamService.getQueue();
        
        assertEquals(2, result.size());
        assertEquals(100, result.get(0).getMediaFileId());
        assertEquals(101, result.get(1).getMediaFileId());
    }
    
    @Test
    void testAddToQueue_ValidVideoMedia_Success() {
        MediaFile videoMedia = new MediaFile();
        videoMedia.setId(100);
        videoMedia.setTitle("Test Video");
        videoMedia.setOriginalFilename("test.mp4");
        videoMedia.setMediaType(MediaType.VIDEO);
        videoMedia.setPublic(true);
        
        when(mediaDb.getMediaFile(100)).thenReturn(videoMedia);
        when(liveStreamDb.addToQueue(100, 5)).thenReturn(1);
        
        LiveStreamQueue result = liveStreamService.addToQueue(5, 100);
        
        assertNotNull(result);
        assertEquals(100, result.getMediaFileId());
        assertEquals(1, result.getId());
        verify(liveStreamDb).addToQueue(100, 5);
    }
    
    @Test
    void testAddToQueue_ValidMusicMedia_Success() {
        MediaFile musicMedia = new MediaFile();
        musicMedia.setId(101);
        musicMedia.setTitle("Test Music");
        musicMedia.setOriginalFilename("test.mp3");
        musicMedia.setMediaType(MediaType.MUSIC);
        musicMedia.setPublic(true);
        
        when(mediaDb.getMediaFile(101)).thenReturn(musicMedia);
        when(liveStreamDb.addToQueue(101, 5)).thenReturn(2);
        
        LiveStreamQueue result = liveStreamService.addToQueue(5, 101);
        
        assertNotNull(result);
        verify(liveStreamDb).addToQueue(101, 5);
    }
    
    @Test
    void testAddToQueue_AudiobookMedia_ThrowsException() {
        MediaFile audiobookMedia = new MediaFile();
        audiobookMedia.setId(102);
        audiobookMedia.setMediaType(MediaType.AUDIOBOOK);
        audiobookMedia.setPublic(true);
        
        when(mediaDb.getMediaFile(102)).thenReturn(audiobookMedia);
        
        assertThrows(IllegalArgumentException.class, () -> {
            liveStreamService.addToQueue(5, 102);
        });
        
        verify(liveStreamDb, never()).addToQueue(anyInt(), anyInt());
    }
    
    @Test
    void testAddToQueue_NonexistentMedia_ThrowsException() {
        when(mediaDb.getMediaFile(999)).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            liveStreamService.addToQueue(5, 999);
        });
    }
    
    @Test
    void testAddToQueue_PrivateMediaByOtherUser_ThrowsSecurityException() {
        MediaFile privateMedia = new MediaFile();
        privateMedia.setId(103);
        privateMedia.setMediaType(MediaType.VIDEO);
        privateMedia.setPublic(false);
        privateMedia.setUploadedBy(10); // Owned by user 10
        
        when(mediaDb.getMediaFile(103)).thenReturn(privateMedia);
        
        assertThrows(SecurityException.class, () -> {
            liveStreamService.addToQueue(5, 103); // User 5 trying to add
        });
    }
    
    @Test
    void testAddToQueue_PrivateMediaByOwner_Success() {
        MediaFile privateMedia = new MediaFile();
        privateMedia.setId(104);
        privateMedia.setTitle("Private Video");
        privateMedia.setOriginalFilename("private.mp4");
        privateMedia.setMediaType(MediaType.VIDEO);
        privateMedia.setPublic(false);
        privateMedia.setUploadedBy(5); // Owned by user 5
        
        when(mediaDb.getMediaFile(104)).thenReturn(privateMedia);
        when(liveStreamDb.addToQueue(104, 5)).thenReturn(3);
        
        LiveStreamQueue result = liveStreamService.addToQueue(5, 104);
        
        assertNotNull(result);
        assertEquals(104, result.getMediaFileId());
        verify(liveStreamDb).addToQueue(104, 5);
    }
    
    @Test
    void testRemoveFromQueue_OwnItem_Success() {
        LiveStreamQueue item = new LiveStreamQueue(1, 100, 5, 1);
        item.setStatus(LiveStreamQueue.QueueStatus.QUEUED);
        
        // Uses lightweight method now
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(Arrays.asList(item));
        when(liveStreamDb.removeFromQueue(1)).thenReturn(true);
        
        boolean result = liveStreamService.removeFromQueue(1, 5);
        
        assertTrue(result);
        verify(liveStreamDb).removeFromQueue(1);
    }
    
    @Test
    void testRemoveFromQueue_OtherUsersItem_ThrowsSecurityException() {
        LiveStreamQueue item = new LiveStreamQueue(1, 100, 10, 1); // Added by user 10
        item.setStatus(LiveStreamQueue.QueueStatus.QUEUED);
        
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(Arrays.asList(item));
        
        assertThrows(SecurityException.class, () -> {
            liveStreamService.removeFromQueue(1, 5); // User 5 trying to remove
        });
        
        verify(liveStreamDb, never()).removeFromQueue(anyInt());
    }
    
    @Test
    void testRemoveFromQueue_CurrentlyPlaying_ThrowsIllegalStateException() {
        LiveStreamQueue item = new LiveStreamQueue(1, 100, 5, 1);
        item.setStatus(LiveStreamQueue.QueueStatus.PLAYING);
        
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(Arrays.asList(item));
        
        assertThrows(IllegalStateException.class, () -> {
            liveStreamService.removeFromQueue(1, 5);
        });
    }
    
    @Test
    void testVoteSkip_ThresholdReached_SkipsMedia() {
        LiveStreamState state = new LiveStreamState();
        state.setRequiredSkipVotes(1); // Skip threshold is 1
        
        LiveStreamQueue currentItem = new LiveStreamQueue(1, 100, 5, 0);
        currentItem.setStatus(LiveStreamQueue.QueueStatus.PLAYING);
        
        // Uses fast getCurrentPlayingQueueId() now
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(1);
        when(liveStreamDb.addSkipVote(1, 10)).thenReturn(true);
        when(liveStreamDb.getSkipVoteCount(1)).thenReturn(1);
        when(liveStreamDb.getStreamState()).thenReturn(state);
        // For skipToNextFast -> selectAndPlayNextMediaFast
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(Arrays.asList(currentItem));
        
        boolean skipped = liveStreamService.voteSkip(10);
        
        assertTrue(skipped);
        verify(liveStreamDb).updateQueueStatus(1, LiveStreamQueue.QueueStatus.SKIPPED);
    }
    
    @Test
    void testVoteSkip_AlreadyVoted_ReturnsFalse() {
        // Uses fast getCurrentPlayingQueueId() now
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(1);
        when(liveStreamDb.addSkipVote(1, 10)).thenReturn(false); // Already voted
        
        boolean skipped = liveStreamService.voteSkip(10);
        
        assertFalse(skipped);
        verify(liveStreamDb, never()).updateQueueStatus(anyInt(), any());
    }
    
    @Test
    void testVoteSkip_NoCurrentlyPlaying_ReturnsFalse() {
        // Uses fast getCurrentPlayingQueueId() - returns null when nothing playing
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(null);
        
        boolean skipped = liveStreamService.voteSkip(10);
        
        assertFalse(skipped);
    }
    
    @Test
    void testCheckAndAdvanceIfNeeded_NoCurrentMedia_SelectsNew() {
        LiveStreamState state = new LiveStreamState();
        state.setCurrentMediaId(0); // No current media
        
        when(liveStreamDb.getStreamState()).thenReturn(state);
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(new ArrayList<>());
        
        // Mock random media selection
        MediaFile publicVideo = new MediaFile();
        publicVideo.setId(200);
        publicVideo.setMediaType(MediaType.VIDEO);
        when(mediaDb.getAllPublicMediaFiles()).thenReturn(Arrays.asList(publicVideo));
        when(liveStreamDb.addToQueue(200, 0)).thenReturn(5);
        
        liveStreamService.checkAndAdvanceIfNeeded();
        
        // Should have tried to select and play media using lightweight query
        verify(liveStreamDb, atLeastOnce()).getQueueItemsLightweight();
    }
    
    @Test
    void testGetCurrentPlayingQueueId_ReturnsCorrectId() {
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(42);
        
        // Verify the fast method is used in voteSkip
        when(liveStreamDb.addSkipVote(42, 10)).thenReturn(false);
        
        liveStreamService.voteSkip(10);
        
        verify(liveStreamDb).getCurrentPlayingQueueId();
        verify(liveStreamDb).addSkipVote(42, 10);
    }
    
    @Test
    void testSkipToNext_UsesCurrentPlayingQueueId() {
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(5);
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(new ArrayList<>());
        
        liveStreamService.skipToNext();
        
        // Should mark current as skipped
        verify(liveStreamDb).updateQueueStatus(5, LiveStreamQueue.QueueStatus.SKIPPED);
        verify(liveStreamDb).clearSkipVotesForItem(5);
    }
    
    @Test
    void testSkipToNext_NoCurrentPlaying_JustSelectsNext() {
        when(liveStreamDb.getCurrentPlayingQueueId()).thenReturn(null);
        when(liveStreamDb.getQueueItemsLightweight()).thenReturn(new ArrayList<>());
        
        liveStreamService.skipToNext();
        
        // Should not try to update any queue status
        verify(liveStreamDb, never()).updateQueueStatus(anyInt(), eq(LiveStreamQueue.QueueStatus.SKIPPED));
    }
}
