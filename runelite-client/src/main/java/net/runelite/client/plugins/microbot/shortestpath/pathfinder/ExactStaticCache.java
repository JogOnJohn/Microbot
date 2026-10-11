package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.io.IOException;
import java.util.*;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.ExactCancellation;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.RoutingStatic;

/** Structural endpoints outlive account capability changes; collision changes still invalidate them. */
final class ExactStaticCache {
    interface Builder {
        RoutingStatic build(CollisionMap map, Map<Integer, Set<Transport>> raw, int start) throws IOException;
    }
    static final class Prepared {
        final RoutingStatic stat;
        final CollisionMap map;
        final boolean reused;
        Prepared(RoutingStatic stat, CollisionMap map, boolean reused) {
            this.stat = stat; this.map = map; this.reused = reused;
        }
    }
    private final Builder builder;
    private Object collisionKey;
    private final Map<Long, Transport> endpoints = new HashMap<>();
    private Set<Long> preparedEndpoints = Collections.emptySet();
    private RoutingStatic stat;
    private CollisionMap map;

    ExactStaticCache(Builder builder) { this.builder = builder; }

    synchronized Prepared prepare(CollisionMap frozen, Map<Integer, Set<Transport>> raw, int start) throws IOException {
        ExactCancellation.check();
        Object key = frozen.exactSnapshotKey();
        if (!Objects.equals(key, collisionKey)) {
            collisionKey = key;
            endpoints.clear();
            preparedEndpoints = Collections.emptySet();
            stat = null;
            map = null;
        }
        raw.values().forEach(edges -> edges.forEach(t -> {
            int origin = WorldPointUtil.packWorldPoint(t.getOrigin());
            long edge = ((long) origin << 32) | (WorldPointUtil.packWorldPoint(t.getDestination()) & 0xffffffffL);
            endpoints.putIfAbsent(edge, t);
        }));
        // Removing inventory/bank capabilities must not shrink structural reachability. The
        // prepared account alone decides which of these edges a search can actually execute.
        if (stat != null && preparedEndpoints.containsAll(endpoints.keySet())
                && (stat.searchIndex(start) >= 0 || stat.siteIndex(start) >= 0)) {
            return new Prepared(stat, map, true);
        }
        Map<Integer, Set<Transport>> structural = new HashMap<>();
        endpoints.values().forEach(t -> structural.computeIfAbsent(WorldPointUtil.packWorldPoint(t.getOrigin()),
                ignored -> new LinkedHashSet<>()).add(t));
        RoutingStatic built = builder.build(frozen, structural, start);
        ExactCancellation.check();
        stat = built;
        map = frozen;
        preparedEndpoints = Set.copyOf(endpoints.keySet());
        return new Prepared(stat, map, false);
    }
}
