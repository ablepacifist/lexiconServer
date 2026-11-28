package lexicon.logic;

import lexicon.data.IChatFileDatabase;
import lexicon.object.ChatFile;
import lexicon.service.OptimizedFileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ChatFileManager implements ChatFileManagerService {

    private static final long MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB
    private static final int THUMBNAIL_MAX_WIDTH = 400;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final String CHAT_UPLOADS = "chat-uploads";

    private final IChatFileDatabase chatFileDatabase;
    private final OptimizedFileStorageService fileStorage;

    @Autowired
    public ChatFileManager(IChatFileDatabase chatFileDatabase, OptimizedFileStorageService fileStorage) {
        this.chatFileDatabase = chatFileDatabase;
        this.fileStorage = fileStorage;
    }

    @Override
    public long deleteChatFile(long fileId) {
        ChatFile chatFile = chatFileDatabase.getChatFile(fileId);
        if (chatFile == null) return -1;
        long freed = 0;
        // The file is on one drive; the stored name says only which file, not which drive.
        for (Path folder : fileStorage.folderOnEveryVolume(CHAT_UPLOADS)) {
            Path dir = folder.toAbsolutePath().normalize();
            freed += deleteInside(dir, chatFile.getStoredFilename());
            if (chatFile.getThumbnailFilename() != null) {
                freed += deleteInside(dir.resolve("thumbnails"), chatFile.getThumbnailFilename());
            }
        }
        chatFileDatabase.deleteChatFile(fileId);
        return freed;
    }

    /** Deletes {@code name} only if it resolves inside {@code dir}; returns the bytes removed. */
    private static long deleteInside(Path dir, String name) {
        if (name == null || name.isBlank()) return 0;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) return 0;
        try {
            long size = Files.exists(target) ? Files.size(target) : 0;
            return Files.deleteIfExists(target) ? size : 0;
        } catch (java.io.IOException e) {
            System.err.println("Could not delete chat file " + target + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public Map<String, Object> uploadChatFile(byte[] fileData, String originalFilename, String mimeType,
                                   int userId, Integer channelId) {
        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException("File data cannot be empty");
        }
        if (fileData.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 8 MB limit");
        }
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported file type: " + mimeType);
        }

        // The drive with the most free space
        Path chatUploadsDir = fileStorage.folderForNewFile(CHAT_UPLOADS);
        Path thumbDir = chatUploadsDir.resolve("thumbnails");
        try {
            Files.createDirectories(chatUploadsDir);
            Files.createDirectories(thumbDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create chat uploads directory", e);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String extension = getExtension(originalFilename);
        String storedFilename = timestamp + "_" + uniqueId + extension;

        // Write original file
        Path filePath = chatUploadsDir.resolve(storedFilename);
        try {
            Files.write(filePath, fileData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store chat file", e);
        }

        // Read dimensions
        Integer width = null;
        Integer height = null;
        String thumbnailFilename = null;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileData));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();

                // Generate thumbnail (skip for GIFs to preserve animation)
                if (!"image/gif".equals(mimeType) && width > THUMBNAIL_MAX_WIDTH) {
                    thumbnailFilename = "thumb_" + storedFilename;
                    // Force JPEG extension for thumbnail
                    thumbnailFilename = thumbnailFilename.replaceAll("\\.[^.]+$", ".jpg");
                    generateThumbnail(image, thumbDir.resolve(thumbnailFilename));
                }
            }
        } catch (IOException e) {
            // Non-fatal: we can serve without dimensions/thumbnail
            System.err.println("Failed to read image dimensions: " + e.getMessage());
        }

        // Build and persist ChatFile record
        ChatFile chatFile = new ChatFile();
        chatFile.setOriginalFilename(originalFilename);
        chatFile.setStoredFilename(storedFilename);
        chatFile.setMimeType(mimeType);
        chatFile.setFileSize(fileData.length);
        chatFile.setWidth(width);
        chatFile.setHeight(height);
        chatFile.setThumbnailFilename(thumbnailFilename);
        chatFile.setUploadedBy(userId);
        chatFile.setChannelId(channelId);
        chatFile.setCreatedAt(LocalDateTime.now());

        long id = chatFileDatabase.addChatFile(chatFile);
        chatFile.setId(id);

        return buildUploadResponse(chatFile);
    }

    @Override
    public ChatFileServingInfo getFileForServing(long fileId) {
        ChatFile chatFile = chatFileDatabase.getChatFile(fileId);
        if (chatFile == null) return null;

        Path filePath = findChatFile(chatFile.getStoredFilename());
        if (filePath == null) return null;

        return new ChatFileServingInfo(filePath, chatFile.getMimeType());
    }

    @Override
    public ChatFileServingInfo getThumbnailForServing(long fileId) {
        ChatFile chatFile = chatFileDatabase.getChatFile(fileId);
        if (chatFile == null) return null;

        Path thumbPath;
        String contentType;

        if (chatFile.getThumbnailFilename() != null) {
            thumbPath = findChatFile("thumbnails/" + chatFile.getThumbnailFilename());
            contentType = "image/jpeg";
        } else {
            // Fall back to original file
            thumbPath = findChatFile(chatFile.getStoredFilename());
            contentType = chatFile.getMimeType();
        }

        if (thumbPath == null) return null;

        return new ChatFileServingInfo(thumbPath, contentType);
    }

    private Map<String, Object> buildUploadResponse(ChatFile chatFile) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", chatFile.getId());
        response.put("url", "/api/chat/files/" + chatFile.getId());
        response.put("thumbnailUrl", "/api/chat/files/" + chatFile.getId() + "/thumb");
        response.put("originalFilename", chatFile.getOriginalFilename());
        response.put("mimeType", chatFile.getMimeType());
        response.put("width", chatFile.getWidth());
        response.put("height", chatFile.getHeight());
        response.put("fileSize", chatFile.getFileSize());
        response.put("uploadedBy", chatFile.getUploadedBy());
        response.put("createdAt", chatFile.getCreatedAt() != null ? chatFile.getCreatedAt().toString() : null);
        return response;
    }

    /** The chat file on whichever drive holds it, or null. Names are unique, so the first match is the file. */
    private Path findChatFile(String nameInChatUploads) {
        if (nameInChatUploads == null || nameInChatUploads.isBlank()) return null;
        for (Path folder : fileStorage.folderOnEveryVolume(CHAT_UPLOADS)) {
            Path candidate = folder.resolve(nameInChatUploads);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private void generateThumbnail(BufferedImage original, Path outputPath) throws IOException {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        int thumbWidth = THUMBNAIL_MAX_WIDTH;
        int thumbHeight = (int) ((double) originalHeight / originalWidth * thumbWidth);

        BufferedImage thumbnail = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, thumbWidth, thumbHeight, null);
        g.dispose();

        ImageIO.write(thumbnail, "jpg", outputPath.toFile());
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
