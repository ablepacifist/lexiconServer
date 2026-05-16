package lexicon.logic;

import lexicon.object.TextMessage;
import java.util.List;

/**
 * Service interface for text message management
 * Used by Mumble bridge for chat message operations
 */
public interface MessageManagerService {

    /**
     * Store a new message
     * @return the generated message ID
     */
    long addMessage(TextMessage message);

    /**
     * Get a message by ID
     */
    TextMessage getMessageById(long messageId);

    /**
     * Get messages for a channel with pagination
     */
    List<TextMessage> getMessagesByChannel(int channelId, int limit, String beforeTimestamp);

    /**
     * Update a message (only owner can edit)
     */
    boolean updateMessage(long messageId, int userId, String newContent);

    /**
     * Soft-delete a message (only owner can delete)
     */
    boolean deleteMessage(long messageId, int userId);

    /**
     * Search messages by content
     */
    List<TextMessage> searchMessages(String searchTerm, int channelId);
}
