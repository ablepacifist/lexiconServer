package lexicon.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class YtDlpService {
    
    @Value("${ytdlp.cookies.path:}")
    private String cookiesPath;
    
    public enum DownloadType {
        AUDIO_ONLY,
        VIDEO
    }
    
    public static class DownloadResult {
        private final boolean success;
        private final File file;
        private final String errorMessage;
        private final String title;
        private final String contentType;
        
        public DownloadResult(boolean success, File file, String title, String contentType, String errorMessage) {
            this.success = success;
            this.file = file;
            this.title = title;
            this.contentType = contentType;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public File getFile() { return file; }
        public String getErrorMessage() { return errorMessage; }
        public String getTitle() { return title; }
        public String getContentType() { return contentType; }
    }
    
    /**
     * Download media from a URL using yt-dlp
     * @param url The URL to download from
     * @param downloadType Whether to download audio only or video+audio
     * @param outputDir The directory to save the downloaded file
     * @return DownloadResult containing success status, file, and any error messages
     */
    public DownloadResult downloadFromUrl(String url, DownloadType downloadType, String outputDir) {
        if (url == null || url.trim().isEmpty()) {
            return new DownloadResult(false, null, null, null, "URL cannot be empty");
        }
        
        // Validate URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return new DownloadResult(false, null, null, null, "Invalid URL format. Must start with http:// or https://");
        }
        
        // Check if yt-dlp is installed
        if (!isYtDlpInstalled()) {
            return new DownloadResult(false, null, null, null, "yt-dlp is not installed on this system");
        }
        
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        // Generate unique filename base
        String filenameBase = "download_" + System.currentTimeMillis();
        String outputTemplate = new File(outputDir, filenameBase + ".%(ext)s").getAbsolutePath();
        
        List<String> command = new ArrayList<>();
        // Use the latest yt-dlp from /usr/local/bin (updated version)
        command.add("/usr/local/bin/yt-dlp");
        
        // Don't use cookies - they cause issues from different networks
        // Instead, use extractor args for better YouTube compatibility
        command.add("--extractor-args");
        command.add("youtube:player_client=android");
        
        // Skip unavailable fragments (helps with live streams and problematic videos)
        command.add("--skip-unavailable-fragments");
        
        // Add user agent to help bypass restrictions
        command.add("--user-agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        
        // Add referer header for better compatibility
        command.add("--add-header");
        command.add("Referer:https://www.youtube.com/");
        
        // Add network retry options for remote connections
        command.add("--retries");
        command.add("10");
        command.add("--fragment-retries");
        command.add("10");
        command.add("--retry-sleep");
        command.add("3");
        
        // Ignore errors for unavailable formats and try alternatives
        command.add("--ignore-errors");
        
        // Set format based on download type with more robust format selection
        if (downloadType == DownloadType.AUDIO_ONLY) {
            // Extract audio - let yt-dlp choose the best available format automatically
            command.add("-x"); // Extract audio
            command.add("--audio-format");
            command.add("mp3");
            command.add("--audio-quality");
            command.add("0"); // Best quality
        } else {
            // For video, just use 'best' - yt-dlp will figure out the best available format
            command.add("-f");
            command.add("best");
        }
        
        // Additional options for better compatibility
        command.add("--no-playlist"); // Only download single video, not playlist
        command.add("--no-check-certificates"); // Skip certificate validation (helps with some sites)
        
        command.add("-o");
        command.add(outputTemplate);
        command.add("--print");
        command.add("after_move:filepath"); // Print final file path
        command.add(url);
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("yt-dlp: " + line);
                }
            }
            
            // Wait for process to complete (with timeout)
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroy();
                return new DownloadResult(false, null, null, null, "Download timed out after 5 minutes");
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode != 0) {
                String errorMsg = parseErrorMessage(output.toString());
                return new DownloadResult(false, null, null, null, "Download failed: " + errorMsg);
            }
            
            // Find the downloaded file
            File downloadedFile = findDownloadedFile(outputDirectory, filenameBase);
            
            if (downloadedFile == null || !downloadedFile.exists()) {
                return new DownloadResult(false, null, null, null, "Download completed but file not found");
            }
            
            // Get title from filename or use default
            String title = extractTitle(downloadedFile.getName(), filenameBase);
            
            // Determine content type
            String contentType = determineContentType(downloadedFile, downloadType);
            
            return new DownloadResult(true, downloadedFile, title, contentType, null);
            
        } catch (IOException e) {
            return new DownloadResult(false, null, null, null, "IO Error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DownloadResult(false, null, null, null, "Download interrupted");
        } catch (Exception e) {
            return new DownloadResult(false, null, null, null, "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Check if yt-dlp is installed and accessible
     */
    private boolean isYtDlpInstalled() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version").start();
            process.waitFor(5, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Find the downloaded file in the output directory
     */
    private File findDownloadedFile(File directory, String filenameBase) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith(filenameBase));
        if (files != null && files.length > 0) {
            // Return the most recently modified file
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            return files[0];
        }
        return null;
    }
    
    /**
     * Extract a clean title from the filename
     */
    private String extractTitle(String filename, String filenameBase) {
        // Remove the base filename pattern and extension
        String title = filename.replace(filenameBase + ".", "");
        int lastDot = title.lastIndexOf('.');
        if (lastDot > 0) {
            title = title.substring(0, lastDot);
        }
        return title.isEmpty() ? "Downloaded Media" : title;
    }
    
    /**
     * Determine content type based on file extension and download type
     */
    private String determineContentType(File file, DownloadType downloadType) {
        String filename = file.getName().toLowerCase();
        
        if (downloadType == DownloadType.AUDIO_ONLY) {
            if (filename.endsWith(".mp3")) return "audio/mpeg";
            if (filename.endsWith(".m4a")) return "audio/mp4";
            if (filename.endsWith(".opus")) return "audio/opus";
            if (filename.endsWith(".ogg")) return "audio/ogg";
            return "audio/mpeg"; // Default for audio
        } else {
            if (filename.endsWith(".mp4")) return "video/mp4";
            if (filename.endsWith(".mkv")) return "video/x-matroska";
            if (filename.endsWith(".webm")) return "video/webm";
            if (filename.endsWith(".avi")) return "video/x-msvideo";
            return "video/mp4"; // Default for video
        }
    }
    
    /**
     * Parse error message from yt-dlp output
     */
    private String parseErrorMessage(String output) {
        // Look for common error patterns
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("ERROR:")) {
                return line.substring(line.indexOf("ERROR:") + 6).trim();
            }
        }
        return "Unknown error occurred";
    }
}
