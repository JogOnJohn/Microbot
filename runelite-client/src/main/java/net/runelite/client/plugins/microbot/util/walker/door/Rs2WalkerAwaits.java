package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket;
import net.runelite.client.plugins.microbot.util.walker.door.model.DoorResolution;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public final class Rs2WalkerAwaits {
    private static final int DOOR_INTERACTION_START_WAIT_MS = 700;
    private static final int DOOR_TRAVERSAL_PROGRESS_WAIT_MS = 3500;
    private static final long DOOR_OPEN_STABLE_BEFORE_CROSS_MS = 600L;
    /** Stationary and not animating for longer than this, with the edge unresolved, means the click didn't land. */
    private static final long DOOR_IDLE_ACCEPT_MIN_MS = 1_200L;
    /** Above this combined wait, say which condition released the door await. */
    private static final long DOOR_AWAIT_SLOW_LOG_MS = 900L;

    private Rs2WalkerAwaits() {
    }

    public static AwaitTicket beginTicket() {
        return new AwaitTicket(System.currentTimeMillis(), Rs2Player.getWorldLocation());
    }

    /**
     * A door that answered with a conversation is never going to move us while we stand here waiting
     * for movement, so waiting out the full budget just burns the window in which the menu could be
     * answered — measured at up to ~2.2s per attempt, which is why answering a guarded door used to
     * take two or three tries to catch. Reads a cached, client-thread-refreshed flag, so polling it
     * costs nothing here.
     */
    private static boolean conversationOpened() {
        return Rs2Dialogue.hasSelectAnOption();
    }

    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp) {
        if (ticket == null) {
            return;
        }
        long startPhaseAt = System.currentTimeMillis();
        sleepUntil(() -> {
            if (Thread.currentThread().isInterrupted() || conversationOpened()) {
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            if (ticket.beforePosition() != null && now != null && !ticket.beforePosition().equals(now)) {
                return true;
            }
            return Rs2Player.isMoving() || Rs2Player.isAnimating() || isDoorEdgeResolved(fromWp, toWp);
        }, DOOR_INTERACTION_START_WAIT_MS);
        long startWaitMs = System.currentTimeMillis() - startPhaseAt;

        // Which condition releases the traversal wait is the whole question for door latency: it
        // already returns the moment the door EDGE resolves, so a wait that runs to its 2200ms cap
        // means the edge is not being observed quickly (the live overlay needs a scene recapture
        // before it sees the door open) rather than the door being slow. Naming the winner turns
        // "doors feel slow" into a specific target — the same play that took the transport problem
        // from four rounds of guessing to a one-shot fix.
        final String[] releasedBy = {"timeout"};
        final long[] edgeResolvedSinceMs = {0L};
        long traversalPhaseAt = System.currentTimeMillis();
        sleepUntil(() -> {
            if (Thread.currentThread().isInterrupted() || conversationOpened()) {
                releasedBy[0] = "conversation-or-interrupt";
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            if (now == null) {
                return false;
            }
            if (isDoorCrossingPassable(fromWp, toWp)) {
                releasedBy[0] = "live-edge-passable";
                return true;
            }
            boolean edgeResolved = isDoorEdgeResolved(fromWp, toWp);
            if (edgeResolved) {
                long nowMs = System.currentTimeMillis();
                if (edgeResolvedSinceMs[0] == 0L) {
                    edgeResolvedSinceMs[0] = nowMs;
                }
                if (resolvedDoorReadyForFollowup(edgeResolved, Rs2Player.isMoving(),
                        Rs2Player.isAnimating(), edgeResolvedSinceMs[0], nowMs,
                        DOOR_OPEN_STABLE_BEFORE_CROSS_MS)) {
                    releasedBy[0] = "edge-resolved-stable";
                    return true;
                }
            } else {
                edgeResolvedSinceMs[0] = 0L;
            }
            if (hasReachedDoorFarSide(now, fromWp, toWp)) {
                releasedBy[0] = "arrived-far-side";
                return true;
            }
            long elapsedMs = System.currentTimeMillis() - ticket.startedAtMs();
            boolean idleAccepted = !edgeResolved && shouldAcceptIdleDoorAwait(
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    elapsedMs,
                    edgeResolved);
            if (idleAccepted) {
                releasedBy[0] = "idle-accepted";
            }
            return idleAccepted;
        }, DOOR_TRAVERSAL_PROGRESS_WAIT_MS);
        long traversalWaitMs = System.currentTimeMillis() - traversalPhaseAt;

        if (startWaitMs + traversalWaitMs >= DOOR_AWAIT_SLOW_LOG_MS
                || "live-edge-passable".equals(releasedBy[0])) {
            WebWalkLog.spInfo("door_await | releasedBy={} startWaitMs={} traversalWaitMs={} from={} to={}",
                    releasedBy[0], startWaitMs, traversalWaitMs, fromWp, toWp);
        }
    }

    /**
     * Whether to stop waiting because nothing is happening.
     * <p>
     * This used to end in {@code return edgeResolved}, which made it dead code: the traversal wait
     * returns early the moment the edge resolves, so by the time this runs {@code edgeResolved} is
     * always false and the branch could never fire. The wait therefore always ran to its full
     * {@link #DOOR_TRAVERSAL_PROGRESS_WAIT_MS} budget whenever a door interaction simply did not take.
     * <p>
     * An unresolved edge with the player standing still and not animating well past the interaction
     * tick means the click did not land; the caller re-enters and tries again, which is strictly
     * better than burning the remaining second. Movement, animation and the minimum elapsed time all
     * still veto — a resolved edge does not bypass them, it is simply no longer required.
     * <p>
     * {@code edgeResolved} is retained in the signature because callers pass their own observation
     * and it keeps the decision table explicit about the case that used to be the only one accepted.
     */
    @SuppressWarnings("unused")
    static boolean shouldAcceptIdleDoorAwait(boolean moving, boolean animating, long elapsedMs, boolean edgeResolved) {
        if (moving || animating) {
            return false;
        }
        return elapsedMs > DOOR_IDLE_ACCEPT_MIN_MS;
    }

    public static DoorResolution awaitDoorEdgeResolution(WorldPoint fromWp, WorldPoint toWp, int timeoutMs) {
        if (fromWp == null || toWp == null) {
            return DoorResolution.FAILED_INVALID;
        }
        if (isDoorEdgeResolved(fromWp, toWp)) {
            return DoorResolution.RESOLVED;
        }
        sleepUntil(() -> isDoorEdgeResolved(fromWp, toWp), timeoutMs);
        return isDoorEdgeResolved(fromWp, toWp) ? DoorResolution.RESOLVED : DoorResolution.FAILED_TIMEOUT;
    }

    public static boolean isDoorEdgeResolved(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp == null || toWp == null) {
            return false;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != toWp.getPlane()) {
            return false;
        }
        if (hasReachedDoorFarSide(player, fromWp, toWp)) {
            return true;
        }
        if (isDoorCrossingPassable(fromWp, toWp)) {
            return true;
        }
        int toDist = player.distanceTo2D(toWp);
        try {
            if (!Rs2Player.isMoving() && toDist <= 4 && Rs2Tile.isTileReachable(toWp)) {
                return true;
            }
        } catch (RuntimeException ex) {
            // Script shutdown can interrupt client-thread invoke in reachability probe.
            Throwable cause = ex.getCause();
            if (Thread.currentThread().isInterrupted() || cause instanceof InterruptedException) {
                return false;
            }
            throw ex;
        }
        return false;
    }

    static boolean hasReachedDoorFarSide(WorldPoint player, WorldPoint fromWp, WorldPoint toWp) {
        if (player == null || fromWp == null || toWp == null
                || player.getPlane() != fromWp.getPlane()
                || player.getPlane() != toWp.getPlane()) {
            return false;
        }
        return player.distanceTo2D(toWp) < player.distanceTo2D(fromWp);
    }

    /** Only positive live collision evidence bypasses the conservative reachability/idle fallback. */
    public static boolean isDoorCrossingPassable(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()
                || Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) != 1
                || Microbot.getClient() == null || Microbot.getClientThread() == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldView view = Microbot.getClient().getTopLevelWorldView();
            if (view == null || view.getPlane() != from.getPlane()) return false;
            LocalPoint a = LocalPoint.fromWorld(view, from);
            LocalPoint b = LocalPoint.fromWorld(view, to);
            CollisionData[] maps = view.getCollisionMaps();
            if (a == null || b == null || maps == null || from.getPlane() >= maps.length
                    || maps[from.getPlane()] == null) return false;
            return cardinalEdgePassable(maps[from.getPlane()].getFlags(),
                    a.getSceneX(), a.getSceneY(), b.getSceneX(), b.getSceneY());
        }).orElse(false);
    }

    static boolean cardinalEdgePassable(int[][] flags, int ax, int ay, int bx, int by) {
        int dx = bx - ax, dy = by - ay;
        if (Math.abs(dx) + Math.abs(dy) != 1 || flags == null || ax < 0 || bx < 0
                || ax >= flags.length || bx >= flags.length || flags[ax] == null || flags[bx] == null
                || ay < 0 || by < 0 || ay >= flags[ax].length || by >= flags[bx].length) return false;
        // The unloaded flag is not included in RuneLite's BLOCK_MOVEMENT_FULL mask.
        int full = CollisionDataFlag.BLOCK_MOVEMENT_FULL | 0x1000000;
        int outgoing = dx == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
                : dx == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST
                : dy == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
        int incoming = dx == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST
                : dx == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
                : dy == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH : CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        return (flags[ax][ay] & (full | outgoing)) == 0 && (flags[bx][by] & (full | incoming)) == 0;
    }

    static boolean resolvedDoorReadyForFollowup(boolean edgeResolved, boolean moving,
                                                boolean animating, long resolvedSinceMs,
                                                long nowMs, long stableForMs) {
        return edgeResolved && !moving && !animating && resolvedSinceMs > 0L
                && nowMs - resolvedSinceMs >= stableForMs;
    }
}
