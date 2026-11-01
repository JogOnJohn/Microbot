# Walker API Synthesis Preparation

Prepared 2026-10-31. The inventory checkpoint is complete; isolated implementation
has now begun under the acceptance contract. No promotion or client lifecycle changes.

**Resumed 2026-11-01:** stages 3-5 are implemented and focused validated. Full
regression, performance, exact-artifact Hub validation and test-JAR preparation
are in progress. The night checkpoint is historical, not a current stop instruction.

## Workspace

Both client and Hub preparation branches are `codex/walker-api-synthesis-20261031`.

| Role | Workspace-relative checkout | Pinned starting commit |
| --- | --- | --- |
| Client development | `repos/Microbot-walker-api` | `3d93dbf85524baf112fa4487121c58e05d48eea0` |
| Hub development | `repos/Microbot-Hub-walker-api` | `0b017fdea8ab94added7bf65a626263e4fa18337` |
| Detached upstream reference; do not edit | `operator-work/reference/Microbot-walker-upstream-20261031` | `41024245fda6245d53d3f7f0ec1b757c1880f5f5` |

Client starts from canonical `playable/shortest-path`, not upstream or the running-client
branch. Hub starts from `main`. Neither protected branch was advanced. The reference
checkout is conventionally reference-only, not filesystem write-protected.

Client upstream was fetched for this inventory. Hub sources use locally available
pinned refs; this task is not another upstream sync or release.

## Read Next

- [Implementation contract and acceptance checklist](IMPLEMENTATION-CONTRACT.md)
- [Stage 1 validation](STAGE-1-VALIDATION.md) and [stage 2 validation/support limits](STAGE-2-VALIDATION.md)
- [Interaction reconciliation](STAGE-3-VALIDATION.md), [typed transports](STAGE-4-VALIDATION.md), and [isolated planning](STAGE-5-VALIDATION.md)
- [API inventory and semantic differences](API-INVENTORY.md)
- [Implementation boundaries and future validation](HANDOFF.md)
- [Reproducible inventory tool](../../tools/walker-api-audit/README.md)
- [Exact API declarations and comparisons](inventory/summary.json)
- Hub counterpart: `docs/walker-api-synthesis/README.md` in the sibling Hub checkout.

The main finding is that there are three different surfaces, not just ours versus
upstream: canonical playable, our runtime/data-sync extensions, and upstream's
typed planner/split executor. Preserve the useful existing contracts rather than
designing a second request/result system.
