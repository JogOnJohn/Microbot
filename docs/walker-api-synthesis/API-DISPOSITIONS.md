# API Dispositions

The generated [declaration ledger](inventory/api-dispositions.csv) accounts for
3,390 source declarations across the pinned playable/runtime/data-sync/upstream
inventories and the implementation inventory: 2,378 preserved declarations,
276 adapted declarations, 54 additions and 682 intentionally unimported declarations.
The implementation API pin is `01b7a04ed12c523426b1cddadb821ab6c2972eda`;
the subsequent in-bounds fixture fix changes tests only, not that API surface.

These are source-declaration dispositions, not resolved JVM availability. Inherited,
generated and out-of-scope members require the exact-artifact compile/linkage checks.
An unimported declaration does not prove an inherited equivalent is unavailable;
an adapted declaration does not prove every upstream semantic option is supported.
No missing original playable declaration is silently accepted by the ledger tool.

| Family | Final implementation decision for the isolated test candidate |
| --- | --- |
| Legacy walker and thin facade | Preserve callable descriptors, state enums, current scripted destinations and executor policy. Selected old binaries and full-source consumers are checked separately. |
| Runtime/data-sync request interactions | Reconcile the public request/result/lifecycle surface with explicit ownership, cancellation and observed effects. Do not import unrelated runtime renderer, route-continuation or host-recovery work. |
| Collision queries | Adapt existing diagnostic terrain queries without connectivity claims or target snapping. |
| Object readiness | Conservative fresh active-top-level GameObject/WallObject support. Instance/secondary-view/model-based readiness is not inferred; existing PoH execution is not redirected through it. |
| Typed transports | Lossless descriptive conversion, rich grouped requirements and exact legacy identity. DTO gate flags never replace runtime admission. All new executor descriptors are UNSUPPORTED because this stage does not activate split execution. |
| Typed bank preparation | Supported subset is documented in TransportEdgeBanking. Unknown snapshots, stale metadata, charged/offhand providers and unsupported preparation return unavailable or throw, never falsely empty-ready. Legacy preparation remains available and unchanged. |
| Typed planning | Isolated local queries for both configured engines; explicit query target/bank policy, bounded cancellation, indexed exact identities and honest partial termination. Custom explicit route-policy objects and ToA-puzzle snapshots throw rather than approximate. |
| Personal PoH/W330 | Preserve detected facilities and existing execution. New planning captures non-observing house facts; hosted precedence requires its toggle and actual W330. Mounted Xeric's, explicit gates and target-distance behavior remain covered. |
| Alternate planner interface/snapshot | Not published as a second engine integration. The local search-owned config implements the chosen query boundary. Engine-neutral value names are reused, not a duplicate request family. |
| Active-route restart/status and policy mutation facade additions | Not imported: they are coupled to upstream's new route generations, shadow selection and split execution. Keep existing lifecycle delegates and cancellation ownership instead. |
| Shadow/canary/stats/comparison/alternate engine | Intentionally excluded: background comparison, rollout policy and alternate-engine execution are not prerequisites for compatibility adapters and would change operational behavior. |
| Split movement/doors/transports, new WalkExit/door ledgers/geometry policy | Retain tested local recovery/exit reasons, door handling and camera policy. Do not expose upstream internal hooks without a concrete consumer and separate behavioral evidence. |
| Replacement transport requirement schema/execution registry | Keep the richer local parser/converter and executable PoH subtypes. Adapt at the typed boundary rather than flatten groups or replace resource formats. |
| Tile-object/queryable/bank-object expansion and object-ID aliases | Keep local cache/model lifetime rules and supported legacy APIs. No mechanical raw-object-to-model migration or wholesale ID alias replacement. Source consumers and inherited binary members are validated where used. |
| Remaining upstream/runtime internals | Intentionally unimported declarations, individually listed with source pins. No claim of universal upstream drop-in compatibility. A future consumer requiring one needs a focused port, not a false success shim. |

Stage 6 decision: retain the current executor. The optional split/alternate-engine
rewrite is not selected. New provenance bookkeeping serves opt-in query results;
ordinary legacy searches avoid its step-list allocations and retain prior dispatch.
Full route/corpus tests and the offline benchmark gate this decision. Live client
latency, long-session heap and gameplay validation remain before any promotion;
the current deliverable is an uninstalled, unpromoted test JAR.
