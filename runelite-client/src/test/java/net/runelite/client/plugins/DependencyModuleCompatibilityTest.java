package net.runelite.client.plugins;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.inject.Inject;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.hunter.HunterPlugin;
import org.junit.Test;
import static org.junit.Assert.*;

public class DependencyModuleCompatibilityTest
{
    @PluginDependency(HunterPlugin.class)
    public static class HunterConsumer extends Plugin
    {
        @Inject HunterPlugin hunter;
    }

    @PluginDependency(net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.class)
    public static class InventorySetupConsumer extends Plugin
    {
        @Inject net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin setups;
    }

    @Test
    public void inventorySetupDependencyUsesTheExistingInstance() throws Exception
    {
        Injector original = RuneLite.getInjector();
        try
        {
            RuneLite.setInjector(Guice.createInjector());
            var setups = new net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin();
            PluginManager manager = new PluginManager(false, null, null, null, null, new PluginModuleFactory());
            InventorySetupConsumer consumer = (InventorySetupConsumer) manager.instantiatePlugin(List.of(setups), (Class) InventorySetupConsumer.class);
            assertSame(setups, consumer.setups);
        }
        finally { RuneLite.setInjector(original); }
    }

    public static class PrivateDependency extends Plugin {}

    @PluginDependency(PrivateDependency.class)
    public static class PrivateConsumer extends Plugin {}

    @Test
    public void hunterDependencyUsesTheExistingTracker() throws Exception
    {
        Injector original = RuneLite.getInjector();
        try
        {
            RuneLite.setInjector(Guice.createInjector());
            HunterPlugin tracker = new HunterPlugin();
            PluginManager manager = new PluginManager(false, null, null, null, null, new PluginModuleFactory());
            HunterConsumer consumer = (HunterConsumer) manager.instantiatePlugin(List.of(tracker), (Class) HunterConsumer.class);
            assertSame(tracker, consumer.hunter);
            assertSame(tracker.getTraps(), consumer.hunter.getTraps());
        }
        finally { RuneLite.setInjector(original); }
    }

    @Test
    public void dependencyWithoutPublicServicesPreservesLoadOrder() throws Exception
    {
        Injector original = RuneLite.getInjector();
        try
        {
            RuneLite.setInjector(Guice.createInjector());
            PluginManager manager = new PluginManager(false, null, null, null, null, new PluginModuleFactory());
            assertNotNull(manager.instantiatePlugin(List.of(new PrivateDependency()), (Class) PrivateConsumer.class));
        }
        finally { RuneLite.setInjector(original); }
    }
}
