# Walker API Test Candidate

Completed 2026-11-01. This is an isolated, uninstalled test candidate, not a
playable-branch promotion or a claim of live gameplay success.

## Artifact

- JAR: `F:\vmware boxs\MBOT\operator-work\artifacts\walker-api-synthesis-20261101\microbot-2.6.47-walker-api-01f341bcb7.jar`
- SHA-256: `ab0694827b1d461185fe9bf59adbd915cb5df86e047d9def88f6f9bcc7d0ea38`
- Artifact source: `01f341bcb7bcec26be94664e43dd628d4ef2a2f4` (clean at build).
- Subsequent isolation tests, tooling and documentation do not change production
  source. The supplemental full-suite result and production class-byte comparison
  are recorded alongside the artifact manifest.
- Local branches: `codex/walker-api-synthesis-20261031` in the client and Hub
  prep worktrees. No pushes, protected-ref changes, installation, launch or VM
  contact occurred. Existing installed/release artifacts remain the rollback path.

## Implementation

Stages 1-5 provide collision/readiness adapters, reconciled owned interactions,
lossless typed transport descriptions and isolated local planning for both search
backends. Stage 6 explicitly retains the current executor: split execution,
shadow/canary routing and a second engine interface are not needed for these
compatibility boundaries. See [API dispositions](API-DISPOSITIONS.md) for every
family and the 3,390-declaration ledger; this is not universal upstream drop-in
compatibility.

Reversible checkpoints, in order: `4fc999f415` interaction safety,
`03b1de8f24` transport values/preparation, `01b7a04ed1` isolated planning,
`01f341bcb7` benchmark fixture correction. Later commits add only validation and
documentation. Hub tooling checkpoints are `7ef9ae18` and `a968843f`.
Do not cherry-pick later dependencies without their prerequisites. Keeping this
branch isolated permits abandoning the entire candidate without touching playable.

## Offline Evidence

Evidence root: `F:\vmware boxs\MBOT\operator-work\output\audits\walker-api-synthesis-20261031`.
The artifact manifest points to the exact run directories and supplemental result.

| Gate | Result and scope |
| --- | --- |
| Full suite at artifact build | 504 suites, 2,935 tests, zero failures/errors, five skips; `candidate-final-r2-20261101` |
| Active-route isolation supplement | Four tests pass: complete published route/owner identity, cancellation, capture failure and concurrent owner replacement; full rerun: 505 suites, 2,939 tests, zero failures/errors, five skips (`supplemental-final-20261101`) |
| Artifact equivalence | All 2,355 compiled API and 3,833 compiled client Java main classes match the existing JAR byte-for-byte; supplemental changes are tests/tooling/docs only |
| Golden/corpus and PoH | Required golden-route, corpus, personal PoH, W330, banking, interaction and client-thread suites executed without skips |
| Converter | 16 Python tests and one fresh generated-resource JVM validator pass; no generated payload adopted |
| Exact Hub source | Seven main plugins and six pinned private snapshots pass against both baseline and candidate: 26 plugin configurations, strict classpath/source guards |
| Old binary linkage | 16 cases, paired baseline/candidate = 32 successful scoped symbolic checks; verifier's 24 self-tests include negative controls |
| Query benchmark | All ten direct/typed pairs pass the pre-recorded investigation threshold: typed p95 <= 3 * direct p95 + 25 ms; max typed p95 18.87 ms |
| Protected refs | Both repos unchanged outside their prep branches; `protected-refs-final-20261101.json` |

Five full-suite skips: `gpu.ShaderTest` (1), `agentserver.uds.UdsParentDirLockdownTest`
(2), `PluginManagerTest` (1), and `runecraft.EssencePouchOverlayTest` (1).
No route/core-safety suite is skipped.

Converter pins: tooling `be0fdca3fdc029684cf19865510c418fdba5c11e`,
data `3a55ac9be3f7481c7cac30d86255789aabb2b3ca`, collision SHA-256
`b630dcb51c05b390e46ccf92c28e96c2eec293e27be9781f57d6d7ec14e62b01`.
Fresh output has no added/removed/endpoint/duration/requirement/adjacency changes;
two other metadata changes remain staged evidence only.

Private source cases: Blackjack `bbfc80ff`, Agility `fa9ecded`, Tithe state
`e8d6b837`, Tithe prehover `22db5ebb`, Hunter `bd6404df`, and historical Homes
`97be5d6d` (compile only, never launched). Main cases: HuntersRumours, Barrows,
HSBlackJack, MicroAgility, TitheFarming, AutoHunter and Blackjack.
Temporary validation checkouts were retired after preserving evidence.

The private Agility binary needs its matching released Shared Automation classes;
the final fixture preserves the old root classes and adds those exact dependencies.
Earlier failed packaging/tool probes are retained as diagnostics, not counted as
passes. Actual final result files and linkage summaries, not just exit codes, were
audited. Linkage is scoped walker/model/queryable symbol resolution, not reflection,
class initialization, whole-JVM compatibility or runtime execution.

The benchmark uses prepared local fixtures and direct versus typed query paths,
not an old-binary whole-client baseline. Heap deltas are not retained allocation
measurements. No live latency or memory-leak conclusion follows from these results.

## Contract Coverage And Limits

| Contract | Evidence / boundary |
| --- | --- |
| API-01 | Explicit declaration dispositions plus selected exact source and binary consumers; no universal compatibility claim |
| STATE/OWNER | Sticky results, owned dispatch/cancel, lock/publication race and stale continuation tests; supplemental full-route isolation |
| PLAN | Search-owned snapshots, bounded query cancellation, both engines and actual selected edge identities; no publication or teardown of the live route |
| SCENE | Fresh client-thread checks, fail-closed unavailable geometry, transformed action slots; instance/secondary-view readiness remains unsupported |
| TRANSPORT | Lossless grouped requirements, resource roundtrip, subtype identity and bounded preparation; unknown/complex gates and unsupported providers fail unavailable |
| POH | Detected personal facilities including mounted Xeric's retained; hosted precedence requires enabled W330 mode and actual W330 |
| RECOVERY | Existing execution retained; queued cancellation and stale owners tested, not exhaustively stress-proven across every real thread interleaving |
| HUB | Exact source matrix and old symbolic-linkage checks; no plugin was installed or enabled |
| PERF | Offline query investigation threshold passed; live/long-session behavior still requires testing |

Typed execution descriptors intentionally return `UNSUPPORTED`. Custom explicit
route-policy objects and ToA puzzle snapshots throw instead of approximating.
Unknown account/varp/bank facts do not silently become zero/empty-ready. Existing
legacy preparation and executor policy remain available and unchanged.

## Authorized Live Test Still Needed

Before promotion, use a separately approved visible test launch and the then-current
workload (not completed Mahogany Homes work). Check cancel/replacement handoffs,
guarded click rejection near interfaces, bank preparation and personal PoH routes.
Check hosted routing only with its toggle enabled on W330; elsewhere personal PoH
must remain preferred. Watch for repeated dispatch, sticky ownership, stalls,
missing plugins, abnormal latency and long-session heap growth.

On failure, close only the identified test process and restore the existing known
release/configuration; the protected playable and runtime refs have not moved.
Installation, gameplay validation, promotion and publication require separate
authorization.
