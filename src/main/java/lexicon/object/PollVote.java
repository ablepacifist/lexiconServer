package lexicon.object;

import java.time.LocalDateTime;

/**
 * PollVote entity — a raw vote row (one voter, one option). Raw row DTO
 * returned by the repository's getVotesByOptionId; no computed fields.
 */
public class PollVote {
    private long id;
    private long optionId;
    private String voterKey;
    private String voterName;
    private LocalDateTime createdAt;

    public PollVote() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getOptionId() { return optionId; }
    public void setOptionId(long optionId) { this.optionId = optionId; }

    public String getVoterKey() { return voterKey; }
    public void setVoterKey(String voterKey) { this.voterKey = voterKey; }

    public String getVoterName() { return voterName; }
    public void setVoterName(String voterName) { this.voterName = voterName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "PollVote{id=" + id + ", optionId=" + optionId + ", voterName='" + voterName + "'}";
    }
}
