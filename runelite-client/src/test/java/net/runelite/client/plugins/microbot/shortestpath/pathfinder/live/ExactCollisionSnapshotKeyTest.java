package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.*;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExactCollisionSnapshotKeyTest {
    private static LiveCollisionRegion region(boolean open) {
        BitSet known = new BitSet(); known.set(0);
        BitSet value = new BitSet(); if (open) value.set(0);
        return new LiveCollisionRegion(1, known, value, new BitSet(), new BitSet());
    }
    @Test public void equivalentRecapturesCompareEqualButAnOpenedEdgeInvalidates() {
        LiveCollisionView closed = new LiveCollisionView(Map.of(1, region(false)));
        LiveCollisionView recapture = new LiveCollisionView(Map.of(1, region(false)));
        LiveCollisionView opened = new LiveCollisionView(Map.of(1, region(true)));
        assertEquals(closed, recapture);
        assertEquals(closed.hashCode(), recapture.hashCode());
        assertNotEquals(closed, opened);
    }
    @Test public void frozenSearchKeepsItsCollisionAfterTheHolderChanges() {
        LiveCollisionOverlay overlay = new LiveCollisionOverlay(); overlay.setEnabled(true);
        overlay.putRegion(0, region(false));
        CollisionMap map = new CollisionMap(SplitFlagMap.fromResources(), overlay);
        map.beginSearch();
        CollisionMap frozen = map.frozenCopy();
        Object key = frozen.exactSnapshotKey();
        overlay.clear(); overlay.putRegion(0, region(true));
        map.beginSearch();
        assertNotEquals(key, map.exactSnapshotKey());
        assertEquals(key, frozen.exactSnapshotKey());
        assertFalse(frozen.n(0, 0, 0));
        assertTrue(map.n(0, 0, 0));
    }
}
