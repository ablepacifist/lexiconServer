package lexicon.logic;

import lexicon.data.IPushSubscriptionDatabase;
import lexicon.object.PushSubscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Web Push notification service implementing RFC 8291 (message encryption)
 * and RFC 8292 (VAPID authentication) using BouncyCastle.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    @Autowired
    private IPushSubscriptionDatabase pushDb;

    @Value("${vapid.public.key:}")
    private String vapidPublicKey;

    @Value("${vapid.private.key:}")
    private String vapidPrivateKey;

    @Value("${vapid.subject:mailto:admin@alex-dyakin.com}")
    private String vapidSubject;

    private ECPublicKey serverPublicKey;
    private ECPrivateKey serverPrivateKey;
    private byte[] serverPublicKeyBytes;
    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (vapidPublicKey.isEmpty() || vapidPrivateKey.isEmpty()) {
            log.warn("VAPID keys not configured — push notifications disabled. Set vapid.public.key and vapid.private.key.");
            return;
        }

        try {
            byte[] pubBytes = base64UrlDecode(vapidPublicKey);
            byte[] privBytes = base64UrlDecode(vapidPrivateKey);

            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC", "BC");
            params.init(new ECGenParameterSpec("prime256v1"));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

            // Public key: uncompressed 65-byte point
            ECPoint point = decodeUncompressedPoint(pubBytes, ecSpec.getCurve());
            serverPublicKey = (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(point, ecSpec));
            serverPublicKeyBytes = pubBytes;

            // Private key: 32-byte scalar
            serverPrivateKey = (ECPrivateKey) kf.generatePrivate(
                    new ECPrivateKeySpec(new java.math.BigInteger(1, privBytes), ecSpec));

            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            log.info("Push notification service initialized with VAPID keys");
        } catch (Exception e) {
            log.error("Failed to initialize push service: {}", e.getMessage(), e);
        }
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public boolean isEnabled() {
        return serverPublicKey != null && serverPrivateKey != null;
    }

    public void subscribe(PushSubscription subscription) {
        pushDb.saveSubscription(subscription);
        log.info("Push subscription saved for userId={}", subscription.getUserId());
    }

    public void unsubscribe(String endpoint) {
        pushDb.deleteByEndpoint(endpoint);
        log.info("Push subscription removed for endpoint={}", endpoint);
    }

    public void unsubscribeAll(int userId) {
        pushDb.deleteByUserId(userId);
        log.info("All push subscriptions removed for userId={}", userId);
    }

    /**
     * Send a push notification to all of a user's subscribed devices.
     */
    public int sendToUser(int userId, String payload) {
        if (!isEnabled()) {
            log.warn("Push service not enabled — cannot send to userId={}", userId);
            return 0;
        }

        List<PushSubscription> subs = pushDb.getByUserId(userId);
        if (subs.isEmpty()) {
            log.debug("No push subscriptions for userId={}", userId);
            return 0;
        }

        int sent = 0;
        for (PushSubscription sub : subs) {
            try {
                int status = sendPush(sub, payload);
                if (status >= 200 && status < 300) {
                    sent++;
                } else if (status == 404 || status == 410) {
                    log.info("Removing stale push subscription (HTTP {}): {}", status, sub.getEndpoint());
                    pushDb.deleteByEndpoint(sub.getEndpoint());
                } else {
                    log.warn("Push send got HTTP {} for userId={}", status, userId);
                }
            } catch (Exception e) {
                log.error("Failed to send push to userId={}: {}", userId, e.getMessage());
            }
        }

        log.info("Sent push to {}/{} subscriptions for userId={}", sent, subs.size(), userId);
        return sent;
    }

    public int sendToUsers(List<Integer> userIds, String payload) {
        int total = 0;
        for (int userId : userIds) {
            total += sendToUser(userId, payload);
        }
        return total;
    }

    /**
     * Build a JSON payload for the push notification service worker.
     */
    public String buildPayload(String title, String body, String url, Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"title\":").append(jsonEscape(title));
        if (body != null) sb.append(",\"body\":").append(jsonEscape(body));
        if (url != null) sb.append(",\"url\":").append(jsonEscape(url));
        if (data != null) sb.append(",\"data\":").append(data.toString());
        sb.append("}");
        return sb.toString();
    }

    private String jsonEscape(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---- RFC 8291 Web Push Encryption + RFC 8292 VAPID ----

    private int sendPush(PushSubscription sub, String payload) throws Exception {
        byte[] userPublicKey = base64UrlDecode(sub.getP256dh());
        byte[] userAuth = base64UrlDecode(sub.getAuth());

        // Encrypt the payload per RFC 8291 (aes128gcm)
        byte[] encrypted = encrypt(payload.getBytes(StandardCharsets.UTF_8), userPublicKey, userAuth);

        // Create VAPID Authorization header
        URI endpoint = URI.create(sub.getEndpoint());
        String audience = endpoint.getScheme() + "://" + endpoint.getHost();
        String authorization = createVapidAuth(audience);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("TTL", "86400")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    /**
     * RFC 8291 aes128gcm content encryption.
     */
    private byte[] encrypt(byte[] payload, byte[] userPublicKeyBytes, byte[] userAuth) throws Exception {
        // Generate ephemeral ECDH key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair ephemeral = kpg.generateKeyPair();
        ECPublicKey ephPublic = (ECPublicKey) ephemeral.getPublic();
        byte[] ephPublicBytes = encodeUncompressedPoint(ephPublic);

        // Decode user's public key
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC", "BC");
        params.init(new ECGenParameterSpec("prime256v1"));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
        ECPoint userPoint = decodeUncompressedPoint(userPublicKeyBytes, ecSpec.getCurve());
        ECPublicKey userPubKey = (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(userPoint, ecSpec));

        // ECDH shared secret
        KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(userPubKey, true);
        byte[] sharedSecret = ka.generateSecret();

        // HKDF to derive IKM: extract with auth as salt, shared secret as ikm, info as context
        byte[] authInfo = buildInfo("WebPush: info\0", userPublicKeyBytes, ephPublicBytes);
        byte[] ikm = hkdf(userAuth, sharedSecret, authInfo, 32);

        // Generate random 16-byte salt
        byte[] salt = new byte[16];
        SecureRandom.getInstanceStrong().nextBytes(salt);

        // Derive content encryption key and nonce from IKM
        byte[] prk = hkdfExtract(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // Pad payload: content + delimiter byte 0x02
        byte[] padded = new byte[payload.length + 1];
        System.arraycopy(payload, 0, padded, 0, payload.length);
        padded[payload.length] = 0x02;

        // AES-128-GCM encryption
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        // Build aes128gcm record: salt(16) + rs(4) + idlen(1) + keyid(65) + ciphertext
        int rs = 4096;
        ByteBuffer result = ByteBuffer.allocate(16 + 4 + 1 + ephPublicBytes.length + ciphertext.length);
        result.put(salt);
        result.putInt(rs);
        result.put((byte) ephPublicBytes.length);
        result.put(ephPublicBytes);
        result.put(ciphertext);

        return result.array();
    }

    /**
     * Create VAPID Authorization header (RFC 8292).
     */
    private String createVapidAuth(String audience) throws Exception {
        long exp = Instant.now().getEpochSecond() + 43200; // 12 hours

        String header = base64UrlEncode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.US_ASCII));
        String payloadJson = String.format("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}", audience, exp, vapidSubject);
        String jwtPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.US_ASCII));

        String unsigned = header + "." + jwtPayload;

        // Sign with ECDSA
        Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
        sig.initSign(serverPrivateKey);
        sig.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        byte[] derSig = sig.sign();
        byte[] rawSig = derToRaw(derSig);

        String jwt = unsigned + "." + base64UrlEncode(rawSig);
        return "vapid t=" + jwt + ", k=" + vapidPublicKey;
    }

    // ---- Utility Methods ----

    private byte[] buildInfo(String type, byte[] clientPub, byte[] serverPub) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buf = ByteBuffer.allocate(typeBytes.length + clientPub.length + serverPub.length);
        buf.put(typeBytes);
        buf.put(clientPub);
        buf.put(serverPub);
        return buf.array();
    }

    private byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        byte[] prk = hkdfExtract(salt, ikm);
        return hkdfExpand(prk, info, length);
    }

    private byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length > 0 ? salt : new byte[32], "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] infoWithCounter = new byte[info.length + 1];
        System.arraycopy(info, 0, infoWithCounter, 0, info.length);
        infoWithCounter[info.length] = 0x01;
        byte[] result = mac.doFinal(infoWithCounter);
        if (result.length >= length) {
            byte[] out = new byte[length];
            System.arraycopy(result, 0, out, 0, length);
            return out;
        }
        return result;
    }

    private ECPoint decodeUncompressedPoint(byte[] data, EllipticCurve curve) {
        int fieldSize = (curve.getField().getFieldSize() + 7) / 8;
        byte[] x = new byte[fieldSize];
        byte[] y = new byte[fieldSize];
        System.arraycopy(data, 1, x, 0, fieldSize);
        System.arraycopy(data, 1 + fieldSize, y, 0, fieldSize);
        return new ECPoint(new java.math.BigInteger(1, x), new java.math.BigInteger(1, y));
    }

    private byte[] encodeUncompressedPoint(ECPublicKey key) {
        byte[] x = key.getW().getAffineX().toByteArray();
        byte[] y = key.getW().getAffineY().toByteArray();
        byte[] result = new byte[65];
        result[0] = 0x04;
        copyPadded(x, result, 1, 32);
        copyPadded(y, result, 33, 32);
        return result;
    }

    private void copyPadded(byte[] src, byte[] dest, int destOffset, int length) {
        if (src.length == length) {
            System.arraycopy(src, 0, dest, destOffset, length);
        } else if (src.length > length) {
            System.arraycopy(src, src.length - length, dest, destOffset, length);
        } else {
            System.arraycopy(src, 0, dest, destOffset + (length - src.length), src.length);
        }
    }

    private byte[] derToRaw(byte[] der) {
        int offset = 2;
        int rLen = der[offset + 1] & 0xFF;
        byte[] r = new byte[rLen];
        System.arraycopy(der, offset + 2, r, 0, rLen);
        offset += 2 + rLen;
        int sLen = der[offset + 1] & 0xFF;
        byte[] s = new byte[sLen];
        System.arraycopy(der, offset + 2, s, 0, sLen);

        byte[] raw = new byte[64];
        copyPadded(r, raw, 0, 32);
        copyPadded(s, raw, 32, 32);
        return raw;
    }

    private byte[] base64UrlDecode(String input) {
        return Base64.getUrlDecoder().decode(input);
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
