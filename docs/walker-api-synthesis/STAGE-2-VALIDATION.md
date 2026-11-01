# Stage 2: Readiness-Only Object Helpers

2026-10-31, isolated `codex/walker-api-synthesis-20261031` client branch.

## API And Deliberate Limits

Added upstream-shaped `Rs2InteractionApproach.isReady(GameObject)` and
`isReady(WallObject)`. No existing walker or Hub caller is redirected to them.
These queries do not click, turn the camera, publish/cancel routes, refresh
transport policy, reserve an interaction or report confirmed interaction success.
The normal cache may hydrate when queried; this is not a route/config mutation.

True means that this snapshot has a live, cached, visible/uncovered object and a
bounded local approach. It is not proof that a requested menu action exists or is
unlocked, that dispatch will succeed later, or that any game effect occurred.
Callers must re-query and retain action/ownership/input/geometry guards at dispatch.

Stricter adaptations relative to pinned upstream:

- Every raw object/player/WorldView/scene read occurs inside client-thread callbacks,
  including the WallObject world location formerly read before the callback.
- Require login and the same active top-level player/object WorldView; unknown,
  replaced or missing scene state fails closed.
- Query the singleton tile-object cache, but additionally require exact raw object
  identity on its current anchor tile. A same-ID/hash replacement or same-tick cache
  remnant cannot authorize readiness. Validate actual null/ragged/small tile arrays.
- Use SceneClickSafety's uncovered click area, not a permissive screen-coordinate
  approximation. Missing or fully covered geometry returns false.
- Preserve upstream's same-plane 12-tile target range. Query collision-aware local
  reachability with a 16-step bound, rather than an unbounded distanceToPath search.
  Game-object readiness requires its selected approach in that bounded map;
  wall-object readiness requires a same-plane reachable tile on the wall tile or
  cardinal-adjacent to it. Diagonal-only proximity is insufficient: it can be a
  blocked corner. This is deliberately more conservative than upstream.
- Runtime scene-read exceptions, absent/timed-out callbacks and interrupted callers
  return false. No persistent worker, retry, retained entity or camera recovery.

Instances and secondary WorldViews intentionally return false in this tranche.
Existing Rs2Tile approach helpers mix top-level/cached/instance coordinate spaces;
publishing positive readiness there needs a dedicated coordinate-consistent adapter
and tests. Do not use this new helper to replace working PoH/instance handling.
Model-based overloads are also deferred; do not unwrap cached models by reflection.

## Validation

Implementation compiled successfully with JDK 17.0.19 and Gradle 8.8, offline,
no persistent daemon, one worker and a 768 MiB Gradle heap (16s).
Final focused run: 37 readiness tests plus ClientThreadGuardrailTest and
QueryableTerminalGuardrailTest all passed, with no skips (45s).

Full offline `:client:runUnitTests` passed in 2m 2s: 480 suites, 2,764 reported
tests, 6 skipped, zero failures and zero errors. All new readiness tests, both
guardrails, SceneClickSafetyTest, WalkerSceneClickSafetyTest and the existing
golden-route/corpus, personal/hosted PoH, banked-item planning, interaction,
handoff, partial-route and endpoint-arrival suites passed without skips.

Builds ran sequentially with the same bounded test settings as stage 1: one test
fork with a 2 GiB heap. Git whitespace checks passed and protected local branch
tips remain unchanged. Logs and XML summaries are under MBOT's local evidence
directory `operator-work/output/audits/walker-api-synthesis-20261031/`:
`stage2-compile.log`, `stage2-focused-tests.log` (initial guardrail failure),
`stage2-focused-r2.log`, `stage2-focused-r3.log`, `stage2-focused-summary.json`,
`stage2-unit-tests.log` and `stage2-test-summary.json`.

The new Rs2InteractionApproachTest uses guarded scene/object/player getters and a
nested-callback-aware client-thread fixture. Both positive readiness paths must
succeed; merely returning false for everything is not sufficient. It also checks
unavailable/stale/replaced scenes and objects, bounded approaches, null geometry,
unsupported coordinate spaces, exceptions and absence of walker/path API calls.
Existing SceneClickSafety tests retain responsibility for actual clipping geometry;
the new helper tests also exercise real SceneClickSafety delegation for a positive
uncovered case and covered/unknown/empty geometry failures.

Independent safety review identified the diagonal-only wall case; it was tightened
and regression-tested. The nearest-only game-object selection can return false
even if another approach exists. That conservative false negative is intentional
for now; this query neither searches for alternate routes nor retries interaction.

The first focused run passed all 34 initial readiness tests but failed the threading
guardrail: new base-TileObject getter bindings expanded its inferred API set to 14
pre-existing legacy sites. Getter calls were narrowed to the two supported typed
interfaces, still inside the same client-thread callbacks; both guardrails then
passed. No scanner rule or baseline was weakened or regenerated, and no unrelated
legacy execution code was changed. Those broader base-interface threading concerns
remain migration debt, not evidence that every legacy wrapper is safe.

## Remaining Gates

No Hub artifact compile/linkage, baseline/candidate performance comparison, release
JAR, installation, VM/client operation, gameplay smoke, push or protected-branch
promotion belongs to this tranche. PoH/instance readiness, interactions and typed
transport/planner integration remain explicit later work, not implied support.
