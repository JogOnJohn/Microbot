package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Rs2WalkerPendingDoorSnapshotTest {
    private final Map<Field, Object> saved = new LinkedHashMap<>();
    private final WorldPoint from = new WorldPoint(3200, 3200, 0);
    private final WorldPoint to = new WorldPoint(3201, 3200, 0);
    private ObjectComposition composition;
    private Map<GameObject, Boolean> eligibility;
    private GameObject door;

    private void replace(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        saved.put(field, field.get(null));
        field.set(null, value);
    }

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        ClientThread dispatcher = mock(ClientThread.class);
        when(dispatcher.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        replace(Microbot.class, "client", client);
        replace(Microbot.class, "clientThread", dispatcher);
        door = mock(GameObject.class);
        when(door.getId()).thenReturn(12345);
        when(door.getWorldLocation()).thenReturn(from);
        composition = mock(ObjectComposition.class);
        when(composition.getName()).thenReturn("Door");
        when(composition.getActions()).thenReturn(new String[]{"Open"});
        when(client.getObjectDefinition(12345)).thenReturn(composition);
        eligibility = new IdentityHashMap<>();
        eligibility.put(door, false);
        replace(Rs2Walker.class, "rawScanWallSnapshot", Collections.emptyList());
        replace(Rs2Walker.class, "rawScanGameObjectSnapshot", Collections.singletonList(door));
        replace(Rs2Walker.class, "rawScanDoorEligibilityCache", eligibility);
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    private boolean probe(WorldPoint endpoint) throws Exception {
        Method method = Rs2Walker.class.getDeclaredMethod("hasDoorLikeSceneObjectOnSegment",
                WorldPoint.class, WorldPoint.class, WorldPoint.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, from, endpoint, from, 13);
    }

    @Test
    public void closedDoorIsStillFoundFromExistingSnapshot() throws Exception {
        assertTrue(probe(to));
        assertTrue(probe(to));
    }

    @Test
    public void openCloseOnlyDoorDoesNotBlockContinuation() throws Exception {
        when(composition.getActions()).thenReturn(new String[]{"Close"});
        assertFalse(probe(to));
    }

    @Test
    public void nestedImpostorResolutionStillUsesLiveDoorState() throws Exception {
        ObjectComposition intermediate = mock(ObjectComposition.class);
        ObjectComposition openDoor = mock(ObjectComposition.class);
        when(composition.getImpostorIds()).thenReturn(new int[]{1});
        when(composition.getImpostor()).thenReturn(intermediate);
        when(intermediate.getImpostorIds()).thenReturn(new int[]{2});
        when(intermediate.getImpostor()).thenReturn(openDoor);
        when(openDoor.getName()).thenReturn("Door");
        when(openDoor.getActions()).thenReturn(new String[]{"Close"});
        assertFalse(probe(to));
    }

    @Test
    public void transportOwnedObjectIsStillExcluded() throws Exception {
        eligibility.put(door, true);
        assertFalse(probe(to));
    }

    @Test
    public void wrongPlaneSegmentIsStillExcluded() throws Exception {
        assertFalse(probe(new WorldPoint(3201, 3200, 1)));
    }
}
