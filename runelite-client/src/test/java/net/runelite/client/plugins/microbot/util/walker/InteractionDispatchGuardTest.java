package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionTargetRuntime;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractionDispatchGuardTest {
    @Test public void legacyEntriesAreUnchangedAndCancellationInvalidatesQueuedEntry() {
        assertTrue(new NewMenuEntry().isDispatchValid());
        AtomicBoolean owned = new AtomicBoolean(true);
        NewMenuEntry entry = new NewMenuEntry().validWhen(owned::get);
        assertTrue(entry.isDispatchValid()); owned.set(false); assertFalse(entry.isDispatchValid());
    }
    @Test public void throwingGuardFailsClosed() {
        assertFalse(new NewMenuEntry().validWhen(() -> { throw new IllegalStateException("stale scene"); }).isDispatchValid());
    }
    @Test public void containedClickboxIsVisibleAndOffscreenOrEmptyIsNot() {
        Path2D shape = new Path2D.Double();
        shape.moveTo(10, 10); shape.lineTo(30, 10); shape.lineTo(30, 30); shape.closePath();
        assertTrue(InteractionTargetRuntime.visible(shape, new Rectangle(0, 0, 100, 100)));
        assertFalse(InteractionTargetRuntime.visible(shape, new Rectangle(200, 200, 100, 100)));
        assertFalse(InteractionTargetRuntime.visible(null, new Rectangle(0, 0, 100, 100)));
    }
}
