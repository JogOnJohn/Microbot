package net.runelite.client.plugins;

public final class PluginLoaderTestSupport
{
    private PluginLoaderTestSupport()
    {
    }

    public static PluginManager createLoader()
    {
        return new PluginManager(false, null, null, null, null, new PluginModuleFactory());
    }
}
