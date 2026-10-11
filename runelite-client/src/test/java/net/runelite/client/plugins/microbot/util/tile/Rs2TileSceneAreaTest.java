package net.runelite.client.plugins.microbot.util.tile;

import org.junit.Test;
import static org.junit.Assert.*;

public class Rs2TileSceneAreaTest {
    @Test public void reachableObjectSideQualifiesButDistantOrInteriorTileDoesNot() {
        boolean[][] visited = new boolean[104][104];
        visited[80][80] = true;
        visited[50][50] = true;
        assertFalse(Rs2Tile.hasReachablePerimeter(visited, 50, 50, 51, 51));
        visited[49][50] = true;
        assertTrue(Rs2Tile.hasReachablePerimeter(visited, 50, 50, 51, 51));
    }

    @Test public void sceneEdgeDoesNotReadOutsideCollisionGrid() {
        boolean[][] visited = new boolean[104][104];
        assertFalse(Rs2Tile.hasReachablePerimeter(visited, 0, 0, 1, 1));
        visited[2][1] = true;
        assertTrue(Rs2Tile.hasReachablePerimeter(visited, 0, 0, 1, 1));
        assertFalse(Rs2Tile.hasReachablePerimeter(visited, 102, 102, 103, 103));
    }
}
