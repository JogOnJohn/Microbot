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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Targeted scene reads; no target entity reference survives a dispatch or probe. */
public final class InteractionTargetRuntime {
    public static final class Snapshot {
        public final WorldPoint position;
        public final boolean sceneReady, moving, ready, confirmed;
        public final long identity;
        Snapshot(WorldPoint p, boolean scene, boolean moving, boolean ready, boolean confirmed, long identity) {
            this.position = p; this.sceneReady = scene; this.moving = moving;
            this.ready = ready; this.confirmed = confirmed; this.identity = identity;
        }
    }
    private final InteractionRequest request;
    private int lastTick = -1;
    private Object lastScene;
    private Snapshot cached;
    public long probeNanos;
    public int probes, cacheHits;

    public InteractionTargetRuntime(InteractionRequest request) { this.request = request; }

    public Snapshot probe(boolean force) {
        if (Microbot.getClientThread() == null) return new Snapshot(null, false, false, false, false, 0);
        long start = System.nanoTime();
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> read(force))
                    .orElse(new Snapshot(null, false, false, false, false, 0));
        } finally { probeNanos += System.nanoTime() - start; }
    }

    private Snapshot read(boolean force) {
        Client c = Microbot.getClient();
        if (c == null || c.getGameState() != GameState.LOGGED_IN || c.getLocalPlayer() == null
                || c.getTopLevelWorldView() == null || c.getTopLevelWorldView().isInstance()) {
            cached = null; lastTick = -1;
            return new Snapshot(null, false, false, false, false, 0);
        }
        Object scene = c.getTopLevelWorldView().getScene();
        if (!force && cached != null && lastTick == c.getTickCount() && lastScene == scene) {
            cacheHits++; return cached;
        }
        probes++;
        WorldPoint player = Rs2Player.getWorldLocation();
        boolean confirmed = false;
        long identity = 0;
        boolean ready = false;
        WorldPoint target = null;
        Shape shape = null;
        boolean action = false;
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
            confirmed = Rs2Dialogue.isInDialogue() && name != null && !name.isHidden() && request.name.equalsIgnoreCase(
                    name.getText().replaceAll("<[^>]*>", "").trim());
        } else {
            Rs2TileObjectModel model = object();
            if (model != null && model.getWorldView() == c.getTopLevelWorldView()) {
                target = model.getWorldLocation(); shape = model.getClickbox(); identity = model.getHash();
                ObjectComposition composition = model.getObjectComposition();
                for (int i = 0; composition != null && composition.getImpostorIds() != null && i < 4; i++)
                    composition = composition.getImpostor();
                action = composition != null && hasAction(composition.getActions(), request.action);
            }
        }
        if (player != null && target != null && player.getPlane() == target.getPlane()
                && player.distanceTo(target) <= 8 && action && !c.isWidgetSelected()
                && !Rs2Dialogue.isInDialogue() && !InputArbiter.isHuman() && !Microbot.pauseAllScripts.get()) {
            Rectangle viewport = new Rectangle(c.getViewportXOffset(), c.getViewportYOffset(),
                    c.getViewportWidth(), c.getViewportHeight());
            if (visible(shape, viewport)) {
                Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(player, 10).keySet();
                ready = request.kind == InteractionRequest.Kind.NPC ? reachable.contains(target)
                        : request.access != null && request.access.getPlane() == player.getPlane()
                        && reachable.contains(request.access);
            }
        }
        lastTick = c.getTickCount(); lastScene = scene;
        return cached = new Snapshot(player, true, Rs2Player.isMoving(), ready, confirmed, identity);
    }

    private Rs2NpcModel npc() {
        return Microbot.getRs2NpcCache().query().withId(request.entityId)
                .where(n -> n.getWorldLocation() != null && n.getWorldLocation().getPlane() == request.anchor.getPlane()
                        && n.getWorldLocation().distanceTo(request.anchor) <= 16).nearest();
    }

    private Rs2TileObjectModel object() {
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
            if (model == null || Microbot.cantReachTarget) return false;
            return model.click(request.action, guard);
        }
        Rs2TileObjectModel model = Microbot.getClientThread().runOnClientThreadOptional(this::object).orElse(null);
        return model != null && model.click(request.action, guard);
    }

    public static boolean visible(Shape shape, Rectangle viewport) {
        return shape != null && !shape.getBounds().isEmpty() && new Area(shape).intersects(viewport);
    }
    private static boolean hasAction(String[] actions, String action) {
        return actions != null && Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);
    }
}
