package net.runelite.client.plugins.microbot.breakhandler;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class BreakPreparationTest
{
    @After
    public void reset()
    {
        BreakPreparation.finishBreak();
    }

    @Test
    public void allOwnersMustPrepareAndNextBreakRequiresPreparationAgain()
    {
        try (BreakPreparation.Handle a = BreakPreparation.register("a");
             BreakPreparation.Handle b = BreakPreparation.register("b"))
        {
            assertFalse(a.isRequested());
            assertTrue(BreakPreparation.shouldDeferBreak());
            assertTrue(a.isRequested());
            a.ready();
            assertTrue(BreakPreparation.shouldDeferBreak());
            b.ready();
            assertFalse(BreakPreparation.shouldDeferBreak());
            BreakPreparation.finishBreak();
            assertFalse(a.isRequested());
            assertTrue(BreakPreparation.shouldDeferBreak());
        }
    }

    @Test
    public void oldHandleCannotReleaseItsReplacement()
    {
        BreakPreparation.Handle old = BreakPreparation.register("a");
        try (BreakPreparation.Handle replacement = BreakPreparation.register("a"))
        {
            BreakPreparation.shouldDeferBreak();
            old.close();
            old.ready();
            assertFalse(BreakPreparation.isAborted());
            assertTrue(BreakPreparation.shouldDeferBreak());
            replacement.ready();
            assertFalse(BreakPreparation.shouldDeferBreak());
        }
        assertFalse(BreakPreparation.hasParticipants());
    }

    @Test
    public void stalledPreparationCancelsAtDeadline() throws Exception
    {
        try (BreakPreparation.Handle handle = BreakPreparation.register("a"))
        {
            BreakPreparation.shouldDeferBreak();
            java.lang.reflect.Field field = BreakPreparation.class.getDeclaredField("requestedAt");
            field.setAccessible(true);
            field.setLong(null, System.currentTimeMillis() - 120_001);
            assertTrue(BreakPreparation.isAborted());
        }
    }

    @Test
    public void failedPreparationCancelsInsteadOfClaimingSafety()
    {
        BreakPreparation.Handle handle = BreakPreparation.register("a");
        BreakPreparation.shouldDeferBreak();
        handle.close();
        assertTrue(BreakPreparation.isAborted());
        BreakPreparation.finishBreak();
        assertFalse(BreakPreparation.isAborted());
    }
}
