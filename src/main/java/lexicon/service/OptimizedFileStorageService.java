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
import java.util.UUID;

/**
 * Enhanced file storage service using optimized file system storage
 */
@Service
public class OptimizedFileStorageService {
    
    @Autowired
    private StorageProperties storageProperties;
    
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