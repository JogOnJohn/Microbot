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
