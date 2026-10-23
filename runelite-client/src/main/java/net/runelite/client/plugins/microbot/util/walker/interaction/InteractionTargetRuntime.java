package net.runelite.client.plugins.microbot.util.walker.interaction;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.Arrays;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Targeted scene reads; no target entity reference survives a dispatch or probe. */
public final class InteractionTargetRuntime {
    public static final class Snapshot {
        public final WorldPoint position;
        public final boolean sceneReady, moving, ready, confirmed, inputBlocked;
        public final long identity;
        public final String readiness;
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity) {
            this(p, scene, moving, ready, confirmed, identity, false);
        }
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity,
                boolean inputBlocked) {
            this(p, scene, moving, ready, confirmed, identity, inputBlocked, "unknown");
        }
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity,
                boolean inputBlocked, String readiness) {
            this.position = p; this.sceneReady = scene; this.moving = moving;
            this.ready = ready; this.confirmed = confirmed; this.identity = identity;
            this.inputBlocked = inputBlocked;
            this.readiness = readiness;
        }
    }
    private final InteractionRequest request;
    private int lastTick = -1;
    private Object lastScene;
    private Snapshot cached;
    public long probeNanos;
    public int probes, cacheHits;
    public long clientReadNanos, clientCpuNanos, maxClientReadNanos;
    public int clientReadSamples, cpuSamples, targetQueries, collisionProbes;
    private static final java.lang.management.ThreadMXBean THREADS =
            java.lang.management.ManagementFactory.getThreadMXBean();
    private static final long[] READ_BUCKET_NS = {250_000, 500_000, 1_000_000, 2_000_000,
            5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000, Long.MAX_VALUE};
    private final long[] clientReadBuckets = new long[READ_BUCKET_NS.length];

    public InteractionTargetRuntime(InteractionRequest request) { this.request = request; }

    public Snapshot probe(boolean force) {
        if (Microbot.getClientThread() == null) return new Snapshot(null, false, false, false, false, 0);
        long start = System.nanoTime();
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> readTimed(force))
                    .orElse(new Snapshot(null, false, false, false, false, 0));
        } finally { probeNanos += System.nanoTime() - start; }
    }

    private Snapshot readTimed(boolean force) {
        long start = System.nanoTime(), cpu = cpuTime();
        try { return read(force); }
        finally {
            long elapsed = System.nanoTime() - start;
            clientReadNanos += elapsed;
            maxClientReadNanos = Math.max(maxClientReadNanos, elapsed);
            clientReadSamples++;
            for (int i = 0; i < READ_BUCKET_NS.length; i++) {
                if (elapsed <= READ_BUCKET_NS[i]) { clientReadBuckets[i]++; break; }
            }
            long endCpu = cpuTime();
            if (cpu >= 0 && endCpu >= cpu) { clientCpuNanos += endCpu - cpu; cpuSamples++; }
        }
    }

    private static long cpuTime() {
        try {
            return THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled()
                    ? THREADS.getCurrentThreadCpuTime() : -1;
        } catch (UnsupportedOperationException | SecurityException ignored) {
            return -1;
        }
    }

    /** Histogram upper bound, not an exact percentile; samples include cache hits. */
    public long clientP95UpperMicros() {
        long rank = (clientReadSamples * 95L + 99) / 100, seen = 0;
        if (rank == 0) return 0;
        for (int i = 0; i < clientReadBuckets.length; i++) {
            seen += clientReadBuckets[i];
            if (seen >= rank) return Math.min(READ_BUCKET_NS[i], maxClientReadNanos) / 1000;
        }
        return maxClientReadNanos / 1000;
    }

    private Snapshot read(boolean force) {
        Client c = Microbot.getClient();
        if (c == null || c.getGameState() != GameState.LOGGED_IN || c.getLocalPlayer() == null
                || c.getTopLevelWorldView() == null) {
            cached = null; lastTick = -1;
            return new Snapshot(null, false, false, false, false, 0);
        }
        if (c.getTopLevelWorldView().isInstance()) {
            cached = null; lastTick = -1;
            // The final target is not interactable here, but the existing POH/transport
            // executor must still be allowed to approach it by leaving the instance.
            return new Snapshot(Rs2Player.getWorldLocation(), true, Rs2Player.isMoving(), false, false, 0);
        }
        Object scene = c.getTopLevelWorldView().getScene();
        if (!force && cached != null && lastTick == c.getTickCount() && lastScene == scene) {
            cacheHits++; return cached;
        }
        probes++;
        WorldPoint player = Rs2Player.getWorldLocation();
        boolean dialogueOpen = Rs2Dialogue.isInDialogue();
        boolean inputBlocked = dialogueOpen || c.isWidgetSelected();
        boolean confirmed = false;
        long identity = 0;
        boolean ready = false;
        WorldPoint target = null;
        Shape shape = null;
        boolean action = false;
        WorldArea footprint = null;
        String readiness = "target-missing";
        if (request.kind == InteractionRequest.Kind.NPC) {
            Rs2NpcModel model = npc();
            NPC npc = model == null ? null : model.getNpc();
            if (npc != null && npc.getWorldView() == c.getTopLevelWorldView()) {
                target = npc.getWorldLocation(); shape = npc.getConvexHull();
                NPCComposition composition = npc.getTransformedComposition();
                action = composition != null && hasAction(composition.getActions(), request.action);
                identity = System.identityHashCode(npc);
            }
            Widget name = c.getWidget(WidgetInfo.DIALOG_NPC_NAME);
            confirmed = dialogueOpen && name != null && !name.isHidden() && request.name.equalsIgnoreCase(
                    name.getText().replaceAll("<[^>]*>", "").trim());
        } else {
            Rs2TileObjectModel model = object();
            if (model != null && model.getWorldView() == c.getTopLevelWorldView()) {
                target = model.getWorldLocation(); shape = model.getClickbox(); identity = model.getHash();
                footprint = model.getInteractionFootprint();
                ObjectComposition composition = model.getObjectComposition();
                for (int i = 0; composition != null && composition.getImpostorIds() != null && i < 4; i++)
                    composition = composition.getImpostor();
                action = composition != null && hasAction(composition.getActions(), request.action);
            }
        }
        if (target != null) readiness = player == null ? "player-missing"
                : player.getPlane() != target.getPlane() ? "wrong-plane"
                : player.distanceTo(target) > 8 ? "outside-probe-radius"
                : !action ? "action-unavailable" : inputBlocked ? "input-blocked" : "off-canvas";
        if (player != null && target != null && player.getPlane() == target.getPlane()
                && player.distanceTo(target) <= 8 && action && !inputBlocked
                && !InputArbiter.isHuman() && !Microbot.pauseAllScripts.get()) {
            Rectangle viewport = new Rectangle(c.getViewportXOffset(), c.getViewportYOffset(),
                    c.getViewportWidth(), c.getViewportHeight());
            if (visible(shape, viewport)) {
                collisionProbes++;
                Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(player, 10).keySet();
                ready = request.kind == InteractionRequest.Kind.NPC ? reachable.contains(target)
                        : request.access != null && request.access.getPlane() == player.getPlane()
                        && reachable.contains(request.access)
                        && objectEdgeReachable(footprint, reachable, c.getTopLevelWorldView());
                readiness = ready ? "ready" : request.kind == InteractionRequest.Kind.NPC ? "npc-unreachable"
                        : request.access == null || !reachable.contains(request.access) ? "access-unreachable" : "object-edge-blocked";
            }
        }
        lastTick = c.getTickCount(); lastScene = scene;
        // An ongoing object animation is useful progress, not permission to reclick.
        // The independent eight-second effect deadline still bounds a stuck animation.
        boolean active = Rs2Player.isMoving()
                || request.kind == InteractionRequest.Kind.OBJECT && Rs2Player.isAnimating();
        return cached = new Snapshot(player, true, active, ready, confirmed, identity, inputBlocked, readiness);
    }

    private Rs2NpcModel npc() {
        targetQueries++;
        return Microbot.getRs2NpcCache().query().withId(request.entityId)
                .where(n -> n.getWorldLocation() != null && n.getWorldLocation().getPlane() == request.anchor.getPlane()
                        && n.getWorldLocation().distanceTo(request.anchor) <= 16).nearest();
    }

    private Rs2TileObjectModel object() {
        targetQueries++;
        return Microbot.getRs2TileObjectCache().query().withId(request.entityId)
                .where(o -> request.anchor.equals(o.getWorldLocation())).first();
    }

    public boolean dispatch(Snapshot expected, BooleanSupplier ownerValid) {
        Snapshot live = probe(true);
        if (!live.ready || live.identity != expected.identity || !ownerValid.getAsBoolean()) return false;
        BooleanSupplier guard = () -> {
            if (!ownerValid.getAsBoolean()) return false;
            Snapshot current = probe(true);
            return current.ready && current.identity == expected.identity;
        };
        if (request.kind == InteractionRequest.Kind.NPC) {
            Rs2NpcModel model = Microbot.getClientThread().runOnClientThreadOptional(this::npc).orElse(null);
            // Guarded dispatch owns obstruction recovery; a global flag from an older
            // action must not veto a freshly reachable target or trigger nested walking.
            if (model == null) return false;
            return model.click(request.action, guard);
        }
        Rs2TileObjectModel model = Microbot.getClientThread().runOnClientThreadOptional(this::object).orElse(null);
        return model != null && model.click(request.action, guard);
    }

    public static boolean visible(Shape shape, Rectangle viewport) {
        return shape != null && !shape.getBounds().isEmpty() && new Area(shape).intersects(viewport);
    }

    public static boolean objectEdgeReachable(WorldArea footprint, Set<WorldPoint> reachable,
            net.runelite.api.WorldView view) {
        if (footprint == null || footprint.getWidth() < 1 || footprint.getHeight() < 1
                || footprint.getWidth() > 8 || footprint.getHeight() > 8 || view == null
                || view.getCollisionMaps() == null || footprint.getPlane() < 0
                || footprint.getPlane() >= view.getCollisionMaps().length
                || view.getCollisionMaps()[footprint.getPlane()] == null
                || footprint.getX() <= view.getBaseX() || footprint.getY() <= view.getBaseY()
                || footprint.getX() + footprint.getWidth() >= view.getBaseX() + view.getSizeX()
                || footprint.getY() + footprint.getHeight() >= view.getBaseY() + view.getSizeY()) return false;
        Rs2WorldArea area = new Rs2WorldArea(footprint);
        for (WorldPoint tile : reachable) {
            if (area.isInMeleeDistance(tile.toWorldArea())
                    && area.canMelee(view, new Rs2WorldArea(tile, 1, 1))) return true;
        }
        return false;
    }
    private static boolean hasAction(String[] actions, String action) {
        return actions != null && Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);
    }
}
