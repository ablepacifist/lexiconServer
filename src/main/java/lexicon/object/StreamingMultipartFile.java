package lexicon.object;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MultipartFile implementation that streams data from a file on disk
 * instead of loading everything into memory
 */
public class StreamingMultipartFile implements MultipartFile {
    
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path filePath;
    private final long size;
    
    public StreamingMultipartFile(String name, String originalFilename, String contentType, Path filePath) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.filePath = filePath;
        try {
            this.size = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get file size", e);
        }
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
        return size == 0;
    }
    
    @Override
    public long getSize() {
        return size;
    }
    
    @Override
    public byte[] getBytes() throws IOException {
        // For large files, avoid this method as it loads everything into memory
        if (size > 100 * 1024 * 1024) { // 100MB threshold
            throw new UnsupportedOperationException("File too large for getBytes(), use getInputStream() instead");
        }
        return Files.readAllBytes(filePath);
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(filePath);
    }
    
    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(filePath, dest.toPath());
    }
}