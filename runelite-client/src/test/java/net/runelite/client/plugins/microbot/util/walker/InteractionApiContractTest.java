package net.runelite.client.plugins.microbot.util.walker;

import java.lang.reflect.Modifier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionRequest;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionResult;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionLifecycle;
import net.runelite.client.plugins.microbot.util.walker.interaction.InteractionTargetRuntime;
import org.junit.Test;

import static org.junit.Assert.*;

/** Consumer-shaped compile/linkage checks, not a substitute for building the full Hub plugin. */
public class InteractionApiContractTest {
    @Test public void historicalInteractionFieldsAndLifecycleMethodsRemainPublic() throws Exception {
        assertEquals(WorldPoint.class, InteractionRequest.class.getField("anchor").getType());
        assertEquals(WorldPoint.class, InteractionRequest.class.getField("access").getType());
        assertEquals(InteractionLifecycle.class, InteractionRequest.class.getField("lifecycle").getType());
        assertEquals(InteractionTargetRuntime.class, InteractionRequest.class.getField("runtime").getType());
        assertTrue(Modifier.isFinal(InteractionRequest.class.getField("anchor").getModifiers()));
        assertEquals(String.class, InteractionLifecycle.class.getMethod("reason").getReturnType());
        assertEquals(int.class, InteractionLifecycle.class.getMethod("attempts").getReturnType());
    }

    @Test public void requestOverloadsDoNotReplaceLegacyDescriptorsOrReturnTypes() throws Exception {
        assertEquals(InteractionResult.class, Rs2Walker.class.getMethod("walkTo", WorldPoint.class, InteractionRequest.class).getReturnType());
        assertEquals(boolean.class, Rs2Walker.class.getMethod("walkTo", WorldPoint.class, int.class).getReturnType());
        assertEquals(InteractionResult.class, Rs2Walker.class.getMethod("walkWithBankedTransportsAndState",
                WorldPoint.class, int.class, InteractionRequest.class).getReturnType());
        assertEquals(WalkerState.class, Rs2Walker.class.getMethod("walkWithBankedTransportsAndState",
                WorldPoint.class, int.class, boolean.class).getReturnType());
        assertEquals(void.class, Rs2Walker.class.getMethod("cancelInteraction", InteractionRequest.class).getReturnType());
    }

    @Test public void bothFactoryShapesPreserveConsumerReadableRequestData() {
        WorldPoint anchor = new WorldPoint(3000, 3300, 0), access = anchor.dx(1);
        InteractionRequest npc = InteractionRequest.npc(1, "Guide", anchor, "Talk-to", () -> false);
        InteractionRequest object = InteractionRequest.object(2, anchor, access, "Repair", () -> false);
        assertEquals(anchor, npc.anchor);
        assertEquals(anchor, object.anchor);
        assertEquals(access, object.access);
        assertNotEquals(npc.id, object.id);
        assertEquals(0, object.lifecycle.attempts());
        assertNotNull(object.lifecycle.reason());
    }

    // Deliberately compiled, never executed: these are the historical caller's typed operations.
    private static InteractionResult consumer(InteractionRequest request, boolean banked) {
        return banked ? Rs2Walker.walkWithBankedTransportsAndState(request.anchor, 0, request)
                : Rs2Walker.walkTo(request.anchor, request);
    }
}
