package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
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
import net.runelite.api.VarbitComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
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
        final ShortestPathConfig policy = mock(ShortestPathConfig.class);
        final ConfigManager configManager = mock(ConfigManager.class);
        final Map<Integer, Integer> varbits = new HashMap<>();
        final Transport edge = new Transport(A, B, "read-only", TransportType.TRANSPORT, false, 1);
        final PathfinderConfig owned;
        int failAt;
        boolean onClientThread;

        Fixture(List<Restriction> restrictions) throws Exception {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::getClient).thenReturn(live);
            micro.when(Microbot::getConfigManager).thenReturn(configManager);
            micro.when(() -> Microbot.getVarbitValue(anyInt())).thenThrow(new AssertionError("shared varbit cache read"));
            when(live.getBoostedSkillLevel(any())).thenThrow(new AssertionError("live off-thread level read"));
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> {
                if (callbacks.incrementAndGet() == failAt) return Optional.empty();
                onClientThread = true;
                try { return Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()); }
                finally { onClientThread = false; }
            });
            when(captured.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(captured.getWorld()).thenReturn(301);
            when(captured.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(captured.getLocalPlayer()).thenReturn(mock(Player.class));
            when(captured.getItemContainer(anyInt())).thenReturn(mock(ItemContainer.class));
            when(captured.getBoostedSkillLevel(any())).thenReturn(99);
            when(captured.getVarps()).thenAnswer(call -> {
                assertTrue("varps must be read on CT", onClientThread);
                return new int[1];
            });
            VarbitComposition definition = mock(VarbitComposition.class);
            when(definition.getMostSignificantBit()).thenReturn(31);
            when(captured.getVarbit(anyInt())).thenAnswer(call -> {
                assertTrue("varbit definition must be read on CT", onClientThread);
                return varbits.containsKey(call.getArgument(0)) ? definition : null;
            });
            when(captured.getVarbitValue(any(int[].class), anyInt())).thenAnswer(call -> {
                assertTrue("varbit value must be read on CT", onClientThread);
                return varbits.get(call.getArgument(1));
            });
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

    @Test public void inventoryChronicleUnknownZeroAndPositiveChargesNeverEmitInput() throws Exception {
        chronicleChargesNeverEmitInput(false);
    }

    @Test public void equippedChronicleUnknownZeroAndPositiveChargesNeverEmitInput() throws Exception {
        chronicleChargesNeverEmitInput(true);
    }

    private void chronicleChargesNeverEmitInput(boolean equipped) throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            when(f.policy.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
            Transport chronicle = new Transport(B, "Chronicle", TransportType.TELEPORTATION_ITEM,
                    false, 20, Set.of(Set.of(ItemID.CHRONICLE)));
            f.owned.getAllTransports().put(null, Set.of(chronicle));
            Rs2ItemModel item = mock(Rs2ItemModel.class);
            when(item.getId()).thenReturn(ItemID.CHRONICLE);
            when(item.getQuantity()).thenReturn(1);
            f.inventory.when(() -> Rs2Inventory.hasItem(ItemID.CHRONICLE)).thenReturn(!equipped);
            f.equipment.when(() -> Rs2Equipment.isWearing(ItemID.CHRONICLE)).thenReturn(equipped);
            if (equipped) f.equipment.when(Rs2Equipment::all).thenAnswer(call -> Stream.of(item));
            else f.inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.of(item));
            for (String charges : new String[]{null, "", "0", "3", "invalid"}) {
                when(f.configManager.getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE))
                        .thenReturn(charges);
                f.owned.refreshForPlanning(B, false);
                assertEquals("charges=" + charges, "3".equals(charges), f.owned.getUsableTeleports().contains(chronicle));
            }
            f.micro.when(Microbot::getConfigManager).thenReturn(null);
            f.owned.refreshForPlanning(B, false);
            assertFalse(f.owned.getUsableTeleports().contains(chronicle));
            f.inventory.verify(() -> Rs2Inventory.interact(ItemID.CHRONICLE, "Check charges"), never());
            f.equipment.verify(() -> Rs2Equipment.interact(ItemID.CHRONICLE, "Check charges"), never());
        }
    }

    @Test public void transportVarbitUsesObservedZeroAndPositiveWithoutSharedCache() throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            f.edge.getVarbits().add(new TransportVarbit(12345, 0, TransportVarbit.Operator.EQUAL));
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            f.varbits.put(12345, 0);
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(f.edge), f.owned.getTransports().get(A));
            f.varbits.put(12345, 1);
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            f.edge.getVarbits().clear();
            f.edge.getVarbits().add(new TransportVarbit(12345, 1, TransportVarbit.Operator.EQUAL));
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(f.edge), f.owned.getTransports().get(A));
            f.micro.verify(() -> Microbot.getVarbitValue(anyInt()), never());
        }
    }

    @Test public void restrictionOnlyVarbitsAndCustomRestrictionsUseOwnedValues() throws Exception {
        Restriction resource = new Restriction(3210, 3201, 0);
        resource.getVarbits().add(new TransportVarbit(54321, 0, TransportVarbit.Operator.EQUAL));
        Restriction custom = new Restriction(3211, 3201, 0);
        custom.getVarbits().add(new TransportVarbit(54322, 1, TransportVarbit.Operator.EQUAL));
        try (Fixture f = new Fixture(List.of(resource))) {
            f.owned.setRestrictedTiles(custom);
            f.owned.refreshForPlanning(B, false);
            assertTrue(f.owned.getRestrictedPointsPacked().contains(resource.getPackedWorldPoint()));
            assertTrue(f.owned.getRestrictedPointsPacked().contains(custom.getPackedWorldPoint()));
            f.varbits.put(54321, 0);
            f.varbits.put(54322, 1);
            f.owned.refreshForPlanning(B, false);
            assertFalse(f.owned.getRestrictedPointsPacked().contains(resource.getPackedWorldPoint()));
            assertFalse(f.owned.getRestrictedPointsPacked().contains(custom.getPackedWorldPoint()));
            f.varbits.put(54321, 1);
            f.varbits.put(54322, 0);
            f.owned.refreshForPlanning(B, false);
            assertTrue(f.owned.getRestrictedPointsPacked().contains(resource.getPackedWorldPoint()));
            assertTrue(f.owned.getRestrictedPointsPacked().contains(custom.getPackedWorldPoint()));
        }
    }

    @Test public void unavailableVarpsDefinitionsAndBatchCannotAdmitZeroGate() throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            f.edge.getVarbits().add(new TransportVarbit(12345, 0, TransportVarbit.Operator.EQUAL));
            f.varbits.put(12345, 0);
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(f.edge), f.owned.getTransports().get(A));
            doReturn(null).when(f.captured).getVarps();
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            doReturn(new int[0]).when(f.captured).getVarps();
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            doReturn(new int[1]).when(f.captured).getVarps();
            doThrow(new IllegalStateException("definition unavailable")).when(f.captured).getVarbit(12345);
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            f.failAt = f.callbacks.get() + 4;
            assertThrows(IllegalStateException.class, () -> f.owned.refreshForPlanning(B, false));
            assertTrue(f.owned.getTransports().isEmpty());
        }
    }

    @Test public void housePolicyToggleUsesCapturedValueAndUnknownFailsClosed() throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            // Admit personal-house entries before testing their varbit policy; no facilities are needed.
            when(f.policy.usePoh()).thenReturn(true);
            Transport inside = new Transport(A, B, "Teleport to House (Inside)", TransportType.TRANSPORT, false, 1);
            Transport outside = new Transport(A, B, "Teleport to House (Outside)", TransportType.TRANSPORT, false, 1);
            inside.getVarbits().add(new TransportVarbit(VarbitID.POH_TELE_TOGGLE, 1, TransportVarbit.Operator.EQUAL));
            outside.getVarbits().add(new TransportVarbit(VarbitID.POH_TELE_TOGGLE, 0, TransportVarbit.Operator.EQUAL));
            f.owned.getAllTransports().put(A, Set.of(inside, outside));
            f.owned.refreshForPlanning(B, false);
            assertTrue(f.owned.isPersonalHouseRoutingEnabled());
            assertTrue(f.owned.allowsPersonalHouseEntry(inside));
            assertNull(f.owned.getTransports().get(A));
            f.varbits.put(VarbitID.POH_TELE_TOGGLE, 0);
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(inside), f.owned.getTransports().get(A));
            f.varbits.put(VarbitID.POH_TELE_TOGGLE, 1);
            f.owned.refreshForPlanning(B, false);
            assertEquals(Set.of(outside), f.owned.getTransports().get(A));
            f.varbits.remove(VarbitID.POH_TELE_TOGGLE);
            f.owned.refreshForPlanning(B, false);
            assertNull(f.owned.getTransports().get(A));
            verify(f.captured, times(4)).getVarbit(VarbitID.POH_TELE_TOGGLE);
            verify(f.captured, times(2)).getVarbitValue(any(int[].class), eq(VarbitID.POH_TELE_TOGGLE));
            f.micro.verify(() -> Microbot.getVarbitValue(anyInt()), never());
        }
    }

    @Test public void fairyRingDiaryGateUsesCapturedVarbitNotSharedCache() throws Exception {
        try (Fixture f = new Fixture(List.of()); MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getQuestState(net.runelite.api.Quest.FAIRYTALE_II__CURE_A_QUEEN))
                    .thenReturn(net.runelite.api.QuestState.FINISHED);
            when(f.policy.useFairyRings()).thenReturn(true);
            Transport ring = new Transport(A, B, "Fairy ring", TransportType.FAIRY_RING, false, 1);
            f.owned.getAllTransports().put(A, Set.of(ring));
            for (Integer diary : new Integer[]{null, 0, 1}) {
                if (diary == null) f.varbits.remove(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
                else f.varbits.put(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, diary);
                f.owned.refreshForPlanning(B, false);
                assertEquals(Integer.valueOf(1).equals(diary), f.owned.getTransports().containsKey(A));
            }
        }
    }

    @Test public void legacyUnknownChronicleStillChecksChargesAndLegacyVarbitStillUsesCache() throws Exception {
        try (Fixture f = new Fixture(List.of())) {
            set(f.owned, "planningCopy", false);
            f.inventory.when(() -> Rs2Inventory.hasItem(ItemID.CHRONICLE)).thenReturn(true);
            when(f.configManager.getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE))
                    .thenReturn(null, "2");
            java.lang.reflect.Method charges = PathfinderConfig.class.getDeclaredMethod("hasChronicleCharges");
            charges.setAccessible(true);
            assertEquals(true, charges.invoke(f.owned));
            f.inventory.verify(() -> Rs2Inventory.interact(ItemID.CHRONICLE, "Check charges"));

            f.edge.getVarbits().add(new TransportVarbit(12345, 0, TransportVarbit.Operator.EQUAL));
            f.micro.when(() -> Microbot.getVarbitValue(12345)).thenReturn(0);
            java.lang.reflect.Method varbits = PathfinderConfig.class.getDeclaredMethod("varbitChecks", Transport.class);
            varbits.setAccessible(true);
            assertEquals(true, varbits.invoke(f.owned, f.edge));
            f.micro.verify(() -> Microbot.getVarbitValue(12345));
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = PathfinderConfig.class.getDeclaredField(name);
        field.setAccessible(true); field.set(object, value);
    }
}
