package lexicon.service;

import lexicon.config.StorageProperties;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL comprehensive tests for OptimizedFileStorageService
 */
class OptimizedFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageProperties storageProperties;
    private OptimizedFileStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        // Setup storage properties with temp directory
        storageProperties = new StorageProperties();
        storageProperties.setBasePath(tempDir.toString());
        
        // Initialize service
        storageService = new OptimizedFileStorageService();
        
        // Inject storage properties using reflection
        var field = OptimizedFileStorageService.class.getDeclaredField("storageProperties");
        field.setAccessible(true);
        field.set(storageService, storageProperties);
        
        // Create required directory structure
        Files.createDirectories(tempDir.resolve("audiobooks"));
        Files.createDirectories(tempDir.resolve("music"));  
        Files.createDirectories(tempDir.resolve("videos"));
        Files.createDirectories(tempDir.resolve("temp"));
        Files.createDirectories(tempDir.resolve("backups"));
    }

    @Test
    void testStoreSmallAudioFile() throws IOException {
        // Create test audio content (small file)
        byte[] audioContent = new byte[1024]; // 1KB
        Arrays.fill(audioContent, (byte) 0x55); // Fill with test pattern
        
        MockMultipartFile audioFile = new MockMultipartFile(
            "file",
            "test-audio.mp3", 
            "audio/mpeg",
            audioContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Test Audio");
        mediaFile.setMediaType(MediaType.MUSIC);
        
        // Store the file
        String relativePath = storageService.storeFile(audioFile, mediaFile);
        
        // Verify file was stored
        assertNotNull(relativePath);
        Path storedFile = tempDir.resolve(relativePath);
        assertTrue(Files.exists(storedFile), "File should exist at: " + storedFile);
        assertEquals(1024L, Files.size(storedFile), "File size should match");
        
        // Verify content integrity
        byte[] storedContent = Files.readAllBytes(storedFile);
        assertArrayEquals(audioContent, storedContent, "File content should match exactly");
        
        // Verify it's in correct directory structure (should be in music or audiobooks)
        assertTrue(relativePath.contains("music") || relativePath.contains("audiobooks"),
                   "Audio file should be in music or audiobooks directory");
    }

    @Test
    void testStoreLargeVideoFile() throws IOException {
        // Create large video content (simulate large file)
        byte[] videoContent = new byte[50 * 1024]; // 50KB (simulating large)
        Arrays.fill(videoContent, (byte) 0xAA);
        
        MockMultipartFile videoFile = new MockMultipartFile(
            "file",
            "large-video.mp4",
            "video/mp4", 
            videoContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Large Video Test");
        mediaFile.setMediaType(MediaType.AUDIOBOOK); // Testing type mapping
        
        // Store the file
        String relativePath = storageService.storeFile(videoFile, mediaFile);
        
        // Verify storage
        assertNotNull(relativePath);
        Path storedFile = tempDir.resolve(relativePath);
        assertTrue(Files.exists(storedFile));
        assertEquals(videoContent.length, Files.size(storedFile));
        
        // Verify in videos directory
        assertTrue(relativePath.contains("videos") || relativePath.contains("audiobooks"));
    }

    @Test 
    void testFileInputStream() throws IOException {
        // Create and store a test file first
        byte[] testContent = "Test file content for input stream".getBytes();
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "stream-test.txt", "text/plain", testContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Stream Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(testFile, mediaFile);
        
        // Test getting input stream
        try (InputStream inputStream = storageService.getFileInputStream(relativePath)) {
            assertNotNull(inputStream);
            
            byte[] readContent = inputStream.readAllBytes();
            assertArrayEquals(testContent, readContent);
        }
    }

    @Test
    void testFileStreaming() throws IOException {
        // Create test file for streaming
        byte[] streamContent = new byte[2048]; // 2KB
        for (int i = 0; i < streamContent.length; i++) {
            streamContent[i] = (byte) (i % 256);
        }
        
        MockMultipartFile streamFile = new MockMultipartFile(
            "file", "streaming-test.mp4", "video/mp4", streamContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Streaming Test");
        mediaFile.setMediaType(MediaType.AUDIOBOOK);
        
        String relativePath = storageService.storeFile(streamFile, mediaFile);
        
        // Test streaming with range
        long rangeStart = 100;
        long rangeEnd = 199; // 100 bytes
        
        OptimizedFileStorageService.FileStreamInfo streamInfo = 
            storageService.getFileForStreaming(relativePath, rangeStart, rangeEnd);
        
        assertNotNull(streamInfo);
        // The caller owns the open file: close it, or Windows cannot delete the temp folder afterwards
        try (java.io.RandomAccessFile file = streamInfo.getFile()) {
            assertEquals(100L, streamInfo.getContentLength());
            assertEquals(rangeStart, streamInfo.getRangeStart());
            assertEquals(rangeEnd, streamInfo.getRangeEnd());

            // Verify we can read the range
            assertNotNull(file);
            assertEquals(rangeStart, file.getFilePointer());
        }
    }

    @Test
    void testDeleteFile() throws IOException {
        // Create test file
        byte[] deleteContent = "File to be deleted".getBytes();
        MockMultipartFile deleteFile = new MockMultipartFile(
            "file", "delete-me.txt", "text/plain", deleteContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Delete Me");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(deleteFile, mediaFile);
        
        // Verify file exists
        assertTrue(Files.exists(tempDir.resolve(relativePath)));
        
        // Delete the file
        boolean deleted = storageService.deleteFile(relativePath);
        
        assertTrue(deleted, "Delete operation should return true");
        assertFalse(Files.exists(tempDir.resolve(relativePath)), "File should no longer exist");
    }

    @Test
    void testGetFileSize() throws IOException {
        // Create file with known size
        byte[] sizeTestContent = new byte[1337]; // Specific size
        Arrays.fill(sizeTestContent, (byte) 0x42);
        
        MockMultipartFile sizeFile = new MockMultipartFile(
            "file", "size-test.bin", "application/octet-stream", sizeTestContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Size Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(sizeFile, mediaFile);
        
        // Test size retrieval
        long retrievedSize = storageService.getFileSize(relativePath);
        assertEquals(1337L, retrievedSize, "Retrieved size should match stored size");
    }

    @Test
    void testFileExists() throws IOException {
        // Create test file
        MockMultipartFile existsFile = new MockMultipartFile(
            "file", "exists-test.txt", "text/plain", "existence test".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Exists Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(existsFile, mediaFile);
        
        // Test existence check
        assertTrue(storageService.fileExists(relativePath), "File should exist");
        assertFalse(storageService.fileExists("nonexistent/file.txt"), "Non-existent file should return false");
    }

    @Test
    void testMoveFile() throws IOException {
        // Create source file
        MockMultipartFile sourceFile = new MockMultipartFile(
            "file", "move-source.txt", "text/plain", "content to move".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Move Source");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String sourcePath = storageService.storeFile(sourceFile, mediaFile);
        
        // Move file
        String destinationPath = "music/moved-file.txt";
        long fileSize = storageService.getFileSize(sourcePath);
        
        String newPath = storageService.moveFileToPath(sourcePath, destinationPath, fileSize);
        
        assertEquals(destinationPath, newPath);
        assertFalse(storageService.fileExists(sourcePath), "Source file should no longer exist");
        assertTrue(storageService.fileExists(destinationPath), "Destination file should exist");
        assertEquals(fileSize, storageService.getFileSize(destinationPath), "File size should be preserved");
    }

    @Test
    void testGenerateChecksum() throws IOException {
        // Create file with known content for checksum
        String knownContent = "Hello, World! This is a checksum test.";
        MockMultipartFile checksumFile = new MockMultipartFile(
            "file", "checksum-test.txt", "text/plain", knownContent.getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Checksum Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(checksumFile, mediaFile);
        
        // Generate checksum
        String checksum1 = storageService.generateChecksum(relativePath);
        String checksum2 = storageService.generateChecksum(relativePath);
        
        assertNotNull(checksum1);
        assertEquals(32, checksum1.length(), "MD5 checksum should be 32 characters");
        assertTrue(checksum1.matches("[a-f0-9]+"), "Checksum should be lowercase hex");
        assertEquals(checksum1, checksum2, "Same file should produce same checksum");
    }

    @Test
    void testFilenameSanitization() throws IOException {
        // Test with problematic filename
        MockMultipartFile problematicFile = new MockMultipartFile(
            "file", "test file with spaces & symbols!@#$.txt", "text/plain", "test content".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Problematic / Filename \\ with | symbols");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(problematicFile, mediaFile);
        
        // Verify file was stored and path is sanitized
        assertNotNull(relativePath);
        assertTrue(storageService.fileExists(relativePath));
        
        // Verify filename doesn't contain problematic characters (the relative path uses the OS separator)
        String filename = java.nio.file.Paths.get(relativePath).getFileName().toString();
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("\\"));
        assertFalse(filename.contains("|"));
    }

    @Test
    void testLargeFileStreamingStorage() throws IOException {
        // Create larger file that triggers streaming storage
        byte[] largeContent = new byte[128 * 1024]; // 128KB
        Arrays.fill(largeContent, (byte) 0xFF);
        
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large-streaming.bin", "application/octet-stream", largeContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Large Streaming File");
        mediaFile.setMediaType(MediaType.OTHER);
        
        // Force large file threshold to be small for testing
        storageProperties.setLargeFileThreshold(64 * 1024); // 64KB threshold
        
        String relativePath = storageService.storeFile(largeFile, mediaFile);
        
        // Verify large file was stored correctly
        assertTrue(storageService.fileExists(relativePath));
        assertEquals(largeContent.length, storageService.getFileSize(relativePath));
        
        // Verify content integrity for large file
        try (InputStream stream = storageService.getFileInputStream(relativePath)) {
            byte[] retrievedContent = stream.readAllBytes();
            assertArrayEquals(largeContent, retrievedContent);
        }
    }

    // ── toWslPath ───────────────────────────────────────────────────────────────────
    // This is what turns the Windows path qBittorrent reports into the path `wsl ln` needs
    // when a torrent is hardlinked into the library. It cannot be exercised without
    // qBittorrent running, so it is pinned here instead.

    @Test
    void mapsTheUncMediaPathIntoTheDistro() {
        assertEquals("/mnt/wsl/PHYSICALDRIVE1p2/lexicon-storage/music/original/song.mp3",
                OptimizedFileStorageService.toWslPath(Path.of(
                        "//wsl.localhost/Ubuntu/mnt/wsl/PHYSICALDRIVE1p2/lexicon-storage/music/original/song.mp3")));
    }

    @Test
    void mapsTheUncPathWhicheverSlashesItArrivesWith() {
        // qBittorrent answers with Windows separators even though it was given forward ones.
        assertEquals("/mnt/wsl/PHYSICALDRIVE1p2/lexicon-storage/torrents/complete",
                OptimizedFileStorageService.toWslPath(Path.of(
                        "\\\\wsl.localhost\\Ubuntu\\mnt\\wsl\\PHYSICALDRIVE1p2\\lexicon-storage\\torrents\\complete")));
    }

    @Test
    void ignoresTheCaseOfTheUncHost() {
        assertEquals("/mnt/wsl/x",
                OptimizedFileStorageService.toWslPath(Path.of("//WSL.localhost/Ubuntu/mnt/wsl/x")));
    }

    @Test
    void mapsADriveLetterToItsMountPoint() {
        assertEquals("/mnt/c/Users/HP/file.mp3",
                OptimizedFileStorageService.toWslPath(Path.of("C:/Users/HP/file.mp3")));
    }

    @Test
    void refusesAPathWithNoDistroAfterTheHost() {
        // Nothing to link into: the caller must fall back to a move rather than run `ln`
        // against a path that does not exist inside WSL.
        assertNull(OptimizedFileStorageService.toWslPath(Path.of("//wsl.localhost/Ubuntu")));
    }

    // ── Two media drives ─────────────────────────────────────────────────────────────

    /** A service over two drives (folders), with WSL reporting the given free bytes for each. */
    private OptimizedFileStorageService twoDrives(Path drive1, Path drive2, long free1, long free2) throws Exception {
        Files.createDirectories(drive1);
        Files.createDirectories(drive2);
        StorageProperties props = new StorageProperties();
        props.setBasePath(drive1.toString());
        props.setAdditionalPaths(java.util.List.of(drive2.toString()));

        WslDiskUsageService disks = org.mockito.Mockito.mock(WslDiskUsageService.class);
        org.mockito.Mockito.when(disks.listPhysicalDriveVolumes()).thenReturn(java.util.List.of(
                new WslDiskUsageService.Volume(drive1.toString().replace('\\', '/'), 1, 2, free1, 0, free1),
                new WslDiskUsageService.Volume(drive2.toString().replace('\\', '/'), 2, 1, free2, 0, free2)));

        OptimizedFileStorageService service = new OptimizedFileStorageService();
        var p = OptimizedFileStorageService.class.getDeclaredField("storageProperties");
        p.setAccessible(true);
        p.set(service, props);
        var d = OptimizedFileStorageService.class.getDeclaredField("wslDiskUsageService");
        d.setAccessible(true);
        d.set(service, disks);
        return service;
    }

    private static MockMultipartFile song(String name) {
        return new MockMultipartFile("file", name, "audio/mpeg", new byte[]{1, 2, 3, 4});
    }

    private static MediaFile songRow(String title) {
        MediaFile row = new MediaFile();
        row.setTitle(title);
        row.setMediaType(MediaType.MUSIC);
        return row;
    }

    @Test
    void aNewFileGoesToTheDriveWithMoreFreeSpace() throws Exception {
        OptimizedFileStorageService service = twoDrives(tempDir.resolve("d1"), tempDir.resolve("d2"), 100, 1_000_000);

        String stored = service.storeFile(song("a.mp3"), songRow("a"));

        assertTrue(stored.startsWith("v2:"), "path on the second drive carries its number: " + stored);
        Path onDisk = tempDir.resolve("d2").resolve(stored.substring(3));
        assertTrue(Files.exists(onDisk));
        assertTrue(service.fileExists(stored));
        assertEquals(4, service.getFileSize(stored));
        try (InputStream in = service.getFileInputStream(stored)) {
            assertArrayEquals(new byte[]{1, 2, 3, 4}, in.readAllBytes());
        }
        assertTrue(service.deleteFile(stored));
        assertFalse(Files.exists(onDisk));
    }

    @Test
    void theFirstDriveIsUsedWhileItHasMoreFreeSpace() throws Exception {
        OptimizedFileStorageService service = twoDrives(tempDir.resolve("d1"), tempDir.resolve("d2"), 1_000_000, 100);

        String stored = service.storeFile(song("b.mp3"), songRow("b"));

        assertFalse(stored.startsWith("v2:"), "volume 1 paths keep the old unprefixed form: " + stored);
        assertTrue(Files.exists(tempDir.resolve("d1").resolve(stored)));
    }

    @Test
    void pathsWrittenBeforeTheSecondDriveStillReadFromTheFirst() throws Exception {
        Path drive1 = tempDir.resolve("d1");
        Files.createDirectories(drive1.resolve("music/compressed"));
        Files.write(drive1.resolve("music/compressed/old.mp3"), new byte[]{9, 9});
        OptimizedFileStorageService service = twoDrives(drive1, tempDir.resolve("d2"), 100, 1_000_000);

        assertEquals(2, service.getFileSize("music/compressed/old.mp3"));
        assertEquals(2, service.getFileSize("music\\compressed\\old.mp3".replace('\\', java.io.File.separatorChar)));
    }

    @Test
    void markersKeepEachDriveItsNumberWhenTheConfigurationSwaps() throws Exception {
        Path drive1 = tempDir.resolve("d1");
        Path drive2 = tempDir.resolve("d2");
        String stored = twoDrives(drive1, drive2, 100, 1_000_000).storeFile(song("c.mp3"), songRow("c"));
        assertTrue(stored.startsWith("v2:"));
        assertEquals("2", Files.readString(drive2.resolve(OptimizedFileStorageService.VOLUME_MARKER)).trim());

        // Windows renumbered the disks: the base path now names the drive that was volume 2.
        OptimizedFileStorageService swapped = twoDrives(drive2, drive1, 100, 1_000_000);

        assertTrue(swapped.fileExists(stored), "a v2: path must still find its file on the same drive");
        assertEquals(drive1.toAbsolutePath(), swapped.primaryVolume().root().toAbsolutePath());
    }

    @Test
    void anUnmountedDriveMeansNotFoundRatherThanAnotherFile() throws Exception {
        Path drive1 = tempDir.resolve("d1");
        OptimizedFileStorageService service = twoDrives(drive1, tempDir.resolve("d2"), 100, 1_000_000);
        Files.createDirectories(drive1.resolve("music"));
        Files.write(drive1.resolve("music/same.mp3"), new byte[]{1});

        assertFalse(service.fileExists("v3:music/same.mp3"));
    }

    @Test
    void theStorageCheckSeesBothDrivesAndDeletesOnTheRightOne() throws Exception {
        Path drive1 = tempDir.resolve("d1");
        Path drive2 = tempDir.resolve("d2");
        OptimizedFileStorageService service = twoDrives(drive1, drive2, 100, 1_000_000);
        Files.createDirectories(drive1.resolve("music"));
        Files.write(drive1.resolve("music/one.mp3"), new byte[]{1});
        Files.createDirectories(drive2.resolve("music"));
        Files.write(drive2.resolve("music/two.mp3"), new byte[]{1, 2});

        var listed = service.listStoredFiles(java.util.List.of("music"));
        var paths = listed.stream().map(OptimizedFileStorageService.StoredFile::relativePath).sorted().toList();
        assertEquals(java.util.List.of("music/one.mp3", "v2:music/two.mp3"), paths);

        assertEquals(-1, service.deleteStoredFile("v2:music/two.mp3", java.util.List.of("videos")), "outside the allowed folders");
        assertEquals(2, service.deleteStoredFile("v2:music/two.mp3", java.util.List.of("music")));
        assertFalse(Files.exists(drive2.resolve("music/two.mp3")));
        assertTrue(Files.exists(drive1.resolve("music/one.mp3")));
    }

    @Test
    void anImportedTorrentStaysOnTheDriveItDownloadedTo() throws Exception {
        Path drive1 = tempDir.resolve("d1");
        OptimizedFileStorageService service = twoDrives(drive1, tempDir.resolve("d2"), 100, 1_000_000);
        Path downloaded = drive1.resolve("torrents/complete/show.mkv");
        Files.createDirectories(downloaded.getParent());
        Files.write(downloaded, new byte[]{7});

        // No WSL in the test, so the hard link falls back to a move; either way it must not cross drives.
        var result = service.linkFileIntoStorage(downloaded, "show.mkv", 1, MediaType.VIDEO);

        assertFalse(result.relativePath().startsWith("v2:"), "stayed on volume 1: " + result.relativePath());
        assertTrue(Files.exists(drive1.resolve(result.relativePath())));
    }

    @Test
    void chatUploadsUseTheFreerDriveAndAreFoundThere() throws Exception {
        OptimizedFileStorageService service = twoDrives(tempDir.resolve("d1"), tempDir.resolve("d2"), 100, 1_000_000);
        lexicon.data.IChatFileDatabase db = org.mockito.Mockito.mock(lexicon.data.IChatFileDatabase.class);
        lexicon.logic.ChatFileManager chat = new lexicon.logic.ChatFileManager(db, service);
        byte[] png = pngBytes();

        chat.uploadChatFile(png, "pic.png", "image/png", 1, null);

        var saved = org.mockito.ArgumentCaptor.forClass(lexicon.object.ChatFile.class);
        org.mockito.Mockito.verify(db).addChatFile(saved.capture());
        Path onDrive2 = tempDir.resolve("d2/chat-uploads").resolve(saved.getValue().getStoredFilename());
        assertTrue(Files.exists(onDrive2));

        org.mockito.Mockito.when(db.getChatFile(5L)).thenReturn(saved.getValue());
        assertEquals(onDrive2, chat.getFileForServing(5L).getPath());
    }

    private static byte[] pngBytes() throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
