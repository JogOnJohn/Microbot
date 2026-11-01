# Stage 4 Typed Transport Boundary

2026-11-01. Added upstream-shaped immutable transport values and lossless adapters.
AND clauses retain ordered OR branches, branch quantities, variation/provider IDs,
declared unlocks and zero-as-absence semantics. Flat getters refuse projections
that would discard branch semantics. Exact roundtrip returns the original legacy
object, including PoH subtypes; fabricated values never confer execution authority.
Executor mappings remain UNSUPPORTED: no split executor is activated by conversion.

The opt-in bank helper evaluates a complete ordered chain with carried/banked
quantities, cumulative consumables, reusable demand and a bounded provider solver.
It prefers carried solutions, rejects unknown inventory/equipment snapshots and
stale edge metadata, excludes charged providers it cannot account for, and reports
unavailable rather than falsely empty-ready. Complex unsupported cases are listed
in TransportEdgeBanking's contract. Existing legacy preparation is not redirected.

Combined focused r5 passed 257 tests without failures/errors/skips, including live
capture timeout/missing-container tests and full shipped-catalog/actual generated
row adapter checks. Independent reviews found and verified fixes for the five bank
preparation issues. This does not establish successful equipment changes or gameplay.

The unchanged converter's 16 Python tests passed. Fresh generation used the pinned
tooling/data commits and collision SHA from sync_manifest.json. Semantic report:
zero added/removed routes, endpoint/duration/requirement/adjacency deltas; two other
metadata changes remain staged only. No generated payload was adopted into resources.
The dedicated JVM validator and full regression results belong to final validation.
