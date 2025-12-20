package lexicon.data;

import lexicon.object.MediaFile;
import lexicon.object.PlaybackPosition;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.InputStream;

/**
 * Mock implementation of IMediaDatabase for testing
 * In-memory storage using ConcurrentHashMap
 */
public class MockMediaDatabase implements IMediaDatabase {
    
    private final Map<Integer, MediaFile> mediaFiles = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> fileData = new ConcurrentHashMap<>();
    private final Map<String, PlaybackPosition> playbackPositions = new ConcurrentHashMap<>();
    private int nextMediaFileId = 1;
    
    @Override
    public int getNextMediaFileId() {
        return nextMediaFileId++;
    }
    
    @Override
    public void addMediaFile(MediaFile mediaFile) {
        if (mediaFile == null) {
            throw new IllegalArgumentException("MediaFile cannot be null");
        }
        mediaFiles.put(mediaFile.getId(), mediaFile);
    }
    
    @Override
    public MediaFile getMediaFile(int mediaFileId) {
        return mediaFiles.get(mediaFileId);
    }
    
    @Override
    public List<MediaFile> getMediaFilesByPlayer(int playerId) {
        return mediaFiles.values().stream()
                .filter(mf -> mf.getUploadedBy() == playerId)
                .sorted((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        return mediaFiles.values().stream()
                .filter(MediaFile::isPublic)
                .sorted((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        String lowerSearchTerm = searchTerm.toLowerCase();
        return mediaFiles.values().stream()
                .filter(mf -> mf.getTitle().toLowerCase().contains(lowerSearchTerm) ||
                             mf.getDescription().toLowerCase().contains(lowerSearchTerm))
                .sorted((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()))
                .collect(Collectors.toList());
    }
    
    @Override
    public void updateMediaFile(MediaFile mediaFile) {
        if (mediaFile != null && mediaFiles.containsKey(mediaFile.getId())) {
            mediaFiles.put(mediaFile.getId(), mediaFile);
        }
    }
    
    @Override
    public void deleteMediaFile(int mediaFileId) {
        mediaFiles.remove(mediaFileId);
        fileData.remove(mediaFileId);
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        return mediaFiles.values().stream()
                .sorted((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public void storeFileData(int mediaFileId, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("File data cannot be null");
        }
        fileData.put(mediaFileId, data);
    }
    
    @Override
    public void storeFileDataStreaming(int mediaFileId, InputStream inputStream, long fileSize) {
        try {
            byte[] data = inputStream.readAllBytes();
            storeFileData(mediaFileId, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file data (streaming): " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] getFileData(int mediaFileId) {
        return fileData.get(mediaFileId);
    }
    
    @Override
    public void deleteFileData(int mediaFileId) {
        fileData.remove(mediaFileId);
    }
    
    @Override
    public void savePlaybackPosition(PlaybackPosition position) {
        if (position == null) {
            throw new IllegalArgumentException("PlaybackPosition cannot be null");
        }
        String key = position.getUserId() + "_" + position.getMediaFileId();
        playbackPositions.put(key, position);
    }
    
    @Override
    public PlaybackPosition getPlaybackPosition(int userId, int mediaFileId) {
        String key = userId + "_" + mediaFileId;
        return playbackPositions.get(key);
    }
    
    @Override
    public List<PlaybackPosition> getUserPlaybackPositions(int userId) {
        return playbackPositions.values().stream()
                .filter(p -> p.getUserId() == userId)
                .sorted((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated()))
                .collect(Collectors.toList());
    }
    
    @Override
    public void deletePlaybackPosition(int userId, int mediaFileId) {
        String key = userId + "_" + mediaFileId;
        playbackPositions.remove(key);
    }
    
    // Utility method for tests
    public void clear() {
        mediaFiles.clear();
        fileData.clear();
        playbackPositions.clear();
        nextMediaFileId = 1;
    }
}
