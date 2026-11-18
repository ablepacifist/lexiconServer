package lexicon.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lexicon.config.StorageProperties;
import lexicon.data.ITorrentDatabase;
import lexicon.logic.MediaManager;
import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import lexicon.object.TorrentDownload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Downloads torrents into Lexicon through the local qBittorrent, which runs bound to the
 * Proton VPN tunnel.
 *
 * Three rules shape everything here:
 *
 *  - **Only .mp3 and .mp4 are ever fetched.** The torrent is added stopped, every other file
 *    is set to "do not download", and only then is it started, so nothing outside the
 *    allowlist touches the disk even briefly.
 *  - **Nothing launches qBittorrent.** Proton has no kill switch on this machine by design,
 *    so a client started before it is bound would announce the real IP to trackers.
 *    Submission is refused unless qBittorrent is already up and bound to the live tunnel.
 *  - **Importing is a hard link, not a copy.** qBittorrent keeps its own path and goes on
 *    seeding; Lexicon gets the same bytes under its own media layout. Deleting the media
 *    removes that link, and removes the torrent once its last file is gone.
 */
@Service
public class TorrentDownloadService implements MediaManager.TorrentCleanup {

    private static final Logger log = LoggerFactory.getLogger(TorrentDownloadService.class);

    /** The only extensions that are ever downloaded or imported. */
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".mp3", ".mp4");

    private static final Pattern MAGNET =
            Pattern.compile("^magnet:\\?.*xt=urn:btih:([0-9A-Fa-f]{40}|[A-Z2-7]{32}).*", Pattern.CASE_INSENSITIVE);

    /** A .torrent is a bencoded dictionary, so it always starts with 'd'. */
    private static final int MAX_TORRENT_FILE_BYTES = 1024 * 1024;

    /** How long a magnet may sit without metadata before we give up on it. */
    private static final Duration METADATA_TIMEOUT = Duration.ofMinutes(10);

    private final QBittorrentClient qbittorrent;
    private final ITorrentDatabase torrentDatabase;
    private final MediaManagerService mediaManager;
    private final MediaManager mediaManagerImpl;
    private final StorageProperties storageProperties;
    private final WslDiskUsageService diskUsageService;
    private final VpnStatusService vpnStatus;

    private final String saveRoot;
    private final int maxActivePerUser;
    private final long minFreeBytes;

    /** Live progress, rebuilt from qBittorrent on every poll. Hash → latest view. */
    private final Map<String, QBittorrentClient.TorrentInfo> progress = new ConcurrentHashMap<>();

    /** Guards the reconciler against overlapping runs when an import takes a while. */
    private volatile boolean reconciling;

    /**
     * Whether the {@code lexicon} category has been created in qBittorrent. The usual order
     * on this machine is server first, qBittorrent later — started by hand once Proton is
     * connected — so the startup attempt normally talks to nothing and the category has to
     * be created on the first submit instead.
     */
    private volatile boolean categoryEnsured;

    @Autowired
    public TorrentDownloadService(QBittorrentClient qbittorrent, ITorrentDatabase torrentDatabase,
                                  MediaManagerService mediaManager, MediaManager mediaManagerImpl,
                                  StorageProperties storageProperties, WslDiskUsageService diskUsageService,
                                  VpnStatusService vpnStatus,
                                  @Value("${torrent.save-root:}") String saveRoot,
                                  @Value("${torrent.max-active-per-user:3}") int maxActivePerUser,
                                  @Value("${torrent.min-free-gb:20}") long minFreeGb) {
        this.qbittorrent = qbittorrent;
        this.torrentDatabase = torrentDatabase;
        this.mediaManager = mediaManager;
        this.mediaManagerImpl = mediaManagerImpl;
        this.storageProperties = storageProperties;
        this.diskUsageService = diskUsageService;
        this.vpnStatus = vpnStatus;
        this.saveRoot = (saveRoot == null || saveRoot.isBlank())
                ? storageProperties.getBasePath() + "/torrents/complete"
                : saveRoot.trim();
        this.maxActivePerUser = maxActivePerUser;
        this.minFreeBytes = minFreeGb * 1024L * 1024L * 1024L;
    }

    @PostConstruct
    void register() {
        // Wired here rather than in MediaManager's constructor because the dependency runs
        // both ways: deletes call into this service, imports call back into the manager.
        mediaManagerImpl.setTorrentCleanup(this);
        ensureCategory();
    }

    /**
     * Idempotent, and does nothing while qBittorrent is unreachable — the usual case at
     * startup, since qBittorrent is launched by hand once Proton is connected. Logged when
     * it does not take: a category that never gets created is otherwise invisible until the
     * first download lands somewhere unexpected.
     */
    private void ensureCategory() {
        if (categoryEnsured || !qbittorrent.isConfigured()) {
            return;
        }
        categoryEnsured = qbittorrent.ensureCategory(saveRoot);
        if (!categoryEnsured) {
            log.info("qBittorrent category '{}' not created yet (qBittorrent unreachable?);"
                    + " will retry on the next submit", QBittorrentClient.CATEGORY);
        }
    }

    @PreDestroy
    void stop() {
        mediaManagerImpl.setTorrentCleanup(null);
    }

    // ── status ───────────────────────────────────────────────────────────────────────

    /** Whether a torrent can be queued right now, and why not when it cannot. */
    public QBittorrentClient.Health health() {
        return qbittorrent.health(vpnStatus.tunnelAddress());
    }

    // ── submitting ───────────────────────────────────────────────────────────────────

    public static class TorrentRequest {
        public byte[] torrentFile;
        public String filename;
        public String urlOrMagnet;
        public int userId;
        public String title;
        public String description;
        public boolean isPublic;
    }

    /** Raised for anything the user can fix: bad input, no space, too many downloads. */
    public static class TorrentRejected extends RuntimeException {
        public TorrentRejected(String message) {
            super(message);
        }
    }

    /**
     * Adds a torrent, chooses its files, and starts it. Returns once it is downloading;
     * importing happens later, in the reconciler.
     */
    public TorrentDownload submit(TorrentRequest request) {
        QBittorrentClient.Health health = health();
        if (!health.usable()) {
            throw new TorrentRejected(health.message());
        }
        if (torrentDatabase.countActiveForUser(request.userId) >= maxActivePerUser) {
            throw new TorrentRejected("You already have " + maxActivePerUser
                    + " torrents downloading. Wait for one to finish.");
        }
        requireFreeSpace(0);
        // qBittorrent is up — this is the first chance to create the category if the server
        // started before it, which is the normal order here.
        ensureCategory();

        String tag = "lexi-" + UUID.randomUUID();
        String magnetHash = null;
        try {
            if (request.torrentFile != null && request.torrentFile.length > 0) {
                validateTorrentFile(request.torrentFile);
                qbittorrent.addTorrentFile(request.torrentFile,
                        request.filename == null || request.filename.isBlank() ? "upload.torrent" : request.filename,
                        saveRoot, tag);
            } else {
                magnetHash = validateUrlOrMagnet(request.urlOrMagnet);
                qbittorrent.addTorrentUrl(request.urlOrMagnet.trim(), saveRoot, tag);
            }
        } catch (IOException e) {
            throw new TorrentRejected("qBittorrent would not accept it: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TorrentRejected("Interrupted while adding the torrent");
        }

        QBittorrentClient.TorrentInfo added = awaitRegistration(tag, magnetHash);
        TorrentDownload download = new TorrentDownload(
                added.hash(), tag,
                request.torrentFile != null ? "file:" + request.filename : request.urlOrMagnet,
                added.name().isBlank() ? "torrent" : added.name(),
                request.userId, null, request.isPublic);
        download.setDisplayName(titleFor(request, added));
        torrentDatabase.save(download);
        progress.put(added.hash(), added);

        // Metadata may not have arrived yet for a magnet; the reconciler picks the files as
        // soon as it does. Either way the torrent stays stopped until they are chosen.
        try {
            if (!added.awaitingMetadata()) {
                chooseFilesAndStart(download, added.hash());
            }
        } catch (TorrentRejected e) {
            throw e;
        } catch (Exception e) {
            fail(download, "Could not choose files: " + e.getMessage());
            throw new TorrentRejected(e.getMessage());
        }
        return download;
    }

    /**
     * Keeps only the allowed extensions, then starts the download. The torrent is removed
     * outright when it contains nothing we would take.
     */
    private void chooseFilesAndStart(TorrentDownload download, String hash)
            throws IOException, InterruptedException {
        List<QBittorrentClient.TorrentFile> files = qbittorrent.files(hash);
        List<Integer> unwanted = new ArrayList<>();
        long wantedBytes = 0;
        int wantedCount = 0;
        for (QBittorrentClient.TorrentFile file : files) {
            if (allowed(file.name())) {
                wantedBytes += file.size();
                wantedCount++;
            } else {
                unwanted.add(file.index());
            }
        }

        if (wantedCount == 0) {
            discard(hash, true);
            torrentDatabase.updateStatus(hash, TorrentDownload.Status.FAILED,
                    "That torrent has no .mp3 or .mp4 files, so there was nothing to download.");
            throw new TorrentRejected("That torrent has no .mp3 or .mp4 files.");
        }
        requireFreeSpace(wantedBytes);

        // Deselect before starting, so unwanted files are never written at all.
        qbittorrent.setFilePriority(hash, unwanted, 0);
        qbittorrent.start(hash);
        torrentDatabase.updateStatus(hash, TorrentDownload.Status.DOWNLOADING, null);
    }

    // ── reconciling ──────────────────────────────────────────────────────────────────

    /**
     * Follows qBittorrent and acts on changes: picks files once a magnet's metadata lands,
     * imports finished torrents, and records failures. qBittorrent is the source of truth,
     * so this survives a server restart with nothing lost.
     */
    @Scheduled(fixedRate = 5000)
    public void reconcile() {
        if (reconciling || !qbittorrent.isConfigured()) {
            return;
        }
        List<TorrentDownload> active = torrentDatabase.findActive();
        if (active.isEmpty()) {
            progress.clear();
            return;
        }
        reconciling = true;
        try {
            Map<String, QBittorrentClient.TorrentInfo> live = qbittorrent
                    .byHashes(active.stream().map(TorrentDownload::getInfoHash).collect(Collectors.toList()))
                    .stream()
                    .collect(Collectors.toMap(QBittorrentClient.TorrentInfo::hash, info -> info, (a, b) -> a));
            progress.keySet().retainAll(live.keySet());
            progress.putAll(live);

            for (TorrentDownload download : active) {
                QBittorrentClient.TorrentInfo info = live.get(download.getInfoHash());
                if (info == null) {
                    // Gone from qBittorrent: removed at the desktop, or never registered.
                    fail(download, "The torrent is no longer in qBittorrent.");
                    continue;
                }
                advance(download, info);
            }
        } catch (Exception e) {
            // qBittorrent being closed is normal here; the next tick picks things up.
            System.err.println("Torrent reconcile skipped: " + e.getMessage());
        } finally {
            reconciling = false;
        }
    }

    private void advance(TorrentDownload download, QBittorrentClient.TorrentInfo info) {
        try {
            if (info.errored()) {
                fail(download, "qBittorrent reported an error for this torrent.");
                return;
            }
            if (download.getStatus() == TorrentDownload.Status.PREPARING) {
                if (info.awaitingMetadata()) {
                    if (download.getAddedAt() != null
                            && download.getAddedAt().plus(METADATA_TIMEOUT).isBefore(LocalDateTime.now())) {
                        discard(download.getInfoHash(), true);
                        fail(download, "No peers answered with the torrent's details.");
                    }
                    return;
                }
                chooseFilesAndStart(download, download.getInfoHash());
                return;
            }
            if (info.complete() && download.getStatus() == TorrentDownload.Status.DOWNLOADING) {
                torrentDatabase.updateStatus(download.getInfoHash(), TorrentDownload.Status.IMPORTING, null);
                importFinished(download, info);
            }
        } catch (TorrentRejected e) {
            fail(download, e.getMessage());
        } catch (Exception e) {
            fail(download, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * Hard-links every finished wanted file into Lexicon.
     *
     * Sequential on purpose: media ids come from SELECT MAX(id)+1, which races if two
     * imports run at once.
     */
    private void importFinished(TorrentDownload download, QBittorrentClient.TorrentInfo info)
            throws IOException, InterruptedException {
        List<QBittorrentClient.TorrentFile> files = qbittorrent.files(download.getInfoHash());
        List<TorrentDownload.ImportedFile> already = torrentDatabase.findImportedFiles(download.getInfoHash());
        Path root = Paths.get(info.savePath().isBlank() ? saveRoot : info.savePath());

        boolean movedAny = false;
        int imported = 0;
        for (QBittorrentClient.TorrentFile file : files) {
            if (!file.wanted() || !file.complete() || !allowed(file.name())) {
                continue;
            }
            if (already.stream().anyMatch(f -> f.fileIndex() == file.index())) {
                imported++;
                continue;
            }
            Path source = root.resolve(file.name().replace('/', java.io.File.separatorChar));
            if (!Files.exists(source)) {
                System.err.println("Torrent file missing on disk, skipping: " + source);
                continue;
            }

            String extension = file.name().substring(file.name().lastIndexOf('.')).toLowerCase(Locale.ROOT);
            String mediaType = extension.equals(".mp3") ? "MUSIC" : "VIDEO";
            MediaManagerService.ImportedMedia result = mediaManager.importLinkedFile(
                    source, download.getAddedBy(),
                    titleForFile(download, file.name()),
                    "Imported from a torrent",
                    download.isPublic(), mediaType,
                    download.getSource());
            torrentDatabase.recordImportedFile(download.getInfoHash(), file.index(),
                    result.mediaFile().getId(), result.mediaFile().getFilePath());
            movedAny |= !result.hardLinked();
            imported++;
        }

        if (imported == 0) {
            return; // Nothing finished yet after all; try again next tick.
        }
        torrentDatabase.updateStatus(download.getInfoHash(), TorrentDownload.Status.COMPLETED,
                movedAny ? "Imported by moving the files, so this torrent can no longer seed." : null);
    }

    // ── deleting ─────────────────────────────────────────────────────────────────────

    /**
     * Called when a Lexicon media file is deleted. Removes that file's hard link, and when
     * it was the torrent's last imported file, removes the torrent and its data too.
     */
    @Override
    public void onMediaDeleted(MediaFile mediaFile) {
        Optional<TorrentDownload.ImportedFile> link = torrentDatabase.findByMediaFileId(mediaFile.getId());
        if (link.isEmpty()) {
            return; // An ordinary upload: nothing torrent-related to do.
        }
        TorrentDownload.ImportedFile imported = link.get();
        torrentDatabase.deleteImportedFile(imported.infoHash(), imported.fileIndex());

        List<TorrentDownload.ImportedFile> remaining = torrentDatabase.findImportedFiles(imported.infoHash());
        if (remaining.isEmpty()) {
            // The last one: the torrent has no reason to exist any more, and its own copy of
            // the data goes with it.
            discard(imported.infoHash(), true);
            torrentDatabase.delete(imported.infoHash());
            progress.remove(imported.infoHash());
        }
    }

    /** Cancel or remove a torrent on the user's behalf, data included. */
    public boolean remove(String infoHash, int userId) {
        Optional<TorrentDownload> found = torrentDatabase.find(infoHash);
        if (found.isEmpty() || found.get().getAddedBy() != userId) {
            return false;
        }
        discard(infoHash, true);
        torrentDatabase.delete(infoHash);
        progress.remove(infoHash);
        return true;
    }

    private void discard(String infoHash, boolean deleteFiles) {
        try {
            qbittorrent.delete(infoHash, deleteFiles);
        } catch (IOException e) {
            System.err.println("Could not remove torrent " + infoHash + " from qBittorrent: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── listing ──────────────────────────────────────────────────────────────────────

    /** What the clients poll: this user's torrents, newest first, with live progress. */
    public List<Map<String, Object>> listForUser(int userId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TorrentDownload download : torrentDatabase.findRecentByUser(userId, limit)) {
            QBittorrentClient.TorrentInfo info = progress.get(download.getInfoHash());
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("infoHash", download.getInfoHash());
            row.put("name", download.getDisplayName());
            row.put("status", download.getStatus().name());
            row.put("error", download.getError());
            row.put("addedAt", download.getAddedAt() == null ? null : download.getAddedAt().toString());
            row.put("percent", info == null ? (download.getStatus() == TorrentDownload.Status.COMPLETED ? 100 : 0)
                    : (int) Math.round(info.progress() * 100));
            row.put("downloadSpeed", info == null ? 0 : info.downloadSpeed());
            row.put("eta", info == null ? 0 : info.eta());
            row.put("stalled", info != null && info.stalled());
            row.put("mediaFileIds", torrentDatabase.findImportedFiles(download.getInfoHash()).stream()
                    .map(TorrentDownload.ImportedFile::mediaFileId).collect(Collectors.toList()));
            out.add(row);
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    static boolean allowed(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        // qBittorrent marks partial files .!qB; those are not finished files anyway.
        return ALLOWED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    static void validateTorrentFile(byte[] data) {
        if (data.length > MAX_TORRENT_FILE_BYTES) {
            throw new TorrentRejected("That .torrent file is too large to be real.");
        }
        if (data.length < 2 || data[0] != 'd') {
            throw new TorrentRejected("That does not look like a .torrent file.");
        }
    }

    /**
     * @return the info hash when the input was a magnet, else null
     */
    static String validateUrlOrMagnet(String input) {
        if (input == null || input.isBlank()) {
            throw new TorrentRejected("Give a .torrent file, a magnet link, or a link to a .torrent.");
        }
        String trimmed = input.trim();
        java.util.regex.Matcher magnet = MAGNET.matcher(trimmed);
        if (magnet.matches()) {
            return magnet.group(1).toLowerCase(Locale.ROOT);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("http://")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("https://")) {
            // Same guard the yt-dlp path uses: no loopback, no LAN, no link-local.
            lexicon.utils.UrlSafety.requirePublicHttpUrl(trimmed);
            return null;
        }
        throw new TorrentRejected("Only magnet links and http(s) links to a .torrent are accepted.");
    }

    /** Waits for qBittorrent to register the add, which is not instant. */
    private QBittorrentClient.TorrentInfo awaitRegistration(String tag, String magnetHash) {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                List<QBittorrentClient.TorrentInfo> byTag = qbittorrent.byTag(tag);
                if (!byTag.isEmpty()) {
                    return byTag.get(0);
                }
                if (magnetHash != null) {
                    // A magnet already in qBittorrent keeps its original tags, so fall back
                    // to the hash from the link itself.
                    List<QBittorrentClient.TorrentInfo> byHash = qbittorrent.byHashes(List.of(magnetHash));
                    if (!byHash.isEmpty()) {
                        return byHash.get(0);
                    }
                }
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                throw new TorrentRejected("qBittorrent stopped answering: " + e.getMessage());
            }
        }
        throw new TorrentRejected("qBittorrent did not register the torrent. It may already be in the list.");
    }

    private void requireFreeSpace(long incomingBytes) {
        try {
            // The storage base path ends in the same mount point WSL reports, e.g.
            // .../PHYSICALDRIVE1p2/lexicon-storage against /mnt/wsl/PHYSICALDRIVE1p2.
            String base = storageProperties.getBasePath().replace('\\', '/');
            long free = diskUsageService.listPhysicalDriveVolumes().stream()
                    .filter(volume -> base.contains(volume.mountPoint))
                    .mapToLong(volume -> volume.freeBytes)
                    .findFirst()
                    .orElse(Long.MAX_VALUE);
            if (free != Long.MAX_VALUE && free - incomingBytes < minFreeBytes) {
                throw new TorrentRejected(String.format(
                        "Not enough room: %d GB free, and this would leave less than the %d GB reserve.",
                        free / (1024L * 1024 * 1024), minFreeBytes / (1024L * 1024 * 1024)));
            }
        } catch (IOException e) {
            // Disk reporting needs WSL; not a reason to refuse a download.
            System.err.println("Could not check free space: " + e.getMessage());
        }
    }

    private String titleFor(TorrentRequest request, QBittorrentClient.TorrentInfo info) {
        if (request.title != null && !request.title.isBlank()) {
            return request.title.trim();
        }
        return info.name().isBlank() ? "Torrent download" : info.name();
    }

    /** One media row per file, so each needs its own readable title. */
    private String titleForFile(TorrentDownload download, String pathInTorrent) {
        String filename = pathInTorrent.substring(pathInTorrent.lastIndexOf('/') + 1);
        String withoutExtension = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        String cleaned = withoutExtension.replace('_', ' ').replace('.', ' ').trim();
        return cleaned.isBlank() ? download.getDisplayName() : cleaned;
    }

    private void fail(TorrentDownload download, String message) {
        torrentDatabase.updateStatus(download.getInfoHash(), TorrentDownload.Status.FAILED, message);
        progress.remove(download.getInfoHash());
    }
}
