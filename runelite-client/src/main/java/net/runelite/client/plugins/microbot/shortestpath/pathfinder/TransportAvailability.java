package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

/** Immutable exact-core view of Microbot's already filtered transport snapshot. */
public final class TransportAvailability {
    public static final Transport[] EMPTY_TRANSPORTS = new Transport[0];
    private final PrimitiveIntHashMap<Transport[]> local;
    private final Transport[] global;

    public TransportAvailability(Map<Integer, Set<Transport>> local, Set<Transport> global) {
        this.local = new PrimitiveIntHashMap<>(Math.max(1, local.size()));
        local.forEach((origin, edges) -> this.local.put(origin,
                edges.stream().filter(t -> t.getOrigin() != null).toArray(Transport[]::new)));
        this.global = global.toArray(new Transport[0]);
    }

    public PrimitiveIntHashMap<Transport[]> getTransportsPacked() { return local; }
    public Transport[] getUsableTeleports() { return global.clone(); }
}
