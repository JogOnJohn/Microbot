package net.runelite.client.plugins.microbot.util.walker;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Rs2WalkerMinigameWidgetTest {
    private MockedStatic<Microbot> microbot;
    private Client client;
    private ClientThread thread;
    private final AtomicBoolean onClientThread = new AtomicBoolean();

    @Before
    public void setUp() {
        client = mock(Client.class);
        thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            boolean previous = onClientThread.getAndSet(true);
            try { return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()); }
            finally { onClientThread.set(previous); }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(thread);
    }

    @After
    public void tearDown() { microbot.close(); }

    @Test
    public void missingWidgetsFailClosed() {
        assertFalse(Rs2Walker.minigameWidgetTextMatches(1, "Castle Wars"));
        assertFalse(Rs2Walker.minigameDropdownSelected(1, 773));
        assertFalse(Rs2Walker.minigameGroupingSelected(1, new Object[]{489, 0, 0}));
        assertNull(Rs2Walker.minigameDestinationEntry(1, "Castle Wars"));
    }

    @Test
    public void textReadsAreOnClientThreadAndNullSafe() {
        Widget widget = mock(Widget.class);
        when(client.getWidget(1)).thenReturn(widget);
        when(widget.getText()).thenAnswer(inv -> { assertTrue(onClientThread.get()); return null; });
        assertFalse(Rs2Walker.minigameWidgetTextMatches(1, "Castle Wars"));
        doAnswer(inv -> { assertTrue(onClientThread.get()); return "Castle Wars"; }).when(widget).getText();
        assertTrue(Rs2Walker.minigameWidgetTextMatches(1, "castle wars"));
        assertFalse(Rs2Walker.minigameWidgetTextMatches(1, "Pest Control"));
        when(client.getWidget(1)).thenReturn(null);
        assertFalse(Rs2Walker.minigameWidgetTextMatches(1, "Castle Wars"));
    }

    @Test
    public void timeoutDoesNotReadWidgets() {
        doReturn(Optional.empty()).when(thread).runOnClientThreadOptional(any());
        assertFalse(Rs2Walker.minigameWidgetTextMatches(1, "Castle Wars"));
        assertNull(Rs2Walker.minigameDestinationEntry(1, "Castle Wars"));
        verifyNoInteractions(client);
    }
}
