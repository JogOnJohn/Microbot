package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.*;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PohHouseConfigurationTest {
    private static Map<String, String> detected() {
        Map<String, String> values = new HashMap<>();
        values.put("pohExitPortalTile", "1859, 7052, 0");
        values.put("jewelleryBox", "ORNATE");
        values.put("Portal Room", "KHARYRLL");
        values.put("Nexus Teleport", "KOUREND,VARROCK,VARROCK_GE,CIVITAS_ILLA_FORTIS,LUMBRIDGE,FALADOR,CAMELOT,ARDOUGNE,WATCHTOWER,SENNTISTEN,MARIM,KHARYRLL,CARRALLANGER,WATERBIRTH,ARCEUUS_LIBRARY,DRAYNOR_MANOR,BATTLEFRONT,MIND_ALTAR,SALVE_GRAVEYARD,FENKENSTRAINS_CASTLE,WEST_ARDOUGNE,HARMONY_ISLAND,CEMETERY,LASSAR");
        values.put("mountedDigsite", "true");
        values.put("mountedXerics", "false");
        values.put("fairyRing", "true");
        values.put("spiritTree", "true");
        return values;
    }

    @Test public void detectedPersonalFacilitiesDoNotBorrowAMountedXerics() {
        PohHouseConfiguration house = PohHouseConfiguration.read(detected()::get);
        assertEquals(new WorldPoint(1859, 7052, 0), house.exitPortal());
        Set<PohTeleport> teleports = house.teleports(true);
        assertEquals(24, teleports.stream().filter(t -> t instanceof NexusPortal).count());
        assertTrue(teleports.containsAll(java.util.Arrays.asList(MountedDigsite.values())));
        assertTrue(teleports.stream().anyMatch(t -> t instanceof JewelleryBox));
        assertFalse(teleports.stream().anyMatch(t -> t instanceof MountedXerics));
        assertFalse(teleports.stream().anyMatch(t -> t instanceof MountedGlory));
        assertTrue(house.enabled("fairyRing"));
        assertTrue(house.enabled("spiritTree"));
        assertFalse(house.teleports(false).stream().anyMatch(t -> t instanceof NexusPortal));
    }

    @Test public void missingOrMalformedDetectionNeverInventsFacilities() {
        assertNull(PohHouseConfiguration.read(key -> null).exitPortal());
        assertTrue(PohHouseConfiguration.read(key -> null).teleports(true).isEmpty());
        for (String tile : new String[]{"", "bad", "1,2", "1,2,9", "-1,2,0"}) {
            Map<String, String> values = Map.of("pohExitPortalTile", tile, "Nexus Teleport", "UNKNOWN_DESTINATION");
            PohHouseConfiguration house = PohHouseConfiguration.read(values::get);
            assertNull(house.exitPortal());
            assertTrue(house.teleports(true).isEmpty());
        }
    }

    @Test public void eachDetectionSettingChangesTheSnapshotFingerprint() {
        for (String key : PohHouseConfiguration.KEYS) {
            Map<String, String> values = detected();
            int before = PohHouseConfiguration.read(values::get).fingerprint();
            values.put(key, "changed");
            assertNotEquals(key, before, PohHouseConfiguration.read(values::get).fingerprint());
        }
    }

    @Test public void transportsUsePersistedDetectionWithoutAnOpenSwingPanel() {
        ConfigManager manager = mock(ConfigManager.class);
        Map<String, String> values = Map.of("pohExitPortalTile", "1859, 7052, 0", "Nexus Teleport", "VARROCK", "mountedXerics", "false");
        when(manager.getConfiguration(eq(ShortestPathPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(inv -> values.get((String) inv.getArgument(1)));
        PohPanel previous = PohPanel.instance;
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getConfigManager).thenReturn(manager);
            PohPanel.instance = null;
            Map<WorldPoint, Set<Transport>> map = PohPanel.getAvailableTransports(Map.of(), true);
            Set<Transport> exits = map.get(new WorldPoint(1859, 7052, 0));
            assertNotNull(exits);
            assertEquals(1, exits.size());
            PohTransport exit = (PohTransport) exits.iterator().next();
            assertEquals(NexusPortal.VARROCK, exit.getTeleport());
            assertFalse(exit.isHostedHouse());
            assertEquals(new WorldPoint(1859, 7052, 0), PohPanel.getExitPortalTile());
        } finally { PohPanel.instance = previous; }
    }

    @Test public void personalAndHostedTransportProvenanceStaySeparate() {
        WorldPoint anchor = new WorldPoint(1859, 7052, 0);
        assertFalse(new PohTransport(anchor, NexusPortal.VARROCK).isHostedHouse());
        assertTrue(new PohTransport(anchor, NexusPortal.VARROCK, true).isHostedHouse());
    }

    @Test public void importedFacilitiesCannotBypassPersonalDetectionButExitsRemainAvailable() {
        Map<WorldPoint, Set<Transport>> catalog = Transport.loadAllFromResources();
        WorldPoint legacyHouse = new WorldPoint(1858, 7051, 0);
        Set<Transport> fromHouse = catalog.get(legacyHouse);
        assertNotNull(fromHouse);
        assertTrue(fromHouse.stream().anyMatch(PohHouseConfiguration::isImportedFacility));
        assertTrue(fromHouse.stream().filter(t -> t.getType() == TransportType.TELEPORTATION_PORTAL)
            .filter(t -> !"Home Portal".equalsIgnoreCase(t.getDisplayInfo())).allMatch(PohHouseConfiguration::isImportedFacility));
        long exits = fromHouse.stream().filter(t -> "Home Portal".equalsIgnoreCase(t.getDisplayInfo())).count();
        assertTrue(exits > 0);
        assertTrue(fromHouse.stream().filter(t -> "Home Portal".equalsIgnoreCase(t.getDisplayInfo()))
            .noneMatch(PohHouseConfiguration::isImportedFacility));
        assertTrue(fromHouse.stream().filter(t -> t.getType() == TransportType.FAIRY_RING || t.getType() == TransportType.SPIRIT_TREE)
            .allMatch(PohHouseConfiguration::isImportedFacility));
        Transport outside = new Transport(new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3201, 0),
            "Ordinary portal", TransportType.TELEPORTATION_PORTAL, true, 1);
        assertFalse(PohHouseConfiguration.isImportedFacility(outside));
        Transport houseEntry = new Transport(null, legacyHouse, "Teleport to House (Inside)",
            TransportType.TELEPORTATION_SPELL, true, 1);
        assertFalse(PohHouseConfiguration.isImportedFacility(houseEntry));
    }
}
