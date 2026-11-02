package lexicon.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Guards the places where the server fetches a URL on a user's behalf (yt-dlp downloads and imports),
 * so a signed-in player cannot use the server to reach services on aragorn or the home network.
 *
 * Accepts only http(s) URLs without embedded credentials whose host resolves exclusively to public
 * addresses. This is a best-effort guard: it cannot stop a hostname from changing its DNS answer between
 * this check and the download, so it narrows the risk rather than closing it completely.
 */
public final class UrlSafety {

    private static final int MAX_URL_LENGTH = 2048;

    private UrlSafety() {
    }

    /**
     * @throws IllegalArgumentException (with a message safe to show the user) when the URL is not acceptable
     */
    public static void requirePublicHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL is too long");
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 0x7f) { // spaces, control characters, newlines
                throw new IllegalArgumentException("Invalid URL format");
            }
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Invalid URL format. Must start with http:// or https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("URLs with embedded credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve the host in that URL");
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new IllegalArgumentException("That URL points to a private or local address");
            }
        }
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            int b2 = b[2] & 0xff;
            return b0 == 0                                    // "this network"
                    || (b0 == 100 && b1 >= 64 && b1 <= 127)   // carrier-grade NAT (also used by some VPNs)
                    || (b0 == 192 && b1 == 0 && b2 == 0)      // IETF protocol assignments
                    || (b0 == 198 && (b1 == 18 || b1 == 19))  // benchmarking
                    || b0 >= 240;                             // reserved / broadcast
        }
        if (b.length == 16) {
            int b0 = b[0] & 0xff;
            if ((b0 & 0xfe) == 0xfc) {                        // fc00::/7 unique local
                return true;
            }
            boolean mapped = true;                            // ::ffff:a.b.c.d: judge the embedded IPv4 address
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            mapped &= (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
            if (mapped) {
                try {
                    return isInternal(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
