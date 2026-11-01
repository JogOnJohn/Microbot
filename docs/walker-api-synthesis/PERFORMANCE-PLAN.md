# Offline Performance Plan

Recorded 2026-11-01 before executing the new comparison workload. This is an
offline query-overhead check, not a live-client performance claim or an agreed
gameplay SLO. The production executor and backend selection remain unchanged.

Compare direct local Pathfinder searches (baseline call path) with the typed,
search-owned config-copy API on identical prepared graph fixtures and backend.
Record warmup/sample counts, workload, JDK, heap, and p50/p95/max times. Separate
copy/adaptation overhead from actual search work. Use short/long/transport and
opposite bank-policy cases where the fixture supports them. Do not label a
headless fixture as PoH runtime or client-thread callback performance evidence.

Initial engineering investigation threshold: typed p95 exceeding three times
direct p95 plus 25 ms on a prepared fixture requires explanation before the test
artifact is described as ready. This generous noise allowance is not a promised
latency limit. Report observed numbers even when below it. Heap/allocation figures
must identify whether measured or unavailable; total JVM heap is not retained
per-route allocation. No forced-GC result is a live memory-leak verdict.

Existing full golden-route and incident-corpus tests remain correctness gates.
Their wall-clock durations are diagnostic only, not an identical-condition
baseline/candidate benchmark. The pinned old release remains the rollback and
binary/source compatibility baseline. Dynamic client-thread timing, long-session
retained heap and live gameplay validation require a later authorized run.
