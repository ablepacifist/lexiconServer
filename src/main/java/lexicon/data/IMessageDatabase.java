package lexicon.data;

import lexicon.object.TextMessage;
import java.util.List;

/**
 * Interface for text message database operations
 * Used by Mumble bridge for chat message storage
 */
public interface IMessageDatabase {

    /**
     * Store a new text message
     * @return the generated message ID
     */
    long addMessage(TextMessage message);

    /**
     * Get a message by ID (excludes soft-deleted)
     */
    TextMessage getMessage(long messageId);

    /**
     * Get messages for a channel with pagination
     * @param channelId the channel to query
     * @param limit max messages to return
     * @param beforeTimestamp only return messages created before this (ISO string), null for latest
     * @return list of messages ordered by createdAt DESC
     */
    List<TextMessage> getMessagesByChannel(int channelId, int limit, String beforeTimestamp);

    /**
     * Update a message's content (sets editedAt)
     */
    boolean updateMessage(long messageId, int userId, String newContent);

    /**
     * Soft-delete a message (sets deletedAt)
     */
    boolean deleteMessage(long messageId, int userId);

    /**
     * Search messages by content text
     * @param searchTerm text to search for
     * @param channelId optional channel filter (-1 for all channels)
     */
    List<TextMessage> searchMessages(String searchTerm, int channelId);
}
