package lexicon.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Queries real disk usage for physical drives mounted into WSL, since Java's
 * Files.getFileStore() on a \\wsl.localhost\... UNC path resolves to the WSL
 * distro's own root filesystem rather than the actual mounted drive (Windows
 * has no visibility into WSL's internal Linux mount table).
 */
@Service
public class WslDiskUsageService {

    // /mnt/wsl/PHYSICALDRIVE<n>p<m> -- the convention every physical-drive mount follows
    private static final Pattern MOUNT_PATTERN =
            Pattern.compile("^/mnt/wsl/PHYSICALDRIVE(\\d+)p(\\d+)$");

    @Value("${lexicon.storage.wsl-distro:Ubuntu}")
    private String distro;

    public static class Volume {
        public final String mountPoint;
        public final int diskNumber;
        public final int partitionNumber;
        public final long totalBytes;
        public final long usedBytes;
        public final long freeBytes;

        Volume(String mountPoint, int diskNumber, int partitionNumber,
               long totalBytes, long usedBytes, long freeBytes) {
            this.mountPoint = mountPoint;
            this.diskNumber = diskNumber;
            this.partitionNumber = partitionNumber;
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.freeBytes = freeBytes;
        }
    }

    /**
     * Lists every ext4 physical-drive mount visible inside WSL, sorted by (disk, partition).
     * Returns an empty list (not an error) if none are currently mounted.
     */
    public List<Volume> listPhysicalDriveVolumes() throws IOException {
        List<String> command = List.of("wsl.exe", "-d", distro, "--",
                "df", "-B1", "--output=target,size,used,avail");

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to launch wsl.exe -- is WSL installed and on PATH? " + e.getMessage(), e);
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        boolean finished;
        try {
            finished = process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for wsl df", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timed out waiting for `wsl -d " + distro + " -- df` to respond");
        }
        if (process.exitValue() != 0) {
            throw new IOException("wsl df exited with code " + process.exitValue() + ": " + String.join(" | ", lines));
        }

        List<Volume> volumes = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // line 0 is the df header
            String line = lines.get(i).replace("\0", "").trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            if (parts.length != 4) continue;

            Matcher m = MOUNT_PATTERN.matcher(parts[0]);
            if (!m.matches()) continue; // not ours (/, /mnt/c, /mnt/wslg, drivers, etc.) -- skip cleanly

            try {
                volumes.add(new Volume(parts[0], Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Long.parseLong(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3])));
            } catch (NumberFormatException ignored) {
                // malformed row -- skip it, don't fail the whole request
            }
        }

        volumes.sort(Comparator.<Volume>comparingInt(v -> v.diskNumber).thenComparingInt(v -> v.partitionNumber));
        return volumes;
    }
}
