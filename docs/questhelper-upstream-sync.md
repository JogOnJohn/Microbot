# QuestHelper and Upstream Sync

Microbot tracks two upstreams:

- `origin`: `https://github.com/JogOnJohn/Microbot.git`
- `upstream`: `https://github.com/chsami/Microbot.git`
- `runelite`: `https://github.com/runelite/runelite.git`
- `questhelper`: `https://github.com/Zoinkwiz/quest-helper.git`
- `shortestpath-tooling`: `https://github.com/osrs-pathfinding/shortest-path-tooling.git`
- `shortestpath`: `https://github.com/Skretzo/shortest-path.git`

Use `upstream` for normal Microbot branch updates. Keep `origin/development` as the clean fork mirror of `upstream/development`. Use `local/development` for local integration work and push it to `origin/local/development` as a backup branch. Promote `local/development` to `origin/development` only when intentionally preparing PR-ready work.

Use `runelite` only when manually reviewing or integrating RuneLite base changes into a Microbot update branch. It is a vendor source, not the normal Microbot upstream.

The remaining remotes are read-only vendor sources. Do not merge their branches directly into a Microbot branch.

## Quest Helper Vendor Refresh

Microbot vendors Quest Helper under `runelite-client/src/main/java/net/runelite/client/plugins/microbot/questhelper`. Refresh it on a dedicated update branch created from `local/development`:

```powershell
git fetch questhelper --prune
git switch local/development
git switch -c update/questhelper-<date>
```

Record the selected upstream commit in `.quest-helper-sync`. Export `src/main/java/com/questhelper` from that commit into a temporary directory, then transform Java packages and imports from `com.questhelper` to `net.runelite.client.plugins.microbot.questhelper` before comparing it with the vendored tree.

This is a controlled, selective refresh rather than a directory replacement. Update quest, diary, miniquest, activity, bank, and player-quest content first. Preserve Microbot-owned automation and integration behavior, especially `QuestScript`, walker calls, plugin wiring, config, panels, and framework classes. Bring framework changes across only when refreshed content requires them, using small compatibility shims where practical.

Review deletions and renames explicitly. Generated helpers, leagues-only code, new panel services, and upstream APIs may depend on framework pieces that Microbot does not vendor. Do not silently include or discard them.

Validation and promotion order:

```powershell
git diff --check
.\gradlew.bat --no-daemon :client:compileJava
git add runelite-client/src/main/java/net/runelite/client/plugins/microbot/questhelper
git commit -m "Update vendored QuestHelper"
git switch local/development
git merge --ff-only update/questhelper-<date>
.\gradlew.bat --no-daemon :client:microbotReleaseJar
```

Test the `local/development` jar before merging it into `spike/shortest-path-upstream`. Preserve spike-only Quest Helper changes during that merge, including the local optimal-route placement of `THE_RED_REEF` after `TROUBLED_TORTUGANS`.

## Refresh Remotes

```powershell
git remote add upstream https://github.com/chsami/Microbot.git
git remote add runelite https://github.com/runelite/runelite.git
git fetch --all --prune
```

If either remote already exists, verify it instead:

```powershell
git remote -v
```

## Update Microbot Branches

Fast-forward the clean fork branch from Microbot upstream when there are no local-only commits:

```powershell
git fetch --all --prune
git merge-base --is-ancestor origin/development upstream/development
git push origin upstream/development:refs/heads/development
```

For the local working branch, merge the refreshed Microbot upstream, validate, and push to the backup branch:

```powershell
git switch local/development
git merge --no-edit upstream/development
./gradlew.bat :client:compileJava
git push origin local/development
```

Promote local work to the fork's clean PR branch only when that work is ready to send upstream:

```powershell
git push origin local/development:refs/heads/development
```

After promotion, open a PR from `JogOnJohn/Microbot:development` to `chsami/Microbot:development`.

## Manual RuneLite Update Flow

Do RuneLite updates on a dedicated branch unless Microbot upstream has already merged them.

```powershell
git fetch --all --prune
git switch -c update/runelite-<version-or-rev> upstream/development
git log --oneline upstream/development..runelite/master
git diff --stat upstream/development..runelite/master
git merge --no-edit runelite/master
```

Before resolving or accepting the merge, inspect changes that can affect Microbot behavior:

```powershell
git diff --name-only upstream/development..runelite/master -- runelite-api runelite-client build.gradle.kts gradle.properties
git diff upstream/development..runelite/master -- runelite-client/src/main/java/net/runelite/client/plugins runelite-api/src/main/java/net/runelite/api
```

Review priority:

- RuneLite API signature or threading changes used by `runelite-client/src/main/java/net/runelite/client/plugins/microbot`
- Plugin manager, config manager, overlay, menu, world-hop, login, and client startup changes
- Generated IDs, gamevals, quests, varbits, item variations, and cache revision updates
- Gradle, dependency, shading, signing, or Java toolchain changes

Validation starts with compile:

```powershell
./gradlew.bat :client:compileJava
```

Use broader validation when the update touches packaging, startup, or runtime behavior:

```powershell
./gradlew.bat :client:assemble
./gradlew.bat buildAll
```

Push the update branch after review and validation:

```powershell
git push -u origin update/runelite-<version-or-rev>
```
