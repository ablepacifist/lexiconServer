package lexicon.service;

import lexicon.config.StorageProperties;
import lexicon.data.ITorrentDatabase;
import lexicon.logic.MediaManager;
import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import lexicon.object.TorrentDownload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The rules that matter for torrent downloads: never fetch anything but mp3/mp4, never
 * download without the VPN, and keep Lexicon and qBittorrent in step when media is deleted.
 */
class TorrentDownloadServiceTest {

    @Mock private QBittorrentClient qbittorrent;
    @Mock private ITorrentDatabase torrentDatabase;
    @Mock private MediaManagerService mediaManager;
    @Mock private MediaManager mediaManagerImpl;
    @Mock private WslDiskUsageService diskUsageService;
    @Mock private VpnStatusService vpnStatus;

    private StorageProperties storageProperties;
    private TorrentDownloadService service;

    private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        storageProperties = new StorageProperties();
        when(qbittorrent.isConfigured()).thenReturn(true);
        when(vpnStatus.tunnelAddress()).thenReturn("10.2.0.2");
        when(diskUsageService.listPhysicalDriveVolumes()).thenReturn(List.of());
        service = new TorrentDownloadService(qbittorrent, torrentDatabase, mediaManager, mediaManagerImpl,
                storageProperties, diskUsageService, vpnStatus, "//host/share/torrents", 3, 20);
    }

    private void healthy() {
        when(qbittorrent.health(anyString())).thenReturn(
                new QBittorrentClient.Health(true, true, "10.2.0.2", ""));
    }

    private QBittorrentClient.TorrentInfo info(String state, double progress) {
        return new QBittorrentClient.TorrentInfo(HASH, "Some Torrent", state, progress, 100,
                0, 0, "lexi-tag", "//host/share/torrents", 0);
    }

    private TorrentDownloadService.TorrentRequest magnetRequest() {
        TorrentDownloadService.TorrentRequest request = new TorrentDownloadService.TorrentRequest();
        request.userId = 7;
        request.urlOrMagnet = "magnet:?xt=urn:btih:" + HASH + "&dn=Some+Torrent";
        return request;
    }

    // ── the mp3/mp4 rule ─────────────────────────────────────────────────────────────

    @Test
    void onlyMp3AndMp4AreAccepted() {
        assertTrue(TorrentDownloadService.allowed("song.mp3"));
        assertTrue(TorrentDownloadService.allowed("Movie.MP4"));
        assertTrue(TorrentDownloadService.allowed("disc 1/02 - track.mp3"));

        assertFalse(TorrentDownloadService.allowed("movie.mkv"));
        assertFalse(TorrentDownloadService.allowed("cover.jpg"));
        assertFalse(TorrentDownloadService.allowed("readme.nfo"));
        assertFalse(TorrentDownloadService.allowed("song.flac"));
        assertFalse(TorrentDownloadService.allowed("archive.rar"));
        // An unfinished qBittorrent part file must not be mistaken for a finished mp3.
        assertFalse(TorrentDownloadService.allowed("song.mp3.!qB"));
        // Not a real extension, just a name that mentions one.
        assertFalse(TorrentDownloadService.allowed("mp3 collection/notes.txt"));
    }

    @Test
    void everythingThatIsNotMp3OrMp4IsDeselectedBeforeTheTorrentStarts() throws Exception {
        healthy();
        when(qbittorrent.byTag(anyString())).thenReturn(List.of(info("stoppedDL", 0)));
        when(qbittorrent.files(HASH)).thenReturn(List.of(
                new QBittorrentClient.TorrentFile(0, "album/01.mp3", 5_000_000, 1, 0),
                new QBittorrentClient.TorrentFile(1, "album/cover.jpg", 50_000, 1, 0),
                new QBittorrentClient.TorrentFile(2, "album/02.mp3", 6_000_000, 1, 0),
                new QBittorrentClient.TorrentFile(3, "album/notes.nfo", 1_000, 1, 0),
                new QBittorrentClient.TorrentFile(4, "extras/clip.mp4", 20_000_000, 1, 0)));

        service.submit(magnetRequest());

        ArgumentCaptor<List<Integer>> deselected = ArgumentCaptor.forClass(List.class);
        verify(qbittorrent).setFilePriority(eq(HASH), deselected.capture(), eq(0));
        assertEquals(List.of(1, 3), deselected.getValue(), "only the non-media files");
        // Order matters: deselect first, start second, or unwanted bytes hit the disk.
        InOrder order = inOrder(qbittorrent);
        order.verify(qbittorrent).setFilePriority(eq(HASH), anyList(), eq(0));
        order.verify(qbittorrent).start(HASH);
    }

    @Test
    void aTorrentWithNoMediaIsRemovedWithItsData() throws Exception {
        healthy();
        when(qbittorrent.byTag(anyString())).thenReturn(List.of(info("stoppedDL", 0)));
        when(qbittorrent.files(HASH)).thenReturn(List.of(
                new QBittorrentClient.TorrentFile(0, "linux.iso", 900_000_000, 1, 0),
                new QBittorrentClient.TorrentFile(1, "readme.txt", 500, 1, 0)));

        TorrentDownloadService.TorrentRejected rejected = assertThrows(
                TorrentDownloadService.TorrentRejected.class, () -> service.submit(magnetRequest()));

        assertTrue(rejected.getMessage().contains(".mp3"), rejected.getMessage());
        verify(qbittorrent).delete(HASH, true);
        verify(qbittorrent, never()).start(anyString());
        verify(torrentDatabase).updateStatus(eq(HASH), eq(TorrentDownload.Status.FAILED), anyString());
    }

    // ── the VPN rule ─────────────────────────────────────────────────────────────────

    @Test
    void nothingIsQueuedWhileQbittorrentIsDown() throws Exception {
        when(qbittorrent.health(any())).thenReturn(new QBittorrentClient.Health(
                false, false, "", "qBittorrent isn't running. Start it at the desktop."));

        TorrentDownloadService.TorrentRejected rejected = assertThrows(
                TorrentDownloadService.TorrentRejected.class, () -> service.submit(magnetRequest()));

        assertTrue(rejected.getMessage().contains("isn't running"));
        verify(qbittorrent, never()).addTorrentUrl(anyString(), anyString(), anyString());
    }

    @Test
    void nothingIsQueuedWhenQbittorrentIsNotBoundToTheTunnel() throws Exception {
        // The real leak: the client is up but listening on the ordinary connection.
        when(qbittorrent.health(any())).thenReturn(new QBittorrentClient.Health(
                true, false, "192.168.1.4", "qBittorrent is bound to 192.168.1.4 but the VPN tunnel is 10.2.0.2."));

        assertThrows(TorrentDownloadService.TorrentRejected.class, () -> service.submit(magnetRequest()));
        verify(qbittorrent, never()).addTorrentUrl(anyString(), anyString(), anyString());
        verify(qbittorrent, never()).addTorrentFile(any(), anyString(), anyString(), anyString());
    }

    @Test
    void aUserIsLimitedToAFewAtOnce() throws Exception {
        healthy();
        when(torrentDatabase.countActiveForUser(7)).thenReturn(3);

        TorrentDownloadService.TorrentRejected rejected = assertThrows(
                TorrentDownloadService.TorrentRejected.class, () -> service.submit(magnetRequest()));

        assertTrue(rejected.getMessage().contains("3 torrents"));
        verify(qbittorrent, never()).addTorrentUrl(anyString(), anyString(), anyString());
    }

    // ── input validation ─────────────────────────────────────────────────────────────

    @Test
    void magnetsAndTorrentLinksAreAccepted() {
        assertEquals(HASH, TorrentDownloadService.validateUrlOrMagnet("magnet:?xt=urn:btih:" + HASH));
        assertEquals(HASH, TorrentDownloadService.validateUrlOrMagnet(
                "magnet:?dn=Name&xt=urn:btih:" + HASH.toUpperCase() + "&tr=udp://tracker:80"));
        // Base32 hashes are still in the wild.
        assertNotNull(TorrentDownloadService.validateUrlOrMagnet(
                "magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"));
        // An http link to a .torrent has no hash to extract yet.
        assertNull(TorrentDownloadService.validateUrlOrMagnet("https://archive.org/download/item/item.torrent"));
    }

    @Test
    void badLinksAreRefused() {
        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateUrlOrMagnet(""));
        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateUrlOrMagnet("not a link"));
        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateUrlOrMagnet("ftp://example.com/x.torrent"));
        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateUrlOrMagnet("magnet:?xt=urn:btih:tooshort"));
        // Server-side request forgery: the same guard the yt-dlp path uses.
        assertThrows(RuntimeException.class,
                () -> TorrentDownloadService.validateUrlOrMagnet("http://127.0.0.1:36568/api/media"));
        assertThrows(RuntimeException.class,
                () -> TorrentDownloadService.validateUrlOrMagnet("http://192.168.1.4/x.torrent"));
    }

    @Test
    void onlyPlausibleTorrentFilesAreAccepted() {
        TorrentDownloadService.validateTorrentFile("d8:announce...e".getBytes());

        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateTorrentFile("<html>nope</html>".getBytes()));
        assertThrows(TorrentDownloadService.TorrentRejected.class,
                () -> TorrentDownloadService.validateTorrentFile(new byte[2 * 1024 * 1024]));
    }

    // ── delete symmetry ──────────────────────────────────────────────────────────────

    @Test
    void deletingAnOrdinaryUploadTouchesNoTorrent() {
        when(torrentDatabase.findByMediaFileId(42)).thenReturn(Optional.empty());

        service.onMediaDeleted(mediaFile(42));

        verifyNoInteractions(qbittorrent);
        verify(torrentDatabase, never()).deleteImportedFile(anyString(), anyInt());
    }

    @Test
    void deletingOneFileOfSeveralKeepsTheTorrentSeeding() throws Exception {
        when(torrentDatabase.findByMediaFileId(42))
                .thenReturn(Optional.of(new TorrentDownload.ImportedFile(HASH, 0, 42, "music/a.mp3")));
        when(torrentDatabase.findImportedFiles(HASH))
                .thenReturn(List.of(new TorrentDownload.ImportedFile(HASH, 1, 43, "music/b.mp3")));

        service.onMediaDeleted(mediaFile(42));

        verify(torrentDatabase).deleteImportedFile(HASH, 0);
        verify(qbittorrent, never()).delete(anyString(), anyBoolean());
        verify(torrentDatabase, never()).delete(anyString());
    }

    @Test
    void deletingTheLastFileRemovesTheTorrentAndItsData() throws Exception {
        when(torrentDatabase.findByMediaFileId(42))
                .thenReturn(Optional.of(new TorrentDownload.ImportedFile(HASH, 0, 42, "music/a.mp3")));
        when(torrentDatabase.findImportedFiles(HASH)).thenReturn(List.of());

        service.onMediaDeleted(mediaFile(42));

        verify(torrentDatabase).deleteImportedFile(HASH, 0);
        verify(qbittorrent).delete(HASH, true);
        verify(torrentDatabase).delete(HASH);
    }

    @Test
    void onlyTheOwnerCanRemoveATorrent() throws Exception {
        TorrentDownload download = new TorrentDownload(HASH, "tag", "magnet:...", "Some Torrent", 7, null, false);
        when(torrentDatabase.find(HASH)).thenReturn(Optional.of(download));

        assertFalse(service.remove(HASH, 99), "another user must not be able to cancel it");
        verify(qbittorrent, never()).delete(anyString(), anyBoolean());

        assertTrue(service.remove(HASH, 7));
        verify(qbittorrent).delete(HASH, true);
        verify(torrentDatabase).delete(HASH);
    }

    private MediaFile mediaFile(int id) {
        MediaFile file = new MediaFile();
        file.setId(id);
        return file;
    }
}
