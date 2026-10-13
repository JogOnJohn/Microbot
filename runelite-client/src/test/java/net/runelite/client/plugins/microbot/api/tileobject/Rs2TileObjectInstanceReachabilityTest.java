package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Optional;
import java.util.concurrent.Callable;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Rs2TileObjectInstanceReachabilityTest {
    @Test public void usesSceneGeometryDespiteDifferentTemplateCoordinatesAndPlane() throws Exception {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        WorldView view = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        Player player = mock(Player.class);
        CollisionData collision = mock(CollisionData.class);
        int[][] flags = new int[104][104];
        when(client.isClientThread()).thenReturn(true);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(player.getWorldView()).thenReturn(view);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(13000, 1000, 0));
        when(view.getId()).thenReturn(-1);
        when(view.getPlane()).thenReturn(0);
        when(view.getScene()).thenReturn(scene);
        when(scene.isInstance()).thenReturn(true);
        when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
        when(collision.getFlags()).thenReturn(flags);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        GameObject box = mock(GameObject.class);
        when(box.getWorldView()).thenReturn(view);
        when(box.getPlane()).thenReturn(0);
        when(box.getWorldLocation()).thenReturn(new WorldPoint(13005, 1000, 0));
        when(box.getSceneMinLocation()).thenReturn(new Point(55, 50));
        when(box.getSceneMaxLocation()).thenReturn(new Point(56, 51));
        DecorativeObject talisman = mock(DecorativeObject.class);
        when(talisman.getWorldView()).thenReturn(view);
        when(talisman.getPlane()).thenReturn(0);
        when(talisman.getLocalLocation()).thenReturn(LocalPoint.fromScene(55, 50));
        when(talisman.getWorldLocation()).thenReturn(new WorldPoint(13005, 1000, 0));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            players.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2001, 7048, 2));
            players.when(Rs2Player::getLocalLocation).thenReturn(LocalPoint.fromScene(50, 50));
            assertTrue(new Rs2TileObjectModel(box).isReachable());
            assertTrue(new Rs2TileObjectModel(talisman).isReachable());
            for (int y = 0; y < 104; y++) flags[53][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
            assertFalse(new Rs2TileObjectModel(box).isReachable());
            assertFalse(new Rs2TileObjectModel(talisman).isReachable());
        }
    }
}
