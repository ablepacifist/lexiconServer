package lexicon.api;

import jakarta.servlet.http.Cookie;
import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import lexicon.logic.RememberMeManagerService.RememberMeResult;
import lexicon.object.Player;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The remember-me cookie is Secure exactly when the visitor used HTTPS, so plain-HTTP LAN logins keep working. */
class SecureCookieTest {

    private Cookie rotatedCookieFor(MockHttpServletRequest req) throws Exception {
        RememberMeManagerService rememberMe = mock(RememberMeManagerService.class);
        PlayerManagerService players = mock(PlayerManagerService.class);
        when(rememberMe.validateAndRotate("old")).thenReturn(new RememberMeResult(5, "new"));
        when(players.getPlayerById(5)).thenReturn(new Player(5, "tester", "x", 1));
        req.setCookies(new Cookie("remember-me", "old"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new RememberMeSessionFilter(rememberMe, players).doFilter(req, resp, new MockFilterChain());
        return resp.getCookie("remember-me");
    }

    @Test
    void secureWhenTheTunnelSaysHttps() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/media/public");
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-Proto", "https");
        assertTrue(rotatedCookieFor(req).getSecure());
    }

    @Test
    void notSecureOnPlainHttpOverTheLan() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/media/public");
        req.setRemoteAddr("192.168.1.20");
        assertFalse(rotatedCookieFor(req).getSecure());
    }

    @Test
    void aLanClientCannotClaimHttpsForItself() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/media/public");
        req.setRemoteAddr("192.168.1.20");
        req.addHeader("X-Forwarded-Proto", "https");
        assertFalse(rotatedCookieFor(req).getSecure());
    }

    @Test
    void secureWhenTheConnectionItselfIsTls() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/media/public");
        req.setSecure(true);
        req.setRemoteAddr("192.168.1.20");
        assertTrue(rotatedCookieFor(req).getSecure());
    }
}
