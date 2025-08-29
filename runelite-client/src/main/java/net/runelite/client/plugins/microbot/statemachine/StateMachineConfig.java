package net.runelite.client.plugins.microbot.statemachine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("statemachine")
public interface StateMachineConfig extends Config {
    @ConfigItem(
            keyName = "example",
            name = "Example Option",
            description = "Example configuration item"
    )
    default boolean example() {
        return false;
    }
}
