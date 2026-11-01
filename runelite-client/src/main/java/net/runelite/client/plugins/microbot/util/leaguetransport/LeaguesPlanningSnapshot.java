package net.runelite.client.plugins.microbot.util.leaguetransport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

/** Existing Leagues knowledge only. No calibration, unlock publication, pruning, lazy load or persistence. */
public final class LeaguesPlanningSnapshot
{
    private final boolean active;
    private final Set<LeaguesRegion> unlocked;
    private final Set<Integer> blocked;
    private final Map<Integer, LeaguesRegion> blockedRegions;
    private final List<Transport> candidates;

    private LeaguesPlanningSnapshot(boolean active, Set<LeaguesRegion> unlocked, Set<Integer> blocked,
            Map<Integer, LeaguesRegion> blockedRegions, List<Transport> candidates) {
        this.active = active;
        this.unlocked = Set.copyOf(unlocked);
        this.blocked = Set.copyOf(blocked);
        this.blockedRegions = Map.copyOf(blockedRegions);
        this.candidates = List.copyOf(candidates);
    }

    public static LeaguesPlanningSnapshot capture(Rs2LeaguesTransport.LeaguesContext context) {
        if (context == null) throw new IllegalStateException("Leagues planning context unavailable");
        if (!context.isActive()) return new LeaguesPlanningSnapshot(false, Set.of(), Set.of(), Map.of(), List.of());
        LeaguesTransportPersistence.requirePlanningState();
        EnumSet<LeaguesRegion> unlocked = context.getUnlockedRegions().clone();
        List<Transport> candidates = new ArrayList<>();
        LeaguesTransportPersistence.planningLandings().forEach((region, landing) -> {
            if (unlocked.contains(region)) candidates.add(new Transport(landing,
                    "Leagues Area: " + region.getDisplayName(), TransportType.SEASONAL_TRANSPORT,
                    true, 31, Collections.emptySet()));
        });
        candidates.addAll(LeaguesTransportObservations.loadCatalogTransportsReadOnly(unlocked));
        return new LeaguesPlanningSnapshot(true, unlocked, LeaguesTransportPersistence.planningBlockedDestinations(),
                LeaguesTransportPersistence.planningBlockedRegions(), candidates);
    }

    public boolean allows(Transport transport) {
        if (transport == null) return false;
        if (!active) return true;
        WorldPoint destination = transport.getDestination();
        if (destination == null) return transport.getType() != TransportType.SEASONAL_TRANSPORT;
        int packed = WorldPointUtil.packWorldPoint(destination);
        if (blocked.contains(packed)) return false;
        LeaguesRegion region = blockedRegions.get(packed);
        return region == null || unlocked.contains(region);
    }

    public void inject(PathfinderConfig config, Rs2LeaguesTransport.LeaguesContext context,
            Set<Transport> teleports, Map<WorldPoint, Set<Transport>> transports,
            PrimitiveIntHashMap<Set<Transport>> packed, Map<TransportType, int[]> stats) {
        for (Transport candidate : candidates) {
            if (!allows(candidate) || !config.isTransportUsableWithLeaguesContext(candidate, context)) continue;
            boolean added;
            if (candidate.getOrigin() == null) {
                added = LeaguesTransportInjection.mergeOriginlessTeleportByBestDuration(teleports, candidate);
            } else {
                Set<Transport> edges = transports.computeIfAbsent(candidate.getOrigin(), k -> new LinkedHashSet<>());
                added = edges.add(candidate);
                packed.put(WorldPointUtil.packWorldPoint(candidate.getOrigin()), edges);
            }
            if (added && candidate.getType() != null) {
                int[] count = stats.computeIfAbsent(candidate.getType(), k -> new int[3]);
                count[0]++; count[1]++;
            }
        }
    }
}
