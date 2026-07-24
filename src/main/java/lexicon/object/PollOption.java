package lexicon.object;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PollOption entity — a single choice within a poll. Options can come from
 * the poll creator's seed list or be added later by any voter (which also
 * counts as that voter's vote for it).
 */
public class PollOption {
    private long id;
    private long pollId;
    private String text;
    private String addedByName; // nullable — null for seed options
    private LocalDateTime createdAt;

    // Transient, not database columns — populated only by the logic layer
    // (getPollDetail/addOption), never by the repository.
    private int voteCount;
    private List<String> voters;
    private boolean votedByMe;

    public PollOption() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPollId() { return pollId; }
    public void setPollId(long pollId) { this.pollId = pollId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAddedByName() { return addedByName; }
    public void setAddedByName(String addedByName) { this.addedByName = addedByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }

    public List<String> getVoters() { return voters; }
    public void setVoters(List<String> voters) { this.voters = voters; }

    public boolean isVotedByMe() { return votedByMe; }
    public void setVotedByMe(boolean votedByMe) { this.votedByMe = votedByMe; }

    @Override
    public String toString() {
        return "PollOption{id=" + id + ", pollId=" + pollId + ", text='" + text + "'}";
    }
}
