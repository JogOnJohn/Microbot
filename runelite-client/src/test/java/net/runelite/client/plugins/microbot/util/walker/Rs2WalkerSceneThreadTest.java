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
    public void unresolvedDoorProbeFailsClosedWhenClientReadIsUnavailable() throws Exception
    {
        Field threadField = Microbot.class.getDeclaredField("clientThread");
        threadField.setAccessible(true);
        Object oldThread = threadField.get(null);
        try
        {
            ClientThread dispatcher = mock(ClientThread.class);
            when(dispatcher.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
            threadField.set(null, dispatcher);
            Method method = Rs2Walker.class.getDeclaredMethod("hasUnresolvedDoorLikeObjectNearRawPath",
                    java.util.List.class, int.class, WorldPoint.class, int.class, int.class, int.class);
            method.setAccessible(true);
            assertEquals(true, method.invoke(null, null, 0, new WorldPoint(3200, 3200, 0), 2, 14, 10));
            verify(dispatcher).runOnClientThreadOptional(any());
        }
        finally { threadField.set(null, oldThread); }
    }

    @Test
    public void batchedCandidateSelectionFailsClosedWhenClientReadIsUnavailable() throws Exception
    {
        Field threadField = Microbot.class.getDeclaredField("clientThread");
        threadField.setAccessible(true);
        Object oldThread = threadField.get(null);
        try
        {
            ClientThread dispatcher = mock(ClientThread.class);
            when(dispatcher.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
            threadField.set(null, dispatcher);
            Method method = Rs2Walker.class.getDeclaredMethod("findFurthestRawPathPointMatchingGated",
                    java.util.List.class, WorldPoint.class, int.class, int.class, java.util.function.Predicate.class);
            method.setAccessible(true);
            assertNull(method.invoke(null, null, new WorldPoint(3200, 3200, 0), 24, 0,
                    (java.util.function.Predicate<WorldPoint>) point -> true));
            verify(dispatcher).runOnClientThreadOptional(any());
        }
        finally { threadField.set(null, oldThread); }
    }

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
