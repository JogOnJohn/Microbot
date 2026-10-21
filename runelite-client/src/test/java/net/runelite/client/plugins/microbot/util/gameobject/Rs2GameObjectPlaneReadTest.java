package net.runelite.client.plugins.microbot.util.gameobject;

import java.lang.reflect.Field;
import java.util.Optional;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2GameObjectPlaneReadTest {
    private ClientThread thread;
    private GameObject object;
    private Object previousThread;
    private Field threadField;

    @Before
    public void setUp() throws Exception {
        thread = mock(ClientThread.class);
        object = mock(GameObject.class);
        when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        threadField = Microbot.class.getDeclaredField("clientThread");
        threadField.setAccessible(true);
        previousThread = threadField.get(null);
        threadField.set(null, thread);
    }

    @After
    public void tearDown() throws Exception {
        threadField.set(null, previousThread);
    }

    @Test
    public void unavailablePlaneSnapshotRejectsWalkBeforeReadingScene() {
        assertFalse(Rs2GameObject.canWalkTo(object, 3));
        verify(thread).runOnClientThreadOptional(any());
        verify(object, never()).getPlane();
        verify(object, never()).getSceneMinLocation();
    }

    @Test
    public void unavailablePlaneSnapshotRejectsLineOfSightBeforeReadingScene() {
        assertFalse(Rs2GameObject.hasLineOfSight(new WorldPoint(3200, 3200, 0), object));
        verify(thread).runOnClientThreadOptional(any());
        verify(object, never()).getPlane();
        verify(object, never()).getSceneMinLocation();
    }
}
