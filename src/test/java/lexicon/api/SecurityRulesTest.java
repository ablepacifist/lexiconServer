package lexicon.api;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real Spring Security chain from {@link LexiconSecurityConfig} (with the real
 * session and service-token filters) in front of a catch-all stub controller, to pin down
 * which routes are open, which need a signed-in player and which the bridge may use.
 */
@SpringJUnitWebConfig(classes = {LexiconSecurityConfig.class, SecurityRulesTest.Stub.class, SecurityTestBeans.class})
@TestPropertySource(properties = {
        "lexicon.service-token=svc-secret-for-tests",
        "app.update.public-metadata=true",
        "app.update.public-download=true",
        "cors.allowed.origins=http://localhost:3000"
})
class SecurityRulesTest {

    @EnableWebMvc
    @RestController
    static class Stub {
        @RequestMapping("/**")
        String any() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private static MockHttpSession playerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 5);
        session.setAttribute("username", "tester");
        return session;
    }

    private ResultActions anonymous(HttpMethod method, String path) throws Exception {
        return mvc.perform(request(method, path));
    }

    private ResultActions asPlayer(HttpMethod method, String path) throws Exception {
        return mvc.perform(request(method, path).session(playerSession()));
    }

    private ResultActions asBridge(HttpMethod method, String path) throws Exception {
        return mvc.perform(request(method, path).header("X-Service-Token", "svc-secret-for-tests"));
    }

    // ---------------------------------------------------------------- open routes

    @Test
    void signInAndTagLoadedRoutesStayOpen() throws Exception {
        for (String[] r : new String[][]{
                {"POST", "/api/auth/login"}, {"POST", "/api/auth/register"}, {"GET", "/api/auth/me"},
                {"POST", "/api/auth/logout"}, {"POST", "/api/auth/sso/validate-token"},
                {"GET", "/api/health"}, {"GET", "/api/info"},
                {"GET", "/api/app/version"}, {"GET", "/api/app/download/latest"},
                {"GET", "/api/media/stream/1"}, {"GET", "/api/stream/1"},
                {"GET", "/api/avatar/image/a/b.png"}, {"GET", "/api/chat/files/1"}, {"GET", "/api/chat/files/1/thumb"},
                {"GET", "/api/livestream/updates"}, {"GET", "/api/notifications/stream"},
                {"GET", "/api/playlists/import-progress/x"}}) {
            anonymous(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------- anonymous callers

    @Test
    void anonymousCallersAreRefusedWith401() throws Exception {
        for (String[] r : new String[][]{
                {"GET", "/api/players"}, {"GET", "/api/players/5"}, {"GET", "/api/players/username/bob"},
                {"PUT", "/api/players/5"}, {"DELETE", "/api/players/5"},
                {"GET", "/api/media/public"}, {"GET", "/api/media/user/5"}, {"POST", "/api/media/upload"},
                {"DELETE", "/api/media/3"}, {"POST", "/api/media/chunked/init"},
                {"GET", "/api/playlists/public"}, {"POST", "/api/playlists"}, {"DELETE", "/api/playlists/2"},
                {"GET", "/api/playback/user/5"}, {"POST", "/api/playback/position"},
                {"POST", "/api/download-queue/start"}, {"GET", "/api/download-queue/active/5"},
                {"GET", "/api/livestream/state"}, {"POST", "/api/livestream/queue"}, {"POST", "/api/livestream/skip"},
                {"POST", "/api/messages"}, {"DELETE", "/api/messages/4"}, {"GET", "/api/messages/channel/1"},
                {"POST", "/api/chat/upload"}, {"POST", "/api/avatar/upload"}, {"GET", "/api/avatar/bob"},
                {"POST", "/api/push/subscribe"}, {"POST", "/api/push/send"},
                {"GET", "/api/notifications"}, {"PUT", "/api/notifications/prefs"},
                {"GET", "/api/events"}, {"POST", "/api/events"}, {"GET", "/api/voice/status"}}) {
            anonymous(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------------- signed-in player

    @Test
    void signedInPlayerCanUseEverything() throws Exception {
        for (String[] r : new String[][]{
                {"GET", "/api/players"}, {"DELETE", "/api/players/5"}, {"POST", "/api/media/upload"},
                {"POST", "/api/playlists"}, {"POST", "/api/playback/position"}, {"POST", "/api/download-queue/start"},
                {"POST", "/api/livestream/queue"},
                {"POST", "/api/avatar/upload"}, {"POST", "/api/push/subscribe"}, {"GET", "/api/notifications"},
                {"POST", "/api/events"}, {"GET", "/api/voice/status"}, {"GET", "/api/app/anything"}}) {
            asPlayer(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isOk());
        }
    }

    @Test
    void bridgeOnlyRoutesRefuseSignedInPlayers() throws Exception {
        for (String[] r : new String[][]{
                {"POST", "/api/messages"}, {"DELETE", "/api/messages/4"}, {"GET", "/api/messages/channel/1"},
                {"POST", "/api/chat/upload"}, {"POST", "/api/notifications"},
                {"POST", "/api/push/send"}, {"POST", "/api/push/send-bulk"}}) {
            asPlayer(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------- the bridge

    @Test
    void bridgeTokenOpensOnlyWhatTheBridgeNeeds() throws Exception {
        for (String[] r : new String[][]{
                {"POST", "/api/messages"}, {"GET", "/api/messages/channel/1"}, {"DELETE", "/api/messages/4"},
                {"POST", "/api/chat/upload"}, {"GET", "/api/avatar/bob"}, {"POST", "/api/avatar/upload"},
                {"GET", "/api/push/vapid-key"}, {"POST", "/api/push/send"}, {"POST", "/api/push/send-bulk"}, {"POST", "/api/notifications"},
                {"GET", "/api/notifications"},
                {"GET", "/api/livestream/state"}, {"POST", "/api/livestream/queue"}, {"POST", "/api/livestream/skip"},
                {"GET", "/api/players"}, {"GET", "/api/players/username/bob"}, {"GET", "/api/media/search"},
                {"GET", "/api/media/3"}, {"GET", "/api/media/public"}, {"GET", "/api/playlists/public"},
                {"GET", "/api/playlists/2"}}) {
            asBridge(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isOk());
        }
    }

    @Test
    void bridgeTokenCannotTouchAccountsOrLibrary() throws Exception {
        for (String[] r : new String[][]{
                {"GET", "/api/players/5"}, {"PUT", "/api/players/5"}, {"DELETE", "/api/players/5"},
                {"POST", "/api/media/upload"}, {"DELETE", "/api/media/3"}, {"PUT", "/api/media/3"},
                {"POST", "/api/playlists"}, {"DELETE", "/api/playlists/2"},
                {"POST", "/api/playback/position"}, {"POST", "/api/download-queue/start"},
                {"POST", "/api/events"}, {"GET", "/api/voice/status"}}) {
            asBridge(HttpMethod.valueOf(r[0]), r[1]).andExpect(status().isForbidden());
        }
    }

    @Test
    void wrongTokenIsJustAnonymous() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/livestream/queue").header("X-Service-Token", "nope"))
                .andExpect(status().isUnauthorized());
        // ...and never locks out a request that also carries a valid player session
        mvc.perform(request(HttpMethod.POST, "/api/livestream/queue").header("X-Service-Token", "nope").session(playerSession()))
                .andExpect(status().isOk());
    }

    @Test
    void tokenIsIgnoredWhenNoneIsConfigured() throws Exception {
        ServiceTokenAuthFilter off = new ServiceTokenAuthFilter("");
        MockMvc bare = MockMvcBuilders.standaloneSetup(new Stub()).addFilters(off).build();
        MockHttpServletRequestBuilder req = request(HttpMethod.GET, "/x").header("X-Service-Token", "");
        bare.perform(req).andExpect(status().isOk());
        // the filter must not have authenticated anybody
        org.junit.jupiter.api.Assertions.assertNull(LexiconPrincipal.current());
    }
}
