package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of MediaManagerService for media file management
 */
@Service
public class MediaManagerServiceImpl implements MediaManagerService {
    
    @Autowired
    private ILexiconDatabase database;
    
    @Value("${lexicon.file.upload-dir:./uploads}")
    private String uploadDir;
    
    @Override
    public MediaFile uploadMediaFile(MultipartFile file, int userId, String title, String description, boolean isPublic) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        try {
            // Calculate file hash for deduplication
            String fileHash = calculateFileHash(file);
            
            // Check if file already exists
            MediaFile existingFile = database.getMediaFileByHash(fileHash);
            if (existingFile != null) {
                // File already exists, create a reference instead of uploading again
                System.out.println("File already exists with hash: " + fileHash + ". Creating reference instead of duplicate upload.");
                database.addMediaFileReference(existingFile.getId(), userId, title, description);
                
                // Return a new MediaFile object representing this user's reference to the existing file
                MediaFile userReference = new MediaFile(
                    database.getNextMediaFileId(),
                    existingFile.getFilename(),
                    file.getOriginalFilename(), // Keep user's original filename
                    file.getContentType(),
                    file.getSize(),
                    existingFile.getFilePath(), // Same physical file
                    userId,
                    title != null ? title : file.getOriginalFilename(),
                    description,
                    isPublic,
                    fileHash
                );
                database.addMediaFile(userReference);
                return userReference;
            }
            
            // File doesn't exist, proceed with normal upload
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);
            
            // Save file to disk
            Files.copy(file.getInputStream(), filePath);
            
            // Create MediaFile object with hash
            int newId = database.getNextMediaFileId();
            MediaFile mediaFile = new MediaFile(
                newId,
                uniqueFilename,
                originalFilename,
                file.getContentType(),
                file.getSize(),
                filePath.toString(),
                userId,
                title != null ? title : originalFilename,
                description,
                isPublic,
                fileHash
            );
            
            // Save to database
            database.addMediaFile(mediaFile);
            
            return mediaFile;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }
    
    /**
     * Calculate SHA-256 hash of file content for deduplication
     */
    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to calculate file hash", e);
        }
    }
    
    @Override
    public MediaFile getMediaFileById(int mediaFileId) {
        return database.getMediaFile(mediaFileId);
    }
    
    @Override
    public List<MediaFile> getMediaFilesByUser(int userId) {
        return database.getMediaFilesByPlayer(userId);
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        return database.getAllPublicMediaFiles();
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        return database.searchMediaFiles(searchTerm);
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        return database.getRecentMediaFiles(limit);
    }
    
    @Override
    public boolean updateMediaFile(MediaFile mediaFile) {
        try {
            database.updateMediaFile(mediaFile);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean deleteMediaFile(int mediaFileId, int userId) {
        try {
            MediaFile mediaFile = database.getMediaFile(mediaFileId);
            if (mediaFile == null) {
                return false;
            }
            
            // Check if user owns the file
            if (mediaFile.getUploadedBy() != userId) {
                return false;
            }
            
            // Delete file from disk
            try {
                Path filePath = Paths.get(mediaFile.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // Log error but continue with database deletion
                e.printStackTrace();
            }
            
            // Delete from database
            database.deleteMediaFile(mediaFileId);
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean hasAccessPermission(int mediaFileId, int userId) {
        MediaFile mediaFile = database.getMediaFile(mediaFileId);
        if (mediaFile == null) {
            return false;
        }
        
        // User can access their own files or public files
        return mediaFile.isPublic() || mediaFile.getUploadedBy() == userId;
    }
    
    @Override
    public byte[] getFileData(int mediaFileId) {
        MediaFile mediaFile = database.getMediaFile(mediaFileId);
        if (mediaFile == null) {
            return null;
        }
        
        try {
            Path filePath = Paths.get(mediaFile.getFilePath());
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
