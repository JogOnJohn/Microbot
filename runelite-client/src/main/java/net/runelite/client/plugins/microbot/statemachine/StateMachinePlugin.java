package net.runelite.client.plugins.microbot.statemachine;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = Mocrosoft + "State Machine",
        description = "Template for building state machine scripts",
        tags = {"microbot", "template"},
        enabledByDefault = false
)
@Slf4j
public class StateMachinePlugin extends Plugin {

    @Inject
    private StateMachineConfig config;

    @Inject
    private StateMachineScript script;

    @Provides
    StateMachineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(StateMachineConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
