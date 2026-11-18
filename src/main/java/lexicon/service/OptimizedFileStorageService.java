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
        // Get appropriate storage path
        String storagePath = storageProperties.getStoragePathForMedia(mediaType, fileSize);
        
        // Ensure directory exists
        Path storageDir = Paths.get(storagePath);
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
        
        // Return relative path for database storage
        Path basePath = Paths.get(storageProperties.getBasePath());
        return basePath.relativize(filePath).toString();
    }
    
    /**
     * Store file from InputStream (for async downloads)
     */
    public String storeFileFromStream(InputStream inputStream, String originalFilename, 
                                      long fileSize, lexicon.object.MediaType mediaType) throws IOException {
        // Get appropriate storage path
        String storagePath = storageProperties.getStoragePathForMedia(mediaType.name(), fileSize);
        
        // Ensure directory exists
        Path storageDir = Paths.get(storagePath);
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
        
        // Return relative path
        Path basePath = Paths.get(storageProperties.getBasePath());
        return basePath.relativize(filePath).toString();
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
        Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
        
        if (!Files.exists(fullPath)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        
        return Files.newInputStream(fullPath);
    }
    
    /**
     * Get file for streaming with range support
     */
    public FileStreamInfo getFileForStreaming(String relativePath, long rangeStart, long rangeEnd) throws IOException {
        Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
        
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
        String storagePath = storageProperties.getStoragePathForMedia(mediaType.name(), fileSize);
        Path storageDir = Paths.get(storagePath);
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

        Path basePath = Paths.get(storageProperties.getBasePath());
        return new LinkResult(basePath.relativize(target).toString(), linked);
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

    /**
     * Delete file from storage
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
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
        Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
        return Files.size(fullPath);
    }
    
    /**
     * Check if file exists
     */
    public boolean fileExists(String relativePath) {
        Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
        return Files.exists(fullPath);
    }
    
    /**
     * Move file to different storage category (e.g., after transcoding)
     */
    public String moveFile(String currentPath, String newMediaType, long newFileSize) throws IOException {
        Path currentFullPath = Paths.get(storageProperties.getBasePath(), currentPath);
        
        if (!Files.exists(currentFullPath)) {
            throw new FileNotFoundException("Source file not found: " + currentPath);
        }
        
        // Get new storage path
        String newStoragePath = storageProperties.getStoragePathForMedia(newMediaType, newFileSize);
        Path newStorageDir = Paths.get(newStoragePath);
        Files.createDirectories(newStorageDir);
        
        // Keep same filename
        String filename = currentFullPath.getFileName().toString();
        Path newFullPath = newStorageDir.resolve(filename);
        
        // Move file
        Files.move(currentFullPath, newFullPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return new relative path
        Path basePath = Paths.get(storageProperties.getBasePath());
        return basePath.relativize(newFullPath).toString();
    }
    
    /**
     * Move file to specific destination path (for testing and direct moves)
     */
    public String moveFileToPath(String sourcePath, String destinationPath, long fileSize) throws IOException {
        Path sourceFullPath = Paths.get(storageProperties.getBasePath(), sourcePath);
        Path destFullPath = Paths.get(storageProperties.getBasePath(), destinationPath);
        
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
        Path fullPath = Paths.get(storageProperties.getBasePath(), relativePath);
        
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