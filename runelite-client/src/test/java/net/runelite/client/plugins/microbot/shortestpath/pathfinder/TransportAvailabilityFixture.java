package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.*;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

public final class TransportAvailabilityFixture {
    private TransportAvailabilityFixture() { }
    public static TransportAvailability of(Transport... transports) {
        Map<Integer, Set<Transport>> local = new HashMap<>();
        Set<Transport> global = new LinkedHashSet<>();
        for (Transport t : transports) {
            if (t.getOrigin() == null) global.add(t);
            else local.computeIfAbsent(WorldPointUtil.packWorldPoint(t.getOrigin()), k -> new LinkedHashSet<>()).add(t);
        }
        return new TransportAvailability(local, global);
    }
}
