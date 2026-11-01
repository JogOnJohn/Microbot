# Remaining Stages Plan

Planning sidecar, 2026-11-01. This document proposes bounded future work; it does
not implement it, authorize builds/deployment, or change any acceptance gate in
[IMPLEMENTATION-CONTRACT.md](IMPLEMENTATION-CONTRACT.md).

## Evidence And Starting State

- Read the contract, [API inventory](API-INVENTORY.md), historical CSV declarations
  and pins in [inventory/summary.json](inventory/summary.json), preparation
  [validation](VALIDATION.md), stage 1/2 validation, stage 3 handoff, implementation
  handoff and the sibling Hub compatibility matrix.
- Inspected the local client at HEAD `8546c9edb86ab56c4c2111d30d27e17fed75b184`.
  Concurrent modifications appeared in `Rs2NpcModel.java`, `Rs2Walker.java` and
  `InteractionReconciliationTest.java` during inspection. They belong to other
  work and are not reviewed, reverted or validated by this sidecar.
- Verified reference HEAD
  `41024245fda6245d53d3f7f0ec1b757c1880f5f5` and clean status at
  `F:\vmware boxs\MBOT\operator-work\reference\Microbot-walker-upstream-20261031`.
  Its source, not a moving upstream tip, informs reuse below.
- Stage 1/2 offline results are historical evidence, not validation of this HEAD.
  [Stage 3](STAGE-3-HANDOFF.md) records reservation-publication, last-poll
  cancellation-cleanup and transformed action-slot blockers, plus untested edits.
  Recheck those against the concurrent implementation and demand new evidence.
  Independent stage-4 value/converter tests can proceed separately; do not combine
  an unfinished interaction tree with a release or call it promotion-ready.

All implementation paths below are relative to this repository. Let `M` mean
`runelite-client/src/main/java/net/runelite/client/plugins/microbot/` and `T` mean
`runelite-client/src/test/java/net/runelite/client/plugins/microbot/`.
Paths described as new are proposed, not present or validated by this task.

## Decisions

1. Keep the current route engine, executor, public legacy descriptors and fields.
   Add upstream-shaped values behind the existing thin facade, with a separately
   owned implementation delegate. Do not paste upstream's large `Rs2PathApi`.
2. Stage 4 must preserve the local requirement expression, not just upstream's
   item map. Stage 5 must isolate mutable planning inputs, not just serialize them.
3. Use bank-route analysis as the first concrete typed-planning consumer: it
   currently constructs synchronous pathfinders and temporarily changes bank
   policy in several places. Typed results can retain selected transports and
   distinguish an unavailable leg from a completed route without changing walking.
4. Stage 6 is optional. No wholesale split executor, alternate planner, canary,
   camera rewrite or automatic destination snapping is required by this contract.
   A demonstrated contract violation still requires a fix in its owning stage;
   calling executor work optional cannot defer a known correctness failure.

## Minimal Upstream Reuse

