package lexicon.service;

import lexicon.data.ILiveStreamDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.object.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class LiveStreamServiceTest {

    @Mock
    private ILiveStreamDatabase liveStreamDb;
    
    @Mock
    private IMediaDatabase mediaDb;
    
    @Mock
    private IPlaylistDatabase playlistDb;
    
    private LiveStreamService liveStreamService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        liveStreamService = new LiveStreamService();
        try {
            var dbField = LiveStreamService.class.getDeclaredField("liveStreamDb");
            dbField.setAccessible(true);
            dbField.set(liveStreamService, liveStreamDb);
            
            var mediaField = LiveStreamService.class.getDeclaredField("mediaDb");
            mediaField.setAccessible(true);
            mediaField.set(liveStreamService, mediaDb);
            
            var playlistField = LiveStreamService.class.getDeclaredField("playlistDb");
            playlistField.setAccessible(true);
            playlistField.set(liveStreamService, playlistDb);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }
    }

    @Test
    void testGetStreamState() {
        LiveStreamState mockState = new LiveStreamState();
        mockState.setCurrentMediaId(1);
        mockState.setChannel("video");
        
        when(liveStreamDb.getStreamState("video")).thenReturn(mockState);
        
        LiveStreamState result = liveStreamService.getStreamState("video");
        assertEquals(1, result.getCurrentMediaId());
        assertEquals("video", result.getChannel());
    }

    @Test
    void testGetQueue() {
        List<LiveStreamQueue> mockQueue = new ArrayList<>();
        LiveStreamQueue item = new LiveStreamQueue();
        item.setId(1);
        item.setChannel("music");
        mockQueue.add(item);
        
        when(liveStreamDb.getQueueItemsLightweight("music")).thenReturn(mockQueue);
        
        List<LiveStreamQueue> result = liveStreamService.getQueue("music");
        assertEquals(1, result.size());
        assertEquals("music", result.get(0).getChannel());
    }

    @Test
    void testAddToQueueVideo() {
        MediaFile media = new MediaFile();
        media.setId(100);
        media.setMediaType(MediaType.VIDEO);
        media.setPublic(true);
        
        when(mediaDb.getMediaFile(100)).thenReturn(media);
        when(liveStreamDb.addToQueue("video", 100, 5)).thenReturn(1);
        
        LiveStreamQueue result = liveStreamService.addToQueue("video", 5, 100);
        assertEquals(100, result.getMediaFileId());
    }

    @Test
    void testAddToQueueMusicToVideoChannelFails() {
        MediaFile media = new MediaFile();
        media.setId(101);
        media.setMediaType(MediaType.MUSIC);
        media.setPublic(true);
        
        when(mediaDb.getMediaFile(101)).thenReturn(media);
        
        assertThrows(IllegalArgumentException.class, () -> {
            liveStreamService.addToQueue("video", 5, 101);
        });
    }

    @Test
    void testAddToQueueVideoToMusicChannelFails() {
        MediaFile media = new MediaFile();
        media.setId(102);
        media.setMediaType(MediaType.VIDEO);
        media.setPublic(true);
        
        when(mediaDb.getMediaFile(102)).thenReturn(media);
        
        assertThrows(IllegalArgumentException.class, () -> {
            liveStreamService.addToQueue("music", 5, 102);
        });
    }
}
