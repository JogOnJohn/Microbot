# Interaction-aware walking (opt-in)

Keep one InteractionRequest per action. Existing coordinate-only walker calls
and WalkerState return meanings are unchanged. A coordinate ARRIVED is not an
interaction SUCCEEDED.

```java
InteractionRequest request = InteractionRequest.npc(
    homeownerId, homeownerName, homeDestination, "Talk-to", () -> contractCompleted);
InteractionResult result = Rs2Walker.walkWithBankedTransportsAndState(
    homeDestination, 0, request);
```

Poll with the same request from the script thread. For ordinary non-banked
approach use the overload Rs2Walker.walkTo(destination, request). Do not call
from the client thread. On shutdown/state replacement call
Rs2Walker.cancelInteraction(request); drop the request only after cancellation
or terminal success. Never create a fresh request on every tick: that would
replace the operation deadline and attempt history.

IN_PROGRESS covers approach, dispatch and awaiting effect. SUCCEEDED requires
the intended NPC dialogue or supplied effect after a dispatch attempt. BLOCKED,
CANCELLED and FAILED are terminal for the caller; report/recover explicitly.
No generic walker method advances a contract dialogue or accepts an assignment.
Existing open dialogue is not taken as a new successful interaction.

Mapped furniture can use InteractionRequest.object(id, exactLocation, access,
action, confirmation). The effect predicate must be fast and read-only (for
example a particular hotspot value changing). Submit separate requests for
Remove and Build. The pilot requires mapped access; it does not infer footprint
or room access for unknown objects. Existing graph staircase transports retain
their existing execution/landing handling, not a nested interaction request.

## Safety and performance

- Resolve by ID and expected location/area, world view, plane, action and actual
  viewport shape. NPC probes use the live entity, not its cached distance field.
- Visibility is insufficient. A bounded local collision path must reach the
  target/access. Objects additionally require a reachable cardinal interaction
  edge on their live footprint, with loaded collision data. A mapped approach
  tile alone cannot establish that the furniture is on the same side of a door.
  Closed crossings remain with normal door-aware routing.
- Read-only probes are memoized per request/game tick/scene; dispatch revalidates
  after mouse approach and again at menu execution. NewMenuEntry guards are
  opt-in; legacy entries have no predicate and retain their existing path.
- The shared walker lock serializes dispatch, not dialogue waiting. Pending
  operations reserve walker input between polls. Explicit external setTarget or
  human input cancels them; late queued guarded clicks are rejected.
- One operation has a 120-second deadline and at most three accepted dispatch
  attempts. Each attempt waits at most eight seconds, with earlier recovery
  after stationary progress loss. Pauses/interruptions cancel instead of clicking.
  The operation deadline is checked at controller polls and guarded in-flight
  input/readiness checks; an existing transport call retains its own bounded
  waits. Expiration prevents further owned input. This is not a preemptive timeout
  or a guarantee that an already-issued server action has been cancelled.
- Object animations defer stationary retry while the independent effect deadline
  still bounds the attempt. Guarded NPC dispatch uses its own fresh collision
  check and bounded recovery, not the global rejection flag from an older action.
- Loading/instances are not accepted as a ready target scene. Ordinary W330
  travel still executes through the existing planner/transport machinery.
- Unrelated open dialogue or a selected widget blocks approach/dispatch, but a
  newly observed intended interaction effect can still complete a pending request.
- interaction_done reports probe wall time including client-thread handoff, plus
  separate clientWallUs, clientCpuUs (only where CPU timing is already enabled),
  cpuSamples, maxClientUs, clientP95UpperUs, targetQueries and collisionProbes.
  The p95 value is a fixed-bucket upper bound, includes memoized reads and is not
  an exact percentile. Missing CPU timing is not zero measured CPU work.
  interaction_end reports terminal reason and attempts for failed/cancelled work.
  No readiness predicate performs input or a second pathfinder search.

## Rollout

Mahogany Homes 1.0.20 provides separate interactionAwareTurnIn and
interactionAwareFurniture settings, both off by default. Furniture adoption is
limited to mapped access points. Deploy the paired client before enabling the
settings, and restore the old Homes/client pair together for rollback. An older
client with the same Microbot version string may not implement this custom API.

Homes must poll an outstanding request before advancing its dialogue, and must
not leave its work/hand-in state while that request remains pending. A completion
chat message or final repaired hotspot can arrive between state-machine ticks;
neither may bypass ownership release. The first poll binds the declared target,
not request.destination(), which is null until binding succeeds.

LEGACY and EXACT share this executor. Backend costs, W330 preferences, house
policy, manual overlays and minimap behavior are not changed by these overloads.
Do not claim planner or end-to-end speedups from a few unmatched contracts.

## Upstream portability

InteractionLifecycle/Request/Result, targeted runtime probes, guarded model input
and tests are separated from the local bank-aware overload. A future Microbot
development PR can propose those generic pieces first, adapting only the narrow
walker hook points. The custom POH/data-sync policy need not be included. No
upstream PR is promised without validation against that branch's threading and
input contracts; local correctness takes priority over reducing a future diff.

## POH pool policy

Web Walker exposes Always/Auto/Off separately from interaction-aware walking.
Always retains the prior pool-first behavior and reuse cooldown. Auto checks
enabled restoration conditions (missing prayer, health below 90%, run below
85%, missing special attack); Off and healthy Auto skip the pool load wait and
selection. These settings do not change W330 route eligibility or priority.

The 2026-10-20 six-house cohort exercised three EXACT and three LEGACY houses,
six NPC hand-ins and eight mapped furniture effects without interaction retries.
Subsequent Barbara access and pool-policy changes have offline coverage only.
Nearby graph stairs remain on the existing transport executor; an on-screen
ladder is not automatically an interaction request in this pilot.

The later d56349e3ea build with Homes 1.0.21 passed five more houses (two EXACT,
three LEGACY), including both Barbara bed effects and five NPC hand-ins, all on
the first attempt. Route replacement now cancels the obsolete search without
interrupting shared configuration refresh: interrupted client-thread reads can
otherwise cache empty fallback inventory/facility values for the next request.
The existing request-identity checks still suppress obsolete search results.
This bounded pilot is not the full controlled/matched efficiency acceptance set.

The 15:19 recurrence exposed two additional cancellation sites in the walker
lifecycle and route-clear paths. cffd917aef applies non-interrupting cancellation
there too, and suppresses stale/interrupted post-refresh searches, fallback and
cave comparisons. Six regression tests exercise these actual walker entry points;
the plugin-only cancellation tests did not cover them. Full suite: 2,449 passed,
six skipped. A focused W330/EXACT Tau journey then completed after a hosted-house
jewellery-box teleport. This does not replace the broader live acceptance matrix.
