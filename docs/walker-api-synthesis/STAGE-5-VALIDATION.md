# Stage 5 Isolated Typed Planning

2026-11-01. Added upstream-shaped request/result/step/termination/metric values and
Rs2PathApi.plan, backed by a search-owned config copy rather than a shared bank-flag
transaction. The new typed bank route probe is a concrete consumer. Existing bank
execution and legacy preparation entry points are not redirected.

The copy captures policy, collision view, catalogs, restrictions, learned blocks,
personal detected facilities and hosted-house facts. Refresh uses explicit query
target/bank policy. Incomplete client/account/varp snapshots fail closed; observed
zero remains valid. Read-only Leagues knowledge cannot start calibration, prune
blacklists, persist data or publish unlock/cache changes. Catalog capture retries
boundedly across copy-on-write teleport publication instead of copying changing sets.

Both configured backends remain selected as before. Opt-in legacy queries now
retain actual forward/reverse TransportNode identities in indexed steps, including
repeated endpoint pairs. No endpoint rematching is used. Ordinary legacy executor
searches avoid the new step-list allocation, and getTransportForStep retains its
previous executor behavior. New query cancellation/budgets do not alter ordinary
executor interruption policy. Search completion, target endpoint, physical arrival
and observed interaction success remain distinct.

Unsupported explicit custom route policies and ToA-puzzle query snapshots throw
rather than silently ignoring policy or mutating puzzle state. No alternate engine,
shadow/canary background service, automatic destination snapping or split executor
is activated. Gate summaries on DTOs are not dispatch authorization. Typed bank
preparation's documented unavailable cases are not claimed as supported execution.

Combined focused r5: 257 tests, zero failures/errors/skips, with the actual generated
resource directory configured. Includes successful owned refresh, unavailable varp
capture, snapshot publication races, personal mounted Xeric's/W330 matrix, Leagues
no-side-effect cases, legacy identity reversal, bank probe honesty and guardrails.
Independent review inspected the targeted safety fixes. Full-suite, performance,
exact-artifact Hub results and test-JAR identity are recorded separately when run.
