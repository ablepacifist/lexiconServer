package lexicon.logic;

import lexicon.data.IMessageDatabase;
import lexicon.object.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of MessageManagerService
 * Handles text message operations for Mumble bridge integration
 */
@Service
public class MessageManager implements MessageManagerService {

    private final IMessageDatabase messageDatabase;

    @Autowired
    public MessageManager(IMessageDatabase messageDatabase) {
        this.messageDatabase = messageDatabase;
    }

    @Override
    public long addMessage(TextMessage message) {
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        if (message.getMessageType() == null || message.getMessageType().isEmpty()) {
            message.setMessageType("TEXT");
        }
        return messageDatabase.addMessage(message);
    }

    @Override
    public TextMessage getMessageById(long messageId) {
        return messageDatabase.getMessage(messageId);
    }

    @Override
    public List<TextMessage> getMessagesByChannel(int channelId, int limit, String beforeTimestamp) {
        if (limit <= 0 || limit > 200) {
            limit = 50; // Default/max sensible limit
        }
        return messageDatabase.getMessagesByChannel(channelId, limit, beforeTimestamp);
    }

    @Override
    public boolean updateMessage(long messageId, int userId, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        return messageDatabase.updateMessage(messageId, userId, newContent.trim());
    }

    @Override
    public boolean deleteMessage(long messageId, int userId) {
        return messageDatabase.deleteMessage(messageId, userId);
    }

    @Override
    public List<TextMessage> searchMessages(String searchTerm, int channelId) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Search term cannot be empty");
        }
        return messageDatabase.searchMessages(searchTerm.trim(), channelId);
    }
}
