package net.runelite.client.plugins.microbot.api.npc.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class Rs2NpcModelActionSlotTest {
    @Test
    public void guardedEntryUsesTransformedSlotAndRevalidatesExactQueuedAction() {
        exercise(true);
    }

    @Test
    public void unguardedEntryRetainsBaseSlotAndHasNoNewDispatchRestrictions() {
        exercise(false);
    }

    private void exercise(boolean guarded) {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(123);
        when(npc.getIndex()).thenReturn(7);
        when(npc.getName()).thenReturn("Guide");
        when(npc.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400, -1));
        NPCComposition base = mock(NPCComposition.class);
        NPCComposition transformed = mock(NPCComposition.class);
        when(client.getNpcDefinition(123)).thenReturn(base);
        when(base.getActions()).thenReturn(new String[]{"Talk-to", null, null, null, null});
        when(npc.getTransformedComposition()).thenReturn(transformed);
        when(transformed.getActions()).thenReturn(new String[]{"Attack", null, "Talk-to", null, null});
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        AtomicBoolean owned = new AtomicBoolean(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class);
             MockedStatic<Rs2UiHelper> ui = mockStatic(Rs2UiHelper.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
            ui.when(() -> Rs2UiHelper.getActorClickbox(npc)).thenReturn(bounds);
            Rs2NpcModel model = new Rs2NpcModel(npc);
            assertTrue(guarded ? model.click("Talk-to", owned::get) : model.click("Talk-to"));
            ArgumentCaptor<NewMenuEntry> captor = ArgumentCaptor.forClass(NewMenuEntry.class);
            microbot.verify(() -> Microbot.doInvoke(captor.capture(), eq(bounds)));
            NewMenuEntry entry = captor.getValue();
            assertEquals(guarded ? MenuAction.NPC_THIRD_OPTION : MenuAction.NPC_FIRST_OPTION, entry.getType());
            assertEquals("Talk-to", entry.getOption());
            assertTrue(entry.isDispatchValid());

            when(transformed.getActions()).thenReturn(new String[]{"Talk-to", null, "Attack", null, null});
            assertEquals(!guarded, entry.isDispatchValid());
            when(transformed.getActions()).thenReturn(new String[]{"Attack", null, "Talk-to", null, null});
            assertTrue(entry.isDispatchValid());
            when(client.isWidgetSelected()).thenReturn(true);
            assertEquals(!guarded, entry.isDispatchValid());
            when(client.isWidgetSelected()).thenReturn(false);
            when(npc.getTransformedComposition()).thenReturn(null);
            assertEquals(!guarded, entry.isDispatchValid());
            when(npc.getTransformedComposition()).thenReturn(transformed);
            entry.type(MenuAction.NPC_SECOND_OPTION);
            assertEquals(!guarded, entry.isDispatchValid());
            entry.type(guarded ? MenuAction.NPC_THIRD_OPTION : MenuAction.NPC_FIRST_OPTION);
            entry.option("Attack");
            assertEquals(!guarded, entry.isDispatchValid());
            entry.option("Talk-to");
            assertTrue(entry.isDispatchValid());
            owned.set(false);
            assertEquals(!guarded, entry.isDispatchValid());
            if (guarded) {
                owned.set(true);
                camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenAnswer(inv -> {
                    when(transformed.getActions()).thenReturn(new String[]{"Talk-to", null, "Attack", null, null});
                    return true;
                });
                microbot.clearInvocations();
                assertFalse(model.click("Talk-to", owned::get));
                microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
            }
        }
    }
}