| Reference source in `M/util/walker/` | Reuse and necessary adaptation |
| --- | --- |
| `Rs2TransportType`, `Rs2TransportExecutor`, `Rs2TerminalTravelMode` | Reuse names/enums as descriptive boundary values. Map actual local handlers explicitly; enum presence is not execution support. Preserve UNKNOWN/UNSUPPORTED and executor/mode consistency. Do not import upstream registry/admission policy wholesale. |
| `Rs2TransportEdge` | Reuse immutable description and package-private source identity concept. Preserve exact local selection/subtype, including executable PoH payloads. Gate booleans are summaries, not complete skill/quest/var/unlock predicates. Never reconstruct an executable Transport from this DTO. |
| `Rs2TransportItemRequirement` | Reuse class name and existing constructor/method descriptors for representable flat clauses. Its map plus global staff/offhand sets cannot losslessly encode local branch-specific providers, repeated item alternatives at different quantities or unlock-only branches. Add a branch-preserving representation under this same type; do not invent a rival transport family. |
| `Rs2TransportLoadout` | Reuse immutable withdrawals/equipment/satisfiable distinction. `unavailable().isEmpty()` is true upstream: every consumer must check satisfiable before treating empty as ready. Audit provider selection and quantity arithmetic against local semantics before reuse. |
| `Rs2RouteRequest`, `Rs2RouteResult`, `Rs2RouteStep`, `Rs2RouteTermination`, `Rs2RouteMetrics` | Reuse upstream names, factories, getters and defensive-copy rules. Preserve target order, per-step selection and unavailable metrics. Search completion/target reachability are not player arrival or observed effects. |
| `Rs2RoutePolicy`, `Rs2PlanningSnapshot`, `Rs2RoutePlanner` | Reuse engine-neutral boundary, adapt capture and local execution. A final callback is not necessarily immutable: upstream captures `config::isDangerousAdjacentTile`; its local planner still reads config. Freeze referenced data or keep an isolated config owned solely by that search. |
| `Rs2ActiveRouteStatus`, shadow/comparison/canary types, `UpstreamRoutePlanner`, vendored `src/upstreamPlanner` | Explicitly defer unless a separately approved consumer requires them. Do not copy active-route lifecycle, background pools, test hooks, planner selection/config or packaging merely to satisfy type dependencies. |

Preserve inventoried public descriptors when adapting upstream types; list additive
members and semantic differences in a disposition ledger. For a legacy flat getter
that cannot represent a richer clause, specify and test an explicit refusal rather
than silently dropping unlock/provider semantics. Resolve this API decision before
publishing the type. Public construction alone must never grant execution authority.

## Stage 4: Typed Transport Conversion

### File And Task Map

| Slice | Files | Bounded implementation |
| --- | --- | --- |
| 4A: value boundary | New `M/util/walker/Rs2Transport{Type,Executor,Edge,ItemRequirement,Loadout}.java`, `Rs2TerminalTravelMode.java` | Adapt reference values; document unsupported mappings and rich clause representation. No runtime caller redirection. Keep legacy `shortestpath.Transport` and its public members intact. |
| 4B: lossless conversion | New `M/util/walker/TransportEdgeAdapter.java` (package-private); read existing `M/shortestpath/Transport.java`, `transport/requirement/{TransportItems,ItemRequirement,Unlock}.java` and `transport/parser/ItemRequirementParser.java` | Copy ordered AND clauses, each ordered OR branch, variation IDs, branch quantity, provider IDs and unlock token. Preserve absence/zero semantics and explicit gates. Retain opaque exact local source identity separately from immutable descriptive fields. No flattened `getItemIdRequirements()` or maximum `getQuantity()` as authority. |
| 4C: typed bank helpers | `M/util/walker/banking/Rs2WalkerBankingPlanner.java`, additive wrappers in `Rs2Walker.java`; package-private adapter entry points as needed | Add `getMissingTransportEdges`, `getMissingTransportEdgeItemIdsWithQuantities`, `getMissingTransportEdgeLoadout`; preserve existing List<Transport> APIs. Names differ deliberately to avoid generic-erasure overload collisions. Share proven evaluation logic, not a second subtly different requirement evaluator. |
| 4D: converter boundary | `scripts/transport_sync/{sync.py,test_sync.py,fixtures/}`, `T/shortestpath/TransportSyncGeneratedResourcesTest.java`, new `T/util/walker/TransportEdgeAdapterTest.java` | Extend fixtures/assertions only as needed to prove parsed resources survive typed conversion. General converter development belongs first in its standalone repository per its README, then reviewed vendoring; do not change resource format to fit DTO limitations. |

Do not expose `getTransportEdgesForDestination(WorldPoint,boolean)` by copying the
current shared-config probe. Its safe implementation belongs in stage 5 after
isolated planning exists. Stage 4 may test it through fixtures internally but must
record the public surface as deferred, not supported.

### Required Fidelity

- List means AND; branches mean OR; quantities belong to branches, not clauses.
  Preserve `SHANTAY_PASS=1|COINS=5`, item variations, `ITEM=0` absence requirements,
  `AXE=1|UNLOCK_CANOE_AXE=1`, and ANDed `UNLOCK_XERICS_HONOUR=1` gates. Unknown unlocks
  fail loudly; never convert an unlock to an item ID, empty satisfied clause or
  provider substitute. Test duplicate IDs across branches without map overwrite.
