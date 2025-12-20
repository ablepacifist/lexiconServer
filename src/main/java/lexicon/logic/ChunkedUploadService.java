package lexicon.logic;

import lexicon.logic.MediaManagerService;
import lexicon.object.ChunkedUpload;
import lexicon.object.ChunkedUploadStatus;
import lexicon.object.MediaFile;
import lexicon.object.StreamingMultipartFile;
import lexicon.object.UploadChunk;
import lexicon.config.StorageProperties;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.UploadProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling chunked file uploads
 * Manages temporary chunk storage and assembly into final files
 */
@Service
public class ChunkedUploadService {
    
    @Autowired
    private MediaManagerService mediaManager;
    
    @Autowired
    private StorageProperties storageProperties;
    
    @Autowired
    private OptimizedFileStorageService fileStorageService;
    
    @Autowired
    private UploadProgressService uploadProgressService;
    
    // In-memory storage for upload sessions (could be moved to database for persistence)
    private final Map<String, ChunkedUpload> activeUploads = new ConcurrentHashMap<>();
    
    public ChunkedUploadService() {
        // Create temp directory if it doesn't exist
        // Note: Initialization will be done in @PostConstruct method
    }
    
    @PostConstruct
    public void init() {
        try {
            // Use StorageProperties for temp directory paths
            Files.createDirectories(Paths.get(storageProperties.getTempChunksPath()));
            Files.createDirectories(Paths.get(storageProperties.getTempProcessingPath()));
            Files.createDirectories(Paths.get(storageProperties.getCachePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directories: " + e.getMessage());
        }
    }
    
    /**
     * Initialize a new chunked upload session
     */
    public ChunkedUpload initializeUpload(String filename, String contentType, long totalSize, 
                                         int chunkSize, int userId, String title, String description, 
                                         boolean isPublic, String mediaType, String checksum) {
        
        // Generate unique upload ID
        String uploadId = UUID.randomUUID().toString();
        
        // Calculate total chunks needed
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
        
        // Create upload session
        ChunkedUpload upload = new ChunkedUpload(
            uploadId, filename, contentType, totalSize, totalChunks, chunkSize,
            userId, title, description, isPublic, mediaType
        );
        upload.setChecksum(checksum);
        
        // Store in active uploads
        activeUploads.put(uploadId, upload);
        
        // Create directory for this upload's chunks
        try {
            Files.createDirectories(Paths.get(storageProperties.getTempChunksPath(), uploadId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create chunk directory for upload: " + e.getMessage());
        }
        
        System.out.println("🚀 Initialized chunked upload: " + filename + " (" + totalSize + " bytes, " + totalChunks + " chunks)");
        
        return upload;
    }
    
    /**
     * Upload a single chunk
     */
    public boolean uploadChunk(String uploadId, int chunkNumber, MultipartFile chunkFile, String checksum) 
            throws IOException {
        
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) {
            throw new IllegalArgumentException("Upload session not found: " + uploadId);
        }
        
        if (upload.getStatus() != ChunkedUploadStatus.IN_PROGRESS) {
            throw new IllegalStateException("Upload session is not in progress: " + upload.getStatus());
        }
        
        // Validate chunk number
        if (chunkNumber < 0 || chunkNumber >= upload.getTotalChunks()) {
            throw new IllegalArgumentException("Invalid chunk number: " + chunkNumber);
        }
        
        // Check if chunk already uploaded
        if (upload.getUploadedChunks().contains(chunkNumber)) {
            System.out.println("⚠️ Chunk " + chunkNumber + " already uploaded for " + uploadId);
            return true; // Already have this chunk
        }
        
        try {
            // Read chunk data
            byte[] chunkData = chunkFile.getBytes();
            
            // Verify chunk checksum if provided
            if (checksum != null && !checksum.isEmpty()) {
                String calculatedChecksum = calculateMD5(chunkData);
                if (!calculatedChecksum.equals(checksum)) {
                    throw new IllegalArgumentException("Chunk checksum mismatch");
                }
            }
            
            // Save chunk to temporary file
            Path chunkPath = Paths.get(storageProperties.getTempChunksPath(), uploadId, "chunk_" + chunkNumber);
            Files.write(chunkPath, chunkData);
            
            // Update upload progress
            upload.addUploadedChunk(chunkNumber);
            upload.setLastActivity(LocalDateTime.now());
            
            // Update progress tracking
            System.out.println("📤 Uploaded chunk " + (chunkNumber + 1) + "/" + upload.getTotalChunks() + " for " + upload.getOriginalFilename());
            
            // Update enhanced progress tracking with WebSocket
            long bytesUploaded = (long) upload.getUploadedChunks().size() * upload.getChunkSize();
            uploadProgressService.updateProgress(uploadId, bytesUploaded, upload.getTotalSize(), "uploading");
            
            System.out.println("📦 Uploaded chunk " + chunkNumber + "/" + (upload.getTotalChunks() - 1) + 
                             " for " + upload.getOriginalFilename() + " (" + upload.getProgress() + "%)");
            
            // Check if upload is complete
            if (upload.isComplete()) {
                upload.setStatus(ChunkedUploadStatus.ASSEMBLING);
                System.out.println("🔄 All chunks uploaded for " + upload.getOriginalFilename() + ", assembling file...");
                System.out.println("✅ All chunks uploaded for " + upload.getOriginalFilename() + ", ready for assembly");
                
                // Start assembly in background
                new Thread(() -> {
                    try {
                        assembleChunks(uploadId);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to assemble chunks for " + uploadId + ": " + e.getMessage());
                        upload.setStatus(ChunkedUploadStatus.FAILED);
                        System.err.println("❌ Upload failed for " + uploadId + ": " + e.getMessage());
                    }
                }).start();
            }
            
            return true;
        } catch (IOException e) {
            throw new IOException("Failed to save chunk: " + e.getMessage());
        }
    }
    
    /**
     * Get upload status
     */
    public ChunkedUpload getUploadStatus(String uploadId) {
        return activeUploads.get(uploadId);
    }
    
    /**
     * Assemble chunks into final file
     */
    private void assembleChunks(String uploadId) throws Exception {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) {
            throw new IllegalStateException("Upload session not found during assembly");
        }
        
        upload.setStatus(ChunkedUploadStatus.ASSEMBLING);
        System.out.println("🔧 Assembling " + upload.getTotalChunks() + " chunks for " + upload.getOriginalFilename() + "...");
        
        System.out.println("🔧 Assembling " + upload.getTotalChunks() + " chunks for " + upload.getOriginalFilename());
        
        // Create temporary file for assembly
        Path tempFile = Paths.get(storageProperties.getTempChunksPath(), uploadId + "_assembled");
        
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            // Write chunks in order using streaming to avoid memory issues
            for (int i = 0; i < upload.getTotalChunks(); i++) {
                Path chunkPath = Paths.get(storageProperties.getTempChunksPath(), uploadId, "chunk_" + i);
                
                if (!Files.exists(chunkPath)) {
                    throw new IllegalStateException("Missing chunk " + i + " during assembly");
                }
                
                // Stream chunk data directly to avoid loading entire chunk into memory
                try (FileInputStream fis = new FileInputStream(chunkPath.toFile())) {
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
        
        // Verify file size
        long assembledSize = Files.size(tempFile);
        if (assembledSize != upload.getTotalSize()) {
            throw new IllegalStateException("Assembled file size mismatch. Expected: " + 
                                          upload.getTotalSize() + ", Got: " + assembledSize);
        }
        
        // Verify file checksum if provided
        if (upload.getChecksum() != null && !upload.getChecksum().isEmpty()) {
            String calculatedChecksum = calculateMD5(Files.readAllBytes(tempFile));
            if (!calculatedChecksum.equals(upload.getChecksum())) {
                throw new IllegalStateException("Assembled file checksum mismatch");
            }
        }
        
        System.out.println("✅ Successfully assembled " + upload.getOriginalFilename() + " (" + assembledSize + " bytes)");
        
        upload.setStatus(ChunkedUploadStatus.COMPLETED);
        System.out.println("✅ File assembled successfully for " + upload.getOriginalFilename() + ", ready for finalization");
    }
    
    /**
     * Finalize upload and create MediaFile in database
     */
    public Map<String, Object> finalizeUpload(String uploadId) throws Exception {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) {
            throw new IllegalArgumentException("Upload session not found: " + uploadId);
        }
        
        if (upload.getStatus() != ChunkedUploadStatus.COMPLETED) {
            throw new IllegalStateException("Upload is not ready for finalization: " + upload.getStatus());
        }
        
        // Path to assembled file
        Path assembledFile = Paths.get(storageProperties.getTempChunksPath(), uploadId + "_assembled");
        
        if (!Files.exists(assembledFile)) {
            throw new IllegalStateException("Assembled file not found");
        }
        
        try {
            // Create a streaming MultipartFile-like object for the media manager
            StreamingMultipartFile streamingMultipartFile = new StreamingMultipartFile(
                upload.getOriginalFilename(),
                upload.getOriginalFilename(), 
                upload.getContentType(),
                assembledFile
            );
            
            // Use existing media manager to store in database
            MediaFile mediaFile = mediaManager.uploadMediaFile(
                streamingMultipartFile,
                upload.getUploadedBy(),
                upload.getTitle(),
                upload.getDescription(),
                upload.isPublic(),
                upload.getMediaType()
            );
            
            System.out.println("🎉 Finalized chunked upload: " + mediaFile.getFilename() + " (ID: " + mediaFile.getId() + ")");
            
            // Mark as completed in progress tracker
            System.out.println("🎉 Upload completed successfully: " + uploadId);
            
            // Cleanup temporary files
            cleanupUpload(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Large file uploaded successfully");
            response.put("mediaFile", mediaFile);
            
            return response;
            
        } catch (Exception e) {
            throw new Exception("Failed to finalize upload: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cancel an upload session
     */
    public boolean cancelUpload(String uploadId) {
        ChunkedUpload upload = activeUploads.remove(uploadId);
        if (upload != null) {
            upload.setStatus(ChunkedUploadStatus.CANCELLED);
            cleanupUpload(uploadId);
            System.out.println("🗑️ Cancelled upload: " + uploadId);
            return true;
        }
        return false;
    }
    
    /**
     * Cleanup temporary files for an upload
     */
    private void cleanupUpload(String uploadId) {
        try {
            // Remove chunk directory
            Path chunkDir = Paths.get(storageProperties.getTempChunksPath(), uploadId);
            if (Files.exists(chunkDir)) {
                Files.walk(chunkDir)
                     .map(Path::toFile)
                     .forEach(File::delete);
                Files.deleteIfExists(chunkDir);
            }
            
            // Remove assembled file
            Path assembledFile = Paths.get(storageProperties.getTempChunksPath(), uploadId + "_assembled");
            Files.deleteIfExists(assembledFile);
            
            // Remove from active uploads
            activeUploads.remove(uploadId);
            
        } catch (IOException e) {
            System.err.println("⚠️ Failed to cleanup upload " + uploadId + ": " + e.getMessage());
        }
    }
    
    /**
     * Calculate MD5 checksum of data
     */
    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Temporary MultipartFile implementation for assembled files
     */
    private static class TempMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;
        
        public TempMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getOriginalFilename() { return originalFilename; }
        
        @Override
        public String getContentType() { return contentType; }
        
        @Override
        public boolean isEmpty() { return bytes.length == 0; }
        
        @Override
        public long getSize() { return bytes.length; }
        
        @Override
        public byte[] getBytes() { return bytes; }
        
        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        
        @Override
        public void transferTo(File dest) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(bytes);
            }
        }
    }
}