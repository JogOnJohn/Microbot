# Microbot Shortest-Path Playable Handoff

Last refreshed: 2026-08-24

## Checkout

- Repository: `https://github.com/JogOnJohn/Microbot`
- Worktree: `C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync`
- Playable branch: `playable/shortest-path`
- Pre-2.6.20 backup: `backup/playable-shortest-path-pre-2.6.20-20260824`
- Upstream Microbot: `https://github.com/chsami/Microbot`
- RuneLite remote is vendor-only; do not treat it as the Microbot upstream.

The playable branch is the source of truth for this work. `repair/shortest-path-post-1824`
is not the launch branch. `spike/shortest-path-data-sync` is separate and was not rebuilt or
promoted during the latest sync.

## Current Versions

- Microbot: `2.6.20`
- RuneLite: `1.12.36`
- Plugin Hub compatibility version: `1.12.36`
- Release jar: `runelite-client/build/libs/microbot-2.6.20.jar`

The desktop launcher reads `microbot.version` from `gradle.properties` and launches the jar from
this worktree:

`C:\Users\Billy\Desktop\Microbot Shortest Path Clients\Shortest Path Spike\Launch Microbot - Shortest Path Spike.bat`

Never overwrite the client jar while that jar is running. Stop the exact Microbot Java process,
build, and then relaunch through the desktop batch file.

## Branch Sync State

The 2026-08-15 upstream sync was promoted through:

1. `upstream/development` -> `development`
2. `development` -> `local/development`
3. `local/development` -> `playable/shortest-path`

Relevant heads after the sync and walker fix:

- `main`: `3157a6b430`
- `development`: `19915968c8`
- `local/development`: `7cfa078182`
- `playable/shortest-path`: use the current branch head

All four branches were pushed to `origin`. A fresh fetch reported `0 ahead / 0 behind` for each.
The upstream walker rewrite from PR 1832 was reverted by Microbot upstream, so its net walker code
was not introduced. The surviving upstream development change was the Death/grave recovery API.

## Walker Regression And Fix

After the sync, live Gem Crab walking was visibly slow. The log proved that route planning was not
the main delay:

- Pathfinder ready: `682ms`
- First minimap click: `9589ms`
- A raw scene pass: `9518ms`
- Raw scan attribution: `doorFind=1944ms`, `transports=7517ms`

Root cause: the new transport-owned door classification could perform client-thread object and
composition work before checking whether a scene object intersected the route segment. That cost
was multiplied across scene objects and candidate route edges.

Fix commit:

`7b9f1754ee fix(walker): avoid off-route door classification stalls`

The fix moves cheap route-geometry rejection ahead of transport ownership checks in:

- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/door/Rs2DoorProbe.java`

A headless regression test was added to:

- `runelite-client/src/test/java/net/runelite/client/plugins/microbot/util/walker/door/Rs2DoorProbeTest.java`

## Validation Completed

The patched source compiled and the focused suite passed:

- Door probe, geometry, classifier, ahead resolver, and await tests
- `Rs2WalkerUnitTest`
- W330 merge and hosted-house transport tests
- `ShortestPathGoldenRouteBaselineTest`
- All 20 named golden route cases passed

The release jar was rebuilt and relaunched successfully on the original machine. The user asked to
push immediately and continue on another machine, so a second live route was not awaited. The next
machine should capture a fresh `path_snapshot` to `first_minimap_click` interval and confirm that the
previous 8-10 second classification gap is gone.

Two one-off exceptions appeared during login before the welcome screen completed:

- RuneLite LootTracker read a null local player.
- GemCrabKiller read a null actor.

They were startup races and are separate from the walker timing regression. Do not attribute them
to the door-filter change unless they recur after the client is fully logged in.

## W330 And Shortest-Path Invariants

Preserve the playable branch's existing behavior:

- World 330 hosted-house entry and POH facility transports
- Spell access to W330 without requiring a house tablet
- Off-thread path planning and current-route handoff behavior
- Generated transport validation path
- Custom route-state diagnostics and recovery logic

Do not replace `Rs2Walker.java`, `PathfinderConfig.java`, or transport resources wholesale from
upstream. Merge selectively and rerun W330 plus golden-route coverage.

## Resume On Another Machine

```powershell
git fetch --all --prune
git switch playable/shortest-path
git pull --ff-only origin playable/shortest-path
git status --short --branch
```

Run the focused regression set:

```powershell
.\gradlew.bat :client:runUnitTests `
  --tests net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorProbeTest `
  --tests net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometryTest `
  --tests net.runelite.client.plugins.microbot.util.walker.Rs2WalkerUnitTest `
  --tests net.runelite.client.plugins.microbot.shortestpath.ShortestPathGoldenRouteBaselineTest `
  --tests net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfigWorld330MergeTest `
  --tests net.runelite.client.plugins.microbot.util.poh.World330HostedHouseTransportTest `
  --console=plain
```

With no Microbot jar process running:

```powershell
.\gradlew.bat :client:microbotReleaseJar --console=plain
```

Inspect `~/.runelite/logs/client.log` during the first real web walk. Compare these markers:

- `phase=path_snapshot`
- `phase=click_candidate_found`
- `phase=first_minimap_click`
- `[Walker] slow raw scene scan`

Before claiming completion, fetch `origin` again and verify:

```powershell
git rev-list --left-right --count `
  playable/shortest-path...origin/playable/shortest-path
```

Expected result: `0 0`.
