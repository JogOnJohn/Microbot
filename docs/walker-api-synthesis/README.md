# Walker API Synthesis Preparation

**Current work (2026-11-01):** the three review findings are fixed and rebuilt.
See [the fixed 2.6.49 candidate](FIXED-CANDIDATE-20261101.md) for current evidence
and remaining gates. The older `01f341bcb7` artifact below remains held; its passing
tests did not cover the findings in [the independent review](POST-REVIEW.md).

Prepared 2026-10-31. The inventory checkpoint is complete; isolated implementation
has now begun under the acceptance contract. No promotion or client lifecycle changes.

**Completed offline 2026-11-01:** the selected implementation stages, regression,
converter, query benchmark and exact-artifact Hub compatibility gates passed.
A versioned test JAR is ready. The optional executor replacement was deliberately
not selected. See [final evidence and test handoff](FINAL-HANDOFF.md) and the
[explicit API dispositions](API-DISPOSITIONS.md). Live validation and promotion
remain separate, unauthorized operations; the running client is untouched.

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
