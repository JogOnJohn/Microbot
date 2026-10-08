package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankRouteSupplyBoundaryTest {
    @Test
    public void bankedSuppliesAreOnlyAvailableAfterReachingTheBank() {
        PathfinderConfig config = mock(PathfinderConfig.class);
        AtomicBoolean banked = new AtomicBoolean(false);
        List<Boolean> modes = new ArrayList<>();
        when(config.isUseBankItems()).thenAnswer(call -> banked.get());
        doAnswer(call -> { banked.set(call.getArgument(0)); return null; })
                .when(config).setUseBankItems(anyBoolean());
        WorldPoint start = new WorldPoint(3241, 3471, 0);
        WorldPoint target = new WorldPoint(3047, 3343, 0);
        try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            api.when(Rs2PathApi::getPathfinderConfig).thenReturn(config);
            bank.when(() -> Rs2Bank.getNearestBank(start)).thenAnswer(call -> {
                assertFalse("Bank selection must not use items still in the bank", banked.get());
                return BankLocation.VARROCK_EAST;
            });
            walker.when(() -> Rs2Walker.getWalkPath(any(WorldPoint.class), any(WorldPoint.class)))
                    .thenAnswer(call -> { modes.add(banked.get()); return List.of(start, call.getArgument(1)); });
            walker.when(() -> Rs2Walker.getTotalTilesFromPath(anyList(), any(WorldPoint.class))).thenReturn(20);
            Rs2WalkerBankingPlanner.compareRoutes(start, target);
            assertEquals(List.of(false, false, true), modes);
            assertFalse("Simulation must restore inventory-only mode", banked.get());
        }
    }
}
