package lexicon.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {

    @Test
    void ordinaryTyposNeverWait() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS_PER_ACCOUNT - 1; i++) {
            assertEquals(0, t.secondsToWait("1.2.3.4", "alice"));
            t.recordFailure("1.2.3.4", "alice");
        }
        assertEquals(0, t.secondsToWait("1.2.3.4", "alice"), "four wrong passwords in a row are still fine");
    }

    @Test
    void repeatedFailuresMakeTheNextAttemptWaitAndTheWaitGrows() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS_PER_ACCOUNT; i++) {
            t.recordFailure("1.2.3.4", "alice");
        }
        long first = t.secondsToWait("1.2.3.4", "alice");
        assertTrue(first >= 1 && first <= 5, "first wait is a few seconds, got " + first);
        t.recordFailure("1.2.3.4", "alice");
        long second = t.secondsToWait("1.2.3.4", "alice");
        assertTrue(second > first, "the wait doubles: " + first + " -> " + second);
        for (int i = 0; i < 30; i++) {
            t.recordFailure("1.2.3.4", "alice");
        }
        assertTrue(t.secondsToWait("1.2.3.4", "alice") <= 300, "never more than five minutes");
    }

    @Test
    void oneAccountsTroubleDoesNotAffectOthers() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 10; i++) {
            t.recordFailure("1.2.3.4", "alice");
        }
        assertTrue(t.secondsToWait("1.2.3.4", "alice") > 0);
        assertEquals(0, t.secondsToWait("1.2.3.4", "bob"), "another user on the same address");
        assertEquals(0, t.secondsToWait("9.9.9.9", "alice"), "the same user from another address");
    }

    @Test
    void usernameCaseDoesNotHelpAGuesser() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS_PER_ACCOUNT; i++) {
            t.recordFailure("1.2.3.4", i % 2 == 0 ? "Alice" : "alice ");
        }
        assertTrue(t.secondsToWait("1.2.3.4", "ALICE") > 0);
    }

    @Test
    void successClearsTheAccountCounter() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS_PER_ACCOUNT; i++) {
            t.recordFailure("1.2.3.4", "alice");
        }
        assertTrue(t.secondsToWait("1.2.3.4", "alice") > 0);
        t.recordSuccess("1.2.3.4", "alice");
        assertEquals(0, t.secondsToWait("1.2.3.4", "alice"));
    }

    @Test
    void oneAddressTryingManyUsernamesIsSlowedDown() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS_PER_ADDRESS; i++) {
            t.recordFailure("6.6.6.6", "user" + i); // every name only fails once
        }
        assertTrue(t.secondsToWait("6.6.6.6", "brand-new-name") > 0);
        assertEquals(0, t.secondsToWait("7.7.7.7", "brand-new-name"));
    }

    // ------------------------------------------------------------------ client address

    @Test
    void forwardedAddressIsBelievedOnlyFromTheLocalTunnel() {
        MockHttpServletRequest viaTunnel = new MockHttpServletRequest();
        viaTunnel.setRemoteAddr("127.0.0.1");
        viaTunnel.addHeader("CF-Connecting-IP", "203.0.113.9");
        assertEquals("203.0.113.9", ClientIp.of(viaTunnel));

        MockHttpServletRequest viaTunnelV6 = new MockHttpServletRequest();
        viaTunnelV6.setRemoteAddr("0:0:0:0:0:0:0:1");
        viaTunnelV6.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        assertEquals("203.0.113.10", ClientIp.of(viaTunnelV6));

        MockHttpServletRequest spoofed = new MockHttpServletRequest();
        spoofed.setRemoteAddr("198.51.100.7");
        spoofed.addHeader("CF-Connecting-IP", "1.1.1.1");
        spoofed.addHeader("X-Forwarded-For", "2.2.2.2");
        assertEquals("198.51.100.7", ClientIp.of(spoofed), "a direct caller cannot pick its own address");

        MockHttpServletRequest lan = new MockHttpServletRequest();
        lan.setRemoteAddr("192.168.1.20");
        assertEquals("192.168.1.20", ClientIp.of(lan));
    }
}
