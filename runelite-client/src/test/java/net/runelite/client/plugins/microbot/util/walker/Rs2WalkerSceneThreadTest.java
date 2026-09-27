package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class Rs2WalkerSceneThreadTest
{
    @Test
    public void sceneWorldViewReadRunsInsideClientThreadDispatch() throws Exception
    {
        Field clientField = Microbot.class.getDeclaredField("client");
        Field threadField = Microbot.class.getDeclaredField("clientThread");
        clientField.setAccessible(true); threadField.setAccessible(true);
        Object oldClient = clientField.get(null), oldThread = threadField.get(null);
        try
        {
            Client client = mock(Client.class);
            ClientThread dispatcher = mock(ClientThread.class);
            AtomicBoolean dispatched = new AtomicBoolean();
            doAnswer(call -> {
                dispatched.set(true);
                try { return Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()); }
                finally { dispatched.set(false); }
            }).when(dispatcher).runOnClientThreadOptional(any());
            when(client.getTopLevelWorldView()).thenAnswer(call -> {
                assertTrue("WorldView reads must be inside the client-thread dispatch", dispatched.get());
                return null;
            });
            clientField.set(null, client); threadField.set(null, dispatcher);
            Method method = Rs2Walker.class.getDeclaredMethod("sceneTileStatus", WorldPoint.class);
            method.setAccessible(true);
            assertEquals("UNKNOWN", method.invoke(null, new WorldPoint(3200, 3200, 0)).toString());
            verify(dispatcher).runOnClientThreadOptional(any());
            verify(client).getTopLevelWorldView();
        }
        finally { clientField.set(null, oldClient); threadField.set(null, oldThread); }
    }
}
