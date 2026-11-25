package lexicon.logic;

import lexicon.data.IChatFileDatabase;
import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IMobileTokenDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.data.IRememberMeDatabase;
import lexicon.object.ChatFile;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.object.Permission;
import lexicon.object.PlaybackPosition;
import lexicon.object.PlayerActivity;
import lexicon.object.Playlist;
import lexicon.object.Role;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.OptimizedFileStorageService.StoredFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finds abandoned accounts and the uploads taking up space, and removes them. All decisions
 * live here; it reads and deletes only through the data-layer CRUD and the media / chat-file
 * services (which also remove the files from disk).
 */
@Service
public class UserAdminManager implements UserAdminManagerService {

    /** No activity for this many days shows as a warning, and twice as long as a danger. */
    static final int INACTIVE_WARN_DAYS = 45;
    static final int INACTIVE_DANGER_DAYS = 90;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<Map<String, String>> USER_SORTS = List.of(
            Map.of("key", "inactive", "label", "Longest unused first"),
            Map.of("key", "storage", "label", "Most storage first"),
            Map.of("key", "newest", "label", "Newest accounts first"),
            Map.of("key", "name", "label", "Name"));
    private static final List<Map<String, String>> UPLOAD_SORTS = List.of(
            Map.of("key", "oldest", "label", "Oldest first"),
            Map.of("key", "newest", "label", "Newest first"),
            Map.of("key", "largest", "label", "Largest first"));

    private final PermissionManagerService permissions;
    private final PlayerManagerService players;
    private final MediaManagerService mediaManager;
    private final ChatFileManagerService chatFiles;
    private final ILexiconDatabase playerDatabase;
    private final IMediaDatabase mediaDatabase;
    private final IChatFileDatabase chatFileDatabase;
    private final IPlaylistDatabase playlistDatabase;
    private final IMobileTokenDatabase mobileTokens;
    private final IRememberMeDatabase rememberMeTokens;
    private final OptimizedFileStorageService storage;
    private final Clock clock;

    /**
     * Folders of the storage drive that hold only files the database lists: media uploads
     * (media_files.file_path) and chat images (chat_files). temp/ (uploads in progress),
     * torrents/ (qBittorrent's own) and backups/ are never touched.
     */
    static final List<String> CHECKED_FOLDERS = List.of("audiobooks", "music", "videos", "other", "chat-uploads");
    static final List<String> SKIPPED_FOLDERS = List.of("temp", "torrents", "backups");
    /** A file changed this recently may be an upload whose row is not written yet. */
    static final long RECENT_MS = 24L * 60 * 60 * 1000;

    @Autowired
    public UserAdminManager(PermissionManagerService permissions, PlayerManagerService players,
                            MediaManagerService mediaManager, ChatFileManagerService chatFiles,
                            ILexiconDatabase playerDatabase, IMediaDatabase mediaDatabase,
                            IChatFileDatabase chatFileDatabase, IPlaylistDatabase playlistDatabase,
                            IMobileTokenDatabase mobileTokens, IRememberMeDatabase rememberMeTokens,
                            OptimizedFileStorageService storage) {
        this(permissions, players, mediaManager, chatFiles, playerDatabase, mediaDatabase, chatFileDatabase,
                playlistDatabase, mobileTokens, rememberMeTokens, storage, Clock.systemDefaultZone());
    }

    UserAdminManager(PermissionManagerService permissions, PlayerManagerService players,
                     MediaManagerService mediaManager, ChatFileManagerService chatFiles,
                     ILexiconDatabase playerDatabase, IMediaDatabase mediaDatabase,
                     IChatFileDatabase chatFileDatabase, IPlaylistDatabase playlistDatabase,
                     IMobileTokenDatabase mobileTokens, IRememberMeDatabase rememberMeTokens,
                     OptimizedFileStorageService storage, Clock clock) {
        this.permissions = permissions;
        this.players = players;
        this.mediaManager = mediaManager;
        this.chatFiles = chatFiles;
        this.playerDatabase = playerDatabase;
        this.mediaDatabase = mediaDatabase;
        this.chatFileDatabase = chatFileDatabase;
        this.playlistDatabase = playlistDatabase;
        this.mobileTokens = mobileTokens;
        this.rememberMeTokens = rememberMeTokens;
        this.storage = storage;
        this.clock = clock;
    }

