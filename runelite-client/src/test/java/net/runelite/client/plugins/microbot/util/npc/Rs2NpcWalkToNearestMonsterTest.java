package net.runelite.client.plugins.microbot.util.npc;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mockStatic;

public class Rs2NpcWalkToNearestMonsterTest {

    private static final String UNKNOWN_MONSTER = "Unknown monster without location data";
    private MockedStatic<Rs2NpcManager> locations;

    @Before
    public void missingLocationLookup() {
        locations = mockStatic(Rs2NpcManager.class);
    }

    @After
    public void closeLocationLookup() {
        locations.close();
    }

    @Test
    public void walkToNearestMonsterReturnsFalseWhenNoLocationIsFound() {
        assertFalse(Rs2Npc.walkToNearestMonster(UNKNOWN_MONSTER));
        locations.verify(() -> Rs2NpcManager.getClosestLocation(UNKNOWN_MONSTER, 1, false));
    }

    @Test
    public void walkToNearestMonsterWithClusteringReturnsFalseWhenNoLocationIsFound() {
        assertFalse(Rs2Npc.walkToNearestMonster(UNKNOWN_MONSTER, 3));
        locations.verify(() -> Rs2NpcManager.getClosestLocation(UNKNOWN_MONSTER, 3, false));
    }

    @Test
    public void walkToNearestMonsterWithWildernessFilterReturnsFalseWhenNoLocationIsFound() {
        assertFalse(Rs2Npc.walkToNearestMonster(UNKNOWN_MONSTER, 3, true));
        locations.verify(() -> Rs2NpcManager.getClosestLocation(UNKNOWN_MONSTER, 3, true));
    }
}
