package lexicon.service;

import lexicon.config.StorageProperties;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Enhanced file storage service using optimized file system storage
 */
@Service
public class OptimizedFileStorageService {
    
    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private WslDiskUsageService wslDiskUsageService;

    // ── Media drives ─────────────────────────────────────────────────────────────────
    //
    // Volume 1 is lexicon.storage.base-path. Every path written before there was a second
    // drive is relative to it with no prefix ("music/original/a.mp3") and stays that way, so
    // no database row changes. Each drive in lexicon.storage.additional-paths is volume 2, 3,
    // ... and its paths carry the number: "v2:music/original/b.mp3".
    //
    // New files go to the drive with the most free space.
    //
    // A drive's number is also written to a .lexicon-volume file at its root the first time it
    // is used, and read back from there. The configured paths name drives by Windows disk number
    // (PHYSICALDRIVE1p2, PHYSICALDRIVE2p1), which can swap when USB drives come up in another
    // order; the marker keeps every "v2:" path on the drive it was written to.

    /** One media drive: its number and the storage folder on it. */
    public record Volume(int id, Path root) {
        public boolean isPrimary() { return id == 1; }
    }

    static final String VOLUME_MARKER = ".lexicon-volume";
    private static final java.util.regex.Pattern VOLUME_PREFIX =
            java.util.regex.Pattern.compile("^v(\\d+):(.*)$", java.util.regex.Pattern.DOTALL);
    private static final long FREE_SPACE_CACHE_MS = 60_000;
    private static final long RECHECK_MISSING_MS = 30_000;

    private List<Volume> volumeCache;
    private long volumesAssignedAt;
    private final Map<Integer, Long> freeSpaceCache = new HashMap<>();
    private long freeSpaceCachedAt;

    /** The configured drives that are mounted, volume 1 first. */
    public synchronized List<Volume> volumes() {
        // Rechecked every 30 seconds while a configured drive is missing, so a drive mounted
        // after start-up is picked up; once all are there the answer cannot change.
        long now = System.currentTimeMillis();
        if (volumeCache == null
                || (volumeCache.size() < configuredRoots().size() && now - volumesAssignedAt > RECHECK_MISSING_MS)) {
            volumeCache = assignVolumes();
            volumesAssignedAt = now;
        }
        return volumeCache;
    }

    /** Volume 1, where every unprefixed path lives, even while it is not mounted. */
    public Volume primaryVolume() {
        return volumes().stream().filter(Volume::isPrimary).findFirst()
                .orElse(new Volume(1, configuredRoots().get(0)));
    }

    /** The drive a stored path names: its "vN:" prefix, or volume 1 without one. */
    public Volume volumeOf(String storedPath) {
        java.util.regex.Matcher m = VOLUME_PREFIX.matcher(storedPath == null ? "" : storedPath);
        if (!m.matches()) return primaryVolume();
        int id = Integer.parseInt(m.group(1));
        return volumes().stream().filter(v -> v.id() == id).findFirst()
                // Not mounted: a path that cannot exist, so the caller gets "not found" for
                // this file rather than some other file.
                .orElse(new Volume(id, primaryVolume().root().resolve("missing-volume-" + id)));
    }

    /** The path inside its drive, without the "vN:" prefix. */
    public static String withoutVolumePrefix(String storedPath) {
        java.util.regex.Matcher m = VOLUME_PREFIX.matcher(storedPath == null ? "" : storedPath);
        return m.matches() ? m.group(2) : storedPath;
    }

    /** Where a path from the database is on disk. */
    public Path resolveStoredPath(String storedPath) {
        return volumeOf(storedPath).root().resolve(withoutVolumePrefix(storedPath));
    }

    /** The path the database holds for a file on the given drive. */
    public String toStoredPath(Volume volume, Path file) {
        String relative = volume.root().relativize(file).toString();
        return volume.isPrimary() ? relative : "v" + volume.id() + ":" + relative;
    }

