# Shortest-Path Data Sync — Handoff

Written 2026-07-12 by Claude after reviewing and amending Codex's foundation work
(commits `ccda64bbf5`, `5e30b3825b`, `a499a5436c`). Read alongside
[SHORTEST_PATH_DATA_SYNC_PLAN.md](SHORTEST_PATH_DATA_SYNC_PLAN.md) (the authoritative plan)
and [TRANSPORT_SCHEMA.md](TRANSPORT_SCHEMA.md) (the contract inventory).

## Review verdict

The foundation matches the plan. Verified directly against `Transport.java`'s parse code:

- Target contract is correct: header-keyed columns, combined `menuOption menuTarget objectID`
  field (both `Action Target 123` and legacy `Action;Target;123` forms), semicolon within-field
  separators, `Item IDs`/`Items`, `Display info`/`Display Info`, `Varplayers`/`VarPlayers` aliases.
- `local_overrides.tsv` is applied last, keyed by the stable identity
  `(category, origin, destination, action, target, objectID)`; the six gate-190 `Duration=9` rows
  are present and survive a sync (litmus test passes: converter reports 0 added/removed/changed
  after overrides).
- The `||` item-alternation fix for the Elemental Workshop wall is correct: the parser splits
  alternatives on `\|\||\s+`, so the old single-`|` token failed the item regex and was silently
  dropped (i.e. the requirement was never enforced).
- Converter is deterministic (verified byte-identical across runs), never writes to the upstream
  checkout, pins tooling + data commits and the paired collision-map hash, and fails loudly on
  unknown files/columns, ambiguous overrides, and checkout/pin mismatches.
- No big-bang adoption occurred. The only live-resource edits are the six executable-gate rows in
  `transports.tsv`, each mirrored in the override layer.

## Amendments made in this session

1. `52a0665ea6` — **Converter hardening** (`scripts/transport_sync/sync.py`):
   - Replaced the Python `csv` module with raw tab split/join. The Java parser never interprets
     CSV quoting, so a field containing `"` must round-trip verbatim; unrepresentable fields
     (embedded tab/newline) now fail loudly.
   - Fixed a structural defect in the semantic diff: `comparison_key` includes all requirement
     fields, so any requirement change moved the row between keys and was reported as an
     unrelated removal+addition — the `requirement_deltas` and `adjacency_deltas` counters were
     structurally always zero. Added/removed rows sharing a stable identity are now paired and
     reclassified as **changed**; uniquely-relocated interactions (same action/target/objectID,
     one endpoint moved) are paired as `endpoint_moved` changes so adjacency (handler dispatch)
     flips are detectable. New summary counter: `endpoint_moves`.
   - Generated output verified byte-identical before/after; 4 new Python tests.
2. `cc7bc1a7d7` — **Validator test isolation**: `TransportSyncGeneratedResourcesTest` now
   Assume-skips when `microbot.transport.generated.dir` is absent, so generic test sweeps don't
   fail on it; it still runs strict under `:client:validateTransportSync`.

## Validation status (2026-07-12)

| Check | Result |
|---|---|
| Python converter tests (`python -m unittest scripts.transport_sync.test_sync`) | 9/9 pass |
| Converter run vs pin | 0 added / 0 removed / 0 changed after 12 overrides |
| Determinism (two runs diffed) | byte-identical |
| `:client:validateTransportSync` (real `Transport` parser over staged output) | pass |
| Golden routes (`ShortestPathGoldenRouteBaselineTest`, 7 routes) | pass |
| `TransportResourceLoadTest` | pass |
| `:client:compileJava` | pass |
| Jar build / live test | **not done** — a Microbot client was running (hard rule) |

**Pre-existing failures, NOT caused by this branch** (reproduced identically with the base
`spike/shortest-path-upstream` copy of `transports.tsv`):
`ShortestPathCoreTest.testKaramjaToIsleOfSoulsDungeonEntrance` / `testKaramjaToIronDragons` /
`testKaramjaToBlueDragons` (all end at `2305 9351 0`, 138–179 tiles short),
`testVarrockSewerPathAvoidsDisabledPalaceTrellisShortcut`, and
`PathfinderBenchmarkTest.benchmarkCorpus` (same Karamja route). These need their own
investigation on the base branch; do not chase them here.

## Environment notes

- The pinned upstream checkout lives at
  `C:\Users\Billy\AppData\Local\Temp\shortest-path-tooling-5956e5b` — **fragile** (Temp cleanup
  will delete it). To recreate: clone `https://github.com/osrs-pathfinding/shortest-path-tooling`,
  `git checkout 5956e5b2`, init the `shortest-path` submodule at `d12cd0f6`. Consider moving it
  somewhere durable and/or documenting in `scripts/transport_sync/README.md`.
- Converter invocation:
  `python scripts/transport_sync/sync.py --upstream-root <tooling checkout>`
- The combined invocation
  `gradlew :client:validateTransportSync :client:runUnitTests --tests "...shortestpath.*"` fails
  spuriously: Gradle applies `--tests` filters to every test task in the invocation. Run the two
  tasks separately.

## Remaining work, in order

1. **Golden routes to ~20** (plan step 9). Have 7; missing: agility shortcuts, quest-gated
   transports, fairy ring / spirit tree network legs, conventional teleports, a long multi-leg
   route, historical Priest in Peril failure cases beyond the two Paterdomus checks.
2. **Jar + live walker testing** (needs the running client closed first):
   `gradlew :client:microbotReleaseJar`, launch via `launch-microbot-shortestpath-data-sync.bat`,
   walk the golden routes capturing `[WebWalk]`, `tp_audit`, `path_teleports`.
3. **Collision endpoint cross-check** (plan step 8, unimplemented): verify transport
   origins/destinations against the shipped `collision-map.zip` (door-into-wall detection).
4. **Marim staircases**: 8 remaining parser-inert rows (`2795–2800 2793/2797`) need verified
   object IDs (Ape Atoll, members). Same repair pattern as the Piscatoris gates
   (`Match interaction` PATCH in `local_overrides.tsv`).
5. **Advance the pin** — the actual payoff. Current pin is semantically identical to live data,
   so nothing new has been adopted yet. Bump `data_commit` (and `tooling_commit` if needed) in
   `sync_manifest.json`, re-run the converter, review `build/transport-sync/report/summary.md`,
   then adopt **category-by-category** (doors/`transports.tsv` first), one reviewed commit each,
   golden routes after each. Duration deltas: local wins unless deliberately accepted.
6. `Region override` (League) rows remain retained-but-unsupported; revisit only if Microbot
   models league regions.
