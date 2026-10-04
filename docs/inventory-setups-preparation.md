# Inventory Setups v1.26 preparation

Updated 2026-09-27 from [dillydill123/inventory-setups](https://github.com/dillydill123/inventory-setups) revision `16f2c0a` (full SHA recorded in the operator audit), using the adapted v1.25 base `a1fbf72` for three-way component merging.

The feature remains `codex/inventory-setups-bank-filter-experiment`. Its previous tip `58225e38cd28d6bcaeaa80877697bd5852c79ed5` is preserved at `backup/inventory-setups-pre-1.26-20260927`.

- Upstream v1.26 adds plugin-message API changes, item-search fixes, current update notes, menu availability handling, and the RuneLite 1.13 Bank Tags lookup fix.
- Microbot package/class names and setup APIs remain intact. A public module supplies the existing MInventorySetupsPlugin through a provider; it does not create or reinject another instance.
- The experiment creates a missing hidden Bank Tags layout before opening a filter, recalculates existing layouts, and immediately closes its own tag when filtering is disabled.
- The old custom bank icon remains reverted (`592825723a`). Its implementation used coordinates `(5,5)` over the bank slot counters and replaced its Java callback with `ScriptID.NULL`. The native Show worn items menu and the setup-panel filter control provide the bank actions.
- The component is integrated into `codex/runelite-1.13-prep`; this is the checkout used for compilation, tests, and the staged client. Original playable/development and the active VM client are unchanged.

Offline checks cover core loading, existing-instance dependency injection, and filtering with missing/existing layouts, disabled filters, and a closed bank. Live bank layout, counters, and filtering are for the user's upcoming update session.

Operator evidence and staged artifacts: `F:\vmware boxs\MBOT\operator-work\output\audits\runelite-1.13-prep\inventory-setups-update`.
