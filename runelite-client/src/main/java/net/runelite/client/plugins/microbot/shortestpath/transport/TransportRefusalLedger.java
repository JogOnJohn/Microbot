package net.runelite.client.plugins.microbot.shortestpath.transport;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransport;

/** Walk-scoped execution refusals, separate from persisted blocked walking edges. */
public final class TransportRefusalLedger {
    private final Map<List<Object>, Integer> refusals = new HashMap<>();
    private final Map<List<Object>, Integer> fallthroughs = new HashMap<>();
    private final Map<List<Object>, Long> lastObservation = new HashMap<>();
    private final Set<List<Object>> blocked = new HashSet<>();
    private WorldPoint goal;
    private long generation;
    private long revision;

    public synchronized void beginWalk(WorldPoint target) {
        if (target == null || !Objects.equals(goal, target)) {
            goal = target;
            generation++;
            refusals.clear();
            fallthroughs.clear();
            lastObservation.clear();
            blocked.clear();
        }
    }

    public synchronized long generation() { return generation; }
    public synchronized long revision() { return revision; }

    public synchronized boolean isBlocked(Transport transport) {
        return transport != null && !blocked.isEmpty() && blocked.contains(key(transport));
    }

    public synchronized void success(Transport transport) {
        List<Object> key = key(transport);
        refusals.remove(key);
        fallthroughs.remove(key);
        lastObservation.remove(key);
    }

    public synchronized boolean failure(long owner, Transport transport, WorldPoint before,
                                        WorldPoint after, boolean interacted, boolean settled, long now) {
        if (owner != generation || goal == null || !settled || !eligible(transport)
                || !stillAtOrigin(transport, before) || !stillAtOrigin(transport, after)) return false;
        List<Object> key = key(transport);
        if (blocked.contains(key)) return false;
        Long previous = lastObservation.get(key);
        // Repeated observations of one tick are not independent failed attempts.
        if (previous != null && now - previous < 600L) return false;
        lastObservation.put(key, now);
        Map<List<Object>, Integer> counts = interacted ? refusals : fallthroughs;
        int strikes = counts.merge(key, 1, Integer::sum);
        if (strikes < (interacted ? 2 : 3)) return false;
        blocked.add(key);
        revision++;
        return true;
    }

    public static boolean eligible(Transport transport) {
        // These handlers already own their cooldowns, instance frames or multi-stage recovery.
        return transport != null && transport.getOrigin() != null && transport.getDestination() != null
                && !(transport instanceof World330HostedHouseTransport)
                && transport.getType() != TransportType.POH
                && transport.getType() != TransportType.SPIRIT_TREE
                && transport.getType() != TransportType.SEASONAL_TRANSPORT
                && !TransportType.isTeleport(transport.getType(), transport.getOrigin());
    }

    public static boolean stillAtOrigin(Transport transport, WorldPoint player) {
        WorldPoint origin = transport == null ? null : transport.getOrigin();
        WorldPoint destination = transport == null ? null : transport.getDestination();
        return player != null && origin != null && destination != null
                && player.getPlane() == origin.getPlane() && player.distanceTo2D(origin) <= 1
                && (destination.getPlane() != origin.getPlane()
                    || player.distanceTo2D(origin) < player.distanceTo2D(destination));
    }

    private static List<Object> key(Transport transport) {
        // Endpoint-only bans would also hide a working ladder/door with the same landing.
        return Arrays.asList(transport.getClass().getName(), transport.getOrigin(), transport.getDestination(),
                transport.getType(), transport.getObjectId(), transport.getAction(), transport.getDisplayInfo());
    }
}
