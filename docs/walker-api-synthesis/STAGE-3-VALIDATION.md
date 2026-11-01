# Stage 3 Interaction Reconciliation

2026-11-01. Resumed from the historical night checkpoint. Fixed publication before
ownership validation, last-holder cancellation cleanup, stale continuations after
temporary unlock, and client-thread teardown deferral. Cleanup is coalesced onto
a bounded worker only when needed; idle threads retire. Transformed NPC/object
action slots are resolved and revalidated against the exact queued opcode.

Independent static reviews identified these issues and inspected the fixes. Tests
exercise the completed setter-publication window, competing callers, intermediate
bank legs, final-unlock cleanup without a second poll, queued CT cleanup, stale
continuations, guarded slot changes and unchanged unguarded model behavior.
The queued-cleanup test is deterministic, not a real-worker scheduling stress test.

Executed combined focused r5: 33 suites, 257 tests, zero failures/errors/skips.
This includes stages 3-5, both threading guardrails, actual generated transport rows
and the new legacy selection utility. Raw log/XML summary are in the workspace
evidence directory under `synthesis-focused-r5-20261101`. Earlier resumed runs
exposed namespace/JUnit fixture compilation and nested Mockito setup errors; those
were corrected without weakening assertions or regenerating baselines.

Full-suite, artifact, Hub compatibility and performance gates are recorded in the
final synthesis handoff when executed. This focused result alone does not prove
promotion or live gameplay readiness. No VM/client action belongs to this stage.
