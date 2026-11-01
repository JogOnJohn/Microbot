package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CollisionMapQueriesTest {
    private static final WorldPoint TARGET = new WorldPoint(3200, 3200, 2);
    private CollisionMap map;

    @Before
    public void setUp() {
        map = mock(CollisionMap.class);
        when(map.hasRegion(anyInt(), anyInt())).thenReturn(true);
        when(map.isBlocked(anyInt(), anyInt(), anyInt())).thenReturn(true);
    }

    @Test
    public void missingMapOrTargetAbstainsWithoutReadingCollision() {
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(null, TARGET, 1));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, null, 1));
        assertNull(CollisionMapQueries.nearestWalkableTile(null, TARGET, 1));
        assertNull(CollisionMapQueries.nearestWalkableTile(map, null, 1));
        verifyNoMoreInteractions(map);
    }

    @Test
    public void unmappedTargetIsPermissiveEvenAtZeroOrNegativeRadius() {
        when(map.hasRegion(TARGET.getX(), TARGET.getY())).thenReturn(false);
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 0));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, -1));
        verify(map, never()).isBlocked(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void unknownNeighborMakesWithinQueryPermissive() {
        // TARGET lies on a region boundary; its southwest neighbor may lack data.
        when(map.hasRegion(3199, 3199)).thenReturn(false);
        assertFalse(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 0));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 1));
        verify(map, never()).isBlocked(3199, 3199, TARGET.getPlane());
    }

    @Test
    public void blockedBoxFailsAndNegativeRadiusIsClampedToCenter() {
        assertFalse(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 2));
        open(-1, -1);
        assertFalse(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 0));
        assertFalse(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, -3));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 1));
        open(0, 0);
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 0));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, -3));
    }

    @Test
    public void withinUsesInclusiveSquareBoundsAndTargetPlane() {
        open(2, 2);
        assertFalse(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 1));
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 2));
        verify(map, never()).isBlocked(anyInt(), anyInt(), eq(0));
        verify(map).isBlocked(3202, 3202, 2);
    }

    @Test
    public void nearestWithNonPositiveRadiusDoesNotInspectEvenAnOpenCenter() {
        open(0, 0);
        assertNull(CollisionMapQueries.nearestWalkableTile(map, TARGET, 0));
        assertNull(CollisionMapQueries.nearestWalkableTile(map, TARGET, -2));
        verifyNoMoreInteractions(map);
    }

    @Test
    public void nearestNeverReturnsCenterAndReturnsNullWhenRingsAreBlocked() {
        open(0, 0);
        assertNull(CollisionMapQueries.nearestWalkableTile(map, TARGET, 3));
        verify(map, never()).isBlocked(TARGET.getX(), TARGET.getY(), TARGET.getPlane());
    }

    @Test
    public void nearestUsesRingThenAscendingXThenAscendingYOrdering() {
        open(-2, -2);
        open(-1, 0);
        open(-1, 1);
        open(0, -1);
        assertEquals(point(-1, 0), CollisionMapQueries.nearestWalkableTile(map, TARGET, 2));
        open(-1, -1);
        assertEquals(point(-1, -1), CollisionMapQueries.nearestWalkableTile(map, TARGET, 2));
    }

    @Test
    public void nearestIncludesOuterRingAndKeepsPlane() {
        open(-2, -1);
        open(-2, 0);
        assertNull(CollisionMapQueries.nearestWalkableTile(map, TARGET, 1));
        assertEquals(point(-2, -1), CollisionMapQueries.nearestWalkableTile(map, TARGET, 2));
        verify(map).isBlocked(3198, 3199, 2);
        verify(map, never()).isBlocked(anyInt(), anyInt(), eq(0));
    }

    @Test
    public void nearestSkipsUnknownCandidatesButDoesNotRequireMappedCenter() {
        when(map.hasRegion(3200, 3200)).thenReturn(false);
        when(map.hasRegion(3199, 3199)).thenReturn(false);
        open(-1, -1);
        open(-1, 0);
        assertEquals(point(-1, 0), CollisionMapQueries.nearestWalkableTile(map, TARGET, 1));
        verify(map, never()).isBlocked(3199, 3199, 2);
    }

    @Test
    public void allUnknownCandidatesHaveNoNearestSuggestion() {
        when(map.hasRegion(anyInt(), anyInt())).thenReturn(false);
        assertNull(CollisionMapQueries.nearestWalkableTile(map, TARGET, 2));
        verify(map, never()).isBlocked(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void isolatedOpenTileIsStandableWithoutProvingConnectivity() {
        open(2, 0);
        assertTrue(CollisionMapQueries.hasWalkableTileWithin(map, TARGET, 2));
        assertEquals(point(2, 0), CollisionMapQueries.nearestWalkableTile(map, TARGET, 2));
        // Every intervening tile is blocked and no edge/path API is consulted.
        verify(map, atLeastOnce()).hasRegion(anyInt(), anyInt());
        verify(map, atLeastOnce()).isBlocked(anyInt(), anyInt(), eq(2));
        verifyNoMoreInteractions(map);
    }

    private void open(int dx, int dy) {
        when(map.isBlocked(TARGET.getX() + dx, TARGET.getY() + dy, TARGET.getPlane()))
                .thenReturn(false);
    }

    private WorldPoint point(int dx, int dy) {
        return new WorldPoint(TARGET.getX() + dx, TARGET.getY() + dy, TARGET.getPlane());
    }
}
