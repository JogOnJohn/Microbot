package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LiveCollisionPersistenceKeyTest
{
    @Test
    public void staticCollisionHashParticipatesInStoreIdentity()
    {
        String first = LiveCollisionPersistence.storeDirectoryName(235, "a".repeat(64));
        String second = LiveCollisionPersistence.storeDirectoryName(235, "b".repeat(64));

        assertNotEquals(first, second);
        assertTrue(first.endsWith("-maaaaaaaaaaaa"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnversionedStaticCollisionMap()
    {
        LiveCollisionPersistence.storeDirectoryName(235, "unknown");
    }
}
