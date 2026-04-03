package lexicon.logic;

import lexicon.object.ChatFile;

/**
 * Service interface for chat file upload operations
 */
public interface ChatFileManagerService {

    /**
     * Store an uploaded chat file (image/GIF), generate thumbnail, and persist metadata.
     * @return the persisted ChatFile with generated ID and URLs
     */
    ChatFile uploadChatFile(byte[] fileData, String originalFilename, String mimeType,
                            int userId, Integer channelId);

    /**
     * Get chat file metadata by ID
     */
    ChatFile getChatFile(long fileId);

    /**
     * Get the full filesystem path for a chat file's stored file
     */
    java.nio.file.Path getFilePath(ChatFile chatFile);

    /**
     * Get the full filesystem path for a chat file's thumbnail
     */
    java.nio.file.Path getThumbnailPath(ChatFile chatFile);
}
