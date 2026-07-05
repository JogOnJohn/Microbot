package net.runelite.client.plugins.microbot.util.poh;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

/**
 * Represents a transport mechanism using the Player-Owned House (POH) teleportation system.
 * This class extends the base Transport class and provides specific implementations
 * for POH teleportation.
 */
@Slf4j
public class PohTransport extends Transport {

    @Getter
    private final PohTeleport teleport;

    public PohTransport(WorldPoint exitPortalPoint, PohTeleport teleport) {
        super(
                java.util.Objects.requireNonNull(exitPortalPoint, "exitPortalPoint is null"),
                java.util.Objects.requireNonNull(teleport, "teleport is null").getDestination(),
                teleport.displayInfo(), TransportType.POH, true, teleport.getDuration()
        );
        this.teleport = teleport;
    }

    /**
     * Executes the Transport's PoH teleportation action.
     *
     * @return true on successful teleportation
     */
    public boolean execute() {
        log.info("[W330POH] PohTransport execute teleport={} origin={} dest={}",
                teleport.displayInfo(), getOrigin(), getDestination());
        boolean poolUsed = PohTeleports.useOrnateRejuvenationPoolIfPresent();
        boolean executed = teleport.execute();
        log.info("[W330POH] PohTransport complete teleport={} poolUsed={} executed={}",
                teleport.displayInfo(), poolUsed, executed);
        return executed;
    }

}
