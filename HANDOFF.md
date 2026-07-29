# Handoff — Microbot spike: walker/pathfinder + agility + QoL

_Last refreshed 2026-07-07. Untracked scratch doc; git is the source of truth, this is just orientation._

## Two repos in play
1. **Engine** — `C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync` (worktree of `C:\Users\Billy\IdeaProjects\Microbot`), branch **`spike/shortest-path-upstream`**. The RuneLite/Microbot client.
2. **Plugin hub** — `C:\Users\Billy\IdeaProjects\Microbot-Hub`. Sideloaded plugins (agility, QoL, etc.), compiled against the client jar.

## Build & deploy cheatsheet
- Engine module is **`:client`** (not `:runelite-client`).
  - Compile only: `./gradlew :client:compileJava --console=plain`
  - Full launcher jar: `./gradlew :client:microbotReleaseJar` → `runelite-client/build/libs/microbot-<version>.jar`.
  - **Version is now 2.6.12** (bumped by a merged `local/development`). The desktop bat `Launch Microbot - Shortest Path Spike.bat` auto-reads `microbot.version` from `gradle.properties`, so it always launches the right jar — no edit needed.
- Hub plugin jars: `./gradlew <PluginName>Jar` → `build/libs/<PluginName>-<ver>.jar`, then copy over the sideload target `~/.runelite/microbot-plugins/<PluginName>.jar` (no version in the deployed name).
  - Agility: `./gradlew MicroAgilityPluginJar` → deploy to `MicroAgilityPlugin.jar`
  - QoL: `./gradlew QoLPluginJar` → deploy to `QoLPlugin.jar`
  - Compile-only for a source set: `./gradlew compileAgilityJava` (or `compileQualityoflifeJava`).

## CRITICAL deploy rule (learned the hard way)
- **Never rebuild the CLIENT jar while the client is running** — overwriting the live `microbot-*.jar` corrupts the running JVM's lazy class/resource loading (`ZipException: invalid LOC header`, `NoClassDefFoundError`, questhelper `iconBackground` NPE). Close the client first, then build.
- Sideloaded **plugin** jars can be copied while running, but the running client keeps the OLD plugin classes until a **full client restart** (toggling the plugin off/on does NOT reload the jar).

## Agent server (live game-state introspection) — USE THIS
- **Preferred: the `/agent-server-gamestate` skill** (user set this up) — invoke it to query live player position, inventory, NPCs, objects, ground items, widgets, dialogue, banking, etc. Use it whenever you need ground truth about the running client instead of guessing from logs.
- Under the hood it's localhost HTTP on `127.0.0.1:8081`, token auto-read from `~/.runelite/.agent-token` (Agent Server plugin, currently on). Raw fallback if needed:
  - `curl -s -H "X-Agent-Token: $(cat ~/.runelite/.agent-token)" http://127.0.0.1:8081/state`
  - Endpoints: `/state /objects?name=&maxDistance= /ground-items?name= /dialogue /widgets/search?q= /inventory /skills` … (`/objects` returns per-object `reachable`). Full docs: `docs/AGENT_SERVER.md`.

## Current branch state
### Engine `spike/shortest-path-upstream` (HEAD a8b81930dc — a merge of local/development)
Key fixes landed this session (all ancestors of HEAD):
- W330 hosted-house POH routing spike (enter advertised max house, drink ornate pool, use jewellery box/nexus/portals as transports).
- `PathfinderConfig.refresh` **coalescing + bounded re-runs** (`MAX_COALESCED_REFRESH_RUNS=3`) — fixes concurrent transport-map corruption AND the client-thread stall when refresh() is hammered during heavy questing.
- `tryDirectLocalWalkBeforePathfinder` no longer reports ARRIVED for collision-blind-near-but-unreachable targets.
- `processWalk` guards null player location (loading screens) instead of NPE-ing the walk task.
- Teleport-label overlay ("Show teleport labels", on by default) — shows the path's spell/item/PoH teleport on the player; fixed a `transports.get(null)` ConcurrentHashMap NPE that spammed the overlay renderer.
- Quest order: The Red Reef placed after Troubled Tortugans.

### Hub `fix/agility-mark-reachability` (HEAD 9e5964e1)
Agility fixes (Seers-driven but mostly general):
- `9df8b5e1` mark reachability via `Rs2Tile.isTileReachable` (not the collision-blind `Rs2Walker.canReach`).
- `48df0fcf` Seers: don't webwalk back to start when an obstacle is reachable (breaks stall loop + dodges cold-pathfinder delay).
- `c299f592` re-scan for the next obstacle before "No obstacle found" (rides plane-transition/flicker window).
- `bb62bbbd` blocklist marks that fail to pick up (20s cooldown) — stops loop thrash.
- `eea8a5b5` lifted the walk-back guard into the BASE `AgilityCourseHandler.handleWalkToStart` → all rooftop courses.
- `9e5964e1` **sticky mark pickup** — keep re-issuing Take + blocking the course until the mark is grabbed or 5s timeout; fixes the first bank-roof mark being skipped.
- (`829cfde1` is a stray GildedAltar whitespace commit that rode along — drop/relocate before any PR.)
- Deployed: `MicroAgilityPlugin.jar` (2026-07-07 17:04).

### Hub `feat/qol-auto-pay-tree-removal` (off `main`, HEAD 488a7e78) — PR-ready
- Single commit: "Auto Pay Tree Removal" toggle in QoL → Dialogue (default on). Clicks "Yes." on the farming "Pay X Coins to have your tree chopped down?" prompt. Matched on the question title (widget 219,1), amount-agnostic. Deployed: `QoLPlugin.jar` (2026-07-07 12:28). User will PR once tested on a real farm run.

## Seers course reference (for future agility work)
Obstacles in order (x,y,plane): wallclimb 2729,3489,0 · gap(bank) 2720,3492,3 · tightrope 2710,3489,2 · gap(mid house) 2710,3476,2 · gap(upper house) 2700,3469,3 · edge(church leapdown) 2703,3461,3. Key insight: obstacles/marks on a different plane than the player are visible but NOT interactable until you complete the prior obstacle and enter that plane.
Mark spawns: bank roof 2727,3498,z3 · Elem WS 2707,3493,z2 & 2710,3493,z2 · mid house 2712,3481,z2 · church 2698,3465.

## Parked / next
1. **Core-walker cold-start delay** (engine): every `walkTo` waits ~2s on a null pathfinder + multi-second to first click. The Seers guard routes around it for lap resets, but it's still there for long/off-course walks. Needs a careful, live-tested pass — do NOT patch the core walker blind (this session had multiple walker regressions). Client-jar change → build only with client closed.
2. **Prifddinas** course overrides `handleWalkToStart` itself, so it didn't get the base walk-back guard. Add when the account reaches lvl 75.
3. **QoL PR** after farm-run test; drop the stray GildedAltar commit if PRing agility work.
