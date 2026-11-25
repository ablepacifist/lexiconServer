package lexicon.logic;

/**
 * Records when each player was last active, for the admin user list.
 *
 * Called for every signed-in request, however the player signed in (password session, the app's
 * mobile token, remember-me, SSO). Only a password sign-in used to leave a trace, so someone who
 * only ever used the app looked like they had not been seen in months.
 */
public interface ActivityTrackerService {
    void recordActivity(int playerId);
}
