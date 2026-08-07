package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathfinderClientThreadGuardTest {
    @Test
    public void clientThreadSearchIsRejectedBeforeMapAccess() throws Exception {
        Field field = Microbot.class.getDeclaredField("clientThread");
        field.setAccessible(true);
        Object original = field.get(null);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.isClientThread()).thenReturn(true);
        PathfinderConfig config = mock(PathfinderConfig.class);
        Pathfinder pathfinder = new Pathfinder(config,
                new WorldPoint(3200, 3200, 0), Set.of(new WorldPoint(3201, 3200, 0)));
        try {
            field.set(null, clientThread);
            pathfinder.run();
            assertTrue(pathfinder.isDone());
            assertTrue(pathfinder.getPath().isEmpty());
            verify(config, never()).getMap();
        } finally {
            field.set(null, original);
        }
    }
}
