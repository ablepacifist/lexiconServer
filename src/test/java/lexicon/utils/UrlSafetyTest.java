package lexicon.utils;

import lexicon.service.YtDlpService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class UrlSafetyTest {

    private static void rejected(String url) {
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.requirePublicHttpUrl(url), url);
    }

    private static void accepted(String url) {
        assertDoesNotThrow(() -> UrlSafety.requirePublicHttpUrl(url), url);
    }

    @Test
    void acceptsPublicHttpAndHttpsAddresses() {
        accepted("https://93.184.216.34/watch?v=abc");
        accepted("http://8.8.8.8/x");
        accepted("HTTPS://1.1.1.1/");
        accepted("https://[2606:4700:4700::1111]/");
    }

    @Test
    void refusesOptionLookalikesAndOtherSchemes() {
        rejected("--exec=calc.exe");
        rejected("-o /etc/passwd");
        rejected("file:///C:/Windows/win.ini");
        rejected("ftp://93.184.216.34/x");
        rejected("javascript:alert(1)");
        rejected("//93.184.216.34/x");
        rejected("93.184.216.34");
        rejected("");
        rejected("   ");
        rejected(null);
    }

    @Test
    void refusesLocalAndPrivateTargets() {
        rejected("http://127.0.0.1:8080/api/player/all");
        rejected("http://localhost/x");
        rejected("http://0.0.0.0/x");
        rejected("http://10.0.0.5/x");
        rejected("http://172.16.4.4/x");
        rejected("http://192.168.1.4:36568/api/players");
        rejected("http://169.254.169.254/latest/meta-data");
        rejected("http://100.64.1.1/x");
        rejected("http://[::1]/x");
        rejected("http://[fd00::1]/x");
        rejected("http://[fe80::1]/x");
        rejected("http://[::ffff:127.0.0.1]/x");
        rejected("http://[::ffff:192.168.1.4]/x");
        rejected("http://224.0.0.1/x");
    }

    @Test
    void refusesCredentialsWhitespaceAndOversizedUrls() {
        rejected("http://user:pw@93.184.216.34/x");
        rejected("http://93.184.216.34/ x");
        rejected("http://93.184.216.34/\nHost: evil");
        rejected("https://93.184.216.34/" + "a".repeat(3000));
    }

    @Test
    void ytDlpServiceRefusesBadUrlsBeforeTouchingTheDisk() throws Exception {
        File dir = Files.createTempDirectory("ytdlp-guard").toFile();
        try {
            YtDlpService service = new YtDlpService();
            for (String bad : new String[]{"--exec=calc.exe", "file:///etc/passwd", "http://127.0.0.1:8080/x"}) {
                YtDlpService.DownloadResult r = service.downloadFromUrl(bad, YtDlpService.DownloadType.AUDIO_ONLY, dir.getPath());
                assertFalse(r.isSuccess(), bad);
                assertNotNull(r.getErrorMessage(), bad);
            }
            assertEquals(0, dir.list().length, "nothing may be created for a refused URL");
        } finally {
            dir.delete();
        }
    }
}
