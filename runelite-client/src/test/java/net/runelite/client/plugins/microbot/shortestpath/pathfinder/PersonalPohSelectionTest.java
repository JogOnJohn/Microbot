package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.leaguetransport.LeaguesRegion;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PersonalPohSelectionTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = PathfinderConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test public void hostedTakesPrecedenceOnlyWhenEnabledAndActuallyOn330() throws Exception {
        PathfinderConfig config = mock(PathfinderConfig.class, CALLS_REAL_METHODS);
        Client client = mock(Client.class);
        set(config, "client", client);
        set(config, "usePoh", true);
        Method prefer = PathfinderConfig.class.getDeclaredMethod("prefersHostedHouse");
        prefer.setAccessible(true);
        for (boolean enabled : new boolean[]{false, true}) {
            set(config, "useWorld330MaxHouse", enabled);
            for (int world : new int[]{302, 330, 331}) {
                when(client.getWorld()).thenReturn(world);
                assertEquals(enabled && world == 330, prefer.invoke(config));
            }
        }
    }

    @Test public void editingDetectedHouseDataChangesTheActualTransportMemoKey() throws Exception {
        PathfinderConfig config = mock(PathfinderConfig.class, CALLS_REAL_METHODS);
        ConfigManager manager = mock(ConfigManager.class);
        Map<String, String> saved = new HashMap<>();
        saved.put("mountedXerics", "false");
        when(manager.getConfiguration(eq("shortestpath"), anyString())).thenAnswer(inv -> saved.get(inv.getArgument(1)));
        Rs2LeaguesTransport.LeaguesContext leagues = mock(Rs2LeaguesTransport.LeaguesContext.class);
        when(leagues.getUnlockedRegions()).thenReturn(EnumSet.noneOf(LeaguesRegion.class));
        Method key = PathfinderConfig.class.getDeclaredMethod("computeTransportRefreshCacheKeyHash", WorldPoint.class, Rs2LeaguesTransport.LeaguesContext.class);
        key.setAccessible(true);
        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = Mockito.mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2RunePouch> pouch = Mockito.mockStatic(Rs2RunePouch.class);
             MockedStatic<PohTeleports> poh = Mockito.mockStatic(PohTeleports.class)) {
            microbot.when(Microbot::getConfigManager).thenReturn(manager);
            WorldPoint target = new WorldPoint(3200, 3200, 0);
            int before = (int) key.invoke(config, target, leagues);
            saved.put("mountedXerics", "true");
            int after = (int) key.invoke(config, target, leagues);
            assertNotEquals(before, after);
        }
    }
}
