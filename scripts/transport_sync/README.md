# Transport data sync

This directory converts a pinned `osrs-pathfinding/shortest-path-tooling` data checkout into
Microbot-compatible transport resources. It never edits the upstream checkout or the live resource
directory. Generated files and reports go under `build/transport-sync/` by default.

The upstream tooling commit and its `shortest-path` data-submodule commit are pinned in
`sync_manifest.json`. Local behavioral fixes live in `local_overrides.tsv` and are applied after the
upstream rows are normalized. A sync fails on unknown files, unknown columns, duplicate identities,
missing override targets, or a checkout whose commit does not match the manifest.

From the repository root:

```powershell
python scripts/transport_sync/sync.py `
  --upstream-root C:\path\to\shortest-path-tooling
```

Review:

- `build/transport-sync/report/summary.md`
- `build/transport-sync/report/semantic-diff.json`
- `build/transport-sync/generated/`

The generated resources are staging artifacts. Copy or adopt one reviewed category at a time; do
not replace the classpath catalog wholesale.