- Preserve carried inventory/equipment versus bank availability. Bank-known is not
  withdrawn, and withdrawn is not equipped. One staff/offhand loadout must satisfy
  the relevant clauses jointly; two mutually exclusive staves cannot combine.
  Preserve combination-rune/provider behavior and currency thresholds.
- Preserve currency, membership, wilderness, skill/combat/max-level/max-quest-point,
  quest-state, varbit/varplayer and declared-unlock admission through the existing
  local authority. Typed gate flags cannot replace full checks. Cached typed edges
  must not bypass changed policy or changed unlock/world state.
- Establish route-chain quantity policy with fixtures: repeated consumable fares or
  runes consume supplies; reusable tools/providers do not multiply unnecessarily;
  inventory already held reduces withdrawals. Detect impossible/overflowing
  requirements, insufficient bank quantities and equipment conflicts explicitly.
  Do not silently substitute max-per-edge or sum-all behavior without parity review.
- Keep PoH subtype identity and current personal-facility selection, mounted Xeric's,
  house/spell gates and W330 hosted precedence. Do not use a lossy round-trip DTO to
  rebuild a `PohTransport`, or infer dispatch capability solely from category.

### Stage-4 Tests And Stop Condition

Add `TransportEdgeAdapterTest`, `Rs2TransportItemRequirementTest` and
`Rs2TransportLoadoutTest` under `T/util/walker/`; expand
`T/util/walker/banking/BankedTransportItemPlanningTest.java` for typed parity.
Test defensive copies by mutating original arrays/maps/branches after conversion;
test unknown/null mappings, unsupported handlers, and same-endpoint distinct edges.
Compare legacy and typed evaluation over the same requirement fixtures and resource
rows. Assert no route, target, future, configuration, mouse or camera mutations.

Round-trip means parse converter TSV -> local requirement tree -> typed tree ->
lossless local requirement tree/semantic form, plus converter read/write fixtures;
compare branch-level predicates, not only item IDs or row counts. Retaining an
opaque Transport pointer alone is not a successful conversion round-trip.
Keep provenance/collision hashes, headers, local overrides, duplicate detection and
blocked-endpoint ratchet in the generated-resource validator. No live resources are
adopted by running a converter. Stop on any unrepresentable clause or unexplained
admission difference; do not mark TRANSPORT-01 met with a smaller fixture subset.

## Stage 5: Typed Planning Integration

### First Consumer And File Map

| Slice | Files | Bounded implementation |
| --- | --- | --- |
| 5A: contracts | New `M/util/walker/Rs2Route{Request,Result,Step,Termination,Metrics,Policy,Planner}.java`, `Rs2PlanningSnapshot.java` | Adapt reference value contracts and invalid-input checks, with no executor dependency. Resolve supported request options explicitly. |
| 5B: isolation | `M/shortestpath/pathfinder/PathfinderConfig.java`, new package-private snapshot/capture support there; new `M/util/walker/RoutePlanningService.java` | Capture game state on the client thread in short bounded callbacks; perform refresh/search on worker/script thread using search-owned state. Extend existing refresh/admission code minimally to accept captured inputs rather than duplicate policy. |
| 5C: local engine result | `M/shortestpath/pathfinder/{Pathfinder,ExactSearchAdapter,PathStep}.java`, new local adapter implementing `Rs2RoutePlanner` | Preserve existing engine choice and route costs. Expose exact ordered selected steps and explicit termination for the planning boundary without replacing the production algorithm. Legacy backend needs real outcome/selection bookkeeping if it cannot provide it today. |
| 5D: facade | `M/util/walker/Rs2PathApi.java` delegates to planning service | Add `plan(Rs2RouteRequest)`; retain old concrete accessors, fields and signatures. No active route publication, executor submission, cancellation of others, marker updates or input/camera work. Update the thin-facade contract comment only to reflect this owned delegate. |
| 5E: consumer migration | `M/util/walker/banking/Rs2WalkerBankingPlanner.java`, `TransportRouteAnalysis.java`, additive `Rs2Walker` wrappers | Migrate destination transport probe first, then direct/to-bank/from-bank comparison as separate changes. Use request purposes and exact typed selected steps. Preserve existing analysis constructors/getters and legacy Transport APIs through internal exact-identity adapters. Add `getTransportEdgesForDestination(WorldPoint,boolean)` only here. |

