package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact.*;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;

/** Adapts the upstream exact engine without changing the walker/QuestHelper search API. */
final class ExactSearchAdapter {
    private static final ExactRoutingSession SESSION = new ExactRoutingSession();
    private static Object cachedCollision;
    private static Set<Long> cachedEdges;
    private static RoutingStatic cachedStatic;
    private static CollisionMap cachedMap;

    static final class Outcome {
        final List<PathStep> steps;
        final ExactForwardSearch.Counters counters;
        final String reason;
        Outcome(List<PathStep> steps, ExactForwardSearch.Counters counters, String reason) {
            this.steps = List.copyOf(steps); this.counters = counters; this.reason = reason;
        }
    }

    static Outcome search(PathfinderConfig config, CollisionMap collision, int start, Set<Integer> goals,
            boolean targetInWilderness, BooleanSupplier cancelled) throws IOException {
        ExactCancellation.begin(cancelled);
        try {
            ExactCancellation.check();
            if (Runtime.getRuntime().maxMemory() < 1024L * 1024 * 1024) {
                throw new IllegalStateException("Exact backend requires at least -Xmx1g; -Xmx2g is recommended for the test client");
            }
            TransportAvailability availability = config.exactAvailability();
            int teleportPenalty = config.getDistanceBeforeUsingTeleport();
            long cutoffMillis = config.getCalculationCutoffMillis();
            boolean avoidHazards = config.isAvoidDangerousNpcs();
            // Microbot's bank-aware wrapper still owns bank selection, withdrawal and per-leg refresh.
            PreparedRoutingAccount account = PreparedRoutingAccount.compile(availability, availability,
                    false, Collections.emptySet(), 0, true,
                    t -> TransportType.isTeleport(t.getType(), t.getOrigin()) ? teleportPenalty : 0);
            CollisionMap frozen = collision.frozenCopy();
            RoutingStatic stat;
            long preparation = System.nanoTime();
            Map<Integer, Set<Transport>> raw = new HashMap<>();
            config.getAllTransports().forEach((origin, edges) -> raw.put(WorldPointUtil.packWorldPoint(origin), new LinkedHashSet<>(edges)));
            config.getTransports().forEach((origin, edges) -> raw.computeIfAbsent(WorldPointUtil.packWorldPoint(origin), k -> new LinkedHashSet<>()).addAll(edges));
            raw.computeIfAbsent(WorldPointUtil.UNDEFINED, k -> new LinkedHashSet<>()).addAll(Arrays.asList(availability.getUsableTeleports()));
            Set<Long> endpoints = new HashSet<>();
            raw.values().forEach(edges -> edges.forEach(t -> endpoints.add(((long) WorldPointUtil.packWorldPoint(t.getOrigin()) << 32)
                    | (WorldPointUtil.packWorldPoint(t.getDestination()) & 0xffffffffL))));
            synchronized (SESSION) {
                Object key = frozen.exactSnapshotKey();
                if (cachedStatic == null || !Objects.equals(key, cachedCollision)
                        || !endpoints.equals(cachedEdges) || (cachedStatic.searchIndex(start) < 0 && cachedStatic.siteIndex(start) < 0)) {
                    RoutingStaticBuilder.Result built = RoutingStaticBuilder.build(frozen, raw,
                            Collections.emptySet(), RoutingCuts.loadFromResources().pairs(), start);
                    ExactCancellation.check();
                    cachedStatic = built.routingStatic;
                    cachedCollision = key; cachedEdges = endpoints; cachedMap = frozen;
                    WebWalkLog.pf("exact_static_build {}", built.diagnostics);
                }
                stat = cachedStatic;
                frozen = cachedMap;
            }
            ExactCancellation.check();
            SiteGraph graph = SESSION.graph(stat, account).value();
            PreparedTarget target = SESSION.target(graph, frozen, goals.stream().mapToInt(Integer::intValue).toArray()).value();
            Set<Integer> blocked = new HashSet<>(config.getRestrictedPointsPacked());
            Set<Long> blockedEdges = new HashSet<>(config.getBlockedTransportEdgesPacked());
            SearchRestrictions restrictions = SearchRestrictions.microbot(config.isAvoidWilderness()
                    && !targetInWilderness, blocked::contains,
                    key -> PathfinderConfig.isBlockedTransportStep((int) (key >> 32), (int) key, blockedEdges),
                    tile -> avoidHazards && config.isDangerousAdjacentTile(tile) && !goals.contains(tile) ? 100 : 0);
            ExactCancellation.check();
            long[] deadline = {System.nanoTime() + cutoffMillis * 1_000_000L};
            boolean[] timedOut = {false};
            ExactForwardSearch.Result result = target.search(start, () -> {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return true;
                timedOut[0] = cutoffMillis > 0 && System.nanoTime() > deadline[0];
                return timedOut[0];
            }, 1, restrictions, () -> deadline[0] = System.nanoTime() + cutoffMillis * 1_000_000L);
            if (cancelled.getAsBoolean()) return new Outcome(Collections.emptyList(), result.counters(), "cancelled");
            ExactRoute route = result.reached() ? result.route() : timedOut[0] ? result.closestRoute() : null;
            List<PathStep> steps = Collections.emptyList();
            if (route != null) {
                ExactCancellation.check();
                if (avoidHazards) {
                    steps = route.steps();
                } else {
                    ExactWalkCanonicalizer.Result canonical = new ExactWalkCanonicalizer(frozen, account, restrictions).canonicalize(route);
                    ExactCancellation.check();
                    steps = new InGameWalkRewriter(frozen, restrictions).rewrite(canonical).path();
                }
            }
            String reason = result.reached() ? "reached-goal" : timedOut[0] ? "time-cutoff" : "unreachable";
            WebWalkLog.pf("exact_done reason={} cost={} preparationMs={} nodes={} transports={}", reason,
                    result.cost(), (System.nanoTime() - preparation) / 1_000_000,
                    result.counters().statesPopped(), result.counters().transportCandidates());
            return new Outcome(steps, result.counters(), reason);
        } finally {
            ExactCancellation.end();
        }
    }
}
