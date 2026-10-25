package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WalkerSceneClickSafetyTest {
    @Test
    public void clickAreaIsBoundedAroundTheProjectedTile() {
        assertEquals(new Rectangle(96, 96, 8, 8), WalkerSceneClickSafety.clickArea(new Point(100, 100)));
        assertTrue(WalkerSceneClickSafety.isSafePoint(new Point(100, 100), new Rectangle(0, 0, 800, 600), Collections.emptyList()));
    }

    @Test
    public void overlappingEdgesAndViewportClippingAreRejected() {
        Rectangle viewport = new Rectangle(0, 0, 800, 600);
        assertFalse(WalkerSceneClickSafety.isSafePoint(new Point(498, 100), viewport,
                Collections.singletonList(new Rectangle(500, 0, 300, 600))));
        assertFalse(WalkerSceneClickSafety.isSafePoint(new Point(2, 100), viewport, Collections.emptyList()));
        assertFalse(WalkerSceneClickSafety.isSafePoint(null, viewport, Collections.emptyList()));
        assertFalse(WalkerSceneClickSafety.isSafePoint(new Point(100, 100), null, Collections.emptyList()));
    }

    @Test
    public void liveOverlayReadsAreOnClientThread() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        Widget panel = mock(Widget.class);
        AtomicBoolean onThread = new AtomicBoolean();
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            onThread.set(true);
            try { return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()); }
            finally { onThread.set(false); }
        });
        when(client.getViewportWidth()).thenReturn(800);
        when(client.getViewportHeight()).thenReturn(600);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU)).thenReturn(panel);
        when(panel.isHidden()).thenAnswer(inv -> { assertTrue(onThread.get()); return false; });
        when(panel.getBounds()).thenAnswer(inv -> { assertTrue(onThread.get()); return new Rectangle(500, 0, 300, 600); });
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertTrue(WalkerSceneClickSafety.isSafe(new Point(100, 100)));
            assertFalse(WalkerSceneClickSafety.isSafe(new Point(600, 100)));
            doReturn(Optional.empty()).when(thread).runOnClientThreadOptional(any());
            assertFalse(WalkerSceneClickSafety.isSafe(new Point(100, 100)));
        }
    }
}
