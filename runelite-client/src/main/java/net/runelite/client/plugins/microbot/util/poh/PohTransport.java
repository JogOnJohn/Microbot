package net.runelite.client.plugins.microbot.util.poh;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.util.poh.data.World330HostedHouse;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import java.util.Objects;
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
        if (landed()) return PohExecutionResult.SUCCESS;
        long recoveryDeadline = System.currentTimeMillis() + 60_000L;
        boolean recovering = false;
        log.info("[W330POH] PohTransport execute teleport={} origin={} dest={}",
                teleport.displayInfo(), getOrigin(), getDestination());
        for (int switches = 0; switches <= 3; switches++) {
            if (stopped(cancelled)) return PohExecutionResult.CANCELLED;
            if (landed()) return PohExecutionResult.SUCCESS;
            if (!World330HostedHouse.ADVERTISED_HOUSE.sceneReady()) return PohExecutionResult.SCENE_PENDING;
            boolean hosted = World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse();
            PohFacilityAccess.Access access = hosted ? confirmedFacilityAccess(cancelled, recoveryDeadline)
                    : PohFacilityAccess.Access.UNKNOWN;
            if (stopped(cancelled)) return PohExecutionResult.CANCELLED;
            if (landed()) return PohExecutionResult.SUCCESS;
            if (hosted && !World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()) return PohExecutionResult.SCENE_PENDING;
            if (access == PohFacilityAccess.Access.PENDING) return PohExecutionResult.SCENE_PENDING;
            boolean unusable = access == PohFacilityAccess.Access.MISSING
                    || access == PohFacilityAccess.Access.UNREACHABLE || access == PohFacilityAccess.Access.OBSTRUCTED;
            if (!unusable) {
                PohExecutionResult result = executeInCurrentHouse(cancelled, recoveryDeadline);
                if (result != PohExecutionResult.TRANSIENT_FAILURE || !hosted) return result;
                if (landed()) return PohExecutionResult.SUCCESS;
                if (!World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()) return PohExecutionResult.SCENE_PENDING;
                // A dispatched click is not arrival. Re-check collision before attributing failure.
                access = confirmedFacilityAccess(cancelled, recoveryDeadline);
                if (stopped(cancelled)) return PohExecutionResult.CANCELLED;
                if (landed()) return PohExecutionResult.SUCCESS;
                if (!World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()) return PohExecutionResult.SCENE_PENDING;
                if (access == PohFacilityAccess.Access.PENDING) return PohExecutionResult.SCENE_PENDING;
                unusable = access == PohFacilityAccess.Access.MISSING || access == PohFacilityAccess.Access.UNREACHABLE
                        || access == PohFacilityAccess.Access.OBSTRUCTED;
                if (!unusable) return result;
            }
            if (landed()) return PohExecutionResult.SUCCESS;
            log.warn("[W330POH] facility_access teleport={} access={} generation={} at={}",
                    teleport.displayInfo(), access, World330HostedHouse.ADVERTISED_HOUSE.houseGeneration(),
                    Rs2Player.getWorldLocation());
            World330HostedHouse.ADVERTISED_HOUSE.excludeCurrentHouseForSession(teleport.displayInfo());
            if (access == PohFacilityAccess.Access.UNREACHABLE)
                World330HostedHouse.ADVERTISED_HOUSE.blacklistUnreachableHouse(teleport.displayInfo());
            // Slow inspection/execution must not consume the separate exit/re-entry budget.
            // Initialize once: successive unsuitable houses cannot keep renewing it.
            if (!recovering) {
                recoveryDeadline = System.currentTimeMillis() + 60_000L;
                recovering = true;
            }
            if (switches == 3 || System.currentTimeMillis() >= recoveryDeadline
                    || !World330HostedHouse.ADVERTISED_HOUSE.switchUnsuitableHouse(cancelled, recoveryDeadline)) {
                if (stopped(cancelled)) return PohExecutionResult.CANCELLED;
                if (landed()) return PohExecutionResult.SUCCESS;
                if (!World330HostedHouse.ADVERTISED_HOUSE.sceneReady()) return PohExecutionResult.SCENE_PENDING;
                World330HostedHouse.ADVERTISED_HOUSE.rejectCurrentHouse();
                return PohExecutionResult.HOUSE_RECOVERY_FAILED;
            }
        }
        return PohExecutionResult.HOUSE_RECOVERY_FAILED;
    }

    private PohFacilityAccess.Access confirmedFacilityAccess(BooleanSupplier cancelled, long deadline) {
        long generation = World330HostedHouse.ADVERTISED_HOUSE.houseGeneration();
        WorldPoint at = Rs2Player.getWorldLocation();
        PohFacilityAccess.Access access = PohFacilityAccess.inspect(teleport);
        if (!World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()
                || generation != World330HostedHouse.ADVERTISED_HOUSE.houseGeneration()) return PohFacilityAccess.Access.PENDING;
        if (access == PohFacilityAccess.Access.MISSING) {
            sleepUntil(() -> stopped(cancelled) || PohFacilityAccess.inspect(teleport) != PohFacilityAccess.Access.MISSING,
                    remaining(deadline, 3000));
            access = PohFacilityAccess.inspect(teleport);
            if (generation != World330HostedHouse.ADVERTISED_HOUSE.houseGeneration()
                    || !Objects.equals(at, Rs2Player.getWorldLocation())) return PohFacilityAccess.Access.PENDING;
        }
        if (access != PohFacilityAccess.Access.UNREACHABLE) return access;
        sleepUntil(() -> stopped(cancelled) || PohFacilityAccess.inspect(teleport) != PohFacilityAccess.Access.UNREACHABLE,
                remaining(deadline, 1200));
        if (generation != World330HostedHouse.ADVERTISED_HOUSE.houseGeneration()
                || !Objects.equals(at, Rs2Player.getWorldLocation())) return PohFacilityAccess.Access.PENDING;
        return PohFacilityAccess.inspect(teleport);
    }

    private PohExecutionResult executeInCurrentHouse(BooleanSupplier cancelled, long deadline) {
        PohMenuInteraction.resetResult();
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
        if (!executed) return PohMenuInteraction.lastResult() == PohMenuInteraction.Result.UNAVAILABLE
                ? PohExecutionResult.DESTINATION_UNAVAILABLE : PohExecutionResult.TRANSIENT_FAILURE;
        boolean landed = sleepUntil(() -> stopped(cancelled) || landed(), remaining(deadline, 10000));
        // Walking to a distant mounted facility can consume the first landing budget.
        // Allow a short, bounded tail for an accepted action that is still progressing.
        if (!landed && !stopped(cancelled) && (Rs2Player.isMoving() || Rs2Player.isAnimating())) {
            landed = sleepUntil(() -> stopped(cancelled) || landed(), remaining(deadline, 5000));
        }
        if (stopped(cancelled)) return PohExecutionResult.CANCELLED;
        log.info("[W330POH] teleport_landing teleport={} confirmed={} at={}", teleport.displayInfo(),
                landed && landed(), Rs2Player.getWorldLocation());
        return landed && landed() ? PohExecutionResult.SUCCESS : PohExecutionResult.TRANSIENT_FAILURE;
    }

    private boolean landed() {
        return teleport instanceof World330HostedHouse
                ? World330HostedHouse.ADVERTISED_HOUSE.isInHostedHouse()
                : hasLanded(Rs2Player.getWorldLocation(), getDestination());
    }

    static boolean hasLanded(WorldPoint player, WorldPoint destination) {
        return player != null && destination != null && player.getPlane() == destination.getPlane()
                && player.distanceTo2D(destination) < 10;
    }

    private static boolean stopped(BooleanSupplier cancelled) {
        return cancelled.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    private static int remaining(long deadline, int timeout) {
        return (int) Math.min(timeout, Math.max(1, deadline - System.currentTimeMillis()));
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
