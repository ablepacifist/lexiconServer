package lexicon.data;

import lexicon.object.MediaFile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of IMediaDatabase for testing
 * In-memory storage using ConcurrentHashMap
 */
public class MockMediaDatabase implements IMediaDatabase {
    
    private final Map<Integer, MediaFile> mediaFiles = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> fileData = new ConcurrentHashMap<>();
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
    public byte[] getFileData(int mediaFileId) {
        return fileData.get(mediaFileId);
    }
    
    @Override
    public void deleteFileData(int mediaFileId) {
        fileData.remove(mediaFileId);
    }
    
    // Utility method for tests
    public void clear() {
        mediaFiles.clear();
        fileData.clear();
        nextMediaFileId = 1;
    }
}
