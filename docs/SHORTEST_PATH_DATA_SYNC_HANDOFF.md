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

Refreshed 2026-07-29. Upstream advanced to tooling `07c9463a` and data `e3dc7c5a`.
The data revision changes only `collision-map.zip`; all 25 transport TSVs are unchanged from the
previous pin. Microbot adopted the new collision archive
(`4cb2d04f84898fc2f90c055ab45a98d3960ce67f57c5ff8786ec3e4e450b3bdd`).

Archive comparison found the same 2,724 mapsquares with one changed entry (`35_88`) relative to
the previous pin. The converter and JVM validation commands below remain the acceptance gates.

For the next refresh: bump `data_commit` (and `tooling_commit` if needed) in
`sync_manifest.json`, checkout those commits in the tooling clone, run the converter, review
`build/transport-sync/report/summary.md`, and adopt one changed category per reviewed commit.
Duration deltas remain protected: local wins unless deliberately accepted.

## Environment notes

- The pinned upstream checkout currently lives at
  `C:\Users\Billy\AppData\Local\Temp\shortest-path-tooling-5956e5b` — the directory retains its
  original name but is checked out at an older pin. It is **fragile**
  because Temp cleanup can delete it. To recreate: clone
  `https://github.com/osrs-pathfinding/shortest-path-tooling`, checkout `07c9463a`, then run
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

## Post-PR #1824 repair (2026-07-29)

The client merge exposed assumptions that the original pipeline did not make atomic. The repair
branch `repair/shortest-path-post-1824` now addresses them:

- pins tooling `07c9463ab6bd55756d8f8630586ed4f82ee4256f` and data
  `e3dc7c5a621ca9cdd4c404ca4da5654b603286e7`, paired with collision SHA-256
  `4cb2d04f84898fc2f90c055ab45a98d3960ce67f57c5ff8786ec3e4e450b3bdd`;
- stages `collision-map.zip` with every catalog and writes payload/pin provenance hashes;
- requires an explicit fresh `-PtransportSyncGeneratedDir=...`; the validator loads that staged
  collision map and parses the four Microbot-only resources as well as managed transports;
- restores object-transport landing waits to `max(5000ms, Duration * 600ms + 2000ms)`, so the
  gate-190 duration overrides affect runtime behavior again;
- makes offline route tests opt out of the user's persisted learned-blocked-edge file;
- includes the static collision-map hash in live-collision persistence identity;
- removes the obsolete Al Kharid toll-dialogue special case and updates its baseline test to the
  current free `Open Gate` objects 44050/44051.

PR #1824's transport data was audited semantically against both its pre-merge parent and the new
official pin. Official upstream now contains all useful unique additions. The only eight PR-only
identities left are the superseded Al Kharid `Pay-toll(10gp)` / quest-gated `Open` rows using object
IDs 2786-2789; they must not be restored over the current 44050/44051 rows.

The public wrapper now generates first, passes that exact output directory into the JVM validator,
and runs the golden/resource tests. Use `-SkipMicrobotValidation` only for converter development;
its warning means the output is not release-ready.
