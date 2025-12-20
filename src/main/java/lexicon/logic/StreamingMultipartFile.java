package lexicon.logic;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A MultipartFile implementation that streams data from a file on disk
 * instead of loading everything into memory, suitable for large files.
 */
public class StreamingMultipartFile implements MultipartFile {
    
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path filePath;
    
    public StreamingMultipartFile(String name, String originalFilename, String contentType, Path filePath) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.filePath = filePath;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }
    
    @Override
    public String getContentType() {
        return contentType;
    }
    
    @Override
    public boolean isEmpty() {
        try {
            return Files.size(filePath) == 0;
        } catch (IOException e) {
            return true;
        }
    }
    
    @Override
    public long getSize() {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }
    
    @Override
    public byte[] getBytes() throws IOException {
        // For small files, we can still use readAllBytes
        // For large files, this will be called by MediaManager.uploadMediaFile
        // We'll need to ensure MediaManager uses getInputStream() instead
        if (getSize() > 100 * 1024 * 1024) { // 100MB threshold
            throw new IOException("File too large for getBytes(), use getInputStream() instead");
        }
        return Files.readAllBytes(filePath);
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(filePath), 8192);
    }
    
    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(filePath, dest.toPath());
    }
}