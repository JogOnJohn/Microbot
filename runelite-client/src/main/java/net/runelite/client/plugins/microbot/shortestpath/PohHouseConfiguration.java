package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.poh.data.*;

/** Persisted personal-house detection, independent of Swing panel lifetime. */
public final class PohHouseConfiguration {
    public static final Set<String> KEYS = Set.of("pohExitPortalTile", "Portal Room", "Nexus Teleport",
        "jewelleryBox", "mountedGlory", "mountedDigsite", "mountedXerics", "mountedMyths",
        "fairyRing", "spiritTree");
    private final Map<String, String> values;

    private PohHouseConfiguration(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static PohHouseConfiguration load() {
        ConfigManager manager = Microbot.getConfigManager();
        return read(key -> manager == null ? null : manager.getConfiguration(ShortestPathPlugin.CONFIG_GROUP, key));
    }

    static PohHouseConfiguration read(Function<String, String> reader) {
        Map<String, String> values = new HashMap<>();
        for (String key : KEYS) {
            String value = reader.apply(key);
            if (value != null) values.put(key, value.trim());
        }
        return new PohHouseConfiguration(values);
    }

    public int fingerprint() { return values.hashCode(); }
    public boolean enabled(String key) { return Boolean.parseBoolean(values.get(key)); }

    public static boolean isImportedFacility(Transport transport) {
        WorldPoint catalogHouse = new WorldPoint(1858, 7051, 0);
        boolean fromHouse = catalogHouse.equals(transport.getOrigin());
        boolean toHouse = catalogHouse.equals(transport.getDestination());
        TransportType type = transport.getType();
        if (type == TransportType.FAIRY_RING || type == TransportType.SPIRIT_TREE) return fromHouse || toHouse;
        if (!fromHouse) return false;
        // Keep the ordinary exit portal and all outside-world transport networks.
        return type == TransportType.WILDERNESS_OBELISK
            || type == TransportType.TELEPORTATION_PORTAL && !"Home Portal".equalsIgnoreCase(transport.getDisplayInfo())
                && !("Home".equalsIgnoreCase(transport.getAction()) && "Portal".equalsIgnoreCase(transport.getName()));
    }

    public WorldPoint exitPortal() {
        String value = values.get("pohExitPortalTile");
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int plane = Integer.parseInt(parts[2].trim());
            return x >= 0 && y >= 0 && plane >= 0 && plane <= 3 ? new WorldPoint(x, y, plane) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Set<PohTeleport> teleports(boolean useNexus) {
        Set<PohTeleport> result = new HashSet<>();
        addEnums(result, PohPortal.class, values.get("Portal Room"));
        if (useNexus) addEnums(result, NexusPortal.class, values.get("Nexus Teleport"));
        String box = values.get("jewelleryBox");
        if (box != null) {
            try { result.addAll(JewelleryBoxType.valueOf(box).getAvailableTeleports()); }
            catch (IllegalArgumentException ignored) { }
        }
        if (enabled("mountedGlory")) Collections.addAll(result, MountedGlory.values());
        if (enabled("mountedDigsite")) Collections.addAll(result, MountedDigsite.values());
        if (enabled("mountedXerics")) Collections.addAll(result, MountedXerics.values());
        if (enabled("mountedMyths")) Collections.addAll(result, MountedMythical.values());
        return result;
    }

    private static <E extends Enum<E> & PohTeleport> void addEnums(Set<PohTeleport> result, Class<E> type, String raw) {
        if (raw == null || raw.isEmpty()) return;
        for (String value : raw.split(",")) {
            try { result.add(Enum.valueOf(type, value.trim())); }
            catch (IllegalArgumentException ignored) { }
        }
    }
}
