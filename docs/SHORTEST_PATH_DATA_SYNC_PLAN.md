# Shortest-Path Upstream Data Sync — Plan

Status: **in progress on `spike/shortest-path-data-sync`**. Foundation started 2026-07-12:
schema inventory, pinned converter, local override layer, semantic report, and real-parser validator
are implemented. Category adoption and golden-route testing remain. Authored 2026-07-11 by Billy + Claude after a week of live
walker debugging on `spike/shortest-path-upstream`. Intended to be executed in its own worktree
session with this file as the primary context.

## Problem

Microbot's transport data (29 TSVs under
`runelite-client/src/main/resources/net/runelite/client/plugins/microbot/shortestpath/`) is a
hand-maintained fork of a living upstream:

- Upstream tooling/data: https://github.com/osrs-pathfinding/shortest-path-tooling
- Upstream data is actively maintained (new content lands fast — Varlamore, Aldarin charters);
  ours drifts and gets patched ad-hoc when live failures surface (e.g. Tree Gnome Stronghold
  gate 190 got `Duration=9` only after a quest walk looped on it).
- Upstream's TSV schema has drifted from ours (e.g. upstream split the combined
  `menuOption menuTarget objectID` column into separate columns). Copying upstream files in
  directly would compile but break runtime transport loading.

Half of the recent walker bug class is stale/missing/wrong transport rows. A repeatable,
validated sync pipeline attacks the root cause.

## Ground truth about OUR format (verified in code, don't trust memory)

The target contract is **`Transport.java`'s parse code**
(`runelite-client/.../microbot/shortestpath/Transport.java`), NOT what the files look like:

- TSVs are **header-keyed** (`fieldMap.get("<header>")`); first line starts with `# `.
- Object interaction is ONE combined column: `menuOption menuTarget objectID`
  (space-separated inside the field, e.g. `Open Gate 190`). NOT semicolon-packed.
- Semicolons are **within-field list separators** (e.g. varbits `4469=0;4468=0`), not the
  column format.
- `||` is OR-alternation in item requirements (e.g. `21146=1||21149=1` = any necklace charge).
- The parser already supports **aliases** for limited schema drift:
  `Item IDs`|`Items`, `Display info`|`Display Info`, `Varplayers`|`VarPlayers`
  (see `firstNonBlank`/`firstPresent`). Extending aliases is often cheaper and less lossy
  than converting data.
- Known columns: Origin, Destination, `menuOption menuTarget objectID`, Currency, Skills,
  Items/Item IDs, Quests, Duration, Display info, Consumable, Wilderness level, isMembers,
  Varbits, Varplayers.
- Teleports get `duration = max(duration, 1)` forced (`TransportType.isTeleport`).

### Execution-sensitive semantics (why data changes are behavior changes)

- **Duration is not just path cost anymore.** `Rs2Walker` uses it for the post-interaction
  landing wait: `max(POST_HANDLE_OBJECT_LANDING_WAIT_MS=5000, duration*600 + 2000)`.
  Gate 190's `Duration=9` encodes its long animation; blindly adopting upstream's cost-tuned
  durations would regress this.
- **Adjacency changes handler dispatch.** `isAdjacentSamePlaneTransport` = origin→dest
  Chebyshev ≤ 1. A row whose origin/dest span changes can flip which execution path fires.
- **TransportType drives per-type handlers** in `Rs2Walker.handleTransports` (POH, canoe,
  spirit tree, minecart, agility shortcut, teleportation item/spell, ...). Category mapping
  mistakes change execution, not just cost.
- `PathfinderConfig.STATIC_BLOCKED_EDGES_PACKED` hardcodes specific coordinates; if upstream
  rows move, these can silently stop matching.
- `useTransport` gate order (feature toggle → members → skills → quests → varbits →
  varplayers → currency → global-teleport-disable → per-type settings) is mirrored by the
  `tp_audit` rejection describer — keep in sync if fields are added.

## Architecture (revised, agreed)

1. **Contract inventory** — document every field `Transport.java` accepts: aliases,
   separators, defaults, runtime meaning. Explicitly include the execution-sensitive
   distinctions above. Deliverable: `docs/TRANSPORT_SCHEMA.md`.

2. **Upstream schema inventory** — classify every upstream column/category as:
   directly compatible / compatible via a (new) parser alias / deterministically
   transformable / unsupported-or-lossy. Deliverable: mapping table in the same doc,
   pinned to a specific upstream commit hash.

3. **Hybrid adapter** — prefer small lossless **parser alias extensions** for naming drift;
   use **Python** (stdlib `csv`) only for structural transformations, category flattening,
   normalization, and validation. Never modify upstream input files. Deterministic output
   (stable sort) so generated-file diffs are reviewable. Deliverable:
   `scripts/transport_sync/` (converter + fixtures + report).

4. **Generated base + local overrides** — generated upstream-derived resources stay separate
   from a versioned `local_overrides.tsv` (or per-category override files), applied LAST,
   keyed by stable transport identity: (type, origin, destination, action, target, objectID).
   Every existing local divergence (gate 190 Duration=9, etc.) moves into overrides during
   adoption. **Without this layer, every sync erases our live-tested fixes.**

