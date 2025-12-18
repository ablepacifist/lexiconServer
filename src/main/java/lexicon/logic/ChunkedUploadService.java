package lexicon.logic;

import lexicon.logic.MediaManagerService;
import lexicon.object.ChunkedUpload;
import lexicon.object.ChunkedUploadStatus;
import lexicon.object.MediaFile;
import lexicon.object.UploadChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private ChunkedUploadProgressTracker progressTracker;
    
    // In-memory storage for upload sessions (could be moved to database for persistence)
    private final Map<String, ChunkedUpload> activeUploads = new ConcurrentHashMap<>();
    
    // Directory for temporary chunk storage
    private final String tempChunkDir = System.getProperty("java.io.tmpdir") + "/lexicon-chunks/";
    
    public ChunkedUploadService() {
        // Create temp directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(tempChunkDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary chunk directory: " + e.getMessage());
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
            Files.createDirectories(Paths.get(tempChunkDir, uploadId));
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
            Path chunkPath = Paths.get(tempChunkDir, uploadId, "chunk_" + chunkNumber);
            Files.write(chunkPath, chunkData);
            
            // Update upload progress
            upload.addUploadedChunk(chunkNumber);
            upload.setLastActivity(LocalDateTime.now());
            
            // Update progress tracker
            progressTracker.updateProgress(upload, "Uploaded chunk " + (chunkNumber + 1) + "/" + upload.getTotalChunks());
            
            System.out.println("📦 Uploaded chunk " + chunkNumber + "/" + (upload.getTotalChunks() - 1) + 
                             " for " + upload.getOriginalFilename() + " (" + upload.getProgress() + "%)");
            
            // Check if upload is complete
            if (upload.isComplete()) {
                upload.setStatus(ChunkedUploadStatus.ASSEMBLING);
                progressTracker.updateProgress(upload, "All chunks uploaded, assembling file...");
                System.out.println("✅ All chunks uploaded for " + upload.getOriginalFilename() + ", ready for assembly");
                
                // Start assembly in background
                new Thread(() -> {
                    try {
                        assembleChunks(uploadId);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to assemble chunks for " + uploadId + ": " + e.getMessage());
                        upload.setStatus(ChunkedUploadStatus.FAILED);
                        progressTracker.markFailed(uploadId, e.getMessage());
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
        progressTracker.updateProgress(upload, "Assembling file from " + upload.getTotalChunks() + " chunks...");
        
        System.out.println("🔧 Assembling " + upload.getTotalChunks() + " chunks for " + upload.getOriginalFilename());
        
        // Create temporary file for assembly
        Path tempFile = Paths.get(tempChunkDir, uploadId + "_assembled");
        
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            // Write chunks in order
            for (int i = 0; i < upload.getTotalChunks(); i++) {
                Path chunkPath = Paths.get(tempChunkDir, uploadId, "chunk_" + i);
                
                if (!Files.exists(chunkPath)) {
                    throw new IllegalStateException("Missing chunk " + i + " during assembly");
                }
                
                byte[] chunkData = Files.readAllBytes(chunkPath);
                fos.write(chunkData);
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
        progressTracker.updateProgress(upload, "File assembled successfully, ready for finalization");
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
        Path assembledFile = Paths.get(tempChunkDir, uploadId + "_assembled");
        
        if (!Files.exists(assembledFile)) {
            throw new IllegalStateException("Assembled file not found");
        }
        
        try {
            // Create a temporary MultipartFile-like object for the media manager
            byte[] fileData = Files.readAllBytes(assembledFile);
            TempMultipartFile tempMultipartFile = new TempMultipartFile(
                upload.getOriginalFilename(),
                upload.getOriginalFilename(), 
                upload.getContentType(),
                fileData
            );
            
            // Use existing media manager to store in database
            MediaFile mediaFile = mediaManager.uploadMediaFile(
                tempMultipartFile,
                upload.getUploadedBy(),
                upload.getTitle(),
                upload.getDescription(),
                upload.isPublic(),
                upload.getMediaType()
            );
            
            System.out.println("🎉 Finalized chunked upload: " + mediaFile.getFilename() + " (ID: " + mediaFile.getId() + ")");
            
            // Mark as completed in progress tracker
            progressTracker.markCompleted(uploadId);
            
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
            Path chunkDir = Paths.get(tempChunkDir, uploadId);
            if (Files.exists(chunkDir)) {
                Files.walk(chunkDir)
                     .map(Path::toFile)
                     .forEach(File::delete);
                Files.deleteIfExists(chunkDir);
            }
            
            // Remove assembled file
            Path assembledFile = Paths.get(tempChunkDir, uploadId + "_assembled");
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