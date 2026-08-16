package lexicon.api;

import lexicon.logic.MobileTokenService;
import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <token>} from the
 * Android (Capacitor) app, which cannot use the session cookie from its local
 * WebView origin.
 *
 * A valid token populates the request's session exactly the way the remember-me
 * cookie fallback in {@link AuthController#getCurrentUser} does, so every other
 * controller keeps reading auth purely via {@code session.getAttribute("userId")}
 * and needs no change.
 *
 * When the token is rotated, the replacement is returned in the
 * {@code X-Mobile-Token} response header for the client to persist.
 */
@Component
@Order(MobileTokenAuthFilter.ORDER)
public class MobileTokenAuthFilter implements Filter {

    /**
     * Ahead of Spring Security's chain, which Boot registers at -100
     * (SecurityProperties.DEFAULT_FILTER_ORDER).
     */
    static final int ORDER = -101;

    public static final String REFRESHED_TOKEN_HEADER = "X-Mobile-Token";

    private static final String BEARER_PREFIX = "Bearer ";

    private final MobileTokenService mobileTokenService;
    private final PlayerManagerService playerManagerService;

    public MobileTokenAuthFilter(MobileTokenService mobileTokenService,
                                 PlayerManagerService playerManagerService) {
        this.mobileTokenService = mobileTokenService;
        this.playerManagerService = playerManagerService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String rawToken = extractBearerToken(httpReq);
        if (rawToken != null && !alreadyAuthenticated(httpReq)) {
            try {
                MobileTokenService.MobileTokenResult result =
                        mobileTokenService.validateAndRotate(rawToken);

                if (result != null) {
                    Player player = playerManagerService.getPlayerById(result.userId());
                    if (player != null) {
                        HttpSession session = httpReq.getSession(true);
                        session.setAttribute("userId", player.getId());
                        session.setAttribute("username", player.getUsername());
                        session.setMaxInactiveInterval(30 * 24 * 60 * 60);

                        if (result.newRawToken() != null) {
                            httpResp.setHeader(REFRESHED_TOKEN_HEADER, result.newRawToken());
                        }
                    } else {
                        // User was deleted — retire their tokens
                        mobileTokenService.clearTokens(result.userId());
                    }
                }
            } catch (Exception e) {
                // Never fail the request on token trouble — fall through unauthenticated
                System.err.println("Mobile token authentication error: " + e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private boolean alreadyAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") != null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
