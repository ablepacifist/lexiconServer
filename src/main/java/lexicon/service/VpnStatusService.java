package lexicon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports the address of the VPN tunnel this machine is currently using.
 *
 * This is the safety check for torrent downloads. Proton VPN runs in split-tunnel mode here,
 * so what keeps aragorn's real IP off the trackers is qBittorrent being bound to the tunnel
 * address. Comparing that binding against the live adapter is how the server knows it is safe
 * to download.
 *
 * Read straight from the network interfaces rather than from the VPN client, so it reflects
 * what the adapter actually has right now.
 *
 * <h2>Why the name is a list</h2>
 * The JVM does not expose Windows' friendly connection name — the one shown in Network
 * Connections and by {@code Get-NetAdapter}. {@link NetworkInterface#getName()} gives a
 * synthetic id ({@code iftype53_32768}) and {@link NetworkInterface#getDisplayName()} gives
 * the <em>driver description</em>, which for Proton's WireGuard adapter is the generic
 * "WireGuard Tunnel" and has the string "Proton" nowhere in it. Matching on "ProtonVPN"
 * alone therefore never matched on Windows, and the gate reported the VPN down while it was
 * up. The candidates are a comma-separated list so the Windows description, the Linux
 * interface name and the OpenVPN adapter can all be covered at once.
 */
@Service
public class VpnStatusService {

    private static final Logger log = LoggerFactory.getLogger(VpnStatusService.class);

    /**
     * Comma-separated substrings; an interface matches if any of them appears in its name or
     * its description. "WireGuard Tunnel" is what Windows reports for Proton's WireGuard
     * adapter, "ProtonVPN" what Linux calls its interface, "TAP-ProtonVPN" the OpenVPN one.
     */
    @Value("${vpn.interface-name:ProtonVPN,WireGuard Tunnel,TAP-ProtonVPN,proton0}")
    private String interfaceNames;

    /**
     * What the last failed lookup saw, so the warning is logged on a change rather than on
     * every poll — the status endpoint is called once a second while the page is open.
     */
    private final AtomicReference<String> lastMiss = new AtomicReference<>();

    /**
     * The tunnel's IPv4 address, or null when there is no tunnel.
     *
     * The address changes whenever Proton reconnects, which is why nothing caches it.
     */
    public String tunnelAddress() {
        List<String> wanted = candidates();
        if (wanted.isEmpty()) {
            return null;
        }
        List<String> seen = new ArrayList<>();
        try {
            for (NetworkInterface candidate : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!candidate.isUp() || candidate.isLoopback()) {
                    continue;
                }
                String address = firstIpv4(candidate);
                if (address == null) {
                    continue;
                }
                if (matches(candidate, wanted)) {
                    lastMiss.set(null);
                    return address;
                }
                seen.add(candidate.getName() + " / " + candidate.getDisplayName() + " (" + address + ")");
            }
        } catch (SocketException e) {
            log.warn("Could not read network interfaces, treating the VPN as down: {}", e.getMessage());
            return null;
        }
        reportMiss(wanted, seen);
        return null;
    }

    public boolean connected() {
        return tunnelAddress() != null;
    }

    /**
     * Logged so that "the VPN isn't connected" when it plainly is can be diagnosed from the
     * log instead of from the network stack: it prints what was looked for against every live
     * adapter that was there to match.
     */
    private void reportMiss(List<String> wanted, List<String> seen) {
        String summary = String.join("; ", seen);
        if (!summary.equals(lastMiss.getAndSet(summary))) {
            log.warn("No VPN adapter matched {} — live adapters are: {}", wanted,
                    summary.isEmpty() ? "none with an IPv4 address" : summary);
        }
    }

    private List<String> candidates() {
        return parseCandidates(interfaceNames);
    }

    /** Splits the configured list into lower-cased substrings. Package-private for the test. */
    static List<String> parseCandidates(String configured) {
        List<String> wanted = new ArrayList<>();
        for (String part : (configured == null ? "" : configured).split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                wanted.add(trimmed);
            }
        }
        return wanted;
    }

    /** The default from {@code application.properties}, so the test checks the real value. */
    static final String DEFAULT_INTERFACE_NAMES = "ProtonVPN,WireGuard Tunnel,TAP-ProtonVPN,proton0";

    private String firstIpv4(NetworkInterface candidate) {
        for (InetAddress address : Collections.list(candidate.getInetAddresses())) {
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }
        }
        return null;
    }

    /**
     * Windows gives the driver description as the display name and a synthetic id as the
     * name; Linux gives the real interface name as both. Both are checked, so one list of
     * candidates covers either.
     */
    private boolean matches(NetworkInterface candidate, List<String> wanted) {
        return matchesAny(candidate.getName(), candidate.getDisplayName(), wanted);
    }

    /**
     * The matching rule on its own, so it can be tested against the names the JVM really
     * reports without a VPN having to be up. Package-private for that reason only.
     *
     * @param wanted already lower-cased, as {@link #candidates()} returns them
     */
    static boolean matchesAny(String name, String displayName, List<String> wanted) {
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String lowerDisplay = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        for (String want : wanted) {
            if (lowerName.contains(want) || lowerDisplay.contains(want)) {
                return true;
            }
        }
        return false;
    }
}
