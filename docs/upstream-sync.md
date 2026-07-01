# Upstream Sync

Microbot tracks two upstreams:

- `origin`: `https://github.com/JogOnJohn/Microbot.git`
- `upstream`: `https://github.com/chsami/Microbot.git`
- `runelite`: `https://github.com/runelite/runelite.git`

Use `upstream` for normal Microbot branch updates. Keep `origin/development` as the clean fork mirror of `upstream/development`. Use `local/development` for local integration work and push it to `origin/local/development` as a backup branch. Promote `local/development` to `origin/development` only when intentionally preparing PR-ready work.

Use `runelite` only when manually reviewing or integrating RuneLite base changes into a Microbot update branch. It is a vendor source, not the normal Microbot upstream.

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
