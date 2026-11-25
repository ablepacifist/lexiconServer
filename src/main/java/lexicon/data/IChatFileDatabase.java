package lexicon.data;

import lexicon.object.ChatFile;

public interface IChatFileDatabase {
    long addChatFile(ChatFile chatFile);
    ChatFile getChatFile(long id);
    /** Every chat upload, newest first (admin storage view). */
    java.util.List<ChatFile> getAllChatFiles();
    /** Removes the row only; the stored files are the caller's to remove. */
    void deleteChatFile(long id);
}
