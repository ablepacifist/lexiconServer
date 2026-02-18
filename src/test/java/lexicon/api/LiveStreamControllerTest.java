package lexicon.api;

import lexicon.service.LiveStreamService;
import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LiveStreamController REST endpoints
 */
public class LiveStreamControllerTest {

    @Mock
    private LiveStreamService liveStreamService;
    
    private LiveStreamController liveStreamController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        liveStreamController = new LiveStreamController();
        // Inject mock via reflection
        try {
            var field = LiveStreamController.class.getDeclaredField("liveStreamService");
            field.setAccessible(true);
            field.set(liveStreamController, liveStreamService);
        } catch (Exception e) {
            fail("Failed to inject mock: " + e.getMessage());
        }
    }

    @Test
    public void testGetState() {
        // Create mock state
        LiveStreamState mockState = new LiveStreamState();
        mockState.setCurrentMediaId(1);
        mockState.setCurrentPositionMs(5000);
        mockState.setRequiredSkipVotes(1);
        
        when(liveStreamService.getStreamState()).thenReturn(mockState);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.getState();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("success"));
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().containsKey("state"));
    }

    @Test
    public void testGetQueue() {
        // Create mock queue
        List<LiveStreamQueue> mockQueue = new ArrayList<>();
        LiveStreamQueue item = new LiveStreamQueue();
        item.setId(1);
        item.setMediaFileId(10);
        item.setAddedBy(5);
        mockQueue.add(item);
        
        when(liveStreamService.getQueue()).thenReturn(mockQueue);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.getQueue();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(1, response.getBody().get("count"));
    }

    @Test
    public void testAddToQueue() {
        // Create mock queue item
        LiveStreamQueue mockItem = new LiveStreamQueue();
        mockItem.setId(1);
        mockItem.setMediaFileId(10);
        mockItem.setAddedBy(5);
        
        when(liveStreamService.addToQueue(eq(5), eq(10))).thenReturn(mockItem);
        
        Map<String, Integer> request = new HashMap<>();
        request.put("userId", 5);
        request.put("mediaFileId", 10);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.addToQueue(request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().containsKey("queueItem"));
    }

    @Test
    public void testAddToQueueMissingParams() {
        Map<String, Integer> request = new HashMap<>();
        request.put("userId", 5);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.addToQueue(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    public void testRemoveFromQueue() {
        when(liveStreamService.removeFromQueue(eq(1), eq(5))).thenReturn(true);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.removeFromQueue(1, 5);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    public void testVoteSkip() {
        when(liveStreamService.voteSkip(eq(5))).thenReturn(true);
        
        Map<String, Integer> request = new HashMap<>();
        request.put("userId", 5);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.voteSkip(request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue((Boolean) response.getBody().get("skipped"));
    }

    @Test
    public void testVoteSkipMissingUserId() {
        Map<String, Integer> request = new HashMap<>();
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.voteSkip(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    public void testAdvanceToNext() {
        doNothing().when(liveStreamService).checkAndAdvanceIfNeeded();
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.advanceToNext();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
