package lexicon.api;

import lexicon.object.TorrentDownload;
import lexicon.service.QBittorrentClient;
import lexicon.service.TorrentDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Torrent downloads into Lexicon.
 *
 * Thin by design: validation, filtering, qBittorrent and the import all live in
 * {@link TorrentDownloadService}. Authentication comes from the security config's catch-all
 * ({@code /api/**} requires ROLE_USER), and {@code IdentityGuard} pins the {@code userId}
 * parameter to the signed-in session, so one user cannot queue or cancel as another.
 */
@RestController
@RequestMapping("/api/torrents")
@CrossOrigin(origins = "*")
public class TorrentController {

    private static final Logger log = LoggerFactory.getLogger(TorrentController.class);

    @Autowired
    private TorrentDownloadService torrentService;

    /**
     * Whether a torrent can be queued right now, so the clients can explain themselves
     * before the user picks a file rather than after.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        QBittorrentClient.Health health = torrentService.health();
        Map<String, Object> response = new HashMap<>();
        response.put("ready", health.usable());
        response.put("qbittorrentUp", health.up());
        response.put("boundToTunnel", health.boundToTunnel());
        response.put("message", health.message());
        return ResponseEntity.ok(response);
    }

    /**
     * Queue a torrent: either a .torrent upload or a magnet / .torrent link.
     * Only .mp3 and .mp4 files inside it are ever downloaded.
     */
    @PostMapping(consumes = {"multipart/form-data", "application/x-www-form-urlencoded"})
    public ResponseEntity<Map<String, Object>> submit(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam("userId") int userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic) {

        TorrentDownloadService.TorrentRequest request = new TorrentDownloadService.TorrentRequest();
        request.userId = userId;
        request.title = title;
        request.description = description;
        request.isPublic = isPublic;
        request.urlOrMagnet = url;
        try {
            if (file != null && !file.isEmpty()) {
                request.torrentFile = file.getBytes();
                request.filename = file.getOriginalFilename();
            }
        } catch (IOException e) {
            return error(HttpStatus.BAD_REQUEST, "Could not read the uploaded .torrent file.");
        }

        try {
            TorrentDownload download = torrentService.submit(request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("infoHash", download.getInfoHash());
            response.put("name", download.getDisplayName());
            response.put("status", download.getStatus().name());
            return ResponseEntity.ok(response);

        } catch (TorrentDownloadService.TorrentRejected e) {
            // Everything the user can act on: no VPN, no space, wrong file, no mp3/mp4.
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Torrent submit failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Could not queue that torrent.");
        }
    }

    /** This user's torrents, newest first, with live progress. */
    @GetMapping("/active/{userId}")
    public ResponseEntity<Map<String, Object>> active(
            @PathVariable int userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<Map<String, Object>> torrents = torrentService.listForUser(userId, Math.min(Math.max(limit, 1), 100));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("torrents", torrents);
        return ResponseEntity.ok(response);
    }

    /** Cancel a download, or remove a finished torrent. Its data in qBittorrent goes too. */
    @DeleteMapping("/{infoHash}")
    public ResponseEntity<Map<String, Object>> remove(
            @PathVariable String infoHash,
            @RequestParam("userId") int userId) {
        boolean removed = torrentService.remove(infoHash, userId);
        if (!removed) {
            return error(HttpStatus.FORBIDDEN, "That torrent is not yours, or it is already gone.");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}
