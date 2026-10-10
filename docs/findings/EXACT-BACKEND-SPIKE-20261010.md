# Exact backend test spike - 2026-10-10

## Scope and provenance

Branch: `spike/shortest-path-data-sync`. Refreshed from playable with merge
`82fd36001c`. Protected playable head remains
`2470b5dfd7e27e32cbcb1a0d3abd989986fb254f`.

Upstream: https://github.com/Skretzo/shortest-path at
`3a55ac9be3f7481c7cac30d86255789aabb2b3ca`. Imported exact engine, required
primitive map, path-step model, wilderness checks, routing cuts and focused tests.
The upstream license is retained verbatim as `EXACT-UPSTREAM-LICENSE.txt` in the
shortest-path resources. Package names and transport accessors are adapted to
Microbot; this is not a wholesale plugin merge.

The queue fix adapts `afd9bd856d51b8d614e34414ee2747c96f48bdee` to both current
legacy search directions: cheaper discoveries replace tentative records, stale
entries are skipped, and pending transports win equal-cost ties. Existing A*
heuristics and bidirectional meeting rules remain; this is not a claim that the
legacy engine is now globally optimal for every transport graph.

## Compatibility boundaries

- `Pathfinder` remains the public facade consumed by walker and QuestHelper.
- `pathfinderBackend` defaults to `LEGACY`; `EXACT` is explicitly opt-in.
- Existing filtered transport availability, W330/POH policy and bank-aware
  wrapper remain responsible for capabilities and banking. Exact internal bank
  planning is disabled rather than introducing a competing bank strategy.
- Chosen transport identities survive path expansion and reach the existing
  walker and hint catalog, including global teleports from intermediate tiles.
- Live collision is frozen per search. Equivalent recaptures reuse preparation;
  changed collision or endpoint sets invalidate it. Learned blocked edges,
  restricted tiles, wilderness avoidance and hazard penalties are checked.
- Existing overlay, minimap zoom, auto-run setting, door handling and plugin
  travel-state handoff policies are not replaced with upstream UI/execution code.
- Cancellation is cooperative during preparation/search and prevents late route
  publication. Timeout may expose a partial route; unreachable does not invent
  one. Exact failures do not silently fall back to legacy.

## Validation

Host JDK 17, Microbot 2.6.30 / RuneLite 1.13.1.

```text
:client:runUnitTests --tests "*shortestpath*" --tests "*walker*"
  --tests "*poh*" --tests "*threadsafety*"
:client:validateTransportSync
  -PtransportSyncGeneratedDir=<spike-root>/build/transport-sync/generated
```

Final combined run: BUILD SUCCESSFUL, 653 tests, zero failures/errors, one
skipped. Generated transport catalog validation also passed (not NO-SOURCE).
Coverage includes both legacy search directions, cheaper chained transport
relaxation, exact synthetic transport identity, collision snapshot keys,
restrictions, cancellation, imported exact-core tests and a packaged-world search
through the public facade.

Fresh converter output had zero semantic changes: additions, removals, changes,
adjacency/duration deltas, endpoint moves and requirement deltas all zero.
The spike's existing sync payload fingerprint is retained.

## Performance and remaining risk

An isolated offline packaged-world smoke measured approximately 6.4 seconds cold
and 47 ms warm on this host. These are not live account or Mahogany Homes
measurements. Real collision changes currently rebuild the static preparation;
that conservative invalidation can be expensive. The search cutoff starts after
preparation, so it does not bound cold preparation time. Cancellation does apply
during preparation. Incremental live-overlay optimization is not claimed here.

The initial combined real-data test exceeded the default 512 MB test heap.
`runUnitTests` now defaults to 2 GB (override with `-PmicrobotTestHeap`). Exact
preflights a minimum 1 GB client heap and recommends 2 GB. This is a test-client
requirement, not a guarantee against every future memory workload.

## Manual test handoff

Only prepare the release test JAR and push this spike. Do not install, restart,
enable plugins or modify playable. Artifact commit/hash belong in the adjacent
artifact manifest, so source provenance does not depend on a self-referential
commit identifier in this document.

When a runtime trial is separately authorized, preserve the playable JAR and
profile, launch the spike JAR with `java -Xmx2g -jar <test-jar>`, then explicitly
select `EXACT` in Shortest Path configuration. Compare LEGACY and EXACT on the
same account/capabilities; collect `exact_static_build`, `exact_done` and walker
completion/interaction timings.

Acceptance workload: Mahogany Homes contract travel and bank wrapper; adjacent
doors and ladders; Larry/Leela upstairs routes; W330 outside-house entry and host
recovery; Nexus/mounted Xeric availability; chained jewellery-box/PvP-arena/glider
routes; QuestHelper hints, world hops, cancellation and replanning. Verify no
late clicks, repeated transitions or teleport-policy regressions. Check cold
rebuild frequency and memory before considering a playable merge. Automated
validation does not establish live gameplay compatibility.
