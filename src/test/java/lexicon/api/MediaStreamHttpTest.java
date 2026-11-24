package lexicon.api;

import lexicon.logic.MediaManagerService;
import lexicon.object.StreamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/media/stream/{id} with a Range header, through Spring's real message converters:
 * a range streamed from disk must arrive whole, with matching Content-Range and Content-Length.
 */
class MediaStreamHttpTest {

    private static final int TOTAL = 6 * 1024 * 1024;

    private final MediaManagerService mediaManager = mock(MediaManagerService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        MediaController controller = new MediaController();
        var field = MediaController.class.getDeclaredField("mediaManager");
        field.setAccessible(true);
        field.set(controller, mediaManager);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void openEndedRangeIsSentToTheEndOfTheFile() throws Exception {
        long start = 2_000_000;
        byte[] rest = new byte[(int) (TOTAL - start)];
        for (int i = 0; i < rest.length; i++) rest[i] = (byte) i;
        when(mediaManager.getStreamData(826, "bytes=" + start + "-")).thenReturn(
                new StreamResult(new ByteArrayInputStream(rest), start, TOTAL - 1, TOTAL, true, "audio/mpeg"));

        MockHttpServletResponse response = mvc.perform(get("/api/media/stream/826").header("Range", "bytes=" + start + "-"))
                .andReturn().getResponse();

        assertEquals(206, response.getStatus());
        assertEquals("bytes " + start + "-" + (TOTAL - 1) + "/" + TOTAL, response.getHeader("Content-Range"));
        assertEquals(String.valueOf(rest.length), response.getHeader("Content-Length"));
        assertEquals("audio/mpeg", response.getContentType());
        assertTrue(Arrays.equals(rest, response.getContentAsByteArray()), "every remaining byte is sent");
    }

    @Test
    void inMemoryRangesStillWork() throws Exception {
        byte[] slice = {1, 2, 3, 4};
        when(mediaManager.getStreamData(5, "bytes=0-3")).thenReturn(
                new StreamResult(slice, 0, 3, 10, true, "audio/mpeg"));

        MockHttpServletResponse response = mvc.perform(get("/api/media/stream/5").header("Range", "bytes=0-3"))
                .andReturn().getResponse();

        assertEquals(206, response.getStatus());
        assertEquals("bytes 0-3/10", response.getHeader("Content-Range"));
        assertArrayEquals(slice, response.getContentAsByteArray());
    }

    @Test
    void unsatisfiableRangeIs416() throws Exception {
        when(mediaManager.getStreamData(826, "bytes=" + TOTAL + "-")).thenReturn(
                new StreamResult((byte[]) null, -1, -1, TOTAL, false, "audio/mpeg"));

        MockHttpServletResponse response = mvc.perform(get("/api/media/stream/826").header("Range", "bytes=" + TOTAL + "-"))
                .andReturn().getResponse();

        assertEquals(416, response.getStatus());
        assertEquals("bytes */" + TOTAL, response.getHeader("Content-Range"));
    }
}
