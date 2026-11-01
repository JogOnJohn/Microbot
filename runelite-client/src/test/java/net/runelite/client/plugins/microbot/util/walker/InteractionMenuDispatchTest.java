package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class InteractionMenuDispatchTest {
    @After
    public void clearMenu() {
        Microbot.targetMenu = null;
    }

    @Test
    public void legacyEntriesRemainValidButExplicitNullAndThrowingGuardsFailClosed() {
        assertTrue(new NewMenuEntry().isDispatchValid());
        assertFalse(new NewMenuEntry().validWhen(null).isDispatchValid());
        assertFalse(new NewMenuEntry().validWhen(() -> { throw new IllegalStateException(); }).isDispatchValid());
    }

    @Test
    public void cancellationAtFinalMenuConsumesEventAndDisarmsEntry() throws Exception {
        AtomicBoolean valid = new AtomicBoolean(true);
        NewMenuEntry entry = new NewMenuEntry().validWhen(valid::get);
        Microbot.targetMenu = entry;
        assertTrue(entry.isDispatchValid());
        valid.set(false);
        MenuOptionClicked event = new MenuOptionClicked(entry);
        dispatch(new MicrobotPlugin(), event);
        assertTrue(event.isConsumed());
        assertNull(Microbot.targetMenu);
    }

    @Test
    public void unguardedMenuStillReachesLegacyHandlers() throws Exception {
        MicrobotPlugin plugin = new MicrobotPlugin();
        Field config = MicrobotPlugin.class.getDeclaredField("microbotConfig");
        config.setAccessible(true);
        config.set(plugin, Mockito.mock(MicrobotConfig.class));
        NewMenuEntry entry = new NewMenuEntry();
        Microbot.targetMenu = entry;
        MenuOptionClicked event = new MenuOptionClicked(entry);
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class, Mockito.RETURNS_DEEP_STUBS);
             MockedStatic<Rs2Gembag> gembag = Mockito.mockStatic(Rs2Gembag.class)) {
            dispatch(plugin, event);
            Mockito.verify(Microbot.getPouchScript()).onMenuOptionClicked(event);
            gembag.verify(() -> Rs2Gembag.onMenuOptionClicked(event));
        }
        assertFalse(event.isConsumed());
        assertNull(Microbot.targetMenu);
    }

    private static void dispatch(MicrobotPlugin plugin, MenuOptionClicked event) throws Exception {
        Method handler = MicrobotPlugin.class.getDeclaredMethod("onMenuOptionClicked", MenuOptionClicked.class);
        handler.setAccessible(true);
        handler.invoke(plugin, event);
    }
}
