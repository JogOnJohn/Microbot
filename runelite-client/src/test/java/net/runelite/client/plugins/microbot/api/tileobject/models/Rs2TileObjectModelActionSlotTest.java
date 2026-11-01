package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class Rs2TileObjectModelActionSlotTest {
    @Test
    public void guardedEntryUsesLiveImpostorSlotAndRejectsSlotChanges() {
        exercise(true);
    }

    @Test
    public void unguardedEntryRetainsLegacyDispatchBehavior() {
        exercise(false);
    }

    private void exercise(boolean guarded) {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        TileObject object = mock(TileObject.class);
        when(object.getId()).thenReturn(456);
        when(object.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400, -1));
        when(object.getWorldView()).thenReturn(mock(WorldView.class));
        ObjectComposition base = mock(ObjectComposition.class);
        ObjectComposition live = mock(ObjectComposition.class);
        when(client.getObjectDefinition(456)).thenReturn(base);
        when(base.getImpostorIds()).thenReturn(new int[]{457});
        when(base.getImpostor()).thenReturn(live);
        when(base.getActions()).thenReturn(new String[]{"Open"});
        when(live.getName()).thenReturn("Door");
        when(live.getActions()).thenReturn(new String[]{"Close", null, "Open", null, null});
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        AtomicBoolean owned = new AtomicBoolean(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class);
             MockedStatic<SceneClickSafety> safety = mockStatic(SceneClickSafety.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
            safety.when(() -> SceneClickSafety.forObject(object, null)).thenReturn(bounds);
            Rs2TileObjectModel model = new Rs2TileObjectModel(object);
            assertTrue(guarded ? model.click("Open", owned::get) : model.click("Open"));
            ArgumentCaptor<NewMenuEntry> captor = ArgumentCaptor.forClass(NewMenuEntry.class);
            microbot.verify(() -> Microbot.doInvoke(captor.capture(), eq(bounds)));
            NewMenuEntry entry = captor.getValue();
            assertEquals(MenuAction.GAME_OBJECT_THIRD_OPTION, entry.getType());
            assertEquals("Open", entry.getOption());
            assertTrue(entry.isDispatchValid());

            when(live.getActions()).thenReturn(new String[]{"Open", null, "Close", null, null});
            assertEquals(!guarded, entry.isDispatchValid());
            when(live.getActions()).thenReturn(new String[]{"Close", null, "Open", null, null});
            assertTrue(entry.isDispatchValid());
            when(base.getImpostor()).thenReturn(null);
            assertEquals(!guarded, entry.isDispatchValid());
            when(base.getImpostor()).thenReturn(live);
            when(client.isWidgetSelected()).thenReturn(true);
            assertEquals(!guarded, entry.isDispatchValid());
            when(client.isWidgetSelected()).thenReturn(false);
            entry.type(MenuAction.GAME_OBJECT_FIRST_OPTION);
            assertEquals(!guarded, entry.isDispatchValid());
            entry.type(MenuAction.GAME_OBJECT_THIRD_OPTION);
            entry.option("Close");
            assertEquals(!guarded, entry.isDispatchValid());
            entry.option("Open");
            assertTrue(entry.isDispatchValid());
            owned.set(false);
            assertEquals(!guarded, entry.isDispatchValid());
            if (guarded) {
                owned.set(true);
                safety.when(() -> SceneClickSafety.forObject(object, null)).thenAnswer(inv -> {
                    when(live.getActions()).thenReturn(new String[]{"Open", null, "Close", null, null});
                    return bounds;
                });
                microbot.clearInvocations();
                assertFalse(model.click("Open", owned::get));
                microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
            }
        }
    }
}
