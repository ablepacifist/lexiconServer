package lexicon.object;

import java.time.LocalDateTime;

/**
 * Poll entity — a single question ("where to eat") belonging to an event,
 * with one or more options that voters check.
 */
public class Poll {
    private long id;
    private long eventId;
    private String question;
    private boolean allowAddOptions = true;
    private int displayOrder;
    private LocalDateTime createdAt;

    public Poll() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public boolean isAllowAddOptions() { return allowAddOptions; }
    public void setAllowAddOptions(boolean allowAddOptions) { this.allowAddOptions = allowAddOptions; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Poll{id=" + id + ", eventId=" + eventId + ", question='" + question + "'}";
    }
}
