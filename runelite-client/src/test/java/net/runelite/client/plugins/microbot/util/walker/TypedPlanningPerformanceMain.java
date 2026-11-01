package net.runelite.client.plugins.microbot.util.walker;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderBackend;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

/**
 * Manually invoked offline harness (not a JUnit timing assertion). Args: warmup samples.
 * Uses identical deterministic prepared catalogs; bank fixtures represent already-admitted bank
 * snapshots, not a live inventory/PoH refresh benchmark. Typed timings include copying and DTOs.
 */
public final class TypedPlanningPerformanceMain
{
    private TypedPlanningPerformanceMain() { }

    public static void main(String[] args)
    {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int samples = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        if (warmup < 1 || warmup > 100 || samples < 2 || samples > 1_000) throw new IllegalArgumentException("warmup/samples");
        System.out.printf("offline=true java=%s maxHeapBytes=%d warmup=%d samples=%d cpus=%d%n",
                System.getProperty("java.version"), Runtime.getRuntime().maxMemory(), warmup, samples,
                Runtime.getRuntime().availableProcessors());
        System.out.println("backend,fixture,bankSnapshot,variant,samples,p50Nanos,p95Nanos,maxNanos,elapsedNanos,heapDeltaBytes,gcCountDelta");
        for (PathfinderBackend backend : PathfinderBackend.values())
        {
            measure(backend, "short", TypedPlanningFixture.SHORT, false, false, warmup, samples);
            measure(backend, "long", TypedPlanningFixture.LONG, false, false, warmup, samples);
            measure(backend, "transport", TypedPlanningFixture.LONG, true, false, warmup, samples);
            measure(backend, "bank-carried", TypedPlanningFixture.LONG, false, false, warmup, samples);
            measure(backend, "bank-available", TypedPlanningFixture.LONG, true, true, warmup, samples);
        }
    }

    private static void measure(PathfinderBackend backend, String fixture, WorldPoint target,
            boolean transport, boolean banked, int warmup, int samples)
    {
        // Copying the fixture config is intentionally excluded from direct timings but included in
        // typed timings. Both receive the same fresh prepared catalog, policy, backend and terrain.
        for (int i = 0; i < warmup; i++)
        {
            run(false, TypedPlanningFixture.config(backend, transport, banked), target);
            run(true, TypedPlanningFixture.config(backend, transport, banked), target);
        }
        long[][] times = {new long[samples], new long[samples]};
        long[] heap = new long[2], gc = new long[2], elapsed = new long[2];
        for (int i = 0; i < samples; i++)
        {
            // Alternate order so the typed side does not always inherit a warmer shared static cache.
            for (int order = 0; order < 2; order++)
            {
                int variant = (i + order) % 2;
                PathfinderConfig config = TypedPlanningFixture.config(backend, transport, banked);
                long heapBefore = usedHeap(), gcBefore = gcCount(), start = System.nanoTime();
                run(variant == 1, config, target);
                long duration = System.nanoTime() - start;
                times[variant][i] = duration;
                elapsed[variant] += duration;
                heap[variant] += usedHeap() - heapBefore;
                gc[variant] += gcCount() - gcBefore;
            }
        }
        for (int variant = 0; variant < 2; variant++)
        {
            Arrays.sort(times[variant]);
            System.out.printf(Locale.ROOT, "%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d%n", backend, fixture,
                    banked, variant == 0 ? "direct" : "typed", samples, percentile(times[variant], .50),
                    percentile(times[variant], .95), times[variant][samples - 1], elapsed[variant], heap[variant], gc[variant]);
        }
    }

    private static void run(boolean typed, PathfinderConfig config, WorldPoint target)
    {
        if (typed)
        {
            Rs2RouteResult result = TypedRoutePlanning.planWithConfig(Rs2RouteRequest.to(TypedPlanningFixture.START, target)
                    .withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER), config);
            if (result.getTerminationReason() != Rs2RouteTermination.TARGET_REACHED || !result.isTargetReached(0))
                throw new AssertionError("typed fixture incomplete: " + result.getTerminationReason());
        }
        else
        {
            Pathfinder search = new Pathfinder(config, TypedPlanningFixture.START, target);
            search.setPlanningBudgetMillis(5_000);
            search.run();
            if (!"reached-goal".equals(search.getTerminationReason()) || search.getPath().isEmpty()
                    || !target.equals(search.getPath().get(search.getPath().size() - 1)))
                throw new AssertionError("direct fixture incomplete: " + search.getTerminationReason());
        }
    }

    private static long percentile(long[] values, double percentile) {
        return values[Math.max(0, (int) Math.ceil(values.length * percentile) - 1)];
    }

    private static long usedHeap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
}
