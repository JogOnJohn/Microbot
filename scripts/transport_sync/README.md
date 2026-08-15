# Transport data sync

Canonical standalone repository:
[`JogOnJohn/microbot-shortest-path-sync`](https://github.com/JogOnJohn/microbot-shortest-path-sync).
The complete maintainer workflow is documented in
[`docs/UPDATE_WORKFLOW.md`](https://github.com/JogOnJohn/microbot-shortest-path-sync/blob/main/docs/UPDATE_WORKFLOW.md).
This directory is the vendored Microbot integration snapshot used by the JVM validator and branch
history. Develop general converter, wrapper, fixtures, and reporting changes in the standalone
repository first, then deliberately vendor a reviewed version here alongside any Microbot-specific
validator or resource changes.

This directory converts a pinned `osrs-pathfinding/shortest-path-tooling` data checkout into
Microbot-compatible transport resources. It never edits the upstream checkout or the live resource
directory. Generated files and reports go under `build/transport-sync/` by default.

The upstream tooling commit, its `shortest-path` data-submodule commit, and the paired collision map
are pinned in `sync_manifest.json`. Local behavioral fixes live in `local_overrides.tsv` and are
applied after the upstream rows are normalized. A sync fails on unknown files, unknown columns,
duplicate identities, missing override targets, checkout/pin mismatches, or a collision hash mismatch.
Each staged payload includes `sync-provenance.properties`, binding the exact files to those pins.

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

Validate the exact fresh staging directory (never an implicit old `build/` directory):

```powershell
.\gradlew.bat :client:validateTransportSync `
  -PtransportSyncGeneratedDir=C:\path\to\generated `
  --console=plain
```

The validator loads the staged collision archive, checks provenance hashes, parses managed and
Microbot-only resources, and applies the blocked-endpoint ratchet before adoption.
