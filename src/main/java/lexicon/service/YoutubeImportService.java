package lexicon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lexicon.logic.MediaManagerService;
import lexicon.utils.UrlSafety;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing playlists from YouTube using yt-dlp
 */
@Service
public class YoutubeImportService {

    @Value("${ytdlp.cookies.path:cookies.txt}")
    private String cookiesPath;

    // Lazy: the playlist code that uses this importer sits on top of the media manager
    @Autowired
    @Lazy
    private MediaManagerService mediaManager;

    /**
     * Fetch playlist metadata from YouTube
     * @return JsonNode containing playlist entries
     */
    public PlaylistMetadata fetchPlaylistMetadata(String playlistUrl) throws Exception {
        UrlSafety.requirePublicHttpUrl(playlistUrl);
        List<String> cmd = new ArrayList<>();
        cmd.add("yt-dlp");
        java.io.File cookiesFile = new java.io.File(cookiesPath);
        if (cookiesFile.exists()) {
            cmd.add("--cookies");
            cmd.add(cookiesFile.getAbsolutePath());
        } else {
            cmd.add("--cookies-from-browser");
            cmd.add("firefox");
        }
        cmd.add("--extractor-args");
        cmd.add("youtube:player_client=android,web");
        cmd.add("--dump-json");
        cmd.add("--flat-playlist");
        cmd.add("--"); // everything after this is a URL, never an option
        cmd.add(playlistUrl);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        
        String firstJsonLine = null;
        String line;
        
        // Read lines until we find the first JSON line (skip WARNING messages)
        while ((line = reader.readLine()) != null) {
            // Skip lines that start with WARNING or other non-JSON content
            if (line.startsWith("{") && line.contains("\"")) {
                firstJsonLine = line;
                break;
            }
            // Also skip lines that are clearly warnings or status messages
            if (line.startsWith("WARNING:") || line.startsWith("ERROR:") || 
                line.startsWith("[") || line.isEmpty()) {
                continue;
            }
        }
        
        if (firstJsonLine == null) {
            throw new Exception("Failed to fetch playlist information - no JSON data found");
        }
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode firstEntry = mapper.readTree(firstJsonLine);
        
        String playlistName = firstEntry.has("playlist_title") 
            ? firstEntry.get("playlist_title").asText() 
            : (firstEntry.has("playlist") ? firstEntry.get("playlist").asText() : "Imported Playlist");
        
        PlaylistMetadata metadata = new PlaylistMetadata();
        metadata.name = playlistName;
        metadata.firstEntry = firstEntry;
        metadata.reader = reader;
        metadata.process = process;
        metadata.mapper = mapper;
        
        return metadata;
    }
    
    /**
     * Download and upload a single video from the playlist
     * @return media ID if successful, -1 if failed
     */
    public int downloadAndUploadMedia(String videoId, String title, String playlistName, 
                                       int userId, boolean isPublic, String mediaType, String downloadType) throws Exception {
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        
        if (mediaManager == null) {
            System.err.println("Playlist import: media manager is not available");
            return -1;
        }
        try {
            // Same code path as the upload-from-url endpoint, called directly: no HTTP round trip to ourselves,
            // so no credentials are needed from inside the server.
            lexicon.object.MediaFile mediaFile = mediaManager.uploadMediaFromUrl(
                videoUrl, userId, title, "From playlist: " + playlistName, isPublic, mediaType, downloadType);
            return mediaFile != null ? mediaFile.getId() : -1;
        } catch (Exception e) {
            System.err.println("Playlist import: could not add " + videoUrl + ": " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Holder class for playlist metadata and stream
     */
    public static class PlaylistMetadata {
        public String name;
        public JsonNode firstEntry;
        public BufferedReader reader;
        public Process process;
        public ObjectMapper mapper;
    }
}
