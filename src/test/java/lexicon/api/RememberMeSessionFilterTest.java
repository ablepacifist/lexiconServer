package lexicon.api;

import jakarta.servlet.http.Cookie;
import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import lexicon.logic.RememberMeManagerService.RememberMeResult;
import lexicon.object.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The "keep me logged in" cookie brings the session back on any API request, race-free. */
class RememberMeSessionFilterTest {

    private RememberMeManagerService rememberMe;
    private PlayerManagerService players;
    private RememberMeSessionFilter filter;

    @BeforeEach
    void setup() {
        rememberMe = mock(RememberMeManagerService.class);
        players = mock(PlayerManagerService.class);
        when(players.getPlayerById(5)).thenReturn(new Player(5, "tester", "x", 1));
        filter = new RememberMeSessionFilter(rememberMe, players);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest withCookie(String value) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/media/public");
        req.setCookies(new Cookie("remember-me", value));
        return req;
    }

    @Test
    void validCookieRestoresSessionAndRotatesCookie() throws Exception {
        when(rememberMe.validateAndRotate("old")).thenReturn(new RememberMeResult(5, "new"));
        MockHttpServletRequest req = withCookie("old");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertEquals(5, req.getSession(false).getAttribute("userId"));
        assertEquals("tester", req.getSession(false).getAttribute("username"));
        Cookie rotated = resp.getCookie("remember-me");
        assertNotNull(rotated);
        assertEquals("new", rotated.getValue());
        assertTrue(rotated.isHttpOnly());
    }

    @Test
    void parallelRequestsWithTheSameOldCookieAllRestore() throws Exception {
        // the token is single-use: only the first call may rotate it
        when(rememberMe.validateAndRotate("old")).thenReturn(new RememberMeResult(5, "new")).thenReturn(null);
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = withCookie("old");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, new MockFilterChain());
            assertEquals(5, req.getSession(false).getAttribute("userId"), "request " + i);
            assertEquals("new", resp.getCookie("remember-me").getValue(), "request " + i);
        }
        verify(rememberMe, times(1)).validateAndRotate("old");
    }

    @Test
    void unknownOrExpiredCookieLeavesRequestAnonymousAndKeepsCookie() throws Exception {
        when(rememberMe.validateAndRotate("junk")).thenReturn(null);
        MockHttpServletRequest req = withCookie("junk");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertNull(req.getSession(false));
        assertNull(resp.getCookie("remember-me"), "this filter never touches a cookie it could not use");
    }

    @Test
    void existingSessionIsLeftAloneAndTokenNotConsumed() throws Exception {
        MockHttpServletRequest req = withCookie("old");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7);
        req.setSession(session);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(rememberMe);
        assertEquals(7, session.getAttribute("userId"));
    }

    @Test
    void noCookieMeansNothingHappens() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(rememberMe);
    }

    @Test
    void deletedAccountClearsItsTokensAndStaysAnonymous() throws Exception {
        when(rememberMe.validateAndRotate("old")).thenReturn(new RememberMeResult(9, "new"));
        when(players.getPlayerById(9)).thenReturn(null);
        MockHttpServletRequest req = withCookie("old");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(req.getSession(false));
        verify(rememberMe).clearTokens(9);
    }

    @Test
    void backendTroubleNeverFailsTheRequest() throws Exception {
        when(rememberMe.validateAndRotate("old")).thenThrow(new RuntimeException("db down"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(withCookie("old"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "the request must still reach the rest of the chain");
    }
}
