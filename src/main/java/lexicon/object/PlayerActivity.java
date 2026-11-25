package lexicon.object;

import java.time.LocalDateTime;

/**
 * One account as the admin user list needs it: who, their role, and when they were last around.
 * Deliberately has no password or email, so it can never leak them into an admin response.
 *
 * @param lastLoginDate  last password sign-in (null if never recorded)
 * @param lastActiveDate last request made while signed in, any way (session, app token,
 *                       remember-me, SSO); null if never recorded
 */
public record PlayerActivity(
        int id,
        String username,
        String role,
        LocalDateTime registrationDate,
        LocalDateTime lastLoginDate,
        LocalDateTime lastActiveDate) {
}
