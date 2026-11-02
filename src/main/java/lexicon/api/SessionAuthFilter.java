package lexicon.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns the login Lexicon already tracks (the {@code userId} session attribute set by
 * {@link AuthController}, the remember-me restore and {@link MobileTokenAuthFilter}) into a
 * Spring Security authentication, so route rules can say "must be signed in".
 *
 * Read-only: it never creates a session, never rotates a token and never touches cookies,
 * so it cannot log anyone out or race with the remember-me rotation.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Spring has already put an anonymous token in place by now; only a service token should win over a session
        if (LexiconPrincipal.current() == null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") instanceof Integer userId) {
                Object name = session.getAttribute("username");
                SecurityContextHolder.getContext().setAuthentication(
                        LexiconPrincipal.user(userId, name == null ? "" : name.toString()));
            }
        }
        chain.doFilter(request, response);
    }
}
