package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class InteractionTargetRuntimeTest {
    @Test public void liveDoorChangeInvalidatesCachedReadinessBeforeDispatch() throws Exception {
        try (MockedStatic<Microbot> micro = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class)) {
            Client c = mock(Client.class);
            ClientThread ct = mock(ClientThread.class);
            when(ct.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            micro.when(Microbot::getClient).thenReturn(c);
            micro.when(Microbot::getClientThread).thenReturn(ct);
            WorldView view = mock(WorldView.class);
            when(c.getTopLevelWorldView()).thenReturn(view);
            when(view.getScene()).thenReturn(mock(Scene.class));
            when(c.getLocalPlayer()).thenReturn(mock(Player.class));
            when(c.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(c.getTickCount()).thenReturn(100);
            when(c.getViewportWidth()).thenReturn(100);
            when(c.getViewportHeight()).thenReturn(100);
            WorldPoint p = new WorldPoint(3242, 3450, 0), n = p.dy(2);
            player.when(Rs2Player::getWorldLocation).thenReturn(p);
            NPC npc = mock(NPC.class);
            when(npc.getWorldView()).thenReturn(view);
            when(npc.getWorldLocation()).thenReturn(n);
            when(npc.getConvexHull()).thenReturn(new Rectangle(10, 10, 20, 20));
            NPCComposition definition = mock(NPCComposition.class);
            when(definition.getActions()).thenReturn(new String[]{"Talk-to"});
            when(npc.getTransformedComposition()).thenReturn(definition);
            Rs2NpcModel model = mock(Rs2NpcModel.class);
            when(model.getNpc()).thenReturn(npc);
            Rs2NpcCache cache = mock(Rs2NpcCache.class, RETURNS_DEEP_STUBS);
            micro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            when(cache.query().withId(10415).where(any()).nearest()).thenReturn(model);
            HashMap<WorldPoint, Integer> reachable = new HashMap<>(); reachable.put(n, 2);
            tiles.when(() -> Rs2Tile.getReachableTilesFromTile(p, 10)).thenReturn(reachable);
            InteractionRequest request = InteractionRequest.npc(10415, "Jeff", n, "Talk-to", () -> false);
            var ready = request.runtime.probe(false);
            assertTrue(ready.ready);
            reachable.clear();
            assertTrue(request.runtime.probe(false).ready); // same-tick memoization is read-only
            assertFalse(request.runtime.dispatch(ready, () -> true)); // fresh collision at dispatch
            verify(model, never()).click(anyString(), any());
            when(view.isInstance()).thenReturn(true);
            assertTrue(request.runtime.probe(true).sceneReady);
            assertFalse(request.runtime.probe(true).ready);
            when(c.getGameState()).thenReturn(GameState.LOADING);
            assertFalse(request.runtime.probe(true).sceneReady);
        }
    }
}