### Isolation Work Required Before Public Planning

The local `PathfinderConfig` contains mutable maps, packed indexes, usable teleports,
refresh-cache state, restrictions, target-dependent filtering and learned/runtime
blocked edges. `refreshTransports` also consults the singleton config's bank policy.
Merely allocating a second config or synchronizing `getPathfinderMutex()` does not
prove isolation. Inventory every mutable/global read used by admission/search, then
capture a coherent input generation including inventory/equipment/bank quantities,
unlocks, levels/quests/vars, world/membership, detected PoH facilities, transport
toggles, overrides, restrictions, backend/cutoff and collision overlays.

Preferred implementation: search-owned config/catalog built from frozen inputs,
sharing only proven immutable archive data. Detached refresh must use its explicit
bank policy, not `ShortestPathPlugin.getPathfinderConfig().useBankItems`. Copy mutable
Transport fields or provide search-local overlays; quest-based action changes must
not mutate the live catalog. Preserve current cache-admission semantics rather than
using all raw catalog edges. Live collision/danger predicates must refer to pinned
data, not mutable config or scene objects. Readiness snapshots remain unrelated.

The local exact backend calls `config.publishExactTransport` inside `runExact`.
Query searches must publish only into search-owned structures; they must not add
teleports or reorder edges in the live config. Retain actual `PathStep` choice and
index. `getTransportForStep(from,to)`/endpoint maps alone can collapse repeated
directed pairs; the per-step list is authoritative. Never catalog-rematch ambiguous
edges or silently replace a chosen PoH subtype with an equivalent-looking edge.

Do not use the reference's set-bank/refresh/search/finally-refresh as the final
isolation design. It restores one flag, not all mutated configuration, and restoration
can itself throw. If a transitional transaction is proposed instead, it is a separate
review blocker until complete state restoration, concurrent refresh/writer safety,
failure during restoration and deadlock-free lock ordering are demonstrated.
No waiting for client-thread callbacks while holding a lock that callback needs.

### Honest Results, Cancellation And Banking

- Local exact reasons currently include `reached-goal`, `time-cutoff`, `unreachable`,
  `cancelled` and `backend-failure`. Map them explicitly to upstream termination
  values. Unknown/null/internal failure is not TARGET_REACHED. Legacy searches
  currently lack equivalent explicit reasons on every exit; add bookkeeping/tests
  rather than infer success from `done`, a nonempty path or the last tile alone.
- Preserve `isSearchCompleted()` semantics: exhausted/cutoff searches may be
  completed but not target-reaching. `isTargetReached(tolerance)` concerns a planned
  endpoint on the right plane, never player arrival or interaction success.
  Validate original requested targets/tolerance, including start==target, multiple
  targets and partial paths. No silent nearest-tile retargeting.
- Reject synchronous plan calls on the client thread before refresh, locking or
  search. Bound work by the existing calculation cutoff; specify cancellation via
  worker interruption/search-local cancellation checkpoints. Cancel only the query,
  preserve interrupt state as appropriate and return CANCELLED without leaking
  policy or touching another owner's active route. A new public cancellation API
  needs explicit disposition; do not copy test-only upstream lifecycle hooks.
- Migrate one bank probe at a time. Keep original scripted destination, caller-held
  intermediate bank target, route ownership and request identity unchanged.
  Analysis must reject partial/unavailable bank legs as complete journeys; preserve
  direct-route lower-bound and unavailable-distance semantics. Freeze supplies for
  each analysis generation, and invalidate/replan if admission inputs change before
  execution. Planning bank inclusion is not permission to withdraw, equip or click.

