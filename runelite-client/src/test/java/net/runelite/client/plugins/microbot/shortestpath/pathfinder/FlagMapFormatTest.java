package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.*;

public class FlagMapFormatTest {
    @Test
    public void readsFourFlagTilesWithoutMixingWallsMovementOrPlanes() {
        BitSet payload = new BitSet();
        // Hand-built upstream payload, independent of FlagMap's writer.
        payload.set(0); // tile (0,0,0): north movement
        payload.set(3); // same tile: east structural boundary
        payload.set(5); // next tile (1,0,0): east movement
        payload.set(64 * 64 * 4); // tile (0,0,1): north movement
        FlagMap map = new FlagMap(3200, 3200, payload.toByteArray());
        assertEquals(2, map.getPlaneCount());
        assertTrue(map.get(3200, 3200, 0, 0));
        assertFalse(map.get(3200, 3200, 0, 1));
        assertTrue(map.get(3200, 3200, 0, 3));
        assertFalse(map.get(3201, 3200, 0, 0));
        assertTrue(map.get(3201, 3200, 0, 1));
        assertTrue(map.get(3200, 3200, 1, 0));
        assertFalse(map.get(3200, 3200, 2, 0));
    }

    @Test
    public void packagedMapNeverInventsExtraPlanesAndHasExpectedTransportLandings() {
        SplitFlagMap flags = SplitFlagMap.fromResources();
        for (byte planes : flags.getRegionMapPlaneCounts()) {
            assertTrue("four-flag map must not decode as extra planes", planes >= 0 && planes <= 4);
        }
        CollisionMap map = new CollisionMap(flags);
        assertFalse(map.isBlocked(3811, 3056, 0));
        assertFalse(map.isBlocked(3810, 3048, 0));
        assertFalse(map.isBlocked(2806, 2724, 0));
    }
}
