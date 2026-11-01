# Fixed Candidate For Agility Testing

2026-11-01. The three independent-review findings are fixed in isolated commits:
`5c291ab27b` (read-only charges and owned varbits) and `1e0911b301` (guarded canvas
clicks). The old `01f341bcb7` JAR remains held; do not mistake it for this candidate.

## Client Artifacts

Under `F:\vmware boxs\MBOT\operator-work\artifacts\walker-api-synthesis-fixed-20261101`:

| Role | Artifact | SHA-256 |
| --- | --- | --- |
| Canonical playable/API candidate | `microbot-2.6.49-walker-api-f33953a8b8.jar` | `9ea4c999925977cfe00cbaef188403bc404c860026a770e791002b254eb07337` |
| Standard runtime candidate | `microbot-2.6.49-runtime-d52144b7d4.jar` | `286be801bbd72eb8661c1fba64a25e5b818af9d82d38ff52fb4262e4a8b9e720` |

Use the standard runtime candidate for the next authorized Agility session: it
retains the existing runtime-specific walker/PoH behavior in addition to the API
fixes. Artifact source pins are `f33953a8b8763e0ce5db3effc6cd7ad71fefcfee` and
`d52144b7d4b904a9224055d2644cb4a91403b995`, respectively. Later documentation-only
commits do not imply a new executable build.

Microbot is 2.6.49. RuneLite remains stable 1.13.4, confirmed against official tags.
This is a selective fork update, not a claim that all upstream 2.6.49 bundles were
imported. Split execution, automatic target snapping, coordinated minigame cost/
refusal rewrites, schema migration, and identity/login/ban-report bundles remain out.

## Validation

- Corrected focused review-fix run: 56 tests, no failures/errors/skips.
- Full canonical candidate: 515 suites, 2,996 tests, no failures/errors, five skips.
- Development: 432 suites, 2,474 tests, no failures/errors, six existing skips.
- Standard runtime: 522 suites, 3,047 tests, no failures/errors, five skips.
- Converter: 16 Python tests and fresh generated-resource JVM validation passed.
- All ten offline direct/typed query benchmark pairs passed the recorded threshold.
- Full Hub: 226 suites, 1,296 tests, no failures/errors, two existing skips.
- Seven main consumers compiled against each exact new client JAR; eight private
  consumers compiled against the exact runtime JAR with immutable source/classpath
  guards. All 16 historical binary cases passed new-runtime scoped linkage checks.
- Agility: ten focused rooftop tests passed without skips; the matching 1.4.4 JAR
  was rebuilt. Source publication and VM staging are recorded separately in the
  final operator manifests. No gameplay was launched or validated tonight.

The intermediate separate data variant passed 3,028 tests after restoring its
non-interrupting shared-refresh cancellation. At the user's request it is not a
separate maintained candidate: the published data-sync source must exactly match
playable, using playable's tests and an exact Git-tree equivalence check. Its old
experiment remains in Git history; no force push or history erasure is needed.

Ordinary client flow now:

```text
development -> playable/shortest-path -> runtime
                   |
                   +-> spike/shortest-path-data-sync (mirror for now)
```

Runtime retains its distinct behavior. Do not blindly merge a data-sync mirror
alignment back into runtime and remove runtime-owned code. Independent data-sync
experiments require an explicit scope change before resuming that integration leg.
The current runtime branch is `codex/tithe-prehover-client-20261025`; the diagram
uses its role name, not a new Git branch. Retired local/development and standalone
dev-plugin hot-reload branches are not recreated.

## Next Authorized Live Session

No client lifecycle or gameplay operation is authorized by this document. For the
next requested Agility launch, first identify the running PID, session, JAR and
Agent Server owner. Check installed versus development-loaded Agility so the plugin
is never loaded twice. Do not overwrite an active/watched plugin while enabled.

Use the newly staged runtime and matching Agility JAR, verify the visible window
and backend separately, then wait for login/break recovery to settle before enabling
Agility. A suitable members world is sufficient; W330 is not mandatory. Personal
PoH remains the default except when hosted mode is enabled and actually on W330.

Validate an Ardougne lap with XP, obstacle progression, script heartbeat and logs.
Pay attention to guarded clicks near chat/side panels, route cancellation, banked
transport handoffs and the first personal-PoH trip. Offline success does not prove
long-session memory stability or every real scheduling interleaving. Do not use
completed Mahogany Homes work for live validation.

Host evidence: `operator-work/output/audits/upstream-sync-20261101-walker` under MBOT.
Final manifests distinguish source publication, VM artifact staging and live testing.
