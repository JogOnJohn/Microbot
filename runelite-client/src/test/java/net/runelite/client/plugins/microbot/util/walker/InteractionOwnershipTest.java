package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractionOwnershipTest {
    @Test public void sameCoordinateDoesNotLetAnotherCallerStealPendingInteraction() throws Exception {
        Field f = Rs2Walker.class.getDeclaredField("activeInteraction"); f.setAccessible(true);
        @SuppressWarnings("unchecked") AtomicReference<InteractionRequest> owner = (AtomicReference<InteractionRequest>) f.get(null);
        InteractionRequest previous = owner.get();
        WorldPoint target = new WorldPoint(3000, 3300, 0);
        InteractionRequest request = InteractionRequest.npc(1, "A", target, "Talk-to", () -> false);
        try {
            owner.set(request);
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithState(target, 0));
            assertEquals(WalkerState.MOVING, Rs2Walker.walkStep(target, 0));
            assertEquals(WalkerState.MOVING, Rs2Walker.walkWithBankedTransportsAndState(target, 0, false));
            assertSame(request, owner.get());
            Rs2Walker.cancelInteraction(request);
            assertFalse(request.active()); assertNull(owner.get());
        } finally { owner.set(previous); }
    }
}
