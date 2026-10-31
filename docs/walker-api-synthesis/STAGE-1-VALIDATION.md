# Stage 1: Collision Queries

2026-10-31, isolated `codex/walker-api-synthesis-20261031` client branch.

## Implementation

- Added upstream-shaped `Rs2PathApi.hasWalkableTileWithin(WorldPoint,int)` and
  `nearestWalkableTile(WorldPoint,int)` as thin ShortestPathPlugin delegates.
- Extracted the existing walker loops into shared stateless CollisionMapQueries.
  Kept the walker map-taking methods as delegates, preserving existing callers,
  diagnostic-only nearest lookup and blocked-target rejection.
- No automatic snapping, route publication/cancellation, config refresh, planner
  replacement, transport changes, object interaction or camera operations.
- No plugin version bump: existing runtime behaviour is unchanged; these are
  additive query APIs and an implementation extraction, not a plugin behaviour fix.

## Executed Validation

JDK 17.0.19; Gradle 8.8, offline, no daemon, one worker, build cache enabled.
Gradle heap 768 MiB; existing external bounded-test init script set one test fork
and 2 GiB test heap. Builds ran sequentially in this checkout.

- `:client:compileJava`: passed in 1m 17s, with existing deprecation/Lombok warnings.
- `:client:runUnitTests`: passed in 3m 15s.
- XML: 479 suites, 2,727 reported tests, 6 skipped, zero failures and zero errors.
- CollisionMapQueriesTest: 12/12 passed.
- Rs2PathApiCollisionQueryTest: 2/2 passed; config/route/future/override state remains
  unchanged and every test restores the static state it temporarily installs.
- Existing ShortestPathGoldenRouteBaselineTest (3), WalkerRouteCorpusTest (20),
  PersonalPohSelectionTest (2), World330HostedHouseAccessTest (13),
  World330HostedHouseTransportTest (1), BankedTransportItemPlanningTest (7),
  WalkerInteractionCompletionTest (5), Rs2WalkerHandoffFailureTest (1),
  Rs2WalkerPartialRouteSafetyTest (4) and Rs2WalkerPathEndpointArrivalTest (4)
  all passed without skips. Counts are JUnit methods, not route-case counts.
- Git whitespace checks passed; protected local branch tips remain unchanged.

New cases cover unknown maps/targets/neighbors, zero/negative and inclusive radii,
same-plane queries, deterministic ring/tie order, excluding the center, skipping
unknown nearest candidates, and standability without proving connectivity.

Raw log and machine-readable XML totals are under MBOT's local evidence directory:
`operator-work/output/audits/walker-api-synthesis-20261031/`, files
`stage1-unit-tests.log` and `stage1-test-summary.json`. Gradle XML is under
`runelite-client/build/test-results/runUnitTests/` in this checkout. Pinned API
inventory CSVs remain historical and were not regenerated to conceal additions.

## Remaining Gates

No baseline-versus-candidate performance claim, Hub exact-artifact compilation or
precompiled-plugin linkage check is established here. No release JAR, installation,
VM operation, live gameplay validation, push or protected-branch promotion occurred.
The rest of the implementation contract remains open. Stage 2 readiness-only
helpers are next; typed transports/planning and interaction reconciliation are not
implicitly included in this first tranche.