    /** The drive whose storage folder holds this absolute path, if any. */
    public Optional<Volume> volumeContaining(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return volumes().stream()
                .filter(v -> normalized.startsWith(v.root().toAbsolutePath().normalize()))
                .findFirst();
    }

    /** The drive a new file goes to: the one with the most free space. */
    public Volume volumeForNewFile() {
        List<Volume> all = volumes();
        if (all.size() <= 1) return primaryVolume();
        Map<Integer, Long> free = freeSpace(all);
        return all.stream()
                .max(Comparator.comparingLong((Volume v) -> free.getOrDefault(v.id(), -1L))
                        // On a tie, or with no figures at all, the lower number wins.
                        .thenComparing(Volume::id, Comparator.reverseOrder()))
                .orElse(primaryVolume());
    }

    /** A storage folder ("chat-uploads") on the drive a new file should go to. */
    public Path folderForNewFile(String folder) {
        return volumeForNewFile().root().resolve(folder);
    }

    /** The same storage folder on every mounted drive, volume 1 first. */
    public List<Path> folderOnEveryVolume(String folder) {
        List<Path> folders = new ArrayList<>();
        for (Volume v : volumes()) folders.add(v.root().resolve(folder));
        if (folders.isEmpty()) folders.add(primaryVolume().root().resolve(folder));
        return folders;
    }

    /** The folder for this media type and size on the given drive. */
    private Path mediaDir(Volume volume, String mediaType, long fileSize) {
        // Volume 1 at the configured base path keeps honouring the per-type path overrides.
        if (volume.isPrimary() && volume.root().equals(Paths.get(storageProperties.getBasePath()))) {
            return Paths.get(storageProperties.getStoragePathForMedia(mediaType, fileSize));
        }
        return volume.root().resolve(storageProperties.getRelativeStoragePathForMedia(mediaType, fileSize));
    }

    private synchronized Map<Integer, Long> freeSpace(List<Volume> all) {
        long now = System.currentTimeMillis();
        if (freeSpaceCache.size() == all.size() && now - freeSpaceCachedAt < FREE_SPACE_CACHE_MS) {
            return freeSpaceCache;
        }
        freeSpaceCache.clear();
        List<WslDiskUsageService.Volume> drives = List.of();
        if (wslDiskUsageService != null) {
            try {
                drives = wslDiskUsageService.listPhysicalDriveVolumes();
            } catch (IOException e) {
                System.err.println("Could not read drive usage from WSL: " + e.getMessage());
            }
        }
        for (Volume v : all) {
            // Java's own figure for a \\wsl.localhost path is the distro's disk, not the drive,
            // so WSL's report is used when it names this drive's mount point.
            String root = v.root().toString().replace('\\', '/');
            long free = drives.stream()
                    .filter(d -> root.contains(d.mountPoint))
                    .mapToLong(d -> d.freeBytes)
                    .findFirst()
                    .orElseGet(() -> usableSpace(v.root()));
            freeSpaceCache.put(v.id(), free);
        }
        freeSpaceCachedAt = now;
        return freeSpaceCache;
    }

    private static long usableSpace(Path root) {
        try {
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    private List<Path> configuredRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get(storageProperties.getBasePath()));
        for (String extra : storageProperties.getAdditionalPaths()) {
            if (extra != null && !extra.isBlank()) roots.add(Paths.get(extra.trim()));
        }
        return roots;
    }

