# Shortest-Path Data Sync — Handoff

Written 2026-07-12 by Claude after reviewing, amending, and extending Codex's foundation work.
Read alongside [SHORTEST_PATH_DATA_SYNC_PLAN.md](SHORTEST_PATH_DATA_SYNC_PLAN.md) (the
authoritative plan) and [TRANSPORT_SCHEMA.md](TRANSPORT_SCHEMA.md) (the contract inventory).

The reusable converter and one-command upstream checkout workflow now live in the public
[`JogOnJohn/microbot-shortest-path-sync`](https://github.com/JogOnJohn/microbot-shortest-path-sync)
repository. `scripts/transport_sync/` remains a vendored integration snapshot so Microbot can pin
the exact converter, overrides, JVM validator inputs, and resource adoption in the same commit.

## Review verdict (of Codex's foundation, commits `ccda6..a499a`)

The foundation matches the plan. Verified directly against `Transport.java`'s parse code:

- Target contract is correct: header-keyed columns, combined `menuOption menuTarget objectID`
  field (both `Action Target 123` and legacy `Action;Target;123` forms), semicolon within-field
  separators, `Item IDs`/`Items`, `Display info`/`Display Info`, `Varplayers`/`VarPlayers` aliases.
- `local_overrides.tsv` is applied last, keyed by the stable identity
  `(category, origin, destination, action, target, objectID)`; the six gate-190 `Duration=9` rows
  survive a sync (litmus test passes: 0 added/removed/changed after overrides).
- The `||` item-alternation fix (Elemental Workshop wall) is correct: the parser splits
  alternatives on `\|\||\s+`, so the old single-`|` token was silently dropped.
- Converter is deterministic (byte-identical across runs), never writes to the upstream checkout,
  pins tooling + data commits and the paired collision-map hash, and fails loudly on unknown
  files/columns, ambiguous overrides, and checkout/pin mismatches.
- No big-bang adoption; every live-resource edit is mirrored in the override layer.

## Work completed after the review (this session's commits)

1. `52a0665ea6` — **Converter hardening**: raw tab split/join instead of the Python `csv` module
   (the Java parser never interprets CSV quoting; unrepresentable fields now fail loudly), and a
   semantic-diff fix — requirement changes previously reported as unrelated removal+addition
   pairs (the `requirement_deltas`/`adjacency_deltas` counters were structurally always zero).
   Added/removed rows sharing a stable identity now pair into **changed** entries; uniquely
   relocated interactions pair as `endpoint_moved` changes so adjacency flips are detectable.
2. `cc7bc1a7d7` — **Validator test isolation**: `TransportSyncGeneratedResourcesTest` Assume-skips
   outside `:client:validateTransportSync` instead of failing generic test sweeps.
3. `f3963770d7` — **Golden routes 7 → 19**: agility shortcut, ship (short + multi-leg), canoe,
   minecart, ecto toll barrier, spirit tree, fairy ring, gnome glider, teleportation lever,
   quest boat, Al Kharid gate long walk.
4. `684461f1e2` — **Collision endpoint cross-check** (plan step 8): every generated transport
   endpoint is checked against the shipped collision map, ratcheted against baseline so a sync
   can never increase the number of collision-blocked endpoints (door-into-wall detection).
5. `fbd3d25b27` — **Marim staircases restored** (last 8 parser-inert rows): the cache object is
   `Stairs` — id 4756 Climb-up (2x3 footprint at 2795/2799 2794 plane 0) and id 4755 Climb-down
   (2x2 at 2795/2799 2795 plane 1), verified via the mejrs cache location dump (name, action,
   and footprint all match the transport endpoints; upstream's rows said "Staircase" with no ID).
   Live rows repaired + 8 Match-interaction overrides + a marim-stairs golden route.
   **Parser-inert transports are now zero (was 14 at the start of the spike).**

## Validation status (2026-07-12, end of session)

| Check | Result |
|---|---|
| Python converter tests (`python -m unittest scripts.transport_sync.test_sync`) | 9/9 pass |
| Converter run vs pin | 0 added / 0 removed / 0 changed after 20 overrides |
| Determinism (two runs diffed) | byte-identical |
| `:client:validateTransportSync` (real parser + collision ratchet) | pass |
| Golden routes (`ShortestPathGoldenRouteBaselineTest`, **20 routes**) | pass |
| `TransportResourceLoadTest` | pass |
| `:client:compileJava` | pass |
| Jar build | pass — built from `da72016108` after stopping the old spike client |
| Live walker test | **not done** — Desktop package is ready for the next session |

### Test artifact built by Codex

- Source artifact: `runelite-client/build/libs/microbot-2.6.12.jar`
- Desktop package: `C:\Users\Billy\Desktop\Microbot Shortest Path Data Sync`
- Desktop jar: `microbot-shortest-path-data-sync-2.6.12-da72016108.jar`
- Desktop launcher: `Launch Microbot - Shortest Path Data Sync.bat`
- SHA-256: `2264E945A508FCD99BD5B3EB00D6A53331F1A034EBE20646ABBB00EF8D0BAA49`
- The jar was inspected after build and contains the repaired Marim, Elemental Workshop,
  Piscatoris, and gate-190 resource rows.
- The prior `spike/shortest-path-upstream` client (PID 9116) was stopped before the build. The
  new jar was deliberately not launched automatically.

**Pre-existing failures, NOT caused by this branch** (reproduced identically with the base
`spike/shortest-path-upstream` copy of `transports.tsv`):
`ShortestPathCoreTest.testKaramjaToIsleOfSoulsDungeonEntrance` / `testKaramjaToIronDragons` /
`testKaramjaToBlueDragons` (all end at `2305 9351 0`, 138–179 tiles short),
`testVarrockSewerPathAvoidsDisabledPalaceTrellisShortcut`, and
`PathfinderBenchmarkTest.benchmarkCorpus` (same Karamja route). Tracked as a separate
task/session against the base branch; do not chase them here.

## Upstream pin status

Refreshed 2026-07-16. Upstream advanced to tooling `86fbe219` and data `7e7e5bf9`.
The data revision changes only `collision-map.zip`; all 25 transport TSVs are unchanged from the
previous pin. Microbot adopted the new collision archive
(`3e1658cf4bd1ad34832b42c3896df61a7fe6421f96758d046e4a8aa39cbbc40a`).

Archive comparison found the same 2,724 mapsquares with one changed entry (`46_123`, 67
directional collision flags). No transport endpoint lies in that mapsquare. The converter reports
0 added / 0 removed / 0 changed after 20 overrides; the JVM collision endpoint ratchet, all 20
golden routes, and resource loading pass.

For the next refresh: bump `data_commit` (and `tooling_commit` if needed) in
`sync_manifest.json`, checkout those commits in the tooling clone, run the converter, review
`build/transport-sync/report/summary.md`, and adopt one changed category per reviewed commit.
Duration deltas remain protected: local wins unless deliberately accepted.

## Environment notes

- The pinned upstream checkout currently lives at
  `C:\Users\Billy\AppData\Local\Temp\shortest-path-tooling-5956e5b` — the directory retains its
  original name but is checked out at tooling `86fbe219` / data `7e7e5bf9`. It is **fragile**
  because Temp cleanup can delete it. To recreate: clone
  `https://github.com/osrs-pathfinding/shortest-path-tooling`, checkout `86fbe219`, then run
  `git submodule update --init --recursive`.
- Converter invocation:
  `python scripts/transport_sync/sync.py --upstream-root <tooling checkout>`
- Do NOT combine `:client:validateTransportSync` with `--tests`-filtered tasks in one Gradle
  invocation — Gradle applies `--tests` filters to every test task and fails spuriously. Run them
  separately.
- Object-ID research pattern that worked for Marim: mejrs cache dumps at
  `https://raw.githubusercontent.com/mejrs/data_osrs/refs/heads/master/` —
  `object_name_collection.json` (name → ids), `locations/{id}.json` (id → world placements,
  `world = 64*i + x`), `location_configs/{id}.json` (name/actions/footprint). Confirm name,
  action, AND footprint-adjacent-to-endpoints before trusting an ID.

## Remaining work

1. **Live walker testing** (the only unfinished plan item that is currently actionable): launch
   the Desktop package via `Launch Microbot - Shortest Path Data Sync.bat`, walk the golden routes — prioritize the newly
   repaired transports (Marim stairs, Piscatoris gates, Elemental Workshop wall, gate 190) —
   capturing `[WebWalk]`, `tp_audit`, `path_teleports`.
2. **Periodic pin re-check**: when upstream moves, run the refresh procedure above.
3. `Region override` (League) rows remain retained-but-unsupported; revisit only if Microbot
   models league regions.

## 2026-08-15 refresh and catalog repair

Official upstream now pins tooling `c68fba975a7e6e45bc69e55f2dd5a109b8f631b7` and data
`3208646f33c8f155d0262c5fc84f8e29f7599838`. Relative to the previous public-tool pin, all 25
transport TSVs are unchanged; the only official payload change is `collision-map.zip`, SHA-256
`21180462094d7e011bf70623ce4bd2ba58e1d13fa895b1f601a6fe4c29155e6c`.

The refresh exposed older branch drift: the spikes retained the managed resource catalog from
Microbot PR #1832 after upstream reverted that PR in #1835. The repair restores all managed TSVs
from the pinned official payload, then reapplies 34 explicit local overrides. Fourteen intentional
post-foundation Microbot transports are now durable UPSERT overrides: Port Sarim back-room doors,
coin-gated Shantay passes, Corsair Cove stairs, and the Taverley Sturdy door. The existing Gnome
gate, Elemental Workshop, Piscatoris, and Marim fixes remain overrides as before. Microbot-owned
`blocked_edges.tsv`, `dangerous_tiles.tsv`, `npcs.tsv`, and `restrictions.tsv` were not replaced.

The semantic comparator now treats parser-equivalent `Action;Target;123` and
`Action Target 123` interactions identically and ignores blank non-identity columns. This removes
false drift without hiding requirements, duration, endpoint, adjacency, or handler changes. The
Gradle validator also declares the staged generated directory as an input, preventing an unsafe
`UP-TO-DATE` result after the candidate payload changes.

Final playable-spike validation: converter 0 added / 0 removed / 0 changed after 34 overrides;
Python converter tests pass; the real-parser/collision validator passes; all 20 golden routes and
`TransportResourceLoadTest` pass; `:client:compileJava` is clean. Two golden signatures were
updated to the restored executable official interactions: Lumbridge bank uses staircase IDs 16671
and 16672, and the modern Al Kharid route uses gate 44050 instead of obsolete toll gate 2786.
No jar was rebuilt because a Microbot client from this worktree was running during the refresh.

The canonical repeatable maintainer guide lives in the public converter repository:
<https://github.com/JogOnJohn/microbot-shortest-path-sync/blob/main/docs/UPDATE_WORKFLOW.md>.
It covers pin discovery, provenance, review, deliberate adoption, validation, promotion to both
spikes, author identity, push, and handoff requirements.

## 2026-09-03 collision refresh

The standalone converter pins tooling `f5bdbc83c0ffdc3da4e6b985d2f41e28eb3f4705` and data
`6ca996a41a6a4b85d0fdb38dc6d56c66b747e29a`. Upstream changed only
`collision-map.zip`; all transport resources remained semantically identical after 37 local
overrides. The adopted collision archive has SHA-256
`74c6c064d78c8d73677343e2ed3bea39c30da2e728c27bb23c5e3ef581c49c8e` and changes three
mapsquare entries: `21_56`, `21_57`, and `53_48`.

The refresh passed the standalone Python suite, staged payload validation, golden-route corpus,
and transport resource loading before promotion from `spike/shortest-path-data-sync` to
`playable/shortest-path`.
