package net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact;

import java.util.*;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportAvailabilityFixture;
import org.junit.Test;
import static org.junit.Assert.*;

public class MicrobotExactCompatibilityTest {
    private static int tile(int x, int y) { return WorldPointUtil.packWorldPoint(x, y, 0); }
    @Test public void blockedInteractionOriginStillPaysWalkingHazardPenalty() throws Exception {
        int start = tile(1, 1), ladder = tile(2, 1), landing = tile(10, 1);
        Transport transport = new ExactTransportFixture.Builder().origin(ladder).destination(landing).type(TransportType.TRANSPORT).duration(2).build();
        // A blocked interaction site attaches to its open cardinal neighbour in the real map.
        SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(start, landing), Map.of(ladder, new int[] {start}), 1);
        var stat = RoutingStaticBuilder.build(collision, Map.of(ladder, Set.of(transport)), Set.of(), new int[0], start).routingStatic;
        var availability = TransportAvailabilityFixture.of(transport);
        var account = PreparedRoutingAccount.compile(availability, availability, false, Set.of(), 0, true, t -> 0);
        var target = PreparedTarget.prepare(new SiteGraph(stat, account), collision, landing);
        var ordinary = target.search(start, () -> false, 1);
        var restricted = target.search(start, () -> false, 1,
                SearchRestrictions.microbot(false, t -> false, e -> false, t -> t == ladder ? 100 : 0), () -> {});
        assertTrue(ordinary.reached());
        assertTrue(restricted.reached());
        assertEquals(ordinary.cost() + 100, restricted.cost());
    }

    @Test public void canonicalizationStopsWhenCancelledDuringExpansion() {
        var collision = new ExactWalkCanonicalizerTest.Grid();
        var account = PreparedRoutingAccount.compile(null, null, false, Set.of(), 0, false, t -> 0);
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        ExactCancellation.begin(() -> checks.incrementAndGet() >= 3);
        try {
            new ExactWalkCanonicalizer(collision, account).walk(tile(3200, 3200), 40, WalkGoal.tile(tile(3200, 3240)));
            fail("cancelled canonicalization must stop during expansion");
        } catch (java.util.concurrent.CancellationException expected) {
            assertTrue(checks.get() >= 3);
        } finally { ExactCancellation.end(); }
    }

    @Test public void gameWalkRewriteStopsWhenCancelledDuringExpansion() {
        var collision = new ExactWalkCanonicalizerTest.Grid();
        var path = new java.util.ArrayList<net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathStep>();
        for (int i = 0; i <= 40; i++) path.add(new net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathStep(tile(3200, 3200 + i), false));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        ExactCancellation.begin(() -> checks.incrementAndGet() >= 3);
        try {
            new InGameWalkRewriter(collision).rewrite(path, new int[] {0, 40});
            fail("cancelled game walk rewrite must stop during expansion");
        } catch (java.util.concurrent.CancellationException expected) {
            assertTrue(checks.get() >= 3);
        } finally { ExactCancellation.end(); }
    }

    @Test public void jewelleryBoxThenGliderKeepsBothTransportIdentities() throws Exception {
        int house = tile(1, 1), arena = tile(10, 1), landing = tile(20, 1);
        Transport jewellery = new ExactTransportFixture.Builder().origin(house).destination(arena).type(TransportType.POH).duration(3).build();
        Transport glider = new ExactTransportFixture.Builder().origin(arena).destination(landing).type(TransportType.GNOME_GLIDER).duration(2).build();
        Map<Integer, Set<Transport>> raw = Map.of(house, Set.of(jewellery), arena, Set.of(glider));
        // All three endpoints are interaction-only sites; no accidental walking link joins them.
        SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(house, arena, landing), Map.of(), 1);
        RoutingStatic stat = RoutingStaticBuilder.build(collision, raw, Set.of(), new int[0], house).routingStatic;
        var availability = TransportAvailabilityFixture.of(jewellery, glider);
        var account = PreparedRoutingAccount.compile(availability, availability, false, Set.of(), 0, true, t -> 0);
        var target = PreparedTarget.prepare(new SiteGraph(stat, account), collision, landing);
        var result = target.search(house, () -> false, 1);
        assertTrue(result.reached());
        assertEquals(5, result.cost());
        assertSame(jewellery, result.path().get(1).getTransport());
        assertSame(glider, result.path().get(2).getTransport());
    }

    @Test public void learnedBlockedEdgesAndRestrictedTilesGateTheActualSearch() throws Exception {
        int a = tile(1, 1), b = tile(2, 1);
        SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(a, b),
                SyntheticCollisionMap.symmetricAdjacency(new int[][]{{a, b}}), 1);
        var stat = RoutingStaticBuilder.build(collision, Map.of(), Set.of(), new int[0], a).routingStatic;
        var availability = TransportAvailabilityFixture.of();
        var account = PreparedRoutingAccount.compile(availability, availability, false, Set.of(), 0, true, t -> 0);
        var target = PreparedTarget.prepare(new SiteGraph(stat, account), collision, b);
        long edge = ((long) a << 32) | (b & 0xffffffffL);
        assertFalse(target.search(a, () -> false, 1, SearchRestrictions.microbot(false, t -> false, e -> e == edge, t -> 0), () -> {}).reached());
        assertFalse(target.search(a, () -> false, 1, SearchRestrictions.microbot(false, t -> t == b, e -> false, t -> 0), () -> {}).reached());
        assertTrue(target.search(a, () -> false, 1).reached());
    }

    @Test public void preparationIsCancellableAndCancellationStateIsCleared() {
        ExactCancellation.begin(() -> true);
        try {
            try {
                ExactCancellation.check();
                fail("cancelled preparation should stop");
            } catch (java.util.concurrent.CancellationException expected) { }
        } finally { ExactCancellation.end(); }
        ExactCancellation.check();
    }
}
