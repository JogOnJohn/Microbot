package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class PathfinderConfigRefreshReadinessTest {
    @Test
    public void loadingAndMissingLoggedInSnapshotsDeferButRealLogoutDoesNot() {
        for (boolean player : new boolean[]{false, true}) {
            for (boolean inventory : new boolean[]{false, true}) {
                assertTrue(PathfinderConfig.shouldDeferTransportRefresh(GameState.LOADING, player, inventory));
                assertEquals(!player || !inventory,
                        PathfinderConfig.shouldDeferTransportRefresh(GameState.LOGGED_IN, player, inventory));
                assertFalse(PathfinderConfig.shouldDeferTransportRefresh(GameState.LOGIN_SCREEN, player, inventory));
            }
        }
    }

    @Test
    public void deferredRefreshPreservesMapsAndPendingTarget() throws Exception {
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        PathfinderConfig config = new PathfinderConfig(null, Collections.emptyMap(),
                Collections.emptyList(), client, null);
        WorldPoint origin = new WorldPoint(1877, 7052, 1);
        WorldPoint target = new WorldPoint(2964, 3378, 0);
        Transport retained = new Transport(origin, target, "Retained hosted exit",
                TransportType.TELEPORTATION_PORTAL, true, 4);
        config.getTransports().put(origin, Collections.singleton(retained));
        config.getUsableTeleports().add(retained);
        config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(origin), Collections.singleton(retained));
        try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            for (GameState state : new GameState[]{GameState.LOADING, GameState.LOGGED_IN}) {
                when(client.getGameState()).thenReturn(state);
                when(client.getLocalPlayer()).thenReturn(null);
                config.refresh(target);
                assertEquals(Collections.singleton(retained), config.getTransports().get(origin));
                assertTrue(config.getUsableTeleports().contains(retained));
                assertTrue(config.getTransportsPacked().get(WorldPointUtil.packWorldPoint(origin)).contains(retained));
                assertEquals(target, field(config, "pendingRefreshTarget"));
                assertTrue(((AtomicBoolean) field(config, "refreshDirty")).get());
            }
            // A player without a container is also unknown, not an empty-inventory refresh.
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            config.refresh(target);
            assertEquals(Collections.singleton(retained), config.getTransports().get(origin));
            assertTrue(((AtomicBoolean) field(config, "refreshDirty")).get());
        }
    }

    @Test
    public void presentEmptyContainerIsReady() throws Exception {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getItemContainer(InventoryID.INV)).thenReturn(mock(ItemContainer.class));
        PathfinderConfig config = new PathfinderConfig(null, Collections.emptyMap(),
                Collections.emptyList(), client, null);
        java.lang.reflect.Method snapshot = PathfinderConfig.class.getDeclaredMethod("hasTransportRefreshSnapshot");
        snapshot.setAccessible(true);
        assertTrue((boolean) snapshot.invoke(config));
    }

    private static Object field(PathfinderConfig config, String name) throws Exception {
        Field field = PathfinderConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(config);
    }
}