5. **Protected Duration policy** — never blindly replace a local Duration. Report every
   Duration delta separately; local values win unless deliberately accepted (they encode
   execution waits, not just cost).

6. **Semantic diff** — compare PARSED transports, not text lines: added/removed/changed;
   handler-classification changes (TransportType); adjacency changes; requirement changes
   (skills/quests/varbits/items/currency); Duration deltas; duplicate/ambiguous identities;
   counts by category and by region. Machine-readable + human summary. This is the primary
   safety mechanism — a "successful" conversion that silently deletes 50 doors must be loud.

7. **Real-parser validation** — a JVM entry point (unit test or main()) that loads generated
   files through the actual `Transport` loader; fail on parse errors, suspicious per-type
   count reductions vs current resources, duplicates, invalid coordinates, unknown fields.
   Python validates syntax; only the real parser validates semantics.

8. **Collision compatibility** — `collision-map.zip` remains its OWN pipeline, but record the
   upstream transport+collision revisions together and cross-check transport endpoints
   against the shipped collision map (a door into a wall = incompatible revisions).

9. **Golden-route regression suite** — extend the existing
   `testing/webwalker/F2PWebWalkerHarnessPlugin` to ~20 representative routes (doors, stairs,
   dungeons, boats, agility shortcuts, quest-gated transports, long multi-leg routes, the
   historical Priest in Peril failures, gate 190, Rimmington→Aldarin charter). Assert the
   transport CHAIN and path-length bounds, not just arrival. Diagnose with the existing
   log channels: `tp_audit` (availability + rejection reasons), `path_teleports` (chosen
   chain — now logged for BOTH script and display-side paths), landing-wait warns, and the
   agent server (`/state`, `/objects`, script lifecycle endpoints) for headless runs.

10. **Category-by-category adoption** — one category per reviewed commit + route-test group:
    doors/gates in `transports.tsv` first, then stairs/ladders, boats/ships/charters,
    agility shortcuts, conventional teleports (items/spells), then complex networks
    (fairy rings, spirit trees, gliders, quetzals, wilderness obelisks, minecarts, canoes,
    seasonal). Never big-bang. Each commit independently revertable.

11. **Isolated branch** — `spike/shortest-path-data-sync` in its own worktree, branched from
    the current playable spike (so the converter is tested against the patched consumption
    logic: Duration landing waits, instance-translated checks, POH gating/blocklist, etc.),
    with its own jar + clearly named launcher BAT. Nothing reaches `local/development` until
    generated data + overrides + JVM validation + golden routes all pass.

## Current resource inventory (29 files)

`agility_shortcuts, blocked_edges, boats, canoes, charter_ships, dangerous_tiles,
fairy_rings, gnome_gliders, hot_air_balloons, magic_carpets, magic_mushtrees, minecarts,
npcs, quetzal_whistle, quetzals, restrictions, seasonal_transports, ships, spirit_trees,
teleportation_boxes, teleportation_items, teleportation_levers, teleportation_minigames,
teleportation_portals, teleportation_portals_poh, teleportation_spells,
teleportation_spells_home, transports, wilderness_obelisks` (+ `collision-map.zip`).

Note: several are Microbot-specific or diverge structurally from upstream's category split —
the schema inventory (step 2) must map categories, not just columns. POH/W330 transports are
generated in code (`PohPanel`, `JewelleryBox` etc.), NOT from TSVs — out of scope for sync.

## Risk register

| Risk | Mitigation |
|---|---|
| Sync erases live-tested local fixes (gate 190 etc.) | Overrides layer (4) + Duration policy (5) |
| Silent capability loss (dropped rows) | Semantic diff (6) + JVM count validation (7) |
| Category/handler misclassification changes execution | Diff reports TransportType + adjacency changes (6) |
| Upstream durations regress landing waits | Duration deltas reported, local wins (5) |
| Transports/collision revision mismatch (door into wall) | Paired revision record + endpoint cross-check (8) |
| Big-bang regression soup | Per-category adoption (10), one commit each |
| Upstream schema drifts again | Header-keyed parsing + pinned commit + converter fails loudly on unknown columns |
| Walker regression history generally | Isolated worktree/jar (11), golden routes (9), never rebuild the live client jar |

## Success criteria

- Converter run is deterministic and pinned to an upstream commit.
- JVM loader reports per-type counts within expected bounds vs current resources; zero parse errors.
- Semantic diff reviewed and accepted per category; no unexplained removals.
- All golden routes pass on the data-sync jar (chains + length bounds).
- Local overrides file contains every deliberate divergence, each with a one-line reason.
- A future upstream refresh is: run converter → read diff → run routes → commit. No archaeology.

## Assessment (for the record)

Possible: yes. Break-the-walker risk: bounded by (4)(6)(7)(9)(10) — failures are behavioral
(bad routes), not crashes, so the diff + golden routes are the real safety net, not compilation.
Stalls: short-term neutral-to-slightly-noisy, long-term fewer (coverage is most of our stall
class). Ad-hoc patching: data-gap patching mostly eliminated; execution-semantics patching
(gate-190 class) remains ours. Python helper: right tool. Maintenance: front-loaded in the
schema inventory; steady-state syncs are cheap. Overall: strong plan with bounded risk.
