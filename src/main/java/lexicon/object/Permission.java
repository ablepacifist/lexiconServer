package lexicon.object;

/**
 * Things a player may be allowed to do anywhere in the stack.
 *
 * <p>Lexicon is the permission authority: Alchemy and the GTW voice bridge receive these names
 * (in {@code /api/auth/login}, {@code /api/auth/me} and {@code /api/auth/sso/validate-token})
 * and check them; they never keep lists of their own. Adding a permission is one line here
 * plus deciding which {@link Role}s grant it.
 */
public enum Permission {
    /** Advance a Holdfast's in-game clock (Alchemy). */
    HOLDFAST_ADVANCE_TIME,
    /** Delete a Holdfast (Alchemy). */
    HOLDFAST_DELETE,
    /** Voice-chat admin: create/remove channels, pins, channel access, diagnostics (GTW bridge). */
    VOICE_ADMIN,
    /** Change other players' roles (Lexicon). */
    MANAGE_PERMISSIONS
}
