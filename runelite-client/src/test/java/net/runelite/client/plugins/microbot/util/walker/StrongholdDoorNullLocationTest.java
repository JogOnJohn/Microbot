package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrongholdDoorNullLocationTest {
    @Test
    public void regionCheckToleratesUnavailablePlayerLocation() throws ReflectiveOperationException {
        var check = Rs2Walker.class.getDeclaredMethod("isInStrongholdOfSecurity");
        check.setAccessible(true);
        try (var player = Mockito.mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);
            assertFalse((Boolean) check.invoke(null));

            player.when(Rs2Player::getWorldLocation).thenReturn(WorldPoint.fromRegion(7505, 10, 10, 0));
            assertTrue((Boolean) check.invoke(null));

            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3200, 3200, 0));
            assertFalse((Boolean) check.invoke(null));
        }
    }
}