    // ── Accounts ──────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> listUsers(int actorId, String sort) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;

        List<MediaFile> media = mediaDatabase.getAllMediaFiles();
        List<ChatFile> chat = chatFileDatabase.getAllChatFiles();
        Map<Integer, long[]> usage = new HashMap<>(); // id -> {count, bytes}
        for (MediaFile m : media) add(usage, m.getUploadedBy(), m.getFileSize());
        for (ChatFile c : chat) add(usage, c.getUploadedBy(), c.getFileSize());

        String key = pick(sort, USER_SORTS, "inactive");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<PlayerActivity> accounts = new ArrayList<>(playerDatabase.getPlayerActivity());
        accounts.sort(userOrder(key, usage));
        int inactive = 0;
        long totalBytes = 0;
        for (PlayerActivity a : accounts) {
            long[] u = usage.getOrDefault(a.id(), new long[2]);
            totalBytes += u[1];
            Map<String, Object> row = userRow(a, u, actorId);
            if (!"ok".equals(row.get("inactiveLevel"))) inactive++;
            rows.add(row);
        }

        Map<String, Object> result = ok();
        result.put("sort", key);
        result.put("sorts", USER_SORTS);
        result.put("users", rows);
        result.put("totals", Map.of(
                "accounts", accounts.size(),
                "inactive", inactive,
                "inactiveLabel", inactive + " not used in " + INACTIVE_WARN_DAYS + "+ days (or never recorded)",
                "storageBytes", totalBytes,
                "storage", humanBytes(totalBytes)));
        return result;
    }

    private Map<String, Object> userRow(PlayerActivity a, long[] usage, int actorId) {
        LocalDateTime lastSeen = latest(a.lastActiveDate(), a.lastLoginDate());
        Long days = lastSeen == null ? null : ChronoUnit.DAYS.between(lastSeen.toLocalDate(), LocalDate.now(clock));
        boolean admin = Role.fromStored(a.role()) == Role.ADMIN;
        boolean you = a.id() == actorId;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.id());
        row.put("username", a.username());
        row.put("role", admin ? "ADMIN" : "USER");
        row.put("isYou", you);
        row.put("registered", day(a.registrationDate()));
        row.put("lastLogin", day(a.lastLoginDate()));
        row.put("lastSeen", day(lastSeen));
        row.put("lastSeenAgo", days == null ? "Never recorded" : ago(days));
        row.put("inactiveDays", days);
        row.put("inactiveLevel", days == null || days >= INACTIVE_DANGER_DAYS ? "danger"
                : days >= INACTIVE_WARN_DAYS ? "warn" : "ok");
        row.put("uploadCount", (int) usage[0]);
        row.put("storageBytes", usage[1]);
        row.put("storage", humanBytes(usage[1]));
        String blocked = you ? "This is your own account." : admin ? "Admins can't be deleted here. Change their role first." : null;
        row.put("canDelete", blocked == null);
        row.put("deleteBlockedReason", blocked);
        row.put("canSetPassword", true);
        return row;
    }

    private static Comparator<PlayerActivity> userOrder(String key, Map<Integer, long[]> usage) {
        Comparator<PlayerActivity> byName = Comparator.comparing(a -> String.valueOf(a.username()).toLowerCase());
        switch (key) {
            case "storage":
                return Comparator.<PlayerActivity>comparingLong(a -> -usage.getOrDefault(a.id(), new long[2])[1]).thenComparing(byName);
            case "newest":
                return Comparator.comparing(PlayerActivity::registrationDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingInt(PlayerActivity::id).reversed());
            case "name":
                return byName;
            default: // inactive: never seen first, then the longest ago
                return Comparator.comparing((PlayerActivity a) -> latest(a.lastActiveDate(), a.lastLoginDate()),
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(byName);
        }
    }

    // ── Uploads ───────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> listUploads(int actorId, String sort, Integer ownerId) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;

        Map<Integer, String> names = playerDatabase.getPlayerActivity().stream()
                .collect(Collectors.toMap(PlayerActivity::id, PlayerActivity::username, (x, y) -> x));
        List<Upload> uploads = new ArrayList<>();
        for (MediaFile m : mediaDatabase.getAllMediaFiles()) {
            uploads.add(new Upload("media", m.getId(), titleOf(m), typeLabel(m.getMediaType()), m.getUploadedBy(),
                    m.getUploadDate(), m.getFileSize(), m.isPublic()));
        }
        for (ChatFile c : chatFileDatabase.getAllChatFiles()) {
            uploads.add(new Upload("chat", c.getId(), c.getOriginalFilename() != null ? c.getOriginalFilename() : "Chat image",
                    "Chat image", c.getUploadedBy(), c.getCreatedAt(), c.getFileSize(), true));
        }
        if (ownerId != null) uploads.removeIf(u -> u.ownerId != ownerId);

        String key = pick(sort, UPLOAD_SORTS, "oldest");
        Comparator<Upload> byDate = Comparator.comparing(u -> u.uploadedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        uploads.sort(switch (key) {
            case "newest" -> byDate.reversed();
            case "largest" -> Comparator.<Upload>comparingLong(u -> -u.bytes).thenComparing(byDate);
            default -> byDate;
        });

        long total = 0;
        int orphans = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Upload u : uploads) {
            total += Math.max(0, u.bytes);
            String owner = names.get(u.ownerId);
            if (owner == null) orphans++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", u.kind);
            row.put("id", u.id);
            row.put("title", u.title);
            row.put("type", u.type);
            row.put("ownerId", u.ownerId);
            row.put("owner", owner != null ? owner : "Deleted account #" + u.ownerId);
            row.put("ownerMissing", owner == null);
            row.put("uploaded", day(u.uploadedAt));
            row.put("uploadedAgo", u.uploadedAt == null ? "Unknown"
                    : ago(ChronoUnit.DAYS.between(u.uploadedAt.toLocalDate(), LocalDate.now(clock))));
            row.put("sizeBytes", u.bytes);
            row.put("size", humanBytes(u.bytes));
            row.put("visibility", u.isPublic ? "Public" : "Private");
            rows.add(row);
        }

        Map<String, Object> result = ok();
        result.put("sort", key);
        result.put("sorts", UPLOAD_SORTS);
        result.put("ownerId", ownerId);
        if (ownerId != null) result.put("ownerName", names.getOrDefault(ownerId, "Deleted account #" + ownerId));
        result.put("uploads", rows);
        result.put("totals", Map.of(
                "count", rows.size(),
                "storageBytes", total,
                "storage", humanBytes(total),
                "orphans", orphans,
                "orphansLabel", orphans == 0 ? "" : orphans + " belong to accounts that no longer exist"));
        return result;
    }

    @Override
    public Map<String, Object> deleteUpload(int actorId, String kind, long uploadId) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;

        long freed;
        if ("media".equals(kind)) {
            if (uploadId > Integer.MAX_VALUE || uploadId < 0) return fail(404, "No such upload.");
            freed = mediaManager.deleteMediaFileAsAdmin((int) uploadId);
        } else if ("chat".equals(kind)) {
            freed = chatFiles.deleteChatFile(uploadId);
        } else {
            return fail(400, "Unknown upload type.");
        }
        if (freed < 0) return fail(404, "That upload no longer exists.");

        Map<String, Object> result = ok();
        result.put("freedBytes", freed);
        result.put("message", "Deleted. Freed " + humanBytes(freed) + ".");
        return result;
    }

    // ── Account actions ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> deleteUser(int actorId, int targetId, String confirmUsername) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;

        PlayerActivity target = find(targetId);
        if (target == null) return fail(404, "That account no longer exists.");
        if (targetId == actorId) return fail(409, "You can't delete your own account.");
        if (Role.fromStored(target.role()) == Role.ADMIN) {
            return fail(409, "Admins can't be deleted here. Change their role first.");
        }
        if (confirmUsername == null || !confirmUsername.trim().equals(target.username())) {
            return fail(400, "Type the username exactly to confirm.");
        }

        long freed = 0;
        int media = 0;
        int images = 0;
        for (MediaFile m : mediaDatabase.getMediaFilesByPlayer(targetId)) {
            long f = mediaManager.deleteMediaFileAsAdmin(m.getId());
            if (f >= 0) { freed += f; media++; }
        }
        for (ChatFile c : chatFileDatabase.getAllChatFiles()) {
            if (c.getUploadedBy() != targetId) continue;
            long f = chatFiles.deleteChatFile(c.getId());
            if (f >= 0) { freed += f; images++; }
        }
        int lists = 0;
        for (Playlist p : playlistDatabase.getPlaylistsByUser(targetId)) {
            if (playlistDatabase.deletePlaylist(p.getId())) lists++;
        }
        for (PlaybackPosition pos : mediaDatabase.getUserPlaybackPositions(targetId)) {
            mediaDatabase.deletePlaybackPosition(targetId, pos.getMediaFileId());
        }
        mobileTokens.deleteByUserId(targetId);
        rememberMeTokens.deleteByUserId(targetId);

        if (!playerDatabase.deletePlayerAccount(targetId)) {
            Map<String, Object> partial = fail(500, "Their uploads were removed, but the account itself could not be deleted. Check the server log.");
            partial.put("freedBytes", freed);
            return partial;
        }

        Map<String, Object> result = ok();
        result.put("freedBytes", freed);
        result.put("message", "Deleted " + target.username() + ": " + media + " upload" + (media == 1 ? "" : "s") + ", "
                + images + " chat image" + (images == 1 ? "" : "s") + ", " + lists + " playlist" + (lists == 1 ? "" : "s")
                + ". Freed " + humanBytes(freed) + ".");
        return result;
    }

    @Override
    public Map<String, Object> setPassword(int actorId, int targetId, String newPassword) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;

        PlayerActivity target = find(targetId);
        if (target == null) return fail(404, "That account no longer exists.");
        try {
            players.setPassword(targetId, newPassword);
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        }
        // Anyone still signed in with the old password (an app token, remember-me) has to sign in again.
        mobileTokens.deleteByUserId(targetId);
        rememberMeTokens.deleteByUserId(targetId);

        Map<String, Object> result = ok();
        result.put("message", "New password set for " + target.username() + ". Their other sign-ins were ended.");
        return result;
    }

    // ── Storage check ─────────────────────────────────────────────────────

    @Override
    public Map<String, Object> storageCheck(int actorId) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;
        Audit audit;
        try {
            audit = audit();
        } catch (Exception e) {
            return fail(500, "Could not read the storage drive: " + e.getMessage());
        }

        Map<String, Object> result = ok();
        List<Map<String, Object>> orphanRows = new ArrayList<>();
        for (StoredFile f : audit.orphans) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", f.relativePath());
            row.put("sizeBytes", f.size());
            row.put("size", humanBytes(f.size()));
            row.put("modified", day(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(f.lastModifiedMillis()), clock.getZone())));
            orphanRows.add(row);
        }
        List<Map<String, Object>> missingRows = new ArrayList<>();
        for (MediaFile m : audit.missing) {
            missingRows.add(Map.of("id", m.getId(), "title", titleOf(m), "path", m.getFilePath()));
        }
        result.put("orphans", orphanRows);
        result.put("orphanCount", audit.orphans.size());
        result.put("orphanBytes", audit.orphanBytes);
        result.put("orphanSize", humanBytes(audit.orphanBytes));
        result.put("missing", missingRows);
        result.put("scannedFiles", audit.scanned);
        result.put("scannedSize", humanBytes(audit.scannedBytes));
        result.put("referencedRows", audit.rows);
        result.put("recentSkipped", audit.recent);
        result.put("checkedFolders", CHECKED_FOLDERS);
        result.put("skippedFolders", SKIPPED_FOLDERS);
        result.put("summary", audit.orphans.isEmpty()
                ? "No wasted space: every file in " + String.join(", ", CHECKED_FOLDERS) + " belongs to an upload."
                : audit.orphans.size() + " file" + (audit.orphans.size() == 1 ? "" : "s") + " (" + humanBytes(audit.orphanBytes)
                + ") are on the drive but in no upload.");
        result.put("canClean", audit.cleanBlockedReason == null && !audit.orphans.isEmpty());
        result.put("cleanBlockedReason", audit.cleanBlockedReason);
        return result;
    }

    @Override
    public Map<String, Object> cleanStorage(int actorId, List<String> paths) {
        Map<String, Object> denied = requireAdmin(actorId);
        if (denied != null) return denied;
        if (paths == null || paths.isEmpty()) return fail(400, "Nothing selected to delete.");
        Audit audit;
        try {
            audit = audit(); // fresh: a file that gained a row since the scan is no longer an orphan
        } catch (Exception e) {
            return fail(500, "Could not read the storage drive: " + e.getMessage());
        }
        if (audit.cleanBlockedReason != null) return fail(409, audit.cleanBlockedReason);

        Set<String> requested = new HashSet<>(paths);
        long freed = 0;
        int deleted = 0;
        int failed = 0;
        for (StoredFile f : audit.orphans) {
            if (!requested.contains(f.relativePath())) continue;
            long bytes = storage.deleteStoredFile(f.relativePath(), CHECKED_FOLDERS);
            if (bytes >= 0) { deleted++; freed += bytes; } else { failed++; }
        }
        int skipped = requested.size() - deleted - failed;

        Map<String, Object> result = ok();
        result.put("deleted", deleted);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("freedBytes", freed);
        result.put("message", "Deleted " + deleted + " file" + (deleted == 1 ? "" : "s") + ", freed " + humanBytes(freed) + "."
                + (failed > 0 ? " " + failed + " could not be deleted (see the server log)." : "")
                + (skipped > 0 ? " " + skipped + " skipped because they are now in use or changed recently." : ""));
        return result;
    }

    /** What the drive holds against what the database lists. */
    private static final class Audit {
        final List<StoredFile> orphans = new ArrayList<>();
        final List<MediaFile> missing = new ArrayList<>();
        long orphanBytes;
        int scanned;
        long scannedBytes;
        int rows;
        int recent;
        String cleanBlockedReason;
    }

    private Audit audit() throws java.io.IOException {
        Audit a = new Audit();
        Set<String> referencedPaths = new HashSet<>();
        Set<String> referencedNames = new HashSet<>();
        List<MediaFile> onDisk = new ArrayList<>();
        for (MediaFile m : mediaDatabase.getAllMediaFiles()) {
            String rel = storageRelative(m.getFilePath());
            if (rel == null) continue; // stored in the database itself
            a.rows++;
            referencedPaths.add(rel);
            referencedNames.add(fileName(rel));
            onDisk.add(m);
        }
        for (ChatFile c : chatFileDatabase.getAllChatFiles()) {
            a.rows++;
            if (c.getStoredFilename() != null) {
                referencedPaths.add("chat-uploads/" + c.getStoredFilename());
                referencedNames.add(c.getStoredFilename());
            }
            if (c.getThumbnailFilename() != null) {
                referencedPaths.add("chat-uploads/thumbnails/" + c.getThumbnailFilename());
                referencedNames.add(c.getThumbnailFilename());
            }
        }

        long now = clock.millis();
        Set<String> diskPaths = new HashSet<>();
        Set<String> diskNames = new HashSet<>();
        for (StoredFile f : storage.listStoredFiles(CHECKED_FOLDERS)) {
            a.scanned++;
            a.scannedBytes += f.size();
            diskPaths.add(f.relativePath());
            diskNames.add(fileName(f.relativePath()));
            // An orphan only if neither its path nor its (unique, timestamped) file name is in any row:
            // a row whose path was written differently must never make its file look unused.
            if (referencedPaths.contains(f.relativePath()) || referencedNames.contains(fileName(f.relativePath()))) continue;
            if (now - f.lastModifiedMillis() < RECENT_MS) { a.recent++; continue; }
            a.orphans.add(f);
            a.orphanBytes += f.size();
        }
        a.orphans.sort(Comparator.comparingLong(StoredFile::size).reversed());

        for (MediaFile m : onDisk) {
            String rel = storageRelative(m.getFilePath());
            if (!diskPaths.contains(rel) && !diskNames.contains(fileName(rel))) a.missing.add(m);
        }
        // Many rows that cannot find their file means the paths are not being matched (or the drive
        // is not the one the database describes). Deleting "unused" files then could delete real
        // media, so cleaning is refused until that is understood.
        int tolerated = Math.max(3, onDisk.size() / 50);
        if (a.missing.size() > tolerated) {
            a.cleanBlockedReason = a.missing.size() + " uploads cannot find their file on the drive, so the check may not be "
                    + "matching paths correctly. Nothing will be deleted until that is looked at.";
        }
        return a;
    }

    /**
     * A media_files.file_path as a path relative to the storage base with '/' separators, or null
     * when the file is kept in the database. Accepts Windows separators and full paths that
     * include ".../lexicon-storage/".
     */
    static String storageRelative(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        String s = filePath.trim().replace('\\', '/');
        int i = s.toLowerCase(Locale.ROOT).lastIndexOf("lexicon-storage/");
        if (i >= 0) s = s.substring(i + "lexicon-storage/".length());
        while (s.startsWith("/")) s = s.substring(1);
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }

    private static String fileName(String rel) {
        int slash = rel.lastIndexOf('/');
        return slash >= 0 ? rel.substring(slash + 1) : rel;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> requireAdmin(int actorId) {
        return permissions.hasPermission(actorId, Permission.MANAGE_USERS)
                ? null : fail(403, "Only an admin can manage users.");
    }

    private PlayerActivity find(int id) {
        return playerDatabase.getPlayerActivity().stream().filter(a -> a.id() == id).findFirst().orElse(null);
    }

    private static void add(Map<Integer, long[]> usage, int owner, long bytes) {
        long[] u = usage.computeIfAbsent(owner, k -> new long[2]);
        u[0]++;
        u[1] += Math.max(0, bytes);
    }

    private static String pick(String requested, List<Map<String, String>> options, String fallback) {
        return options.stream().map(o -> o.get("key")).filter(k -> k.equals(requested)).findFirst().orElse(fallback);
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static String day(LocalDateTime t) {
        return t == null ? "Unknown" : t.format(DAY);
    }

    static String ago(long days) {
        if (days <= 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 60) return days + " days ago";
        if (days < 730) return (days / 30) + " months ago";
        return (days / 365) + " years ago";
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) return Math.max(0, bytes) + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < units.length - 1);
        return (v >= 100 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v)) + " " + units[i];
    }

    private static String titleOf(MediaFile m) {
        if (m.getTitle() != null && !m.getTitle().isBlank()) return m.getTitle();
        if (m.getOriginalFilename() != null && !m.getOriginalFilename().isBlank()) return m.getOriginalFilename();
        return "Upload #" + m.getId();
    }

    private static String typeLabel(MediaType t) {
        if (t == null) return "Other";
        return switch (t) {
            case AUDIOBOOK -> "Audiobook";
            case VIDEO -> "Video";
            case MUSIC -> "Music";
            default -> "Other";
        };
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", 200);
        m.put("success", true);
        return m;
    }

    private static Map<String, Object> fail(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    private record Upload(String kind, long id, String title, String type, int ownerId,
                          LocalDateTime uploadedAt, long bytes, boolean isPublic) {
    }
}
