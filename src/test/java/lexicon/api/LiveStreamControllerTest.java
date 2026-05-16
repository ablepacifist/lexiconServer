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

public class LiveStreamControllerTest {

    @Mock
    private LiveStreamService liveStreamService;
    
    private LiveStreamController liveStreamController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        liveStreamController = new LiveStreamController();
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
        LiveStreamState mockState = new LiveStreamState();
        mockState.setCurrentMediaId(1);
        mockState.setChannel("video");
        
        when(liveStreamService.getStreamState("video")).thenReturn(mockState);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.getState("video");
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    public void testGetQueue() {
        List<LiveStreamQueue> mockQueue = new ArrayList<>();
        LiveStreamQueue item = new LiveStreamQueue();
        item.setId(1);
        item.setChannel("music");
        mockQueue.add(item);
        
        when(liveStreamService.getQueue("music")).thenReturn(mockQueue);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.getQueue("music");
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("count"));
    }

    @Test
    public void testAddToQueue() {
        LiveStreamQueue mockItem = new LiveStreamQueue();
        mockItem.setId(1);
        mockItem.setChannel("video");
        
        when(liveStreamService.addToQueue(eq("video"), eq(5), eq(10))).thenReturn(mockItem);
        
        Map<String, Integer> request = new HashMap<>();
        request.put("userId", 5);
        request.put("mediaFileId", 10);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.addToQueue("video", request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    public void testVoteSkip() {
        when(liveStreamService.voteSkip("video", 5)).thenReturn(true);
        
        Map<String, Integer> request = new HashMap<>();
        request.put("userId", 5);
        
        ResponseEntity<Map<String, Object>> response = liveStreamController.voteSkip("video", request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("skipped"));
    }
}
