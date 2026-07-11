package net.runelite.client.plugins.microbot.util.poh.data;

import net.runelite.api.coords.WorldPoint;

public interface PohTeleport {

    /**
     * Executes the teleport.
     *
     * @return true if successful, false otherwise.
     */
    boolean execute();

    /**
     * Whether THIS PLAYER can actually take this teleport. Some destinations are quest-locked
     * player-side even when the facility offers them (e.g. the jewellery box lists Dondakan's
     * Rock, but the game refuses it without Between a Rock). Transport assembly filters on this
     * so the pathfinder never routes through an option the player can't click.
     */
    default boolean isUnlockedForPlayer() {
        return true;
    }

    WorldPoint getDestination();

    /**
     * Gets the duration of the teleport equal to the amount of tiles that can be ran in the same time.
     *
     * @return number of tiles that can be ran in the same time.
     */
    int getDuration();

    /**
     * Returns a string containing information about the PohTransport, including its Method and Destination.,
     *
     * @return PohTeleportMethod -> Destination.toString()
     */
    String displayInfo();

    /**
     * Enum value used to trace back the PohTransport from the Enum class it originates from
     *
     * @return Enum's value.name()
     */
    String name();
}
