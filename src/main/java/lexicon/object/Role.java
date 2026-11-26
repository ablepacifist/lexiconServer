package lexicon.object;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A player's role, stored in {@code PLAYERS.ROLE}. Each role grants a fixed set of
 * {@link Permission}s; nothing else in the stack decides what a role means.
 */
public enum Role {
    /** Every signed-in player: may start a stopped game server and ask for a restart. */
    USER(EnumSet.of(Permission.GAMESERVER_START, Permission.GAMESERVER_REQUEST_RESTART)),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** The role named by a stored value; anything unknown or empty is {@link #USER}. */
    public static Role fromStored(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
