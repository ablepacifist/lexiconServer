package lexicon.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import lexicon.object.Player;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brings back a signed-in session from the "keep me logged in" cookie on any API request, not only on
 * {@code /api/auth/me}.
 *
 * Why: sessions live in memory, so a server restart drops them all. Before the API required a login that
 * did not matter (an already-open page kept working); now a returning player whose page is still open
 * would get 401s until something happened to call {@code /api/auth/me}. This restores the session
 * transparently, exactly the way {@code /me} does.
 *
 * The remember-me token is single-use and rotates, and a page fires several requests at once with the
 * same cookie. So a successful restore is remembered for a few seconds and re-used by the parallel
 * requests; otherwise the losers of the race would look logged out.
 *
 * It never clears cookies and never fails a request: an unknown or expired token just leaves the request
 * anonymous (and {@code /me} keeps its own job of telling the browser to drop a dead cookie).
 */
public class RememberMeSessionFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "remember-me";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60;
    private static final long REUSE_WINDOW_MS = 20_000;

    private record Restored(int userId, String newRawToken, long at) {
    }

    private final RememberMeManagerService rememberMe;
    private final PlayerManagerService players;
    private final ConcurrentHashMap<String, Restored> recent = new ConcurrentHashMap<>();

    public RememberMeSessionFilter(RememberMeManagerService rememberMe, PlayerManagerService players) {
        this.rememberMe = rememberMe;
        this.players = players;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (LexiconPrincipal.current() == null && !hasSignedInSession(request)) {
            String rawToken = cookieValue(request);
            if (rawToken != null && !rawToken.isEmpty()) {
                try {
                    restore(rawToken, request, response);
                } catch (Exception e) {
                    System.err.println("Remember-me restore error: " + e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void restore(String rawToken, HttpServletRequest request, HttpServletResponse response) {
        Restored restored;
        synchronized (this) {
            long now = System.currentTimeMillis();
            recent.values().removeIf(r -> now - r.at() > REUSE_WINDOW_MS);
            restored = recent.get(rawToken);
            if (restored == null) {
                RememberMeManagerService.RememberMeResult result = rememberMe.validateAndRotate(rawToken);
                if (result == null) {
                    return;
                }
                restored = new Restored(result.userId(), result.newRawToken(), now);
                recent.put(rawToken, restored);
            }
        }

        Player player = players.getPlayerById(restored.userId());
        if (player == null) {
            rememberMe.clearTokens(restored.userId()); // the account was deleted
            return;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", player.getId());
        session.setAttribute("username", player.getUsername());
        session.setMaxInactiveInterval(30 * 24 * 60 * 60);

        Cookie cookie = new Cookie(COOKIE_NAME, restored.newRawToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // same as AuthController; made conditional on HTTPS later in this phase
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    private static boolean hasSignedInSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") != null;
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
