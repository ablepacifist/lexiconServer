package lexicon.api;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing without ever locking a real person out for long.
 *
 * Failures are counted per "address + username" (so one person's typos never affect anyone else) and,
 * with a much higher allowance, per address alone (so one machine cannot try many usernames). After the
 * allowance, each further failure makes the next attempt wait twice as long, up to five minutes. A
 * successful sign-in clears the address+username counter, and idle counters are forgotten after 30
 * minutes. The remember-me and mobile-token paths do not go through here at all.
 */
@Component
public class LoginThrottle {

    static final int FREE_ATTEMPTS_PER_ACCOUNT = 5;
    static final int FREE_ATTEMPTS_PER_ADDRESS = 25;
    private static final long FIRST_WAIT_SECONDS = 5;
    private static final long MAX_WAIT_SECONDS = 300;
    private static final long FORGET_AFTER_MS = 30 * 60 * 1000L;

    private static final class Entry {
        int failures;
        long lastFailureAt;
        long blockedUntil;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Seconds the caller must still wait before another attempt, or 0 when it may try now. */
    public long secondsToWait(String address, String username) {
        return Math.max(wait(accountKey(address, username)), wait(addressKey(address)));
    }

    public void recordFailure(String address, String username) {
        fail(accountKey(address, username), FREE_ATTEMPTS_PER_ACCOUNT);
        fail(addressKey(address), FREE_ATTEMPTS_PER_ADDRESS);
    }

    public void recordSuccess(String address, String username) {
        entries.remove(accountKey(address, username));
    }

    private long wait(String key) {
        Entry e = entries.get(key);
        if (e == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (e) {
            if (now - e.lastFailureAt > FORGET_AFTER_MS) {
                entries.remove(key, e);
                return 0;
            }
            return e.blockedUntil > now ? (e.blockedUntil - now + 999) / 1000 : 0;
        }
    }

    private void fail(String key, int freeAttempts) {
        long now = System.currentTimeMillis();
        if (entries.size() > 10_000) { // keep memory bounded under a flood of distinct keys
            entries.entrySet().removeIf(en -> now - en.getValue().lastFailureAt > FORGET_AFTER_MS);
        }
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        synchronized (e) {
            if (now - e.lastFailureAt > FORGET_AFTER_MS) {
                e.failures = 0;
            }
            e.failures++;
            e.lastFailureAt = now;
            if (e.failures >= freeAttempts) {
                int over = Math.min(e.failures - freeAttempts, 16);
                long seconds = Math.min(MAX_WAIT_SECONDS, FIRST_WAIT_SECONDS << over);
                e.blockedUntil = now + seconds * 1000;
            }
        }
    }

    private static String accountKey(String address, String username) {
        return "u|" + address + "|" + (username == null ? "" : username.trim().toLowerCase());
    }

    private static String addressKey(String address) {
        return "a|" + address;
    }
}
