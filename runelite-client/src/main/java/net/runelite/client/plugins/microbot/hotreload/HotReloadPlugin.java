package net.runelite.client.plugins.microbot.hotreload;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Development-only support for reloading an external plugin from a rebuilt JAR.
 *
 * <p>The watcher is deliberately inert unless {@value #DEV_JAR_PROPERTY} names a JAR.
 * It reloads only that external JAR; client classes, mixins, and the built-in Microbot
 * plugins still require a normal client restart.</p>
 */
@PluginDescriptor(
        name = "Microbot Hot Reload",
        description = "Reloads a selected external development plugin JAR",
        tags = {"developer", "hotreload"},
        developerPlugin = true,
        enabledByDefault = true
)
public class HotReloadPlugin extends Plugin
{
    public static final String DEV_JAR_PROPERTY = "microbot.hotreload.devjar";

    @Inject
    private PluginManager pluginManager;

    @Inject
    private EventBus eventBus;

    private HotReloadService service;

    @Override
    protected void startUp()
    {
        String configuredJar = System.getProperty(DEV_JAR_PROPERTY);
        if (configuredJar == null || configuredJar.trim().isEmpty())
        {
            return;
        }

        Path devJar = Paths.get(configuredJar.trim()).toAbsolutePath();
        service = new HotReloadService(devJar, pluginManager, eventBus, getClass().getClassLoader());
        service.start();
    }

    @Override
    protected void shutDown()
    {
        if (service != null)
        {
            service.close();
            service = null;
        }
    }
}
