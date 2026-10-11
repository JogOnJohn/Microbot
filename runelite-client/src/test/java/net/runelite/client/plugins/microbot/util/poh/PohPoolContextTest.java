package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PohPoolContextTest {
    @Test public void scenePlaneAndObjectIdentityMustStillMatch() {
        Client client = mock(Client.class);
        WorldView view = mock(WorldView.class);
        Player player = mock(Player.class);
        Scene scene = mock(Scene.class);
        GameObject pool = mock(GameObject.class);
        Tile tile = mock(Tile.class);
        Tile[][][] tiles = new Tile[4][104][104];
        tiles[0][50][52] = tile;
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getLocalLocation()).thenReturn(LocalPoint.fromScene(50, 50));
        when(player.getAnimation()).thenReturn(AnimationID.POH_POOL_DRINK);
        when(view.getScene()).thenReturn(scene);
        when(view.getPlane()).thenReturn(0);
        when(scene.getTiles()).thenReturn(tiles);
        when(pool.getSceneMinLocation()).thenReturn(new Point(50, 52));
        when(pool.getSceneMaxLocation()).thenReturn(new Point(51, 53));
        when(tile.getGameObjects()).thenReturn(new GameObject[]{pool});
        PohTeleports.PoolTarget target = new PohTeleports.PoolTarget(pool, scene, 0, 2, 7);
        try (MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class)) {
            PohTeleports.PoolObservation observed = PohTeleports.observePoolOnClient(client, target);
            assertTrue(PohPoolInteraction.isDrinking(observed.animation, observed.distance, observed.sameHouse));
            when(view.getPlane()).thenReturn(1);
            assertFalse(PohTeleports.observePoolOnClient(client, target).sameHouse);
            when(view.getPlane()).thenReturn(0);
            when(view.getScene()).thenReturn(mock(Scene.class));
            assertFalse(PohTeleports.observePoolOnClient(client, target).sameHouse);
            when(view.getScene()).thenReturn(scene);
            when(tile.getGameObjects()).thenReturn(new GameObject[]{mock(GameObject.class)});
            assertFalse(PohTeleports.observePoolOnClient(client, target).sameHouse);
            when(player.getLocalLocation()).thenReturn(null);
            assertFalse(PohTeleports.observePoolOnClient(client, target).sameHouse);
        }
    }

    @Test public void leavingHouseDuringPoolWorkDoesNotDispatchOrBlockTeleport() {
        PohTeleport teleport = mock(PohTeleport.class);
        when(teleport.getDestination()).thenReturn(new WorldPoint(1754, 3564, 0));
        when(teleport.displayInfo()).thenReturn("Glade regression");
        PohTransport transport = new PohTransport(new WorldPoint(2053, 7052, 0), teleport);
        try (MockedStatic<PohTeleports> pools = mockStatic(PohTeleports.class)) {
            pools.when(PohTeleports::isInHouse).thenReturn(false);
            assertFalse(transport.execute());
            verify(teleport, never()).execute();
            assertEquals(PohMenuInteraction.Result.CANCELLED, PohMenuInteraction.lastResult());
        }
    }
}
