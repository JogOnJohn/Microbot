package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedger;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedgerTest;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

public class Rs2WalkerPreparationReuseTest {
    @Test
    public void walkerOnlyInvalidatesWhenBlockedEligibilityIsRestored() throws Exception {
        PathfinderConfig config = mock(PathfinderConfig.class);
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        when(config.getTransportRefusals()).thenReturn(ledger);
        Method begin = Rs2Walker.class.getDeclaredMethod("beginTransportRefusalWalk", WorldPoint.class);
        begin.setAccessible(true);
        WorldPoint goal = new WorldPoint(3222, 9618, 0);
        WorldPoint origin = new WorldPoint(3222, 3218, 0);
        try (MockedStatic<Rs2PathApi> api = mockStatic(Rs2PathApi.class)) {
            api.when(Rs2PathApi::getPathfinderConfig).thenReturn(config);
            begin.invoke(null, goal);
            begin.invoke(null, (WorldPoint) null);
            begin.invoke(null, goal);
            verify(config, never()).invalidateTransportRefreshCache();
            var transport = TransportRefusalLedgerTest.transport(100);
            ledger.failure(ledger.generation(), transport, origin, origin, true, true, 1000);
            ledger.failure(ledger.generation(), transport, origin, origin, true, true, 2000);
            begin.invoke(null, goal);
            verify(config, never()).invalidateTransportRefreshCache();
            begin.invoke(null, (WorldPoint) null);
            verify(config).invalidateTransportRefreshCache();
            begin.invoke(null, goal);
            verify(config, times(1)).invalidateTransportRefreshCache();
        }
    }
}
