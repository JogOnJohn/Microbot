---
name: walker-transports-playbook
description: Work on the Microbot engine's webwalker, pathfinder, or transport data (Rs2Walker, PathfinderConfig, ShortestPathPlugin, transports.tsv) in C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync. Use when diagnosing walking/stall/re-click/teleport bugs, reading WebWalk logs, adding or tuning transports (gates, doors, boats, teleports), or changing walker wait/retry behavior.
---

# Walker / pathfinder / transports playbook

Engine repo: `C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync` (worktree of Microbot),
branch `playable/shortest-path`. Gradle module is **`:client`** (NOT `:runelite-client`).

## HARD BUILD RULES
- **NEVER run `:client:microbotReleaseJar` while the client is running.** It overwrites the live
  launcher jar and corrupts the running JVM (`ZipException: invalid LOC header`,
  `NoClassDefFoundError`, questhelper iconBackground NPE). Close the client first.
- `./gradlew :client:compileJava --console=plain` is SAFE while the client runs (writes classes,
  not the jar). Use it to validate changes early; build the jar when the user closes the client.
- Launcher bat reads `microbot.version` from `gradle.properties` → launches
  `runelite-client/build/libs/microbot-<version>.jar`.

## Regression history — walk carefully
The core walker has repeatedly regressed from well-intentioned patches. Do NOT patch
`Rs2Walker.processWalk`/refresh logic blind; every change needs a live-tested pass.
Known past traps:
- `PathfinderConfig.refresh()` races (transport-map corruption) → fixed with coalescing +
  bounded re-runs (`MAX_COALESCED_REFRESH_RUNS=3`). Serializing it naively DROPPED refreshes
  (POH teleport regression); unbounded coalescing pinned the client thread.
- `tryDirectLocalWalkBeforePathfinder` false-ARRIVED on collision-blind-near targets → gated on
  `Rs2Tile.isTileReachable`.
- `ConcurrentHashMap.get(null)` throws NPE (transports map). No null keys, ever.

## Reachability semantics
- `Rs2Walker.canReach(target)`: pathfinder endpoint 2x2 area ∩ target 4x4 area — collision-blind,
  false-positives across rooftop sections/fences. Path-adjacent legacy checks only.
- `Rs2Tile.isTileReachable(target)`: BFS from player over live collision — the truthful primitive.
- `Rs2Reachable.isReachable(target)` (behind `IEntity.isReachable()`): BROKEN — BFSes from the
  target and checks the target against itself (always true in-scene); per-tick cache keyed only by
  tick number. Don't trust it; don't add callers.

## transports.tsv (shortestpath resources)
Tab-separated, 10 columns:
`Origin | Destination | menuOption menuTarget objectID | Skills | Items | Quests | Varbits | VarPlayers | Duration | Display info`
- Coordinates are `x y plane` space-separated inside the column.
- **Duration** = game ticks (600ms each). Drives BOTH pathfinder cost AND the post-interaction
  landing wait in `Rs2Walker`: `max(POST_HANDLE_OBJECT_LANDING_WAIT_MS=5000, duration*600 + 2000)`.
  A transport with a long animation/auto-walk (e.g. Tree Gnome Stronghold gate 190, Duration=9)
  can be fixed IN DATA ALONE by setting Duration — no code change.
- One row per origin→destination direction; multi-tile gates need a row per approach tile.
- Symptom of a too-short wait: walker re-clicks the object mid-animation, then
  `exit | r=cancel:processWalk:path-loop`.

## Reading WebWalk logs
Lines are tagged `[WebWalk]` in `~/.runelite/logs/client.log` (INFO) and the live console.
Vocabulary:
- `tmark | phase=<name> elapsed=..ms goal=.. at=.. detail=..` — per-phase trace inside a walk
  segment. Key phases: `transport_handoff_enter/expected_hit`, `post_transport_segment_handler`
  (`handled=true` = transport done), `post_transport_raw_scene_scan[_skip]`,
  `post_transport_current_tile_transport`, `post_transport_settling_yield`.
- `early_exit | r=<reason>` — segment ended intentionally (`transport-handled`,
  `transport-settling-yield`).
- `exit | r=cancel:processWalk:path-loop nullCur=true` — walk cancelled: recalculated path came
  back null/looping. Usually means the walker is somewhere the plan didn't expect (mid-gate,
  mid-animation) or the pathfinder is mid-refresh.
- `clear | <source>` — walk state cleared externally (e.g. `client-ui:quest-helper-toggle-disable`,
  `agility:shutdown`).

## Key code landmarks (Rs2Walker.java)
- `processWalk` — main loop; stall/path-loop cancellation lives here.
- `handleTransports` / `handleObject` — scene-object transports; landing wait honors Duration.
- `isAdjacentSamePlaneTransport` — origin→dest Chebyshev ≤ 1 only (a 3-tile gate is NOT adjacent).
- `tryDirectLocalWalkBeforePathfinder` — early-ARRIVED short-circuit, gated on isTileReachable.
- Constants ~line 200: `POST_HANDLE_OBJECT_LANDING_WAIT_MS`, `SHIP_NPC_BOAT_LANDING_WAIT_MS`.

## Parked / known issues
- Core-walker cold-start delay: ~2s null pathfinder + slow first click on every `walkTo`. Needs a
  careful live-tested pass with the client closed — do not patch blind.
