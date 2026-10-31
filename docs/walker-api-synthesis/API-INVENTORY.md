# API Inventory

All findings refer to the pinned commits in [summary.json](inventory/summary.json),
not moving branch tips. Source declarations and caller locations are indexed by
file, line and Git blob. Matching signatures do not prove matching behaviour.

## Core Surface

| Variant | Rs2Walker methods | Rs2PathApi methods | External util.walker types |
| --- | ---: | ---: | ---: |
| Canonical playable | 84 | 20 | 66 |
| Running-client branch | 87 | 20 | 73 |
| Data-sync branch | 87 | 20 | 73 |
| Upstream development | 85 | 51 | 124 |

Counts are explicit externally visible declarations, not inherited or Lombok-generated
members. Full scope also includes shortestpath, PoH, tileobject and legacy gameobject.
The CSV comparison keys retain source type spellings; imports are not type-resolved.

## Equivalents, Gaps And Proposed Ownership

| Family | Current implementation | Upstream addition | Recommended direction, not implemented |
| --- | --- | --- | --- |
| Script movement | Rs2Walker legacy movement/state methods | Most signatures retained; split movement/doors/transports helpers | Keep public legacy contract; delegate selectively only after behavioural tests |
| Path facade | 20 stateless delegation methods | 31 additional methods, typed planning and active-route diagnostics | Keep our thin facade; implement new behaviour behind an owned delegate |
| Typed planning | Pathfinder/config/plugin state | Rs2RouteRequest, Rs2RouteResult, Rs2RoutePlanner, policy/snapshot types | Reuse upstream concepts and names; do not invent competing route types |
| Interaction handoff | walkWithStateUntil, banked predicate/owned-target variants | Rs2InteractionApproach readiness helpers | Preserve ownership and predicate semantics; readiness is not interaction success |
| Confirmed interaction | Runtime and data-sync interaction package; absent from canonical | Not the same abstraction as typed route planning | Deliberately reconcile our interaction API first; retain request identity and cancellation |
| Transport requirements | Legacy Transport and banked routing | Rs2TransportEdge, Rs2TransportLoadout and edge requirement helpers | Boundary adapter must preserve requirement groups/quantities and converter format |
| Object access | Legacy Rs2GameObject and raw scene objects | Tile-object cache/models and split execution | Explicit conversion/lifetime/thread policy; not a mechanical rename |
| Collision queries | Walker package-private search helpers and public walkability check | Public facade nearest/within helpers | Query-only adapter is plausible; automatic destination snapping stays separate |
| Diagnostics | Current route/telemetry hooks | Planner shadow/canary/comparison infrastructure | Optional later work, not required to expose a helper |

### Rs2Walker Differences

79 declared method shapes match across canonical and upstream. Canonical-only methods:

```text
isAutoRunEnabled()
logPathTeleportsOnce(WorldPoint,List<WorldPoint>)
recordTransportAttempt(Transport)
walkWithBankedTransportsUntil(WorldPoint,int,BooleanSupplier)
walkWithBankedTransportsUntil(WorldPoint,int,AtomicReference<WorldPoint>,BooleanSupplier)
```

Upstream-only methods:

```text
getMissingTransportEdgeItemIdsWithQuantities(List<Rs2TransportEdge>)
getMissingTransportEdgeLoadout(List<Rs2TransportEdge>)
getMissingTransportEdges(List<Rs2TransportEdge>)
getTransportEdgesForDestination(WorldPoint,boolean)
isActiveDoorEdge(WorldPoint,WorldPoint)
requestRecoveryReplanForTest()
```

WalkerState has the same four constants: UNREACHABLE, MOVING, ARRIVED, EXIT.
This does not establish equivalent state transitions. Do not publish test hooks
as new supported plugin APIs merely because upstream exposes them.

## Semantic Traps

1. **Canonical is not runtime.** Runtime `19049dd3b907` and data-sync
   `6a8317fca647` declare the interaction package. Canonical does not. Runtime adds
   `walkTo(WorldPoint,InteractionRequest)`,
   `walkWithBankedTransportsAndState(WorldPoint,int,InteractionRequest)` and
   `cancelInteraction(InteractionRequest)`. Matching package/method counts between
   runtime and data-sync do not prove identical bodies.
