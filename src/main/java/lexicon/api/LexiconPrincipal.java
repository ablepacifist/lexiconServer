package lexicon.api;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Who a request is acting as, once {@link SessionAuthFilter} or
 * {@link ServiceTokenAuthFilter} has recognised it.
 *
 * A signed-in player is {@code ROLE_USER} and carries their id. A trusted
 * backend (the GTW Mumble bridge) is {@code ROLE_SERVICE}: it has no player id
 * of its own and may act on behalf of any player id it names.
 */
public final class LexiconPrincipal extends AbstractAuthenticationToken {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_SERVICE = "ROLE_SERVICE";

    private final Integer userId;
    private final String username;

    private LexiconPrincipal(Integer userId, String username, String role) {
        super(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role)));
        this.userId = userId;
        this.username = username;
        setAuthenticated(true);
    }

    public static LexiconPrincipal user(int userId, String username) {
        return new LexiconPrincipal(userId, username, ROLE_USER);
    }

    public static LexiconPrincipal service() {
        return new LexiconPrincipal(null, "service", ROLE_SERVICE);
    }

    /** The player id, or null for the service principal. */
    public Integer getUserId() {
        return userId;
    }

    public boolean isService() {
        return userId == null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    /**
     * True when a signed-in <em>player</em> names a different player id than their own. The trusted bridge
     * (service) may name anyone, and an anonymous caller has already been dealt with by the route rules.
     */
    public static boolean impersonates(Object claimedUserId) {
        LexiconPrincipal p = current();
        if (p == null || p.isService() || claimedUserId == null) {
            return false;
        }
        try {
            return p.getUserId() != Integer.parseInt(claimedUserId.toString().trim());
        } catch (NumberFormatException e) {
            return false; // not a number: the controller will reject it as a bad request
        }
    }

    /** Same idea for a username: a signed-in player may only name themselves. */
    public static boolean impersonatesName(String claimedUsername) {
        LexiconPrincipal p = current();
        if (p == null || p.isService() || claimedUsername == null) {
            return false;
        }
        return !claimedUsername.trim().equalsIgnoreCase(String.valueOf(p.getPrincipal()));
    }

    /** The principal of the current request, or null when it is anonymous. */
    public static LexiconPrincipal current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof LexiconPrincipal p ? p : null;
    }
}
