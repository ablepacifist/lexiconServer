package lexicon.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A signed-in player can only act as themselves; the bridge can act as anyone. */
class IdentityGuardTest {

    @RestController
    static class Stub {
        @PostMapping("/api/playlists")
        String create(@RequestParam("userId") int userId) { return "ok"; }

        @DeleteMapping("/api/playback/position/{userId}/{mediaId}")
        String delete(@PathVariable int userId, @PathVariable int mediaId) { return "ok"; }

        @GetMapping("/api/playback/user/{userId}")
        String positions(@PathVariable int userId) { return "ok"; }

        @GetMapping("/api/notifications")
        String notifications(@RequestParam int userId) { return "ok"; }

        @GetMapping("/api/media/user/{userId}")
        String library(@PathVariable int userId) { return "ok"; }

        @GetMapping("/api/playlists")
        String lists(@RequestParam(required = false) Integer userId) { return "ok"; }

        @PostMapping("/api/livestream/skip")
        String skip(@RequestBody Map<String, Integer> body) { return "ok"; }

        @PostMapping("/api/avatar/remove")
        String removeBody(@RequestBody Map<String, Object> body) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Stub())
                .addInterceptors(new IdentityGuard.Interceptor())
                .setControllerAdvice(new IdentityGuard.BodyAdvice())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(int id) {
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.user(id, "tester"));
    }

    private ResultActions json(MockHttpServletRequestBuilder b, String body) throws Exception {
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void playerActingAsThemselvesIsAllowed() throws Exception {
        signInAs(5);
        mvc.perform(post("/api/playlists").param("userId", "5")).andExpect(status().isOk());
        mvc.perform(delete("/api/playback/position/5/9")).andExpect(status().isOk());
        mvc.perform(get("/api/playback/user/5")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").param("userId", "5")).andExpect(status().isOk());
        json(post("/api/livestream/skip"), "{\"userId\":5}").andExpect(status().isOk());
    }

    @Test
    void playerActingAsSomeoneElseIsRefused() throws Exception {
        signInAs(5);
        mvc.perform(post("/api/playlists").param("userId", "6")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/playback/position/6/9")).andExpect(status().isForbidden());
        mvc.perform(get("/api/playback/user/6")).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").param("userId", "6")).andExpect(status().isForbidden());
        json(post("/api/livestream/skip"), "{\"userId\":6}").andExpect(status().isForbidden());
        json(post("/api/avatar/remove"), "{\"userId\":\"6\",\"username\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void browsingOtherPeoplesPublicLibraryAndPlaylistsStillWorks() throws Exception {
        signInAs(5);
        mvc.perform(get("/api/media/user/6")).andExpect(status().isOk());
        mvc.perform(get("/api/playlists").param("userId", "6")).andExpect(status().isOk());
    }

    @Test
    void bridgeMayActForAnyPlayer() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.service());
        mvc.perform(post("/api/playlists").param("userId", "6")).andExpect(status().isOk());
        json(post("/api/livestream/skip"), "{\"userId\":6}").andExpect(status().isOk());
    }

    @Test
    void onlyTheTwoLiveStreamChannelsAreAccepted() throws Exception {
        MockMvc channels = MockMvcBuilders.standaloneSetup(new Stub())
                .addInterceptors(new IdentityGuard.ChannelInterceptor()).build();
        channels.perform(post("/api/livestream/skip").param("channel", "music").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5}"))
                .andExpect(status().isOk());
        channels.perform(post("/api/livestream/skip").param("channel", "video").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5}"))
                .andExpect(status().isOk());
        channels.perform(post("/api/livestream/skip").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5}"))
                .andExpect(status().isOk());
        channels.perform(post("/api/livestream/skip").param("channel", "x' OR '1'='1").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5}"))
                .andExpect(status().isBadRequest());
        channels.perform(post("/api/livestream/skip").param("channel", "podcast").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usernameCheckIsCaseInsensitiveAndSelfOnly() {
        signInAs(5);
        org.junit.jupiter.api.Assertions.assertFalse(LexiconPrincipal.impersonatesName("Tester"));
        org.junit.jupiter.api.Assertions.assertTrue(LexiconPrincipal.impersonatesName("someone"));
        SecurityContextHolder.getContext().setAuthentication(LexiconPrincipal.service());
        org.junit.jupiter.api.Assertions.assertFalse(LexiconPrincipal.impersonatesName("someone"));
    }
}
