package lexicon.object;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event entity — a get-together (e.g. "My Birthday Aug 7") that owns one or
 * more polls, shared with friends via a single link.
 */
public class Event {
    private long id;
    private String title;
    private String description;
    private LocalDate eventDate;
    private Integer createdByUserId; // nullable
    private LocalDateTime createdAt;

    // Transient, not a database column — populated only by the logic layer's
    // listEvents() method (via getPollsByEventId(id).size()); defaults to 0.
    private int pollCount = 0;

    public Event() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getPollCount() { return pollCount; }
    public void setPollCount(int pollCount) { this.pollCount = pollCount; }

    @Override
    public String toString() {
        return "Event{id=" + id + ", title='" + title + "', eventDate=" + eventDate + "}";
    }
}