### Stage-5 Tests And Stop Condition

Add focused `Rs2RouteValuesTest`, `Rs2PathApiPlanningTest`,
`PlanningSnapshotIsolationTest`, `RoutePlanningCancellationTest` and
`TransportRouteAnalysisTest` in `T/util/walker/`; add pathfinder outcome/step tests
under `T/shortestpath/pathfinder/`. Adapt relevant reference test cases, not its
canary/alternate-engine harness or assumptions about config restoration.

Required deterministic cases: concurrent owner starts/replaces/stops during query;
two queries with opposite bank policies; mutation of input containers after capture;
refresh/search/capture failure, cancellation before/during search and failed cleanup;
cache hit/miss; singleton bank-policy trap; exact transport publication; ambiguous
and repeated endpoint pairs; both configured backends; exhaustion/cutoff/failure;
multi-target/plane/tolerance; held versus banked supplies; PoH/W330 matrix.
Before/after snapshots cover active pathfinder/future/executor identity, route target,
bank holder, interaction reservation, marker, overrides, policy, catalogs/indexes,
cache state and relevant global transport/PoH state. Query-owned metrics are allowed;
new global mutations must be justified, not silently omitted from assertions.

Run the consumer probe and three-leg analysis against unchanged baseline fixtures,
comparing destinations, transport sequence/identity, costs and failure semantics.
Stop if the adapter needs an executor swap or an unexplained route baseline rewrite.
No concrete consumer benefit or no safe isolation means stage 5 remains open, not
complete via DTO-only compilation.

## Stage 6: Optional Executor Changes

No stage-6 implementation is selected now. First compare the post-stage-5 candidate
against OWNER-01, SCENE-01, POH-01 and RECOVERY-01 evidence. If those gates pass,
retain the existing executor and record the split-executor APIs as deferred with
rationale; do not claim they were implemented. If a gate fails, identify the smallest
reproducer and fix it before promotion regardless of this optional label.

| Trigger | Narrow possible owner/files | Required tests before considering it |
| --- | --- | --- |
| Wrong same-endpoint transport dispatched | `Rs2Walker.java`, `M/util/walker/transport/`, local `Pathfinder` selected-step boundary | Distinct fare/action/subtype and repeated-edge fixtures; exact chosen identity reaches existing handler; stale generation cannot dispatch. |
| Route owner or bank leg lost on handoff/recovery | `Rs2Walker.java`, `lifecycle/`, `banking/`, `recovery/` | Competing owners, retained interaction identity, lock-busy, replacement/error and last-poll cancellation; cleanup affects only owned route and preserves original destination. Stage-3 blockers stay stage-3 obligations. |
| Stale object/unsafe click or wrong floor | `interaction/`, existing scene snapshot/input guards, `door/`, `awaits/`, `obstacle/` | Despawn/impostor/plane/instance/occlusion transitions, exact menu opcode at emission, positive effect confirmation, missing snapshot fail-closed, no client-thread blocking. |
| Proven repeated refusal or stalled recovery | Existing transport refusal ledger and recovery owners | Bounded retries/expiry, cancellation, door/stair transitions, partial-route honesty and unchanged golden routes. No unbounded retry or automatic snapping. |

A split into upstream `Rs2WalkerMovement`, `Rs2WalkerDoors` or `Rs2WalkerTransports`
is not itself a contract outcome. Reuse isolated proven algorithms only after a
reproducer shows need. Keep camera policy unchanged. Measure planning lock time,
client-thread callbacks, latency and memory before/after any executor change; no
unmeasured 'performance improvement' claim or automatic alternate-engine rollout.

## Validation And Delivery Order

These are future commands/tasks, not actions performed by this planning task.
Each implementation slice should be independently reviewable/reversible with its
tests; keep production migration separate from additive values/conversion. Recheck
current status and protected refs before starting, and do not stage concurrent work.

1. Resolve stage-3 blockers with deterministic tests and validate its exact final
   tree. Reuse last validated stage 2 as a comparison point, not proof of stage 3.
