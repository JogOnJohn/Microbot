package net.runelite.client.plugins.microbot.util.walker.interaction;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.Scene;
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
import net.runelite.client.plugins.microbot.util.misc.SceneClickSafety;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Targeted scene reads; no target entity reference survives a dispatch or probe. */
public final class InteractionTargetRuntime {
    public static final class Snapshot {
        public final WorldPoint position;
        public final boolean sceneReady, moving, ready, confirmed, inputBlocked;
        public final long identity;
        public final String readiness;
        public final boolean confirmationAllowed;
        private final long targetIdentity, viewIdentity, sceneIdentity;
        private final int plane, baseX, baseY;
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity) {
            this(p, scene, moving, ready, confirmed, identity, false);
        }
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity,
                boolean inputBlocked) {
            this(p, scene, moving, ready, confirmed, identity, inputBlocked, "unknown");
        }
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity,
                boolean inputBlocked, String readiness) {
            this(p, scene, moving, ready, confirmed, identity, inputBlocked, readiness, scene,
                    0, 0, 0, -1, 0, 0);
        }
        private Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed,
                long identity, boolean inputBlocked, String readiness, boolean confirmationAllowed,
                long targetIdentity, long viewIdentity, long sceneIdentity, int plane, int baseX, int baseY) {
            this.confirmationAllowed = confirmationAllowed;
            this.targetIdentity = targetIdentity; this.viewIdentity = viewIdentity;
            this.sceneIdentity = sceneIdentity; this.plane = plane; this.baseX = baseX; this.baseY = baseY;
            this.position = p; this.sceneReady = scene; this.moving = moving;
            this.ready = ready; this.confirmed = confirmed; this.identity = identity;
            this.inputBlocked = inputBlocked;
            this.readiness = readiness;
        }
    }
    private final InteractionRequest request;
    private int lastTick = -1;
    private final WeakReferenceIdentity targetIdentity = new WeakReferenceIdentity();
    private final WeakReferenceIdentity viewIdentity = new WeakReferenceIdentity();
    private final WeakReferenceIdentity sceneIdentity = new WeakReferenceIdentity();
    private WeakReference<Object> lastScene = new WeakReference<>(null);
    private Snapshot cached;
    private Set<WorldPoint> cachedReachable;
    private long lastViewIdentity;
    private int lastPlane, lastBaseX, lastBaseY;
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

    /** Exact reference generations without retaining an entity or relying on hash uniqueness. */
    private static final class WeakReferenceIdentity {
        private WeakReference<Object> reference = new WeakReference<>(null);
        private long generation;
        long of(Object value) {
            if (value == null) return 0;
            if (reference.get() != value) {
                reference = new WeakReference<>(value);
                generation++;
            }
            return generation;
        }
    }

    public Snapshot probe(boolean force) {
        if (Microbot.getClientThread() == null || Thread.currentThread().isInterrupted()) return absent();
        long start = System.nanoTime();
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> readTimed(force))
                    .orElseGet(this::absent);
        } catch (RuntimeException unavailable) {
            cached = null; cachedReachable = null; lastTick = -1;
            return new Snapshot(null, false, false, false, false, 0);
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
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client c = Microbot.getClient();
            if (c == null || c.getGameState() != GameState.LOGGED_IN || c.getLocalPlayer() == null
                    || c.getTopLevelWorldView() == null) return absent();
            WorldView view = c.getTopLevelWorldView();
            Player actor = c.getLocalPlayer();
            Scene scene = view.getScene();
            if (scene == null || actor.getWorldView() != view
                    || c.getWorldView(view.getId()) != view) return absent();
            long viewStamp = viewIdentity.of(view), sceneStamp = sceneIdentity.of(scene);
            int tick = c.getTickCount(), plane = view.getPlane(), bx = view.getBaseX(), by = view.getBaseY();
            WorldPoint player = actor.getWorldLocation();
            boolean moving = Rs2Player.isMoving();
            boolean dialogueOpen = Rs2Dialogue.isInDialogue();
            boolean inputBlocked = dialogueOpen || c.isWidgetSelected();
            if (view.isInstance() || scene.isInstance()) {
                absent();
                // Routing out of POH/banked transports remains possible; effects cannot confirm here.
                return new Snapshot(Rs2Player.getWorldLocation(), true, moving, false, false, 0, inputBlocked, "instance",
                        false, 0, viewStamp, sceneStamp, plane, bx, by);
            }
            if (player == null || player.getPlane() != plane) return absent();
            boolean reuse = !force && cached != null && lastTick == tick && lastScene.get() == scene
                    && lastViewIdentity == viewStamp && lastPlane == plane
                    && lastBaseX == bx && lastBaseY == by && player.equals(cached.position);
            if (reuse) cacheHits++; else { probes++; cachedReachable = null; }
            boolean confirmed = false, action = false, ready = false;
            long identity = 0;
            long actualIdentity = 0;
            WorldPoint target = null;
            WorldArea footprint = null;
            Rectangle clear = null;
            if (request.kind == InteractionRequest.Kind.NPC) {
                Rs2NpcModel model = npc();
                NPC npc = model == null ? null : model.getNpc();
                if (npc != null && npc.getWorldView() == view && view.npcs() != null
                        && view.npcs().byIndex(npc.getIndex()) == npc && npc.getId() == request.entityId) {
                    target = npc.getWorldLocation();
                    NPCComposition composition = npc.getTransformedComposition();
                    action = composition != null && hasAction(composition.getActions(), request.action);
                    identity = System.identityHashCode(npc);
                    actualIdentity = targetIdentity.of(npc);
                    clear = SceneClickSafety.forActor(npc, null);
                }
                Widget name = c.getWidget(WidgetInfo.DIALOG_NPC_NAME.getId());
                String text = name == null || name.isHidden() ? null : name.getText();
                confirmed = dialogueOpen && text != null && request.name.equalsIgnoreCase(
                        text.replaceAll("<[^>]*>", "").trim());
            } else {
                Rs2TileObjectModel model = object();
                TileObject live = liveObject(model, view);
                if (live != null && live.getId() == request.entityId) {
                    target = model.getWorldLocation();
                    footprint = model.getInteractionFootprint();
                    identity = model.getHash();
                    actualIdentity = targetIdentity.of(live);
                    clear = SceneClickSafety.forObject(live, null);
                    ObjectComposition composition = model.getObjectComposition();
                    for (int i = 0; composition != null && composition.getImpostorIds() != null && i < 4; i++)
                        composition = composition.getImpostor();
                    action = composition != null && hasAction(composition.getActions(), request.action);
                }
            }
            String readiness = target == null ? "target-missing"
                    : player.getPlane() != target.getPlane() ? "wrong-plane"
                    : player.distanceTo(target) > 8 ? "outside-probe-radius"
                    : !action ? "action-unavailable" : inputBlocked ? "input-blocked" : "off-canvas";
            if (target != null && target.getPlane() == plane && player.distanceTo(target) <= 8
                    && action && !inputBlocked && !InputArbiter.isHuman() && !Microbot.pauseAllScripts.get()
                    && clear != null && !clear.isEmpty()) {
                if (cachedReachable == null) {
                    collisionProbes++;
                    java.util.Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(player, 10);
                    if (reachable != null) cachedReachable = new java.util.HashSet<>(reachable.keySet());
                }
                if (cachedReachable != null) {
                    ready = request.kind == InteractionRequest.Kind.NPC ? cachedReachable.contains(target)
                            : request.access != null && request.access.getPlane() == plane
                            && cachedReachable.contains(request.access)
                            && objectEdgeReachable(footprint, cachedReachable, view);
                }
                readiness = ready ? "ready" : request.kind == InteractionRequest.Kind.NPC ? "npc-unreachable"
                        : request.access == null || cachedReachable == null || !cachedReachable.contains(request.access)
                        ? "access-unreachable" : "object-edge-blocked";
            }
            if (c.getGameState() != GameState.LOGGED_IN || c.getTopLevelWorldView() != view
                    || view.getScene() != scene || c.getTickCount() != tick || view.getPlane() != plane
                    || view.getBaseX() != bx || view.getBaseY() != by
                    || c.getLocalPlayer() != actor || actor.getWorldView() != view) return absent();
            lastTick = tick; lastScene = new WeakReference<>(scene); lastViewIdentity = viewStamp;
            lastPlane = plane; lastBaseX = bx; lastBaseY = by;
            boolean active = moving || request.kind == InteractionRequest.Kind.OBJECT && Rs2Player.isAnimating();
            return cached = new Snapshot(player, true, active, ready, confirmed, identity, inputBlocked, readiness,
                    true, actualIdentity, lastViewIdentity, sceneStamp, plane, bx, by);
        }).orElseGet(this::absent);
    }

    private Snapshot absent() {
        cached = null; cachedReachable = null; lastTick = -1; lastScene.clear();
        return new Snapshot(null, false, false, false, false, 0);
    }

    private Rs2NpcModel npc() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            targetQueries++;
            if (Microbot.getRs2NpcCache() == null) return null;
            return Microbot.getRs2NpcCache().query().withId(request.entityId)
                    .where(n -> n.getWorldLocation() != null
                            && n.getWorldLocation().getPlane() == request.anchor.getPlane()
                            && n.getWorldLocation().distanceTo(request.anchor) <= 16).nearest();
        }).orElse(null);
    }

    private Rs2TileObjectModel object() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            targetQueries++;
            if (Microbot.getRs2TileObjectCache() == null) return null;
            return Microbot.getRs2TileObjectCache().query().withId(request.entityId)
                    .where(o -> request.anchor.equals(o.getWorldLocation())).first();
        }).orElse(null);
    }

    private TileObject liveObject(Rs2TileObjectModel model, WorldView view) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (model == null || model.getWorldView() != view || view.getScene() == null) return null;
            WorldPoint anchor = model.getWorldLocation();
            if (anchor == null || !anchor.equals(request.anchor) || anchor.getPlane() != view.getPlane()) return null;
            int x = anchor.getX() - view.getBaseX(), y = anchor.getY() - view.getBaseY(), p = view.getPlane();
            Tile[][][] tiles = view.getScene().getTiles();
            if (tiles == null || p < 0 || p >= tiles.length || tiles[p] == null || x < 0
                    || x >= tiles[p].length || tiles[p][x] == null || y < 0 || y >= tiles[p][x].length) return null;
            Tile tile = tiles[p][x][y];
            if (tile == null) return null;
            GameObject[] objects = tile.getGameObjects();
            if (objects != null) for (GameObject object : objects)
                if (object != null && model.represents(object)) return object;
            TileObject wall = tile.getWallObject(), ground = tile.getGroundObject(), decor = tile.getDecorativeObject();
            if (wall != null && model.represents(wall)) return wall;
            if (ground != null && model.represents(ground)) return ground;
            return decor != null && model.represents(decor) ? decor : null;
        }).orElse(null);
    }

    private static boolean sameTarget(Snapshot a, Snapshot b) {
        return a != null && b != null && a.ready && b.ready && a.identity == b.identity
                && a.targetIdentity == b.targetIdentity && a.viewIdentity == b.viewIdentity
                && a.sceneIdentity == b.sceneIdentity && a.plane == b.plane && a.baseX == b.baseX && a.baseY == b.baseY;
    }

    public boolean dispatch(Snapshot expected, BooleanSupplier ownerValid) {
        if (expected == null || ownerValid == null || Microbot.getClientThread() == null) return false;
        try {
            if (!ownerValid.getAsBoolean() || !sameTarget(probe(true), expected)) return false;
            if (request.kind == InteractionRequest.Kind.NPC) {
                Rs2NpcModel model = npc();
                if (model == null) return false;
                BooleanSupplier guard = () -> {
                    try {
                        if (!ownerValid.getAsBoolean() || !sameTarget(probe(true), expected)) return false;
                        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                            NPC npc = model.getNpc();
                            WorldView view = Microbot.getClient().getTopLevelWorldView();
                            return npc != null && view != null && npc.getWorldView() == view && view.npcs() != null
                                    && view.npcs().byIndex(npc.getIndex()) == npc
                                    && targetIdentity.of(npc) == expected.targetIdentity
                                    && SceneClickSafety.forActor(npc, null) != null;
                        }).orElse(false);
                    } catch (RuntimeException unavailable) { return false; }
                };
                return guard.getAsBoolean() && model.click(request.action, guard);
            }
            Rs2TileObjectModel model = object();
            if (model == null) return false;
            BooleanSupplier guard = () -> {
                try {
                    if (!ownerValid.getAsBoolean() || !sameTarget(probe(true), expected)) return false;
                    return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                        WorldView view = Microbot.getClient().getTopLevelWorldView();
                        TileObject live = view == null ? null : liveObject(model, view);
                        return live != null && targetIdentity.of(live) == expected.targetIdentity
                                && SceneClickSafety.forObject(live, null) != null;
                    }).orElse(false);
                } catch (RuntimeException unavailable) { return false; }
            };
            return guard.getAsBoolean() && model.click(request.action, guard);
        } catch (RuntimeException unavailable) { return false; }
    }

    public static boolean visible(Shape shape, Rectangle viewport) {
        return shape != null && !shape.getBounds().isEmpty() && new Area(shape).intersects(viewport);
    }

    public static boolean objectEdgeReachable(WorldArea footprint, Set<WorldPoint> reachable,
            net.runelite.api.WorldView view) {
        if (Microbot.getClientThread() == null || reachable == null) return false;
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
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
                    if (tile != null && area.isInMeleeDistance(tile.toWorldArea())
                            && area.canMelee(view, new Rs2WorldArea(tile, 1, 1))) return true;
                }
                return false;
            }).orElse(false);
        } catch (RuntimeException unavailable) { return false; }
    }
    private static boolean hasAction(String[] actions, String action) {
        return actions != null && Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);
    }
}
