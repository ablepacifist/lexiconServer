package lexicon.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The avatar proxy forwards uploads to the Mumble bridge. By then it has checked the caller is the player named, so it
 * tells the bridge to trust that name with the shared service token. These tests talk to a stand-in bridge only.
 */
class AvatarProxyControllerTest {

    private static final String BRIDGE = "http://bridge.test";
    private static final String TOKEN = "shared-service-token-for-tests-0123456789";

    private AvatarProxyController controller;
    private MockRestServiceServer bridge;

    @BeforeEach
    void setUp() {
        controller = new AvatarProxyController();
        ReflectionTestUtils.setField(controller, "bridgeBaseUrl", BRIDGE);
        ReflectionTestUtils.setField(controller, "serviceToken", TOKEN);
        bridge = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(controller, "restTemplate")).build();
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.user(5, "carol"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("avatar", "me.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
    }

    @Test
    void uploadIsForwardedToTheBridgeWithTheServiceToken() {
        bridge.expect(requestTo(BRIDGE + "/api/avatar/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", TOKEN))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andRespond(withSuccess("{\"avatarUrl\":\"/uploads/avatars/carol_1.png\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> r = controller.uploadAvatar("carol", 5, png());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("/uploads/avatars/carol_1.png", r.getBody().get("avatarUrl"));
        bridge.verify();
    }

    @Test
    void removeIsForwardedToTheBridgeWithTheServiceToken() {
        bridge.expect(requestTo(BRIDGE + "/api/avatar/remove"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", TOKEN))
                .andExpect(content().json("{\"username\":\"carol\",\"userId\":5}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> r = controller.removeAvatar(Map.of("username", "carol", "userId", 5));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        bridge.verify();
    }

    @Test
    void anotherPlayersAvatarIsNeverForwarded() {
        // no request may reach the bridge at all
        assertEquals(HttpStatus.FORBIDDEN, controller.uploadAvatar("dave", 9, png()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.removeAvatar(Map.of("username", "dave", "userId", 9)).getStatusCode());
        bridge.verify();
    }

    @Test
    void withoutAConfiguredTokenNoTokenHeaderIsSent() {
        ReflectionTestUtils.setField(controller, "serviceToken", "");
        bridge.expect(requestTo(BRIDGE + "/api/avatar/upload"))
                .andExpect(headerDoesNotExist("X-Service-Token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        controller.uploadAvatar("carol", 5, png());

        bridge.verify();
    }

    @Test
    void readingAnAvatarStillNeedsNoToken() {
        bridge.expect(requestTo(BRIDGE + "/api/avatar/carol"))
                .andExpect(headerDoesNotExist("X-Service-Token"))
                .andRespond(withSuccess("{\"avatarUrl\":\"/uploads/avatars/default.jpg\"}", MediaType.APPLICATION_JSON));

        assertEquals(HttpStatus.OK, controller.getAvatar("carol").getStatusCode());
        bridge.verify();
    }
}
