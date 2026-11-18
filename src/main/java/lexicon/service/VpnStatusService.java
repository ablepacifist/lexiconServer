package lexicon.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Locale;

/**
 * Reports the address of the VPN tunnel this machine is currently using.
 *
 * This is the safety check for torrent downloads. Proton VPN runs in split-tunnel mode here
 * with no kill switch — a machine-wide one would take the website, the APIs and remote
 * access down with it — so what keeps aragorn's real IP off the trackers is qBittorrent
 * being bound to the tunnel address. Comparing that binding against the live adapter is how
 * the server knows it is safe to download.
 *
 * Read straight from the network interfaces rather than from the VPN client, so it reflects
 * what the adapter actually has right now.
 */
@Service
public class VpnStatusService {

    @Value("${vpn.interface-name:ProtonVPN}")
    private String interfaceName;

    /**
     * The tunnel's IPv4 address, or null when there is no tunnel.
     *
     * The address changes whenever Proton reconnects, which is why nothing caches it.
     */
    public String tunnelAddress() {
        String wanted = interfaceName == null ? "" : interfaceName.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return null;
        }
        try {
            for (NetworkInterface candidate : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!matches(candidate, wanted) || !candidate.isUp()) {
                    continue;
                }
                for (InetAddress address : Collections.list(candidate.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Could not read network interfaces: " + e.getMessage());
        }
        return null;
    }

    public boolean connected() {
        return tunnelAddress() != null;
    }

    /** Windows reports the friendly name as the display name; Linux only has the short name. */
    private boolean matches(NetworkInterface candidate, String wanted) {
        String name = candidate.getName() == null ? "" : candidate.getName().toLowerCase(Locale.ROOT);
        String display = candidate.getDisplayName() == null
                ? "" : candidate.getDisplayName().toLowerCase(Locale.ROOT);
        return name.contains(wanted) || display.contains(wanted);
    }
}
