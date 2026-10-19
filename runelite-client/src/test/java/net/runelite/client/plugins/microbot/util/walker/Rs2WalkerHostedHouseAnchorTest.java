package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerHostedHouseAnchorTest {
    @Test
    public void detectsObservedPoolTemplateChunkJump() {
        assertTrue(Rs2Walker.hostedHouseAnchorDiscontinuous(new WorldPoint(2053, 7052, 0),
                new WorldPoint(2093, 7040, 0)));
    }

    @Test
    public void nearbyFacilityApproachDoesNotReanchor() {
        assertFalse(Rs2Walker.hostedHouseAnchorDiscontinuous(new WorldPoint(2053, 7052, 0),
                new WorldPoint(2057, 7052, 0)));
    }

    @Test
    public void invalidOrDifferentPlaneIsNotThisRecovery() {
        assertFalse(Rs2Walker.hostedHouseAnchorDiscontinuous(null, new WorldPoint(2093, 7040, 0)));
        assertFalse(Rs2Walker.hostedHouseAnchorDiscontinuous(new WorldPoint(2053, 7052, 1),
                new WorldPoint(2093, 7040, 0)));
    }
}
