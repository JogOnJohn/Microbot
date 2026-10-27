package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelCompositionTest {

    private static final int OBJECT_ID = 43700;

    private MockedStatic<Microbot> microbot;
    private Client client;
    private TileObject tileObject;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        when(client.getViewportWidth()).thenReturn(800);
        when(client.getViewportHeight()).thenReturn(600);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getClient).thenReturn(client);

        tileObject = mock(TileObject.class);
        when(tileObject.getId()).thenReturn(OBJECT_ID);
        when(tileObject.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400, -1));
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void unresolvedImpostorFallsBackToBaseComposition() {
        ObjectComposition base = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(null);
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(base);

        assertSame(base, new Rs2TileObjectModel(tileObject).getObjectComposition());
    }

    @Test
    public void resolvedImpostorIsReturned() {
        ObjectComposition base = mock(ObjectComposition.class);
        ObjectComposition impostor = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(impostor);
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(base);

        assertSame(impostor, new Rs2TileObjectModel(tileObject).getObjectComposition());
    }

    @Test
    public void clickReturnsFalseWithoutInvokingWhenImpostorIsUnresolved() {
        ObjectComposition base = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(null);
        when(base.getActions()).thenReturn(new String[5]);
        when(base.getName()).thenReturn("null");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(base);

        assertFalse(new Rs2TileObjectModel(tileObject).click("Enter"));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void clickReturnsFalseWhenCompositionIsMissing() {
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(null);

        assertFalse(new Rs2TileObjectModel(tileObject).click("Enter"));
    }

    @Test
    public void visibleObjectStillRefusesAnUnavailableAction() {
        ObjectComposition base = mock(ObjectComposition.class);
        ObjectComposition impostor = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(impostor);
        when(impostor.getActions()).thenReturn(new String[]{"Open"});
        when(impostor.getName()).thenReturn("Door");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(base);

        assertFalse(new Rs2TileObjectModel(tileObject).click("Climb-up"));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void defaultClickRejectsHiddenImpostor() {
        ObjectComposition base = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(base);
        assertFalse(new Rs2TileObjectModel(tileObject).click());
        assertFalse(new Rs2TileObjectModel(tileObject).click((String) null));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void defaultClickRejectsMissingComposition() {
        assertFalse(new Rs2TileObjectModel(tileObject).click());
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void missingActionsRejectNamedClick() {
        visibleObject();
        assertFalse(new Rs2TileObjectModel(tileObject).click("Open"));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void validDefaultClickKeepsFirstOption() {
        visibleObject();
        WorldView view = mock(WorldView.class);
        when(tileObject.getWorldView()).thenReturn(view);
        Rectangle bounds = new Rectangle(10, 10, 20, 20);
        when(tileObject.getClickbox()).thenReturn(bounds);
        try (MockedStatic<Rs2Camera> camera = Mockito.mockStatic(Rs2Camera.class)) {
            camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
            assertTrue(new Rs2TileObjectModel(tileObject).click());
            microbot.verify(() -> Microbot.doInvoke(argThat(entry -> entry.getType() == MenuAction.GAME_OBJECT_FIRST_OPTION), eq(bounds)));
        }
    }

    @Test
    public void unavailableClickboxTurnsOnceAndNeverInvokes() {
        visibleObject();
        when(tileObject.getWorldView()).thenReturn(mock(WorldView.class));
        try (MockedStatic<Rs2Camera> camera = Mockito.mockStatic(Rs2Camera.class)) {
            camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
            assertFalse(new Rs2TileObjectModel(tileObject).click());
            camera.verify(() -> Rs2Camera.turnTo(tileObject));
            microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
        }
    }

    @Test
    public void sceneExceptionDoesNotReportSuccessfulClick() {
        visibleObject();
        when(tileObject.getLocalLocation()).thenThrow(new IllegalStateException("Scene changed"));
        assertFalse(new Rs2TileObjectModel(tileObject).click());
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    private ObjectComposition visibleObject() {
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getName()).thenReturn("Door");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);
        return composition;
    }
}
