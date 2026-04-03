package lexicon.data;

import lexicon.object.ChatFile;

public interface IChatFileDatabase {
    long addChatFile(ChatFile chatFile);
    ChatFile getChatFile(long id);
}
