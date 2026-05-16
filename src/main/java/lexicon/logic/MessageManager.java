package lexicon.logic;

import lexicon.data.IChatFileDatabase;
import lexicon.data.IMessageDatabase;
import lexicon.object.ChatFile;
import lexicon.object.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of MessageManagerService
 * Handles text message operations for Mumble bridge integration
 */
@Service
public class MessageManager implements MessageManagerService {

    private static final Set<String> ATTACHMENT_TYPES = Set.of("IMAGE", "GIF", "MIXED");

    private final IMessageDatabase messageDatabase;
    private final IChatFileDatabase chatFileDatabase;

    @Autowired
    public MessageManager(IMessageDatabase messageDatabase, IChatFileDatabase chatFileDatabase) {
        this.messageDatabase = messageDatabase;
        this.chatFileDatabase = chatFileDatabase;
    }

    @Override
    public long addMessage(TextMessage message) {
        // IMAGE/GIF messages may have empty content (caption is optional)
        boolean isAttachmentMessage = message.getMessageType() != null && ATTACHMENT_TYPES.contains(message.getMessageType());
        if (!isAttachmentMessage && (message.getContent() == null || message.getContent().trim().isEmpty())) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        if (message.getMessageType() == null || message.getMessageType().isEmpty()) {
            message.setMessageType("TEXT");
        }
        return messageDatabase.addMessage(message);
    }

    @Override
    public TextMessage getMessageById(long messageId) {
        TextMessage message = messageDatabase.getMessage(messageId);
        if (message != null) {
            enrichWithAttachment(message);
        }
        return message;
    }

    @Override
    public List<TextMessage> getMessagesByChannel(int channelId, int limit, String beforeTimestamp) {
        if (limit <= 0 || limit > 200) {
            limit = 50; // Default/max sensible limit
        }
        List<TextMessage> messages = messageDatabase.getMessagesByChannel(channelId, limit, beforeTimestamp);
        messages.forEach(this::enrichWithAttachment);
        return messages;
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
        List<TextMessage> messages = messageDatabase.searchMessages(searchTerm.trim(), channelId);
        messages.forEach(this::enrichWithAttachment);
        return messages;
    }

    /**
     * If a message has messageType IMAGE/GIF/MIXED and a mediaFileId, attach the chat file metadata.
     */
    private void enrichWithAttachment(TextMessage message) {
        if (message.getMediaFileId() == null) return;
        String type = message.getMessageType();
        if (type == null || !ATTACHMENT_TYPES.contains(type)) return;

        ChatFile chatFile = chatFileDatabase.getChatFile(message.getMediaFileId());
        if (chatFile == null) return;

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("id", chatFile.getId());
        attachment.put("url", "/api/chat/files/" + chatFile.getId());
        attachment.put("thumbnailUrl", "/api/chat/files/" + chatFile.getId() + "/thumb");
        attachment.put("originalFilename", chatFile.getOriginalFilename());
        attachment.put("mimeType", chatFile.getMimeType());
        attachment.put("width", chatFile.getWidth());
        attachment.put("height", chatFile.getHeight());
        attachment.put("fileSize", chatFile.getFileSize());
        message.setAttachment(attachment);
    }
}
