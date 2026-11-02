package lexicon.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lets a trusted backend (the GTW Mumble bridge) call Lexicon without a player login by
 * sending {@code X-Service-Token: <secret>}. The secret comes from LEXICON_SERVICE_TOKEN;
 * when that is empty the filter is inert, so nothing changes until it is configured.
 *
 * A wrong token is simply ignored (the request stays anonymous) rather than rejected, so a
 * stale header can never lock out a request that is also carrying a valid player session.
 */
public class ServiceTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final byte[] expectedDigest;

    public ServiceTokenAuthFilter(String configuredToken) {
        this.expectedDigest = (configuredToken == null || configuredToken.isBlank())
                ? null
                : sha256(configuredToken.trim());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (expectedDigest != null) {
            String presented = request.getHeader(HEADER);
            // Compare fixed-length digests in constant time so the secret's length and content do not leak
            if (presented != null && MessageDigest.isEqual(expectedDigest, sha256(presented.trim()))) {
                SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.service());
            }
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
