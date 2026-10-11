package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.widgets.Widget;
import org.junit.Test;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PohMenuInteractionTest {
    private Widget option(String text) {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn(text);
        when(widget.getBounds()).thenReturn(new Rectangle(10, 10, 100, 20));
        return widget;
    }

    @Test
    public void searchesDynamicChildrenButRejectsHiddenAndEmptyBounds() {
        Widget root = mock(Widget.class);
        Widget hidden = option("Xeric's Glade");
        when(hidden.isHidden()).thenReturn(true);
        Widget unlaidOut = option("Xeric's Glade");
        when(unlaidOut.getBounds()).thenReturn(new Rectangle());
        Widget good = option("<col=ff0000>2. Xeric's Glade</col>");
        when(root.getDynamicChildren()).thenReturn(new Widget[]{hidden, unlaidOut, good});
        assertSame(good, PohMenuInteraction.findOption(root, "Xeric's Glade"));
        assertNull(PohMenuInteraction.findOption(root, "Xeric's Heart"));
    }

    @Test
    public void lateOptionIsSelectedWithoutRepeatedApproachClick() {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger ready = new AtomicInteger();
        Widget option = option("Xeric's Glade");
        assertEquals(PohMenuInteraction.Result.CLICKED, PohMenuInteraction.select(
                () -> ready.get() == 1 ? option : null,
                () -> { opens.incrementAndGet(); return true; },
                condition -> { ready.set(1); return condition.getAsBoolean(); }, widget -> true));
        assertEquals(1, opens.get());
    }

    @Test
    public void failedClickGetsOneRetryUsingTheOpenMenu() {
        AtomicInteger clicks = new AtomicInteger();
        assertEquals(PohMenuInteraction.Result.CLICKED, PohMenuInteraction.select(
                () -> option("Woodcutting Guild"), () -> { fail(); return false; },
                condition -> { fail(); return false; }, widget -> clicks.incrementAndGet() == 2));
        assertEquals(2, clicks.get());
    }

    @Test
    public void readinessTimeoutIsTransientAndDoesNotReclickApproach() {
        AtomicInteger opens = new AtomicInteger();
        assertEquals(PohMenuInteraction.Result.TRANSIENT_FAILURE, PohMenuInteraction.select(
                () -> null, () -> { opens.incrementAndGet(); return true; },
                condition -> false, widget -> { fail(); return false; }));
        assertEquals(1, opens.get());
    }

    @Test
    public void explicitLockedOptionIsUnavailableWithoutClicking() {
        assertEquals(PohMenuInteraction.Result.UNAVAILABLE, PohMenuInteraction.select(
                () -> option("<str>Woodcutting Guild</str>"), () -> false,
                condition -> false, widget -> { fail(); return false; }));
        assertEquals(600_000L, PohTeleports.failedTeleportCooldownMs(PohMenuInteraction.Result.UNAVAILABLE));
        assertEquals(15_000L, PohTeleports.failedTeleportCooldownMs(PohMenuInteraction.Result.TRANSIENT_FAILURE));
        assertEquals(0L, PohTeleports.failedTeleportCooldownMs(PohMenuInteraction.Result.CANCELLED));
    }

    @Test
    public void interruptionDoesNotClickOrExcludeDestination() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(PohMenuInteraction.Result.CANCELLED, PohMenuInteraction.select(
                    () -> { fail(); return null; }, () -> false, condition -> false, widget -> false));
        } finally { Thread.interrupted(); }
    }
}
