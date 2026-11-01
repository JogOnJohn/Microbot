package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.util.walker.WalkerApiAssertions.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OwnedPlanningRefreshTest {
    private static final WorldPoint A = new WorldPoint(3201, 3201, 0), B = new WorldPoint(3210, 3201, 0);

    /** Equivalent to the frozen Client proxy, without requiring an interactive client/cache hierarchy. */
    private static final class Fixture implements AutoCloseable {
        final Client captured = mock(Client.class), live = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final AtomicInteger callbacks = new AtomicInteger();
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
        final MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
        final MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
        final MockedStatic<Rs2LeaguesTransport> leagues = mockStatic(Rs2LeaguesTransport.class);
        final Transport edge = new Transport(A, B, "read-only", TransportType.TRANSPORT, false, 1);
        final PathfinderConfig owned;
        int failAt;

        Fixture(List<Restriction> restrictions) throws Exception {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::getClient).thenReturn(live);
            when(live.getBoostedSkillLevel(any())).thenThrow(new AssertionError("live off-thread level read"));
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> {
                if (callbacks.incrementAndGet() == failAt) return Optional.empty();
                return Optional.ofNullable(((Callable<?>) call.getArgument(0)).call());
            });
            when(captured.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(captured.getWorld()).thenReturn(301);
            when(captured.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(captured.getLocalPlayer()).thenReturn(mock(Player.class));
            when(captured.getItemContainer(anyInt())).thenReturn(mock(ItemContainer.class));
            when(captured.getBoostedSkillLevel(any())).thenReturn(99);
            ShortestPathConfig policy = mock(ShortestPathConfig.class);
            when(policy.calculationCutoff()).thenReturn(10);
            when(policy.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
            equipment.when(Rs2Equipment::all).thenAnswer(call -> Stream.empty());
            bank.when(Rs2Bank::getAll).thenAnswer(call -> Stream.empty());
            Rs2LeaguesTransport.LeaguesContext inactive = mock(Rs2LeaguesTransport.LeaguesContext.class);
            leagues.when(Rs2LeaguesTransport::planningContext).thenReturn(inactive);
            owned = new PathfinderConfig(SplitFlagMap.fromResources(), Map.of(A, Set.of(edge)), restrictions, captured, policy, false);
            set(owned, "planningCopy", true);
            set(owned, "planningHouse", PathfinderConfig.PlanningHouse.empty());
            set(owned, "planningDeadlineNanos", Long.MAX_VALUE);
            set(owned, "maximumQuestPoints", 1);
        }

        @Override public void close() { leagues.close(); bank.close(); equipment.close(); inventory.close(); micro.close(); }
    }

    @Test public void successfulOwnedRefreshUsesCapturedRestrictionLevelsAndExplicitBankPolicy() throws Exception {
        Restriction restriction = new Restriction(Map.of("Origin", "3210 3201 0", "Skills", "50 Agility"));
        try (Fixture f = new Fixture(List.of(restriction))) {
            f.owned.refreshForPlanning(B, true);
            assertTrue(f.owned.isUseBankItems());
            assertEquals(Set.of(f.edge), f.owned.getTransports().get(A));
            assertFalse(f.owned.getRestrictedPointsPacked().contains(WorldPointUtil.packWorldPoint(B)));
            when(f.captured.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(1);
            f.owned.refreshForPlanning(B, false);
            assertFalse(f.owned.isUseBankItems());
            assertTrue(f.owned.getRestrictedPointsPacked().contains(WorldPointUtil.packWorldPoint(B)));
            verify(f.live, never()).getBoostedSkillLevel(any());
            f.leagues.verify(() -> Rs2LeaguesTransport.injectLeaguesTransports(any(), any(), any(), any(), any(), any()), never());
        }
    }

    @Test public void observedVarplayerZeroCanAdmitButTimedOutBatchCannot() throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            f.edge.getVarplayers().add(new TransportVarPlayer(12345, 0, TransportVarPlayer.Operator.EQUAL));
            f.failAt = 4;
            assertThrows(IllegalStateException.class, () -> f.owned.refreshForPlanning(B, false));
            assertTrue(f.owned.getTransports().isEmpty());
            f.failAt = 0;
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(f.edge), f.owned.getTransports().get(A));
        }
    }

    @Test public void restrictionVarplayerTimeoutIsNotZero() throws Exception {
        Restriction restriction = new Restriction(3210, 3201, 0);
        restriction.getVarplayers().add(new TransportVarPlayer(54321, 0, TransportVarPlayer.Operator.EQUAL));
        try (Fixture f = new Fixture(List.of(restriction))) {
            f.failAt = 5;
            assertThrows(IllegalStateException.class, () -> f.owned.refreshForPlanning(B, true));
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = PathfinderConfig.class.getDeclaredField(name);
        field.setAccessible(true); field.set(object, value);
    }
}
