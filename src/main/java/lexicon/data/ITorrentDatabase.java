package lexicon.data;

import lexicon.object.TorrentDownload;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for the torrent → media mapping. qBittorrent owns the download state; this
 * only remembers which torrent produced which Lexicon media file, so a delete can reach
 * back and remove the torrent.
 */
public interface ITorrentDatabase {

    void save(TorrentDownload download);

    void updateStatus(String infoHash, TorrentDownload.Status status, String error);

    Optional<TorrentDownload> find(String infoHash);

    List<TorrentDownload> findActive();

    List<TorrentDownload> findRecentByUser(int userId, int limit);

    int countActiveForUser(int userId);

    void delete(String infoHash);

    // ── imported files ───────────────────────────────────────────────────────────────

    void recordImportedFile(String infoHash, int fileIndex, int mediaFileId, String linkPath);

    /** The torrent file behind a Lexicon media id, if that media came from a torrent. */
    Optional<TorrentDownload.ImportedFile> findByMediaFileId(int mediaFileId);

    List<TorrentDownload.ImportedFile> findImportedFiles(String infoHash);

    void deleteImportedFile(String infoHash, int fileIndex);
}
