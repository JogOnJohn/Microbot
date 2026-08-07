package net.runelite.client.plugins.microbot.util.walker.lifecycle;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public final class Rs2WalkerLifecycleRuntime {

    private Rs2WalkerLifecycleRuntime() {
    }

    public static void applyWalkerDestination(WorldPoint target) {
        if (target == null) {
            return;
        }
        if (!Microbot.isLoggedIn()) {
            log.warn("Unable to apply walker destination: not logged in");
            return;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Unable to apply walker destination: client unavailable");
            return;
        }
        Player localPlayer = Microbot.getClientThread().invoke(() -> client.getLocalPlayer());
        if (!Rs2PathApi.isStartPointSet() && localPlayer == null) {
            log.warn("Start point is not set and player is null");
            return;
        }

        WorldMapPointManager wmm = Microbot.getWorldMapPointManager();
        if (wmm == null) {
            Rs2Walker.clearWalkingRoute("walker:wmm-unavailable retry-setTarget dest=" + target);
            return;
        }
        wmm.removeIf(x -> x == Rs2PathApi.getMarker());
        Rs2PathApi.setMarker(new WorldMapPoint(target, Rs2PathApi.MARKER_IMAGE));
        Rs2PathApi.getMarker().setName("Target");
        Rs2PathApi.getMarker().setTarget(Rs2PathApi.getMarker().getWorldPoint());
        Rs2PathApi.getMarker().setJumpOnClick(true);
        wmm.add(Rs2PathApi.getMarker());

        WorldPoint start = Microbot.getClientThread().invoke(() -> {
            if (client.getTopLevelWorldView().isInstance()) {
                LocalPoint localLoc = Rs2Player.getLocalLocation();
                WorldPoint computed = localLoc != null ? WorldPoint.fromLocalInstance(client, localLoc) : null;
                if (computed == null) {
                    log.warn("[Walker] setTarget: instance localPoint conversion returned null (localLoc={} target={}) — falling back to raw world location",
                            localLoc, target);
                    computed = Rs2Player.getWorldLocation();
                }
                WorldPoint exitPortal = net.runelite.client.plugins.microbot.shortestpath.PohPanel.getExitPortalTile();
                if (exitPortal != null) {
                    Microbot.log("[Walker] In POH instance — remapping pathfinder start " + computed
                            + " -> exit portal " + exitPortal);
                    computed = exitPortal;
                }
                return computed;
            }
            return Rs2Player.getWorldLocation();
        });
        final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        final WorldPoint effectiveStart = (Rs2PathApi.isStartPointSet() && pathfinder != null)
                ? pathfinder.getStart()
                : start;
        Rs2PathApi.setLastLocation(effectiveStart);
        restartPathfinding(effectiveStart, target);
    }

    public static boolean restartPathfinding(WorldPoint start, WorldPoint end) {
        return restartPathfinding(start, Set.of(end));
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        if (start == null || ends == null || ends.isEmpty()) {
            return false;
        }
        Set<WorldPoint> requestedEnds = new HashSet<>(ends);
        Pathfinder pending = new Pathfinder(Rs2PathApi.getPathfinderConfig(), start, requestedEnds);
        boolean inCave = Rs2Player.isInCave();
        synchronized (Rs2PathApi.getPathfinderMutex()) {
            Pathfinder previous = Rs2PathApi.getPathfinder();
            if (previous != null) {
                previous.cancel();
            }
            if (Rs2PathApi.getPathfinderFuture() != null) {
                Rs2PathApi.getPathfinderFuture().cancel(true);
            }
            ExecutorService executor = ensurePathfindingExecutor();
            Rs2PathApi.setPathfinder(pending);
            Rs2PathApi.setPathfinderFuture(executor.submit(() -> planRoute(pending, start, requestedEnds, inCave)));
        }
        return true;
    }

    private static void planRoute(Pathfinder pending, WorldPoint start, Set<WorldPoint> ends, boolean inCave) {
        synchronized (Rs2PathApi.getPathfinderConfig()) {
            if (!isCurrent(pending)) {
                return;
            }
            try {
                Rs2PathApi.getPathfinderConfig().refresh(ends.iterator().next());
                if (!inCave) {
                    pending.run();
                    return;
                }

                pending.run();
                Pathfinder walkingOnly;
                try {
                    Rs2PathApi.getPathfinderConfig().setIgnoreTeleportAndItems(true);
                    walkingOnly = new Pathfinder(Rs2PathApi.getPathfinderConfig(), start, ends);
                    walkingOnly.run();
                } finally {
                    Rs2PathApi.getPathfinderConfig().setIgnoreTeleportAndItems(false);
                }
                if (isCurrent(pending)) {
                    Rs2PathApi.setPathfinder(selectCavePath(pending, walkingOnly, ends));
                }
            } catch (RuntimeException failure) {
                log.warn("[Walker] background planner refresh failed; using the previous snapshot", failure);
                if (isCurrent(pending)) {
                    pending.run();
                }
            }
        }
    }

    private static Pathfinder selectCavePath(Pathfinder base, Pathfinder walkingOnly, Set<WorldPoint> ends) {
        boolean walkingPathAvailable = !walkingOnly.getPath().isEmpty();
        boolean basePathAvailable = !base.getPath().isEmpty();
        if (!walkingPathAvailable) {
            return basePathAvailable ? base : walkingOnly;
        }
        WorldPoint lastPath = walkingOnly.getPath().get(walkingOnly.getPath().size() - 1);
        int reachedDistance = Rs2Walker.config != null ? Rs2Walker.config.reachedDistance() : 10;
        boolean walkingPathReachable = lastPath.distanceTo(ends.iterator().next()) <= reachedDistance;
        return walkingPathReachable && basePathAvailable && base.getPath().size() >= walkingOnly.getPath().size()
                ? walkingOnly
                : (basePathAvailable ? base : walkingOnly);
    }

    private static boolean isCurrent(Pathfinder expected) {
        synchronized (Rs2PathApi.getPathfinderMutex()) {
            return Rs2PathApi.getPathfinder() == expected;
        }
    }

    private static ExecutorService ensurePathfindingExecutor() {
        ExecutorService executor = Rs2PathApi.getPathfindingExecutor();
        if (executor == null || executor.isShutdown()) {
            ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
            executor = Executors.newSingleThreadExecutor(shortestPathNaming);
            Rs2PathApi.setPathfindingExecutor(executor);
        }
        return executor;
    }
}
