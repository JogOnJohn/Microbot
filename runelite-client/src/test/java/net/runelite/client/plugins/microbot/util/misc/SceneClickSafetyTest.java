package net.runelite.client.plugins.microbot.util.misc;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class SceneClickSafetyTest {
    @Test public void coveredAndUnknownGeometryCannotAuthorizeClicks() {
        Rectangle target = new Rectangle(80, 80, 20, 20);
        Rectangle viewport = new Rectangle(0, 0, 800, 600);
        assertNull(SceneClickSafety.clearArea(target, viewport, List.of(target), null));
        assertNull(SceneClickSafety.clearArea(target, null, List.of(), null));
        assertNull(SceneClickSafety.clearArea(target, viewport, null, null));
        assertNull(SceneClickSafety.clearArea(null, viewport, List.of(), null));
    }

    @Test public void existingSafePrehoverPointWinsOverALargerClearFragment() {
        Rectangle target = new Rectangle(20, 20, 100, 100);
        Rectangle cover = new Rectangle(30, 20, 15, 100);
        Point preferred = new Point(23, 50);
        Rectangle clear = SceneClickSafety.clearArea(target, new Rectangle(0, 0, 800, 600), List.of(cover), preferred);
        assertTrue(SceneClickSafety.contains(clear, preferred));
        assertFalse(clear.intersects(cover));
    }

    @Test public void originRegionsDoNotBecomeLegacyFallbackClicks() {
        Rectangle clear = SceneClickSafety.clearArea(new Rectangle(0, 0, 40, 40),
            new Rectangle(0, 0, 800, 600), List.of(), null);
        assertNotNull(clear);
        assertFalse(Rs2UiHelper.isFallbackRectangle(clear));
        assertNull(SceneClickSafety.clearArea(new Rectangle(0, 0, 1, 1),
            new Rectangle(0, 0, 800, 600), List.of(), null));
    }

    @Test public void rectangleSubtractionNeverEscapesTargetViewportOrOverlays() {
        Random random = new Random(173);
        Rectangle viewport = new Rectangle(10, 10, 800, 600);
        for (int i = 0; i < 400; i++) {
            Rectangle target = new Rectangle(random.nextInt(900)-50, random.nextInt(700)-50,
                random.nextInt(200)+1, random.nextInt(150)+1);
            List<Rectangle> covers = Arrays.asList(new Rectangle(0, 430, 510, 180), new Rectangle(580, 20, 220, 450));
            Rectangle clear = SceneClickSafety.clearArea(target, viewport, covers, null);
            if (clear == null) continue;
            assertTrue(target.contains(clear));
            assertTrue(viewport.contains(clear));
            for (Rectangle cover : covers) {
                Rectangle padded = new Rectangle(cover);
                padded.grow(4, 4);
                assertFalse(clear.intersects(padded));
            }
        }
    }

    @Test public void shapeViewportAndWidgetsShareAClientThreadSnapshot() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        GameObject object = mock(GameObject.class);
        Widget widget = mock(Widget.class);
        boolean[] onClientThread = {false};
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            onClientThread[0] = true;
            try { return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()); }
            finally { onClientThread[0] = false; }
        });
        when(object.getClickbox()).thenAnswer(inv -> { assertTrue(onClientThread[0]); return new Rectangle(100, 100, 30, 30); });
        when(client.getViewportWidth()).thenAnswer(inv -> { assertTrue(onClientThread[0]); return 800; });
        when(client.getViewportHeight()).thenReturn(600);
        when(client.getWidget(anyInt())).thenAnswer(inv -> { assertTrue(onClientThread[0]); return widget; });
        when(widget.isHidden()).thenAnswer(inv -> { assertTrue(onClientThread[0]); return false; });
        when(widget.getBounds()).thenAnswer(inv -> { assertTrue(onClientThread[0]); return new Rectangle(0, 400, 500, 200); });
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            assertNotNull(SceneClickSafety.forObject(object, null));
            doReturn(null).when(widget).getBounds();
            assertNull(SceneClickSafety.forObject(object, null));
            doReturn(Optional.empty()).when(thread).runOnClientThreadOptional(any());
            assertNull(SceneClickSafety.forObject(object, null));
        }
    }

    @Test public void warningsAreThrottledIndependentlyByObject() {
        assertTrue(SceneClickSafety.shouldWarnCovered(987651, 100_000));
        assertTrue(SceneClickSafety.shouldWarnCovered(987652, 100_000));
        assertFalse(SceneClickSafety.shouldWarnCovered(987651, 100_001));
        assertTrue(SceneClickSafety.shouldWarnCovered(987651, 110_000));
    }
}
