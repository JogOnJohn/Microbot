package net.runelite.client.plugins.microbot.util.world;

import java.util.Arrays;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HardcoreWorldSelectionTest {
    private World world(int id, boolean hardcore) {
        World world = new World();
        world.setId(id);
        world.setPlayers(100);
        world.setTypes(hardcore ? EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)
                : EnumSet.of(WorldType.MEMBERS));
        return world;
    }

    @Test
    public void ordinaryWorldAccessRetainsWorld330AndRejectsHardcore() {
        assertTrue(Rs2WorldUtil.isWorldAccessible(world(330, false), true, false));
        assertFalse(Rs2WorldUtil.isWorldAccessible(world(900, true), true, false));
    }

    @Test
    public void automaticLoginAndHopSelectionCannotPickHardcore() {
        WorldResult result = new WorldResult();
        result.setWorlds(Arrays.asList(world(900, true), world(330, false)));
        WorldService service = mock(WorldService.class);
        when(service.getWorlds()).thenReturn(result);
        Client client = mock(Client.class);
        when(client.getWorldType()).thenReturn(EnumSet.of(net.runelite.api.WorldType.MEMBERS));
        when(client.getWorld()).thenReturn(900);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getWorldService).thenReturn(service);
            microbot.when(Microbot::getClient).thenReturn(client);
            for (int i = 0; i < 20; i++) {
                assertEquals(330, Login.getRandomWorld(true, null));
                assertEquals(330, Login.getNextWorld(true, null));
                assertEquals(330, LoginManager.getRandomWorld(true, null));
                assertEquals(330, LoginManager.getNextWorld(true, null));
            }
        }
    }
}
