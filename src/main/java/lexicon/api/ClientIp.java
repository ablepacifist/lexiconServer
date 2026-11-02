package lexicon.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address of the real client behind a request.
 *
 * Public traffic reaches this server through Cloudflare's tunnel, which connects from the same machine
 * (loopback) and passes the visitor's address in {@code CF-Connecting-IP}. Anyone else can send those
 * headers too, so they are believed only when the connection itself comes from loopback; a direct
 * (LAN) connection is identified by its own address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (isLoopback(peer)) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return peer;
    }

    /**
     * True when the visitor reached us over HTTPS: either the connection itself is TLS, or the local Cloudflare
     * tunnel (which terminates TLS) says so. A direct LAN connection over plain HTTP is not secure, and its
     * claims about being HTTPS are ignored.
     */
    public static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        return isLoopback(request.getRemoteAddr()) && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private static boolean isLoopback(String addr) {
        return addr != null && (addr.equals("127.0.0.1") || addr.equals("::1") || addr.equals("0:0:0:0:0:0:0:1")
                || addr.startsWith("127."));
    }
}
