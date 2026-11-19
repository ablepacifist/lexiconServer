package lexicon.object;

import java.time.LocalDateTime;

/**
 * A torrent Lexicon asked qBittorrent to download.
 *
 * qBittorrent stays the source of truth for progress — this row exists so that a Lexicon
 * media file can be traced back to the torrent that produced it, which is what lets deleting
 * the media also delete the torrent. It therefore survives a server restart, while the
 * in-memory progress view does not.
 */
public class TorrentDownload {

    public enum Status {
        /** Added to qBittorrent, stopped, waiting for metadata before files can be chosen. */
        PREPARING,
        DOWNLOADING,
        /** Downloaded; the wanted files are being linked into Lexicon. */
        IMPORTING,
        /** Everything wanted was imported. qBittorrent keeps seeding. */
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String infoHash;
    private String tag;
    private String source;
    private String displayName;
    private int addedBy;
    private String mediaType;
    private boolean isPublic;
    private Status status = Status.PREPARING;
    private String error;
    private LocalDateTime addedAt;
    private LocalDateTime completedAt;

    public TorrentDownload() {
    }

    public TorrentDownload(String infoHash, String tag, String source, String displayName,
                           int addedBy, String mediaType, boolean isPublic) {
        this.infoHash = infoHash;
        this.tag = tag;
        this.source = source;
        this.displayName = displayName;
        this.addedBy = addedBy;
        this.mediaType = mediaType;
        this.isPublic = isPublic;
        this.addedAt = LocalDateTime.now();
    }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getAddedBy() { return addedBy; }
    public void setAddedBy(int addedBy) { this.addedBy = addedBy; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    /** Still being worked on, so it counts against the per-user limit. */
    public boolean isActive() {
        return status == Status.PREPARING || status == Status.DOWNLOADING || status == Status.IMPORTING;
    }

    /** One file of a torrent that became a Lexicon media file. */
    public record ImportedFile(String infoHash, int fileIndex, int mediaFileId, String linkPath) {
    }
}
