package net.runelite.client.plugins.microbot.questhelper.steps;

import org.junit.Test;
import static org.junit.Assert.*;

public class ObjectOutlineSceneChangeTest {
    @Test public void missingInjectedModelSkipsOnlyThatOutline() {
        assertFalse(ObjectStep.drawOutlineIfModelReady(() -> {throw new NullPointerException("model composition missing");}));
        assertTrue(ObjectStep.drawOutlineIfModelReady(() -> {}));
    }
    @Test(expected = IllegalStateException.class) public void unrelatedFailureIsNotSuppressed() {
        ObjectStep.drawOutlineIfModelReady(() -> {throw new IllegalStateException("not a model load");});
    }
}
