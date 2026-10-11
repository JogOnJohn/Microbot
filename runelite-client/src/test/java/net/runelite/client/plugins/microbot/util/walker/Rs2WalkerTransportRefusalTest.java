package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedger;
import net.runelite.client.plugins.microbot.shortestpath.transport.TransportRefusalLedgerTest;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Rs2WalkerTransportRefusalTest {
    @Test public void walkerRecalcRetainsBansButNewDestinationAndCancellationInvalidateCatalog() throws Exception {
        PathfinderConfig previous = ShortestPathPlugin.pathfinderConfig;
        PathfinderConfig config = mock(PathfinderConfig.class);
        TransportRefusalLedger ledger = new TransportRefusalLedger();
        when(config.getTransportRefusals()).thenReturn(ledger);
        Method begin = Rs2Walker.class.getDeclaredMethod("beginTransportRefusalWalk", WorldPoint.class);
        begin.setAccessible(true);
        Transport transport = TransportRefusalLedgerTest.transport(100);
        WorldPoint goal = transport.getDestination(), origin = transport.getOrigin();
        try {
            ShortestPathPlugin.pathfinderConfig = config;
            begin.invoke(null, goal);
            ledger.failure(ledger.generation(), transport, origin, origin, true, true, 1000);
            ledger.failure(ledger.generation(), transport, origin, origin, true, true, 2000);
            begin.invoke(null, goal);
            assertTrue(ledger.isBlocked(transport));
            verify(config, times(1)).invalidateTransportRefreshCache();
            begin.invoke(null, new WorldPoint(3200, 3200, 0));
            assertFalse(ledger.isBlocked(transport));
            begin.invoke(null, (WorldPoint) null);
            verify(config, times(3)).invalidateTransportRefreshCache();
        } finally {
            ShortestPathPlugin.pathfinderConfig = previous;
        }
    }
}