2. **Two arguments do not identify an overload.** Canonical already has
   `walkTo(WorldPoint,int)`. An arity-only caller audit can incorrectly appear
   compatible with `walkTo(WorldPoint,InteractionRequest)`. The pinned private Homes
   caller actually uses a separate three-argument collision:
   `walkWithBankedTransportsAndState(WorldPoint,int,InteractionRequest)` versus
   canonical `(WorldPoint,int,boolean)`, with different return types too. It also
   requires request factories/cancellation and public request/lifecycle members.
3. **Arrival, readiness and effect confirmation differ.** Our predicate-based
   `walkWithStateUntil` can return ARRIVED when its completion predicate becomes
   true before coordinate arrival. Runtime InteractionResult only reports SUCCEEDED
   for its interaction confirmation, after a dispatched attempt and the scene,
   anchor-plane and proximity gates. Retain the same request object across polls:
   runtime reservation uses identity, not just equal field values. Cancel on
   abandonment, stop or replacement; do not create a new request every tick.
   Cancellation marks the request immediately; owned-route cleanup can wait for
   the next cancellation checkpoint when the lock is busy.
4. **Preserve banked target ownership.** Existing caller-held AtomicReference
   ownership, lock-busy MOVING and changed-owner EXIT handling cannot be replaced
   by an unconditional success boolean. Canonical caller-driven handoff clears its
   route/holder and unlocks before returning to the caller. Runtime internal
   dispatch clears its owned route but retains the interaction reservation and
   walker lock until the surrounding finally block. Keep route ownership,
   interaction reservation and lock ownership distinct; do not require an
   unlock-before-dispatch rewrite.
5. **Planning is not movement.** Upstream `Rs2PathApi.plan` is synchronous, rejects
   client-thread invocation, serializes on pathfinder state and may temporarily
   change bank/config transport state. It does not publish an active route but is
   not a pure query. Restoring the bank flag and refreshing is not full configuration
   rollback; the restoration refresh can itself throw. Its snapshot/policy/shadow/canary dependencies cannot simply
   be pasted into our thin facade.
6. **Search complete is not target reached.** Rs2RouteResult.isSearchCompleted()
   includes SEARCH_EXHAUSTED and CUTOFF_REACHED, excluding only CANCELLED/FAILED.
   Even TARGET_REACHED describes the planner, not physical player arrival.
7. **Nearest tile is not reachable tile.** The upstream nearest helper searches
   rings starting at radius 1, on the same plane, for mapped nonblocked tiles.
   It neither returns radius-zero target nor proves connectivity from the start.
   The within-radius helper deliberately permits unknown maps/regions. Preserve
   instance/unmapped handling, and do not silently move scripted destinations.
   Both pinned walkers use the nearest lookup diagnostically and reject the original
   blocked destination rather than automatically snapping it. The nearest helper
   returns null for absent map/target or zero/negative radius; the within helper
   accepts absent map/target, checks radius zero for nonpositive radii and permits
   an unknown neighboring region. These are deliberately different query contracts.
8. **Read scene state on the client thread.** Rs2InteractionApproach is a readiness
   check, not an interaction executor. Its WallObject branch reads world location
   before its client-thread callback; adapt rather than copying that threading
   assumption. Cached models and raw RuneLite objects have different lifetimes.

## Caller Evidence

Hub main's lexical static call inventory includes 167 two-argument walkTo calls in
80 files and 163 one-argument calls in 71 files. These are call sites, not plugin
counts or type-resolved overloads. Legacy Rs2GameObject usage also remains substantial.
Keep backward-compatible wrappers during any implementation phase.

The Hub reports contain main plus changed-file snapshots for 17 private/experimental
branches. Do not sum them as unique calls. A branch with zero matching changed-file
records still inherits calls from main. Dirty uncommitted Shared Automation documents
were not included or changed. See the Hub report for exact caller rows.

## Evidence Limits

The JDK parser performs syntax parsing only, not symbol analysis, annotation processing
or compilation. It does not expand inheritance, implicit constructors, generated
getters, instance/fluent receiver types, reflection, or arbitrary aliases. Wildcard
static-import calls are explicitly candidates. CSV availability is an arity-based
screening aid, never build or behavioural proof. See the tool README before reusing it.
