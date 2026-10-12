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
    private static final RoutingStaticBuilder.TerrainCache TERRAIN = new RoutingStaticBuilder.TerrainCache();
    private static final ExactStaticCache STATIC = new ExactStaticCache((map, raw, start) -> {
        RoutingStaticBuilder.Result built = RoutingStaticBuilder.build(map, raw,
                Collections.emptySet(), RoutingCuts.loadFromResources().pairs(), start, TERRAIN);
        WebWalkLog.pf("exact_static_build {}", built.diagnostics);
        return built.routingStatic;
    });

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
            long preparation = System.nanoTime();
            TransportAvailability availability = config.exactAvailability().excluding(config::isWalkScopedTransportBlocked);
            long availabilityReady = System.nanoTime();
            int teleportPenalty = config.getDistanceBeforeUsingTeleport();
            long cutoffMillis = config.getCalculationCutoffMillis();
            boolean avoidHazards = config.isAvoidDangerousNpcs();
            // Microbot's bank-aware wrapper still owns bank selection, withdrawal and per-leg refresh.
            PreparedRoutingAccount account = PreparedRoutingAccount.compile(availability, availability,
                    false, Collections.emptySet(), 0, true,
                    t -> TransportType.isTeleport(t.getType(), t.getOrigin()) ? teleportPenalty : 0);
            long accountReady = System.nanoTime();
            CollisionMap frozen = collision.frozenCopy();
            RoutingStatic stat;
            Map<Integer, Set<Transport>> raw = new HashMap<>();
            config.getAllTransports().forEach((origin, edges) -> raw.put(WorldPointUtil.packWorldPoint(origin), new LinkedHashSet<>(edges)));
            config.getTransports().forEach((origin, edges) -> raw.computeIfAbsent(WorldPointUtil.packWorldPoint(origin), k -> new LinkedHashSet<>()).addAll(edges));
            raw.computeIfAbsent(WorldPointUtil.UNDEFINED, k -> new LinkedHashSet<>()).addAll(Arrays.asList(availability.getUsableTeleports()));
            long structuralReady = System.nanoTime();
            ExactStaticCache.Prepared prepared = STATIC.prepare(frozen, raw, start);
            long staticReady = System.nanoTime();
            stat = prepared.stat;
            frozen = prepared.map;
            WebWalkLog.pf("exact_static_cache reused={}", prepared.reused);
            ExactCancellation.check();
            ExactRoutingSession.Lookup<SiteGraph> graphLookup = SESSION.graph(stat, account);
            SiteGraph graph = graphLookup.value();
            long graphReady = System.nanoTime();
            ExactRoutingSession.Lookup<PreparedTarget> targetLookup = SESSION.target(graph, frozen,
                    goals.stream().mapToInt(Integer::intValue).toArray());
            PreparedTarget target = targetLookup.value();
            long targetReady = System.nanoTime();
            Set<Integer> blocked = new HashSet<>(config.getRestrictedPointsPacked());
            Set<Long> blockedEdges = new HashSet<>(config.getBlockedTransportEdgesPacked());
            SearchRestrictions restrictions = SearchRestrictions.microbot(config.isAvoidWilderness()
                    && !targetInWilderness, blocked::contains,
                    key -> PathfinderConfig.isBlockedTransportStep((int) (key >> 32), (int) key, blockedEdges),
                    tile -> avoidHazards && config.isDangerousAdjacentTile(tile) && !goals.contains(tile) ? 100 : 0);
            ExactCancellation.check();
            long preparationMs = (System.nanoTime() - preparation) / 1_000_000;
            long searchStarted = System.nanoTime();
            long[] deadline = {System.nanoTime() + cutoffMillis * 1_000_000L};
            boolean[] timedOut = {false};
            ExactForwardSearch.Result result = target.search(start, () -> {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return true;
                timedOut[0] = cutoffMillis > 0 && System.nanoTime() > deadline[0];
                return timedOut[0];
            }, 1, restrictions, () -> deadline[0] = System.nanoTime() + cutoffMillis * 1_000_000L);
            long searchFinished = System.nanoTime();
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
            WebWalkLog.pf("exact_phases availabilityMs={} accountMs={} structuralMs={} staticMs={} graphMs={} targetMs={} searchMs={} rewriteMs={} staticReused={} graphReused={} targetReused={}",
                    (availabilityReady - preparation) / 1_000_000, (accountReady - availabilityReady) / 1_000_000,
                    (structuralReady - accountReady) / 1_000_000, (staticReady - structuralReady) / 1_000_000,
                    (graphReady - staticReady) / 1_000_000, (targetReady - graphReady) / 1_000_000,
                    (searchFinished - searchStarted) / 1_000_000, (System.nanoTime() - searchFinished) / 1_000_000,
                    prepared.reused, graphLookup.reused(), targetLookup.reused());
            WebWalkLog.pf("exact_done reason={} cost={} preparationMs={} totalMs={} nodes={} transports={}", reason,
                    result.cost(), preparationMs, (System.nanoTime() - preparation) / 1_000_000,
                    result.counters().statesPopped(), result.counters().transportCandidates());
            return new Outcome(steps, result.counters(), reason);
        } finally {
            ExactCancellation.end();
        }
    }
}
