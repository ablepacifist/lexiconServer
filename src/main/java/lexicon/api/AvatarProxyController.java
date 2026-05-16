package lexicon.api;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy controller that forwards avatar API requests to the Mumble Bridge
 * at https://voice.alex-dyakin.com
 *
 * Endpoints:
 *   GET  /api/avatar/{username}  → bridge GET  /api/avatar/{username}
 *   POST /api/avatar/upload      → bridge POST /api/avatar/upload (multipart)
 *   POST /api/avatar/remove      → bridge POST /api/avatar/remove (JSON)
 */
@RestController
@RequestMapping("/api/avatar")
@CrossOrigin(origins = "*")
public class AvatarProxyController {

    private static final String BRIDGE_BASE_URL = "https://voice.alex-dyakin.com";

    private final RestTemplate restTemplate;

    public AvatarProxyController() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get avatar URL for a user
     * GET /api/avatar/{username} → proxy to bridge GET /api/avatar/{username}
     */
    @GetMapping("/{username}")
    public ResponseEntity<Map<String, Object>> getAvatar(@PathVariable String username) {
        try {
            String url = BRIDGE_BASE_URL + "/api/avatar/" + username;
            ResponseEntity<Map> bridgeResponse = restTemplate.getForEntity(url, Map.class);

            Map<String, Object> response = new HashMap<>();
            if (bridgeResponse.getBody() != null) {
                response.putAll(bridgeResponse.getBody());
            }
            return ResponseEntity.status(bridgeResponse.getStatusCode()).body(response);

        } catch (HttpClientErrorException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Bridge returned error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (Exception e) {
            System.err.println("Avatar proxy GET error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reach avatar service: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }

    /**
     * Upload avatar (multipart proxy)
     * POST /api/avatar/upload → proxy to bridge POST /api/avatar/upload
     *
     * Expected multipart fields: username (string), userId (int, optional), avatar (file)
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam("avatar") MultipartFile avatarFile) {
        try {
            String url = BRIDGE_BASE_URL + "/api/avatar/upload";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("username", username);
            if (userId != null) {
                body.add("userId", userId.toString());
            }

            // Wrap the file bytes in a ByteArrayResource that reports the original filename
            ByteArrayResource fileResource = new ByteArrayResource(avatarFile.getBytes()) {
                @Override
                public String getFilename() {
                    return avatarFile.getOriginalFilename();
                }
            };
            body.add("avatar", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> bridgeResponse = restTemplate.postForEntity(url, requestEntity, Map.class);

            Map<String, Object> response = new HashMap<>();
            if (bridgeResponse.getBody() != null) {
                response.putAll(bridgeResponse.getBody());
            }
            return ResponseEntity.status(bridgeResponse.getStatusCode()).body(response);

        } catch (HttpClientErrorException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Bridge returned error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (Exception e) {
            System.err.println("Avatar proxy upload error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to upload avatar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }

    /**
     * Remove avatar (JSON proxy)
     * POST /api/avatar/remove → proxy to bridge POST /api/avatar/remove
     *
     * Expected JSON body: { "username": "...", "userId": 123 }
     */
    @PostMapping("/remove")
    public ResponseEntity<Map<String, Object>> removeAvatar(@RequestBody Map<String, Object> payload) {
        try {
            String url = BRIDGE_BASE_URL + "/api/avatar/remove";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> bridgeResponse = restTemplate.postForEntity(url, requestEntity, Map.class);

            Map<String, Object> response = new HashMap<>();
            if (bridgeResponse.getBody() != null) {
                response.putAll(bridgeResponse.getBody());
            }
            return ResponseEntity.status(bridgeResponse.getStatusCode()).body(response);

        } catch (HttpClientErrorException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Bridge returned error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (Exception e) {
            System.err.println("Avatar proxy remove error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to remove avatar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }

    /**
     * Proxy the actual avatar image file through the Lexicon backend.
     * This avoids cross-origin blocks in browsers like Brave that have aggressive shields.
     * GET /api/avatar/image/{path} → bridge GET /uploads/avatars/{path}
     */
    @GetMapping("/image/{*path}")
    public ResponseEntity<byte[]> proxyAvatarImage(@PathVariable String path) {
        try {
            String url = BRIDGE_BASE_URL + "/uploads/avatars/" + path;
            ResponseEntity<byte[]> bridgeResponse = restTemplate.getForEntity(url, byte[].class);

            if (bridgeResponse.getStatusCode().is2xxSuccessful() && bridgeResponse.getBody() != null) {
                // Determine content type from the bridge response, fallback to jpeg
                MediaType contentType = bridgeResponse.getHeaders().getContentType();
                if (contentType == null) contentType = MediaType.IMAGE_JPEG;

                return ResponseEntity.ok()
                    .contentType(contentType)
                    .header("Cache-Control", "no-cache, must-revalidate")
                    .body(bridgeResponse.getBody());
            }
            return ResponseEntity.notFound().build();

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            System.err.println("Avatar image proxy error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
