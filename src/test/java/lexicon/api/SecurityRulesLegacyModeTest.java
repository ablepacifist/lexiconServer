package lexicon.api;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The deploy-time transition mode (LEXICON_LEGACY_OPEN_ROUTES=true): the routes the GTW bridge used to
 * call without credentials stay open until it sends X-Service-Token, but everything that changes or
 * exposes accounts and the library is already closed.
 */
@SpringJUnitWebConfig(classes = {LexiconSecurityConfig.class, SecurityRulesTest.Stub.class, SecurityTestBeans.class})
@TestPropertySource(properties = {
        "lexicon.service-token=svc-secret-for-tests",
        "lexicon.security.legacy-open-routes=true",
        "cors.allowed.origins=http://localhost:3000"
})
class SecurityRulesLegacyModeTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void bridgeRoutesStayOpenWithoutCredentials() throws Exception {
        for (String[] r : new String[][]{
                {"POST", "/api/messages"}, {"GET", "/api/messages/channel/1"}, {"POST", "/api/chat/upload"},
                {"GET", "/api/avatar/bob"}, {"POST", "/api/push/send"}, {"POST", "/api/notifications"},
                {"GET", "/api/livestream/state"}, {"POST", "/api/livestream/queue"},
                {"GET", "/api/players"}, {"GET", "/api/players/username/bob"}, {"GET", "/api/media/search"},
                {"GET", "/api/playlists/public"}}) {
            mvc.perform(request(HttpMethod.valueOf(r[0]), r[1])).andExpect(status().isOk());
        }
    }

    @Test
    void accountsAndLibraryAreAlreadyClosed() throws Exception {
        for (String[] r : new String[][]{
                {"GET", "/api/players/5"}, {"PUT", "/api/players/5"}, {"DELETE", "/api/players/5"},
                {"POST", "/api/media/upload"}, {"DELETE", "/api/media/3"}, {"POST", "/api/playlists"},
                {"POST", "/api/playback/position"}, {"GET", "/api/playback/user/5"},
                {"POST", "/api/download-queue/start"}, {"POST", "/api/events"}}) {
            mvc.perform(request(HttpMethod.valueOf(r[0]), r[1])).andExpect(status().isUnauthorized());
        }
    }
}
