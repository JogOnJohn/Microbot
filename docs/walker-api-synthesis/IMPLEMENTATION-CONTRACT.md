# Walker API Synthesis Implementation Contract

Agreed 2026-10-31. This is the acceptance checklist for implementation, not a claim
that every item below has been delivered. API inventory pins and semantic details
remain in [API-INVENTORY.md](API-INVENTORY.md); sequencing and environment details
remain in [HANDOFF.md](HANDOFF.md).

## Objective

Make upstream-facing APIs usable with our proven walker, reducing future porting
work without losing routing, transport or interaction behaviour. Preserve behaviour
first, expose compatible APIs second, improve internals third. A wholesale walker
replacement is not the objective.

## Requirements And Acceptance Evidence

| ID | Required outcome | Acceptance evidence before promotion |
| --- | --- | --- |
| API-01 | Preserve legacy methods, JVM descriptors, return types and public fields. Classify inventoried APIs as supported, adapted, deferred or intentionally unsupported. | Disposition ledger; source compile and linkage checks against exact candidate artifact; no unaccounted APIs |
| STATE-01 | Separate planner completion, physical arrival, readiness and observed interaction success. | Tests for partial/exhausted planning, predicate handoff, physical arrival and effect confirmation; no unconditional success adapters |
| OWNER-01 | Preserve route ownership, banked intermediate targets, request identity and cancellation under contention. | Competing-owner, retained-request, banked target, stop/replacement/error and owned-route-only cleanup tests |
| PLAN-01 | Keep planning separate from execution; queries must not publish active routes or leak temporary inventory/config policy. | Before/after state tests; cancellation and exception restoration; no hidden executor/camera/click side effects |
| SCENE-01 | Fresh object resolution, client-thread game reads, safe geometry and fail-closed unavailable scene snapshots. Never block the client thread. | Despawn/transition/plane/occlusion tests and existing client-thread guardrails; no stale entity used as authority |
| TRANSPORT-01 | Preserve alternative/grouped requirements, quantities, banked supplies, unlock gates and converter formats. | Converter round-trip and generated-resource parsing; requirement-group/quantity tests; route transport-chain parity |
| POH-01 | Use detected personal facilities; W330 hosted precedence only when enabled and on W330. Respect explicit house/teleport gates. | Personal-house and hosted-world matrix, detected facilities including mounted Xeric's, cached-edge dispatch gates |
| RECOVERY-01 | Preserve doors/stairs, plane transitions, partial-route honesty, bounded retry/cancellation and original scripted destination. | Golden routes and incident corpus plus door/floor/partial-route tests; no silent snapping or unbounded retry |
| HUB-01 | Preserve main and private consumer behaviour, not merely matching method names. | Exact-client compile matrix, representative precompiled linkage, ownership/predicate/Blackjack run-policy/Tithe-coordinate checks |
| PERF-01 | Establish planning latency, client-thread work and memory baselines; give bounded, meaningful diagnostics without sensitive data. | Repeatable baseline/candidate workloads and measurements, agreed regression limits; no unexplained regression or logging flood |

No measurable performance claim or numeric regression threshold is established by
the syntax inventory. Record workloads, warm-up, sample counts, host/VM conditions,
metrics and acceptable tolerances before evaluating performance-changing work.

## Tranches

| Stage | Scope | Status |
| --- | --- | --- |
| 0 | Inventory, independent review, acceptance contract and regression discovery | Prepared; complete runtime/performance baseline still pending |
| 1 | Collision-query facade adapters and contract tests; keep existing executor policy | Implemented and offline validated; promotion pending |
| 2 | Readiness-only object helpers; client-thread/freshness and no-effect tests | Not started |
| 3 | Reconcile canonical/runtime/data-sync interaction APIs before publishing adapters | Not started |
| 4 | Typed transport conversion with requirement/converter fidelity | Not started |
| 5 | Typed planning integration where a concrete consumer benefit justifies it | Not started |
| 6 | Optional executor changes, separately justified and measured | Deferred; not implied by API synthesis |

Each stage needs a small reversible commit and acceptance evidence. Query-only
adapters do not wait for the interaction migration. Neither partial readiness nor
planning termination may be mapped to confirmed interaction success.

## API Disposition Ledger

The pinned CSV inventory remains historical evidence; do not silently rewrite it
to describe implementation progress. Maintain explicit dispositions as stages land.

| Surface | Disposition for first tranche |
| --- | --- |
| Existing public canonical walker/facade methods | Preserve unchanged contracts |
| Rs2PathApi.hasWalkableTileWithin(WorldPoint,int) | Adapted existing canonical collision query; no reachability guarantee |
| Rs2PathApi.nearestWalkableTile(WorldPoint,int) | Adapted existing canonical radius-1 diagnostic query; never auto-retarget |
| Readiness-only object helpers | Deferred to stage 2 |
| Runtime/data-sync request-based interactions | Deferred to deliberate stage 3 reconciliation |
| Typed transport/route/planner and shadow/canary additions | Deferred to stages 4/5; do not wholesale copy upstream facade |
| Split executor and upstream test-only public hooks | Deferred; not supported merely by exposing API names |
| Every other inventoried declaration | Deferred pending explicit per-family disposition before its promotion |

## Release Gates

1. Every in-scope API has an explicit disposition; record why deferred/unsupported
   APIs are not part of that stage. Resolve all dispositions before calling the
   complete synthesis finished.
2. Existing full golden-route/corpus, PoH, banking, interaction and threading suites
   pass alongside new focused tests. Changed routes need individual explanation and
   approval, not regenerated baselines to conceal drift.
3. Selected main/private Hub consumers compile against the exact candidate client,
   with no silent published-artifact fallback. Linkage checks cover old binaries.
4. Applicable performance baselines show no unexplained regression. First query-only
   stage proves no new state/config/route mutations; broader claims remain pending.
5. Builds, JAR generation, installation and live validation are separate evidence.
   Obtain authorization before VM lifecycle or plugin changes; use the then-current
   workload, not completed Mahogany Homes work.
6. Playable, protected branches and the running client stay unchanged until gates
   pass and promotion is authorized. Record exact commits/artifact hashes and a
   reversible rollback point for each promotion. Push is not implied by local work.

## Stage Record

Update status and evidence as work proceeds. Never label an entire stage complete
because an API compiles or because its first unit test passes.

- Stage 1: client compile passed; full offline runUnitTests reported 2,727 tests,
  6 skipped, zero failures/errors, including all 14 new query tests. See
  [stage 1 evidence and remaining gates](STAGE-1-VALIDATION.md). Implementation is
  isolated; promotion, Hub artifact/linkage checks, performance comparison and live
  validation remain pending. No deployment or executor change was performed.
