package lexicon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adapter-matching rule behind the torrent VPN gate.
 *
 * These names are not invented: they are what {@code NetworkInterface} actually reports on
 * aragorn, captured while Proton VPN was connected. The gate used to look for "ProtonVPN"
 * and nothing else, which matches neither the synthetic name nor the driver description
 * Windows hands the JVM, so the server reported "Proton VPN isn't connected" with the
 * tunnel plainly up at 10.2.0.2.
 */
class VpnStatusServiceTest {

    private static final List<String> DEFAULTS =
            VpnStatusService.parseCandidates(VpnStatusService.DEFAULT_INTERFACE_NAMES);

    @Test
    @DisplayName("matches Proton's WireGuard adapter as Windows reports it to the JVM")
    void matchesWindowsWireGuardAdapter() {
        // Friendly name "ProtonVPN" is invisible to Java; this is all it is given.
        assertTrue(VpnStatusService.matchesAny("iftype53_32768", "WireGuard Tunnel", DEFAULTS));
    }

    @Test
    @DisplayName("matches Proton's OpenVPN adapter and the Linux interface name")
    void matchesOtherProtonAdapters() {
        assertTrue(VpnStatusService.matchesAny("eth7", "TAP-ProtonVPN Windows Adapter V9", DEFAULTS));
        assertTrue(VpnStatusService.matchesAny("proton0", "proton0", DEFAULTS));
    }

    @Test
    @DisplayName("does not match the adapters that carry the machine's real address")
    void ignoresRealAdapters() {
        assertFalse(VpnStatusService.matchesAny(
                "ethernet_32769", "Realtek USB GbE Family Controller", DEFAULTS));
        assertFalse(VpnStatusService.matchesAny(
                "ethernet_32775", "Hyper-V Virtual Ethernet Adapter", DEFAULTS));
        assertFalse(VpnStatusService.matchesAny(
                "wireless_32768", "Intel(R) Wi-Fi 6 AX200 160MHz", DEFAULTS));
        assertFalse(VpnStatusService.matchesAny("loopback_0", "Software Loopback Interface 1", DEFAULTS));
    }

    @Test
    @DisplayName("an empty or blank setting matches nothing, so the gate stays closed")
    void blankConfigurationMatchesNothing() {
        assertTrue(VpnStatusService.parseCandidates("").isEmpty());
        assertTrue(VpnStatusService.parseCandidates(" , , ").isEmpty());
        assertTrue(VpnStatusService.parseCandidates(null).isEmpty());
        assertFalse(VpnStatusService.matchesAny("iftype53_32768", "WireGuard Tunnel", List.of()));
    }

    @Test
    @DisplayName("the list is split, trimmed and case-insensitive")
    void parsesTheList() {
        List<String> parsed = VpnStatusService.parseCandidates(" ProtonVPN , WireGuard Tunnel ");
        assertTrue(parsed.contains("protonvpn"));
        assertTrue(parsed.contains("wireguard tunnel"));
        assertTrue(VpnStatusService.matchesAny("x", "wireguard tunnel", parsed));
        assertTrue(VpnStatusService.matchesAny("x", "WIREGUARD TUNNEL", parsed));
    }
}