2. Implement 4A/4B, then 4C/4D. Run requirement/converter focused tests and real
   generated-resource validation. Record dispositions before enabling typed callers.
3. Implement 5A/5B and prove isolation before exposing `plan`; then 5C/5D and one
   5E bank consumer per review. Assess stage 6 only from remaining failures.
4. On authorized implementation runs, use JDK 17 and sequential bounded Gradle
   tasks: `:client:compileJava`, focused `:client:runUnitTests --tests <class>`, then
   full `:client:runUnitTests`. `:client:test` can be skipped and is not proof.
   Run converter fixture tests with `python -m unittest discover -s scripts -p test_sync.py`;
   generate into a fresh explicit staging directory using the pinned manifest, then
   `:client:validateTransportSync -PtransportSyncGeneratedDir=<fresh-directory>`.
   Its generated-resource test normally skips in generic sweeps; demand a real
   non-skipped validator result and inspect provenance/diff before any adoption.
5. Full unchanged suites include `ShortestPathGoldenRouteBaselineTest`,
   `WalkerRouteCorpusTest`, `PersonalPohSelectionTest`, `World330HostedHouseAccessTest`,
   `World330HostedHouseTransportTest`, `PathfinderConfigWorld330MergeTest`,
   `BankedTransportItemPlanningTest`, `TransportItemsSatisfactionTest`,
   `TransportUpstreamRequirementsTest`, `TransportResourceLoadTest`, interaction and
   handoff tests, partial-route/endpoint/floor/door/recovery tests, scene-click tests,
   `ClientThreadGuardrailTest` and `QueryableTerminalGuardrailTest`. Include stage-3
   regressions and every new focused suite. Record skips/failures honestly; never
   regenerate golden-route or guardrail baselines to hide drift.
6. Establish PERF-01 baseline before evaluating broader changes: pin baseline and
   candidate commits/resources/backend, warm-up, sample count, workload inputs,
   host/VM/JDK/heap, warm/cold caches; measure total/p50/p95/max planning time,
   lock wait, client-thread callback count/time, allocation/retained heap and bounded
   diagnostic volume. Include short/long/multi-target, bank three-leg, transport-chain,
   PoH, cutoff and contended-owner workloads. Agree tolerances before candidate
   evaluation, compare identical conditions, explain every regression. Numeric
   thresholds are not established by this plan.
7. Use the sibling Hub matrix: main Hunter Rumours, Barrows, HSBlackJack, Agility;
   full selected private Blackjack/Agility/both Tithe/AutoHunter sources; historical
   Homes interaction branch for compile/linkage only, never gameplay. Preserve
   predicate handoff, run-permitted fallback, exact Tithe coordinates/plane, retained
   requests and shutdown cancellation. Compare each to its compatible baseline.
   Future JDK 11 focused tasks must use matching `<PluginClassName>Jar`,
   `-PpluginList=<PluginClassName>` and
   `-PmicrobotClientPath=<exact-candidate.jar>` with remote fallback disabled.
   Link representative previously compiled binaries against that same artifact;
   source recompilation does not prove descriptors/return types/public fields.
8. Publish per-slice evidence: exact source commit/dirty patch identity, input pins,
   full commands/logs/XML counts/skips, candidate artifact SHA-256 when authorized,
   baseline comparison, API dispositions and remaining gates. Resolve every
   inventoried family explicitly before calling complete synthesis finished.
   Deferred APIs are not implemented APIs; current deferrals remain visible.
9. Installation, VM/client lifecycle, plugin enablement, live tests, promotion and
   push each remain separately authorized. Preserve playable/protected branches
   and current client. Any future live proof uses the then-current approved workload,
   not completed Homes; verify visible window and backend separately, record exact
   artifact/rollback point. Build success cannot substitute for live proof.

## Evidence From This Sidecar

Read-only repository/reference inspection and documentation-only validation. The
only owned output is this new file. No production edits, application/converter
tests, Gradle builds, artifacts, commits, pushes, VM operations or promotion were
performed. All implementation stages and unresolved release gates retain their
existing status.
