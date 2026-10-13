package net.runelite.client.plugins.microbot.util.poh;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;
import java.util.function.BooleanSupplier;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

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
        return executeDetailed(() -> false) == PohExecutionResult.SUCCESS;
    }

    public PohExecutionResult executeDetailed(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return PohExecutionResult.CANCELLED;
        if (!World330HostedHouse.ADVERTISED_HOUSE.sceneReady()) return PohExecutionResult.SCENE_PENDING;
        long recoveryDeadline = System.currentTimeMillis() + 60_000L;
        PohMenuInteraction.resetResult();
        log.info("[W330POH] PohTransport execute teleport={} origin={} dest={}",
                teleport.displayInfo(), getOrigin(), getDestination());
        if (teleport instanceof MountedXerics
                && World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()
                && !ensureFacility(
                        () -> {
                            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()
                                    || System.currentTimeMillis() >= recoveryDeadline) return false;
                            sleepUntil(() -> cancelled.getAsBoolean() || MountedXerics.getObject() != null, 3000);
                            boolean present = !cancelled.getAsBoolean() && MountedXerics.getObject() != null;
                            if (!present) log.info("[W330POH] facility_lookup missing=MountedXerics generation={}",
                                    World330HostedHouse.ADVERTISED_HOUSE.houseGeneration());
                            return present;
                        }, () -> World330HostedHouse.ADVERTISED_HOUSE.switchUnsuitableHouse(cancelled,
                                recoveryDeadline), cancelled)) {
            if (!World330HostedHouse.ADVERTISED_HOUSE.sceneReady()) return PohExecutionResult.SCENE_PENDING;
            if (!cancelled.getAsBoolean() && !Thread.currentThread().isInterrupted())
                World330HostedHouse.ADVERTISED_HOUSE.rejectCurrentHouse();
            return cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()
                    ? PohExecutionResult.CANCELLED : PohExecutionResult.HOUSE_RECOVERY_FAILED;
        }
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return PohExecutionResult.CANCELLED;
        boolean poolUsed = PohTeleports.useOrnateRejuvenationPoolIfPresent();
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted() || !PohTeleports.isInHouse()) {
            PohMenuInteraction.markCancelled();
            log.info("[W330POH] cancelled POH transport after pool: house changed or interrupted");
            return PohExecutionResult.CANCELLED;
        }
        boolean executed = teleport.execute();
        log.info("[W330POH] PohTransport complete teleport={} poolUsed={} executed={}",
                teleport.displayInfo(), poolUsed, executed);
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()
                || PohMenuInteraction.lastResult() == PohMenuInteraction.Result.CANCELLED)
            return PohExecutionResult.CANCELLED;
        return executed ? PohExecutionResult.SUCCESS
                : PohMenuInteraction.lastResult() == PohMenuInteraction.Result.UNAVAILABLE
                    ? PohExecutionResult.DESTINATION_UNAVAILABLE : PohExecutionResult.TRANSIENT_FAILURE;
    }

    static boolean ensureFacility(BooleanSupplier facilityPresent, BooleanSupplier switchHouse) {
        return ensureFacility(facilityPresent, switchHouse, () -> false);
    }

    static boolean ensureFacility(BooleanSupplier facilityPresent, BooleanSupplier switchHouse, BooleanSupplier cancelled) {
        for (int attempts = 0; attempts <= 3; attempts++) {
            if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) return false;
            if (facilityPresent.getAsBoolean()) return true;
            if (attempts == 3 || !switchHouse.getAsBoolean()) return false;
        }
        return false;
    }

}
