package lexicon.service;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for transcoding video files using FFmpeg
 * Converts uploaded videos to web-optimized formats (H.264/AAC in MP4)
 */
@Service
public class VideoTranscodingService {

    private static final String FFMPEG_COMMAND = "ffmpeg";
    
    /**
     * Check if FFmpeg is available on the system
     */
    public boolean isFFmpegAvailable() {
        try {
            Process process = new ProcessBuilder(FFMPEG_COMMAND, "-version")
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("FFmpeg not found: " + e.getMessage());
            return false;
        }
    }

    /**
     * Transcode video to web-optimized H.264/AAC MP4
     * 
     * @param inputPath Path to the input video file
     * @param outputPath Path where transcoded video will be saved
     * @param maxWidth Maximum width (maintains aspect ratio), default 1920
     * @return true if transcoding succeeded
     */
    public boolean transcodeVideo(String inputPath, String outputPath, int maxWidth) {
        if (!isFFmpegAvailable()) {
            System.err.println("FFmpeg is not available, skipping transcoding");
            return false;
        }

        try {
            List<String> command = buildFFmpegCommand(inputPath, outputPath, maxWidth);
            
            System.out.println("Starting transcoding: " + inputPath);
            System.out.println("Command: " + String.join(" ", command));
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output in a separate thread to avoid blocking
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("FFmpeg: " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            outputThread.start();
            
            int exitCode = process.waitFor();
            outputThread.join(5000); // Wait up to 5 seconds for output thread
            
            if (exitCode == 0) {
                System.out.println("Transcoding completed successfully: " + outputPath);
                return true;
            } else {
                System.err.println("Transcoding failed with exit code: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error during transcoding: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Transcode video with default max width of 1920px
     */
    public boolean transcodeVideo(String inputPath, String outputPath) {
        return transcodeVideo(inputPath, outputPath, 1920);
    }

    /**
     * Build FFmpeg command for web-optimized transcoding
     */
    private List<String> buildFFmpegCommand(String inputPath, String outputPath, int maxWidth) {
        List<String> command = new ArrayList<>();
        
        command.add(FFMPEG_COMMAND);
        command.add("-i");
        command.add(inputPath);
        
        // Video codec: H.264 with medium preset for balance of speed/quality
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("medium");
        command.add("-crf");
        command.add("23"); // Constant Rate Factor (18-28, lower = better quality)
        
        // Scale video to max width while maintaining aspect ratio
        command.add("-vf");
        command.add("scale='min(" + maxWidth + ",iw):-2'"); // -2 ensures even dimensions
        
        // Audio codec: AAC
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        
        // MP4 optimization for web streaming
        command.add("-movflags");
        command.add("+faststart"); // Move metadata to beginning for faster start
        
        // Overwrite output file if exists
        command.add("-y");
        
        command.add(outputPath);
        
        return command;
    }

    /**
     * Generate output filename for transcoded video
     * Adds "_transcoded" suffix before extension
     */
    public String generateTranscodedFilename(String originalPath) {
        Path path = Paths.get(originalPath);
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        
        if (lastDot > 0) {
            String nameWithoutExt = filename.substring(0, lastDot);
            return nameWithoutExt + "_transcoded.mp4";
        } else {
            return filename + "_transcoded.mp4";
        }
    }

    /**
     * Get file extension from path
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Check if file is a video based on extension
     */
    public boolean isVideoFile(String filename) {
        String ext = getFileExtension(filename);
        return ext.matches("mp4|avi|mov|mkv|flv|wmv|webm|m4v|mpg|mpeg");
    }

    /**
     * Check if video needs transcoding (not already H.264 MP4)
     */
    public boolean needsTranscoding(String filename) {
        String ext = getFileExtension(filename);
        // Even MP4 might not be H.264, but we'll assume most modern MP4s are good
        // For production, you'd want to probe the file with ffprobe
        return !ext.equals("mp4");
    }
}
