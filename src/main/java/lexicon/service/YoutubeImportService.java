package lexicon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Service for importing playlists from YouTube using yt-dlp
 */
@Service
public class YoutubeImportService {
    
    private static final String COOKIES_FILE = "/home/alex/Documents/lexicon/Lexicon/full-back-end-server/lexiconServer/cookies.txt";
    private static final String MEDIA_UPLOAD_URL = "http://localhost:36568/api/media/upload-from-url";
    
    /**
     * Fetch playlist metadata from YouTube
     * @return JsonNode containing playlist entries
     */
    public PlaylistMetadata fetchPlaylistMetadata(String playlistUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "--cookies", COOKIES_FILE,
            "--dump-json",
            "--flat-playlist",
            playlistUrl
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        
        String firstLine = reader.readLine();
        if (firstLine == null) {
            throw new Exception("Failed to fetch playlist information");
        }
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode firstEntry = mapper.readTree(firstLine);
        
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
                                       int userId, boolean isPublic) throws Exception {
        String videoUrl = "https://music.youtube.com/watch?v=" + videoId;
        
        ProcessBuilder pb = new ProcessBuilder(
            "curl", "-s", "-X", "POST",
            MEDIA_UPLOAD_URL,
            "-F", "url=" + videoUrl,
            "-F", "userId=" + userId,
            "-F", "title=" + title,
            "-F", "description=From playlist: " + playlistName,
            "-F", "isPublic=" + isPublic,
            "-F", "mediaType=MUSIC",
            "-F", "downloadType=AUDIO_ONLY"
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        
        process.waitFor();
        reader.close();
        
        // Parse response to get media ID
        String responseStr = response.toString();
        if (responseStr.contains("\"id\"")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode mediaResponse = mapper.readTree(responseStr);
            return mediaResponse.get("id").asInt();
        }
        
        return -1;
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
