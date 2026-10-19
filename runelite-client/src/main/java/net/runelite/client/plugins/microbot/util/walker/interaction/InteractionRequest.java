package net.runelite.client.plugins.microbot.util.walker.interaction;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;

/** Retain one request per operation across walkTo polls; cancel it on plugin shutdown. */
public final class InteractionRequest {
    private static final AtomicLong IDS = new AtomicLong();
    public enum Kind { NPC, OBJECT }
    public final long id = IDS.incrementAndGet();
    public final Kind kind;
    public final int entityId;
    public final String name;
    public final String action;
    public final WorldPoint anchor;
    public final WorldPoint access;
    public final InteractionLifecycle lifecycle = new InteractionLifecycle();
    public final AtomicReference<WorldPoint> ownedTarget = new AtomicReference<>();
    public final InteractionTargetRuntime runtime = new InteractionTargetRuntime(this);
    private final BooleanSupplier effect;
    private volatile WorldPoint goal;

    private InteractionRequest(Kind kind, int entityId, String name, String action,
            WorldPoint anchor, WorldPoint access, BooleanSupplier effect) {
        this.kind = Objects.requireNonNull(kind);
        this.entityId = entityId;
        this.name = Objects.requireNonNull(name);
        this.action = Objects.requireNonNull(action);
        this.anchor = Objects.requireNonNull(anchor);
        this.access = access;
        this.effect = Objects.requireNonNull(effect);
        if (entityId < 0 || action.isBlank()) throw new IllegalArgumentException("Invalid interaction target/action");
    }

    public static InteractionRequest npc(int id, String name, WorldPoint near, String action,
            BooleanSupplier additionalConfirmation) {
        return new InteractionRequest(Kind.NPC, id, name, action, near, null, additionalConfirmation);
    }

    public static InteractionRequest object(int id, WorldPoint location, WorldPoint access, String action,
            BooleanSupplier confirmation) {
        return new InteractionRequest(Kind.OBJECT, id, "", action, location, access, confirmation);
    }

    public synchronized boolean bind(WorldPoint destination) {
        if (goal == null) goal = Objects.requireNonNull(destination);
        return goal.equals(destination);
    }

    public boolean effectObserved() { return effect.getAsBoolean(); }
    public WorldPoint destination() { return goal; }
    public boolean active() { return lifecycle.active(); }
    public void cancel() { lifecycle.cancel(); }
}
