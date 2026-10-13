package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.widgets.Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import net.runelite.api.DecorativeObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PohMenuInteractionTest {
    @Test public void mountedXericUsesReachableCopyInsteadOfUnreachableSceneMatch() {
        var cache = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache.class);
        var query = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable.class, RETURNS_SELF);
        var reachable = mock(net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel.class);
        when(cache.query()).thenReturn(query);
        when(query.nearestReachable()).thenReturn(reachable);
        when(reachable.getId()).thenReturn(MountedXerics.GLADE.getObjectId());
        var composition = mock(net.runelite.api.ObjectComposition.class);
        when(reachable.getObjectComposition()).thenReturn(composition);
        when(composition.getActions()).thenReturn(new String[]{"Glade", "Teleport menu"});
        when(reachable.click("Glade")).thenReturn(true);
        try (MockedStatic<net.runelite.client.plugins.microbot.Microbot> microbot =
                     mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class)) {
            microbot.when(net.runelite.client.plugins.microbot.Microbot::getRs2TileObjectCache).thenReturn(cache);
            assertTrue(MountedXerics.GLADE.execute());
            verify(reachable).click("Glade");
            objects.verifyNoInteractions();
        }
    }

    @Test public void mountedXericSearchesObservedOptionsSiblingAndReusesOpenMenu() {
        Widget background = mock(Widget.class), options = mock(Widget.class);
        Widget glade = option("2: Xeric's Glade");
        Widget lookout = option("1: Xeric's Lookout");
        when(options.getDynamicChildren()).thenReturn(new Widget[]{lookout, glade});
        DecorativeObject talisman = mock(DecorativeObject.class);
        when(talisman.getId()).thenReturn(33414);
        var cache = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache.class);
        var query = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        try (MockedStatic<net.runelite.client.plugins.microbot.Microbot> microbot =
                     mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class);
             MockedStatic<PohMenuInteraction> menus = mockStatic(PohMenuInteraction.class, CALLS_REAL_METHODS)) {
            microbot.when(net.runelite.client.plugins.microbot.Microbot::getRs2TileObjectCache).thenReturn(cache);
            widgets.when(() -> Rs2Widget.getWidget(InterfaceID.MENU, 0)).thenReturn(background);
            widgets.when(() -> Rs2Widget.getWidget(InterfaceID.MENU, 3)).thenReturn(options);
            objects.when(() -> Rs2GameObject.getDecorativeObject(MountedXerics.IDS)).thenReturn(talisman);
            menus.when(() -> PohMenuInteraction.select(anyString(), any(BooleanSupplier.class), any(Supplier.class)))
                    .thenAnswer(call -> {
                        Supplier<Widget[]> roots = call.getArgument(2);
                        Widget found = null;
                        for (Widget root : roots.get()) {
                            found = PohMenuInteraction.findOption(root, call.getArgument(0));
                            if (found != null) break;
                        }
                        assertSame(glade, found);
                        return PohMenuInteraction.select(() -> glade,
                                () -> { fail("Must reuse the visible menu"); return false; },
                                condition -> false, selected -> selected == glade) == PohMenuInteraction.Result.CLICKED;
                    });
            assertTrue(MountedXerics.GLADE.execute());
        }
    }

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
