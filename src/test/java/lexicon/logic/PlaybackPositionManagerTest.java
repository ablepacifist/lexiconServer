import lexicon.data.IMediaDatabase;
import lexicon.logic.PlaybackPositionManager;
import lexicon.object.PlaybackPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlaybackPositionManager
 */
public class PlaybackPositionManagerTest {
    
    private IMediaDatabase mockDatabase;
    private PlaybackPositionManager manager;
    
    @BeforeEach
    public void setUp() {
        mockDatabase = mock(IMediaDatabase.class);
        manager = new PlaybackPositionManager(mockDatabase);
    }
    
    @Test
    public void testSavePosition_Success() {
        // Arrange
        int userId = 1;
        int mediaFileId = 100;
        double position = 120.5;
        double duration = 3600.0;
        boolean completed = false;
        
        doNothing().when(mockDatabase).savePlaybackPosition(any(PlaybackPosition.class));
        
        // Act
        boolean result = manager.savePosition(userId, mediaFileId, position, duration, completed);
        
        // Assert
        assertTrue(result);
        
        ArgumentCaptor<PlaybackPosition> captor = ArgumentCaptor.forClass(PlaybackPosition.class);
        verify(mockDatabase, times(1)).savePlaybackPosition(captor.capture());
        
        PlaybackPosition saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(mediaFileId, saved.getMediaFileId());
        assertEquals(position, saved.getPosition(), 0.001);
        assertEquals(duration, saved.getDuration(), 0.001);
        assertEquals(completed, saved.isCompleted());
    }
    
    @Test
    public void testSavePosition_DatabaseException() {
        // Arrange
        doThrow(new RuntimeException("Database error")).when(mockDatabase).savePlaybackPosition(any());
        
        // Act
        boolean result = manager.savePosition(1, 100, 120.5, 3600.0, false);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    public void testGetPosition_Found() {
        // Arrange
        int userId = 1;
        int mediaFileId = 100;
        PlaybackPosition expected = new PlaybackPosition();
        expected.setUserId(userId);
        expected.setMediaFileId(mediaFileId);
        expected.setPosition(250.0);
        expected.setDuration(3600.0);
        
        when(mockDatabase.getPlaybackPosition(userId, mediaFileId)).thenReturn(expected);
        
        // Act
        PlaybackPosition result = manager.getPosition(userId, mediaFileId);
        
        // Assert
        assertNotNull(result);
        assertEquals(expected.getUserId(), result.getUserId());
        assertEquals(expected.getMediaFileId(), result.getMediaFileId());
        assertEquals(expected.getPosition(), result.getPosition(), 0.001);
    }
    
    @Test
    public void testGetPosition_NotFound() {
        // Arrange
        when(mockDatabase.getPlaybackPosition(1, 100)).thenReturn(null);
        
        // Act
        PlaybackPosition result = manager.getPosition(1, 100);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    public void testGetPosition_DatabaseException() {
        // Arrange
        when(mockDatabase.getPlaybackPosition(1, 100)).thenThrow(new RuntimeException("Database error"));
        
        // Act
        PlaybackPosition result = manager.getPosition(1, 100);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    public void testGetUserPositions_MultipleResults() {
        // Arrange
        int userId = 1;
        PlaybackPosition pos1 = new PlaybackPosition();
        pos1.setUserId(userId);
        pos1.setMediaFileId(100);
        
        PlaybackPosition pos2 = new PlaybackPosition();
        pos2.setUserId(userId);
        pos2.setMediaFileId(101);
        
        List<PlaybackPosition> expected = Arrays.asList(pos1, pos2);
        when(mockDatabase.getUserPlaybackPositions(userId)).thenReturn(expected);
        
        // Act
        List<PlaybackPosition> result = manager.getUserPositions(userId);
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(100, result.get(0).getMediaFileId());
        assertEquals(101, result.get(1).getMediaFileId());
    }
    
    @Test
    public void testGetUserPositions_EmptyResult() {
        // Arrange
        when(mockDatabase.getUserPlaybackPositions(1)).thenReturn(List.of());
        
        // Act
        List<PlaybackPosition> result = manager.getUserPositions(1);
        
        // Assert
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testGetUserPositions_DatabaseException() {
        // Arrange
        when(mockDatabase.getUserPlaybackPositions(1)).thenThrow(new RuntimeException("Database error"));
        
        // Act
        List<PlaybackPosition> result = manager.getUserPositions(1);
        
        // Assert
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testDeletePosition_Success() {
        // Arrange
        doNothing().when(mockDatabase).deletePlaybackPosition(1, 100);
        
        // Act
        boolean result = manager.deletePosition(1, 100);
        
        // Assert
        assertTrue(result);
        verify(mockDatabase, times(1)).deletePlaybackPosition(1, 100);
    }
    
    @Test
    public void testDeletePosition_DatabaseException() {
        // Arrange
        doThrow(new RuntimeException("Database error")).when(mockDatabase).deletePlaybackPosition(1, 100);
        
        // Act
        boolean result = manager.deletePosition(1, 100);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    public void testShouldMarkCompleted_NearEnd() {
        // Within 30 seconds of end
        assertTrue(manager.shouldMarkCompleted(3570.0, 3600.0));
        assertTrue(manager.shouldMarkCompleted(3590.0, 3600.0));
        assertTrue(manager.shouldMarkCompleted(3599.9, 3600.0));
    }
    
    @Test
    public void testShouldMarkCompleted_NotNearEnd() {
        // More than 30 seconds from end
        assertFalse(manager.shouldMarkCompleted(0.0, 3600.0));
        assertFalse(manager.shouldMarkCompleted(1800.0, 3600.0));
        assertFalse(manager.shouldMarkCompleted(3500.0, 3600.0));
    }
    
    @Test
    public void testShouldMarkCompleted_EdgeCase() {
        // Exactly 30 seconds from end
        assertTrue(manager.shouldMarkCompleted(3570.0, 3600.0));
        // Just over 30 seconds from end
        assertFalse(manager.shouldMarkCompleted(3569.9, 3600.0));
    }
}