    /** Numbers each mounted drive by its marker, or by its place in the configuration (then writes the marker). */
    private List<Volume> assignVolumes() {
        List<Path> roots = configuredRoots();
        Map<Integer, Path> byId = new HashMap<>();
        List<Integer> unmarked = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            Path root = roots.get(i);
            if (!Files.isDirectory(root)) continue; // not mounted right now
            Integer marked = readVolumeMarker(root);
            if (marked == null) {
                unmarked.add(i);
            } else if (byId.containsKey(marked)) {
                System.err.println("Two storage drives claim volume " + marked + "; ignoring " + root);
            } else {
                if (marked != i + 1) {
                    System.err.println("Storage drive " + root + " is volume " + marked
                            + " by its marker, not " + (i + 1) + " by its place in the configuration; using the marker.");
                }
                byId.put(marked, root);
            }
        }
        for (int i : unmarked) {
            int id = i + 1;
            if (byId.containsKey(id)) {
                System.err.println("Storage drive " + roots.get(i) + " has no marker and volume " + id
                        + " is taken; leaving it unused.");
                continue;
            }
            byId.put(id, roots.get(i));
            writeVolumeMarker(roots.get(i), id);
        }
        List<Volume> result = new ArrayList<>();
        byId.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.add(new Volume(e.getKey(), e.getValue())));
        return result;
    }

    private static Integer readVolumeMarker(Path root) {
        Path marker = root.resolve(VOLUME_MARKER);
        if (!Files.isRegularFile(marker)) return null;
        try {
            return Integer.parseInt(Files.readString(marker).trim());
        } catch (IOException | NumberFormatException e) {
            System.err.println("Unreadable storage marker " + marker + ": " + e.getMessage());
            return null;
        }
    }

    private static void writeVolumeMarker(Path root, int id) {
        try {
            Files.writeString(root.resolve(VOLUME_MARKER), id + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Could not write storage marker in " + root + ": " + e.getMessage());
        }
    }


    /**
     * Store a file using the optimized storage strategy
     */
    public String storeFile(MultipartFile file, MediaFile mediaFile) throws IOException {
        // Determine storage strategy based on file size
        long fileSize = file.getSize();
        String mediaType = mediaFile.getMediaType().name();
        
        // Store ALL files on file system now (changed from previous database strategy)
        return storeOnFileSystem(file, mediaFile, fileSize, mediaType);
    }
    
    /**
     * Store file on file system with optimized organization
     */
    private String storeOnFileSystem(MultipartFile file, MediaFile mediaFile, long fileSize, String mediaType) throws IOException {
        // The drive with the most free space, then the folder for this type on it
        Volume volume = volumeForNewFile();
        Path storageDir = mediaDir(volume, mediaType, fileSize);
        Files.createDirectories(storageDir);

        // Generate unique filename with timestamp and original extension
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(file.getOriginalFilename());
        String filename = String.format("%s_%s_%s%s", 
            timestamp, 
            uniqueId, 
            sanitizeFilename(mediaFile.getTitle()), 
            extension
        );
        
        Path filePath = storageDir.resolve(filename);
        
        // Store file with streaming for large files
        if (fileSize > storageProperties.getLargeFileThreshold()) {
            storeFileStreaming(file, filePath);
        } else {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Return the path for the database: relative, with "vN:" when not on volume 1
        return toStoredPath(volume, filePath);
    }
    
    /**
     * Store file from InputStream (for async downloads)
     */
    public String storeFileFromStream(InputStream inputStream, String originalFilename, 
                                      long fileSize, lexicon.object.MediaType mediaType) throws IOException {
        // The drive with the most free space, then the folder for this type on it
        Volume volume = volumeForNewFile();
        Path storageDir = mediaDir(volume, mediaType.name(), fileSize);
        Files.createDirectories(storageDir);

        // Generate unique filename
String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);
        String cleanName = sanitizeFilename(originalFilename.replace(extension, ""));
        String filename = String.format("%s_%s_%s%s", timestamp, uniqueId, cleanName, extension);
        
        Path filePath = storageDir.resolve(filename);
        
        // Stream directly to file
        try (BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
             FileOutputStream outputStream = new FileOutputStream(filePath.toFile());
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream)) {
            
            byte[] buffer = new byte[storageProperties.getStreamingBufferSize()];
            int bytesRead;
            
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
            }
        }
        
        // Return the path for the database
        return toStoredPath(volume, filePath);
    }
    
    /**
     * Store large file using streaming to avoid memory issues
     */
    private void storeFileStreaming(MultipartFile file, Path filePath) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(filePath.toFile());
             BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream)) {
            
            byte[] buffer = new byte[storageProperties.getStreamingBufferSize()];
            int bytesRead;
            
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
            }
        }
    }
    
    /**
     * Get file input stream for serving files
     */
    public InputStream getFileInputStream(String relativePath) throws IOException {
        Path fullPath = resolveStoredPath(relativePath);
        
        if (!Files.exists(fullPath)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        
        return Files.newInputStream(fullPath);
    }
    
    /**
     * Get file for streaming with range support
     */
    public FileStreamInfo getFileForStreaming(String relativePath, long rangeStart, long rangeEnd) throws IOException {
        Path fullPath = resolveStoredPath(relativePath);
        
        if (!Files.exists(fullPath)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        
        long fileSize = Files.size(fullPath);
        
        // Validate range
        if (rangeStart < 0) rangeStart = 0;
        if (rangeEnd < 0 || rangeEnd >= fileSize) rangeEnd = fileSize - 1;
        
        RandomAccessFile randomAccessFile = new RandomAccessFile(fullPath.toFile(), "r");
        randomAccessFile.seek(rangeStart);
        
        return new FileStreamInfo(
            randomAccessFile,
            rangeStart,
            rangeEnd,
            fileSize,
            rangeEnd - rangeStart + 1
        );
    }
    
    /**
     * Place an existing file that is ALREADY on the storage volume into its proper media
     * directory by hard-linking it, and return the relative path the database should hold.
     *
     * This exists for torrent downloads: qBittorrent must keep its own copy at its own path
     * to go on seeding, while Lexicon needs the file under its media layout. A hard link
     * gives both — one set of bytes, two names — so importing a finished torrent costs no
     * disk and no copy time.
     *
     * The link is made by `ln` inside WSL rather than with {@link Files#createLink}: the
     * volume is ext4 and visible only inside WSL, reached from Windows over a
     * \\wsl.localhost 9P share that does not support creating hard links.
     *
     * Falls back to a move when linking fails (same volume, so still a rename rather than a
     * copy). The caller is told which happened, because a move means the torrent loses its
     * data and stops seeding.
     *
     * @param source absolute path of the existing file, on the storage volume
     * @return where the file ended up, and whether seeding survived
     */
    public LinkResult linkFileIntoStorage(Path source, String originalFilename, long fileSize,
                                          MediaType mediaType) throws IOException {
        // The drive the torrent downloaded to: a hard link cannot cross drives.
        Volume volume = volumeContaining(source).orElseGet(this::volumeForNewFile);
        Path storageDir = mediaDir(volume, mediaType.name(), fileSize);
        Files.createDirectories(storageDir);

        // Same naming as storeFileFromStream, so torrent imports are indistinguishable from
        // uploads once they are in the library.
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);
        String cleanName = sanitizeFilename(originalFilename.replace(extension, ""));
        Path target = storageDir.resolve(String.format("%s_%s_%s%s", timestamp, uniqueId, cleanName, extension));

        boolean linked = wslHardLink(source, target);
        if (!linked) {
            // A move keeps the import working; the torrent will then be reported as missing
            // files by qBittorrent, which the caller surfaces rather than hiding.
            Files.move(source, target);
        }

        return new LinkResult(toStoredPath(volume, target), linked);
    }

    /** Where an imported file landed, and whether the torrent can still seed it. */
    public record LinkResult(String relativePath, boolean hardLinked) {
    }

    /** True when the link was created. Never throws: the caller has a fallback. */
    private boolean wslHardLink(Path source, Path target) {
        String linuxSource = toWslPath(source);
        String linuxTarget = toWslPath(target);
        if (linuxSource == null || linuxTarget == null) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(
                    "wsl.exe", "-d", storageProperties.getWslDistro(), "--",
                    "ln", linuxSource, linuxTarget)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    System.err.println("Hard link failed: " + reader.lines().collect(java.util.stream.Collectors.joining(" ")));
                }
                return false;
            }
            return Files.exists(target);
        } catch (IOException e) {
            System.err.println("Could not run wsl.exe to hard link: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Translates a Windows path on the storage volume into the path WSL sees, so `ln` can be
     * handed both ends of the link. Returns null for anything that is not on a path WSL can
     * reach, which makes the caller fall back to a move.
     */
    static String toWslPath(Path path) {
        String text = path.toAbsolutePath().toString().replace('\\', '/');
        String marker = "//wsl.localhost/";
        if (text.toLowerCase().startsWith(marker)) {
            // //wsl.localhost/Ubuntu/mnt/... -> /mnt/...
            int afterDistro = text.indexOf('/', marker.length());
            if (afterDistro < 0) {
                return null;
            }
            String inside = text.substring(afterDistro);
            // Java normalises a bare \\host\share to a trailing separator, which would
            // leave "/" — the whole distro — as the answer. Nothing may be linked there.
            return inside.equals("/") ? null : inside;
        }
        if (text.length() > 2 && text.charAt(1) == ':') {
            // C:/x -> /mnt/c/x. Not the storage volume, but harmless to support.
            return "/mnt/" + Character.toLowerCase(text.charAt(0)) + text.substring(2);
        }
        return null;
    }

    /** A file on the storage drive: its path relative to the storage base ('/' separated), size and modified time. */
    public record StoredFile(String relativePath, long size, long lastModifiedMillis) {}

    /**
     * Every regular file under the given folders of the storage base (e.g. "audiobooks"). Links are
     * not followed and folders that do not exist are skipped. Read-only.
     */
    public List<StoredFile> listStoredFiles(List<String> folders) throws IOException {
        List<StoredFile> files = new ArrayList<>();
        // Every drive, each file named the way the database names it ("vN:" off volume 1).
        for (Volume volume : volumes()) {
            Path base = volume.root().toAbsolutePath().normalize();
            String prefix = volume.isPrimary() ? "" : "v" + volume.id() + ":";
            for (String folder : folders) {
                Path root = base.resolve(folder).normalize();
                if (!root.startsWith(base) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var walk = Files.walk(root)) {
                    for (Path p : (Iterable<Path>) walk::iterator) {
                        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue;
                        String rel = prefix + base.relativize(p).toString().replace('\\', '/');
                        files.add(new StoredFile(rel, Files.size(p), Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis()));
                    }
                }
            }
        }
        return files;
    }

    /**
     * Delete one stored file, only if it is a regular file inside one of {@code allowedFolders}.
     * Returns the bytes freed, or -1 when it was refused, missing or could not be deleted.
     */
    public long deleteStoredFile(String relativePath, List<String> allowedFolders) {
        if (relativePath == null || relativePath.isBlank()) return -1;
        Path base = volumeOf(relativePath).root().toAbsolutePath().normalize();
        Path target = base.resolve(withoutVolumePrefix(relativePath).replace('\\', '/')).normalize();
        boolean allowed = allowedFolders.stream().map(f -> base.resolve(f).normalize()).anyMatch(target::startsWith);
        if (!allowed || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return -1;
        try {
            long size = Files.size(target);
            Files.delete(target);
            return size;
        } catch (IOException e) {
            System.err.println("Failed to delete stored file: " + relativePath + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * Delete file from storage
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path fullPath = resolveStoredPath(relativePath);
            return Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            System.err.println("Failed to delete file: " + relativePath + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file size
     */
    public long getFileSize(String relativePath) throws IOException {
        Path fullPath = resolveStoredPath(relativePath);
        return Files.size(fullPath);
    }
    
    /**
     * Check if file exists
     */
    public boolean fileExists(String relativePath) {
        Path fullPath = resolveStoredPath(relativePath);
        return Files.exists(fullPath);
    }
    
    /**
     * Move file to different storage category (e.g., after transcoding)
     */
    public String moveFile(String currentPath, String newMediaType, long newFileSize) throws IOException {
        // Stays on its own drive, so the move is a rename rather than a copy
        Volume volume = volumeOf(currentPath);
        Path currentFullPath = resolveStoredPath(currentPath);

        if (!Files.exists(currentFullPath)) {
            throw new FileNotFoundException("Source file not found: " + currentPath);
        }

        // Get new storage path
        Path newStorageDir = mediaDir(volume, newMediaType, newFileSize);
        Files.createDirectories(newStorageDir);
        
        // Keep same filename
        String filename = currentFullPath.getFileName().toString();
        Path newFullPath = newStorageDir.resolve(filename);
        
        // Move file
        Files.move(currentFullPath, newFullPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return the new path for the database
        return toStoredPath(volume, newFullPath);
    }
    
    /**
     * Move file to specific destination path (for testing and direct moves)
     */
    public String moveFileToPath(String sourcePath, String destinationPath, long fileSize) throws IOException {
        Path sourceFullPath = resolveStoredPath(sourcePath);
        Path destFullPath = resolveStoredPath(destinationPath);
        
        if (!Files.exists(sourceFullPath)) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }
        
        // Ensure destination directory exists
        Files.createDirectories(destFullPath.getParent());
        
        // Move file
        Files.move(sourceFullPath, destFullPath, StandardCopyOption.REPLACE_EXISTING);
        
        return destinationPath;
    }
    
    /**
     * Generate checksum for file integrity verification
     */
    public String generateChecksum(String relativePath) throws IOException {
        Path fullPath = resolveStoredPath(relativePath);
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream inputStream = Files.newInputStream(fullPath);
                 BufferedInputStream bufferedInput = new BufferedInputStream(inputStream)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        } catch (Exception e) {
            throw new IOException("Failed to generate checksum", e);
        }
    }
    
    public Map<String, Object> getStorageInfo() throws IOException {
        List<WslDiskUsageService.Volume> drives = wslDiskUsageService.listPhysicalDriveVolumes();

        List<Map<String, Object>> volumes = new ArrayList<>();
        long totalBytes = 0, usedBytes = 0, freeBytes = 0;
        int i = 1;
        for (WslDiskUsageService.Volume v : drives) {
            volumes.add(Map.of("label", "Media Drive " + i++,
                    "totalBytes", v.totalBytes, "usedBytes", v.usedBytes, "freeBytes", v.freeBytes));
            totalBytes += v.totalBytes;
            usedBytes += v.usedBytes;
            freeBytes += v.freeBytes;
        }

        return Map.of("volumes", volumes, "totalBytes", totalBytes, "usedBytes", usedBytes, "freeBytes", freeBytes);
    }

    /**
     * Helper methods
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
    
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        
        // Remove or replace invalid characters
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_")
                                 .replaceAll("_{2,}", "_"); // Replace multiple underscores with single
        
        // Limit length - use the sanitized string's length, not the original
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }
    
    /**
     * Inner class for file streaming information
     */
    public static class FileStreamInfo {
        private final RandomAccessFile file;
        private final long rangeStart;
        private final long rangeEnd;
        private final long totalSize;
        private final long contentLength;
        
        public FileStreamInfo(RandomAccessFile file, long rangeStart, long rangeEnd, long totalSize, long contentLength) {
            this.file = file;
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.totalSize = totalSize;
            this.contentLength = contentLength;
        }
        
        public RandomAccessFile getFile() { return file; }
        public long getRangeStart() { return rangeStart; }
        public long getRangeEnd() { return rangeEnd; }
        public long getTotalSize() { return totalSize; }
        public long getContentLength() { return contentLength; }
    }
}