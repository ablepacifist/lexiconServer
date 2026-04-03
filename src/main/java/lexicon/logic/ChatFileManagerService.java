package lexicon.logic;

import lexicon.object.ChatFile;

import java.util.Map;

/**
 * Service interface for chat file upload operations
 */
public interface ChatFileManagerService {

    /**
     * Store an uploaded chat file (image/GIF), generate thumbnail, persist metadata,
     * and return the full API response map with URLs.
     */
    Map<String, Object> uploadChatFile(byte[] fileData, String originalFilename, String mimeType,
                                       int userId, Integer channelId);

    /**
     * Get the original file for serving. Returns null if file doesn't exist.
     */
    ChatFileServingInfo getFileForServing(long fileId);

    /**
     * Get the thumbnail for serving. Falls back to original if no thumbnail. Returns null if not found.
     */
    ChatFileServingInfo getThumbnailForServing(long fileId);

    /**
     * Simple result holder for file serving responses.
     */
    class ChatFileServingInfo {
        private final java.nio.file.Path path;
        private final String contentType;

        public ChatFileServingInfo(java.nio.file.Path path, String contentType) {
            this.path = path;
            this.contentType = contentType;
        }

        public java.nio.file.Path getPath() { return path; }
        public String getContentType() { return contentType; }
    }
}
