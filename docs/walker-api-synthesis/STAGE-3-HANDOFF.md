# Stage 3 Night Checkpoint: Not Ready To Promote

Superseded for current status by [the final handoff](FINAL-HANDOFF.md): the known
blockers below were resolved and the offline candidate gates passed. This document
retains the original checkpoint evidence; live validation and promotion remain
separate pending operations.

Historical checkpoint. Work resumed on 2026-11-01 at the user's request to finish
the synthesis and prepare a test JAR. The stop instruction below applied to that
night, not the resumed task. Ownership publication, contended cleanup and action
slot fixes are now drafted; independent review also found and addressed stale
continuation and client-thread cleanup gaps. The final resumed tree still needs
fresh executed validation. Do not treat the old focused result as current proof.

2026-10-31. User requested wrapping up for the night. Stop implementation after
this local WIP checkpoint; no background agent or build should be left running.

Branch: `codex/walker-api-synthesis-20261031`. Last fully validated stage-2 commit:
`0662208751` (2,764 reported tests, 6 skipped, zero failures/errors). Current stage 3
is unfinished and has known review blockers. Do not merge, push, build a release
from it, install it or replace playable with this branch head.

## Draft Work Preserved

- Ported request/result/lifecycle contracts from runtime `19049dd3b9078cc5d7b0ce7828788182e3ce9437`,
  compared with data-sync `6a8317fca6476d3d06c8b2c9e21c60825db0fec0`.
- Added execution overloads and scoped cancellation, retained request identities,
  sticky terminal results, intermediate bank-target tracking, effect-confirmation
  gates and instance approach-only snapshots.
- Ported guarded model/menu/mouse input prerequisites, including final-press and
  final-menu validation. Unset legacy guards stay valid; explicit null guards fail
  closed. Attempt admission is not server acknowledgement or observed success.
- Added singleton-cache/live-scene validation, bounded local probes, weak reference
  generations for cross-probe identity, and consumer-shaped API descriptor tests.
- Excluded unrelated runtime route-continuation, door, hosted-house and general
  async planner-refresh changes. Handoff cancellation avoids interrupting refresh
  only for the opt-in interaction context; ordinary cancellation remains unchanged.
- No existing Hub plugin was migrated. Historical consumer-shaped tests are not
  a full Hub artifact compile or previously compiled plugin linkage check.

## Must Fix Before Further Validation Or Promotion

1. **P1: Reservation publication race.** `advanceInteraction` checks currentTarget
   before publishing activeInteraction. A complete legacy setTarget can fit between
   that check and publication while both routeMutations reads see zero. Publish the
   reservation, reject/drain in-flight writes, then revalidate current route
   ownership before proceeding. Add a deterministic completed-mutation race test.
2. **P1: Contended cancellation has no guaranteed cleanup.** If cancelInteraction
   marks cancellation after a poll chooses WAIT but cannot obtain walkerLock,
   the poll can unlock without releasing the reservation. Shutdown may stop all
   future polls, blocking other callers indefinitely. Guarantee eventual cleanup
   without requiring a second cancellation/poll, without blocking the client thread
   or creating an unbounded cleanup worker. Existing contention test calls cancel
   again after the holder exits and therefore does not prove this property.
3. **P2: Action-slot/opcode mismatch.** NPC readiness checks transformed actions,
   but the model derives an opcode from the base definition. Object impostors can
   move an action to a different slot during pointer travel. Presence of the action
   alone does not validate the queued opcode. Resolve the live transformed slot
   and revalidate the exact queued action/opcode at emission/menu dispatch; add
   tests that exercise real model entry construction, not mocked model.click only.

Read-only reviewers reported these blockers after the latest focused green run.
Their reviews are findings, not executed concurrency/live-game proof.

## Executed Evidence And Its Limit

- Focused r1: 142 tests, 3 failures (threading inference/method rename and two mock
  setup verification issues); corrected without regenerating guardrail baselines.
- Focused r2: 149 tests, 1 failure (pre-replacement snapshot was expected to regain
  permission). Tightened the test to require a new snapshot after replacement,
  retaining a positive same-NPC movement/reacquisition case.
- Focused r3: 17 suites, 152 tests, zero failures/errors/skips, 50s. Includes input,
  interaction, API-shape and both guardrail coverage, not the complete client suite.
- **After r3**, added bank-leg cancellation checkpoint and post-approach terminal
  recheck plus two regression tests. These last edits have NOT been compiled or
  tested at this checkpoint. The 152-test result does not validate the final tree.
- No full stage-3 suite, performance comparison, exact Hub artifact compile,
  release JAR, installation, gameplay validation, remote push or branch promotion.

Raw logs: MBOT workspace `operator-work/output/audits/walker-api-synthesis-20261031/`,
`stage3-focused-r1.log`, `stage3-focused-r2.log`, `stage3-focused-r3.log` and
`stage3-focused-r3-summary.json`. Build XML reflects r3 and is not evidence for
the later edits. Builds used JDK 17, offline Gradle, sequential one-worker tasks.

## Resume Order

1. Read this checkpoint and current Git status; preserve other worktrees and the
   running client. Reconfirm protected refs before changing anything.
2. Fix all three review blockers with deterministic regression cases. In particular,
   cancellation must clean up after the last poll, with no second cancel call.
3. Review default/legacy behaviour and descriptor compatibility again. Keep the
   normal source APIs intact and do not silently remove a guard to pass tests.
4. Rerun focused tests against the exact final source, then the complete offline
   client/golden-route/PoH/banking/threading suites. Archive new results separately.
5. Validate exact-artifact Hub consumers/linkage and performance before promotion.
   Live validation, installation and pushes require separate authorization.

Playable, data-sync, runtime and other protected branch tips are unchanged. No VM
or client lifecycle action was performed. Existing Shared Automation dirty documents
remain unrelated and untouched. Subagents are closed; no work continues overnight.
