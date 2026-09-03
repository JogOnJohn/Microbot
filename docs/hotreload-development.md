# Development Plugin Hot Reload

This is a development-only external-plugin reload loop. The client watches one
exact plugin JAR path, stops and unloads its previous generation, then loads the
rebuilt JAR through a fresh classloader. It does **not** reload client classes,
mixins, parent-loaded shared APIs, or built-in Microbot plugins.

The client must be launched from a branch containing this feature, currently
`feature/dev-plugin-hotreload` at `aea0e6abe0`; `playable/shortest-path` does
not contain the watcher.

## First-time client build and launch

Build the development client JAR once using JDK 17 or newer:

```powershell
cd C:\Users\VMAdmin2\IdeaProjects\Microbot
.\gradlew.bat :client:microbotReleaseJar --console=plain
```

Launch that JAR with the exact absolute path to the plugin JAR. The JVM property
must precede `-jar`:

```powershell
& "C:\Users\VMAdmin2\.jdks\temurin-17.0.19\bin\java.exe" `
  "-Dmicrobot.hotreload.devjar=C:\path\to\YourPlugin-<version>.jar" `
  -jar "C:\Users\VMAdmin2\IdeaProjects\Microbot\runelite-client\build\libs\microbot-2.6.20.jar"
```

The watcher is idle without `microbot.hotreload.devjar`. Its startup log should
say `Watching development plugin JAR`, and every adopted rebuild should say
`Reloaded <n> plugin(s)`.

## Validated Bizza AutoHunter profile

The following is the tested Bizza 12345 profile. Keep the AutoHunter version
stable while the client is running; a version change changes the JAR filename,
so the client must be relaunched with the new exact path.

| Item | Value |
| --- | --- |
| Client checkout | `C:\Users\VMAdmin2\IdeaProjects\Microbot` |
| Client branch | `feature/dev-plugin-hotreload` |
| Client JDK | `C:\Users\VMAdmin2\.jdks\temurin-17.0.19` |
| Hub checkout | `C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-autohunter` |
| Hub Gradle JDK | Temurin 11 |
| Watched JAR | `...\build\libs\AutoHunterPlugin-1.4.1.jar` |

```powershell
& "C:\Users\VMAdmin2\.jdks\temurin-17.0.19\bin\java.exe" `
  "-Dmicrobot.hotreload.devjar=C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-autohunter\build\libs\AutoHunterPlugin-1.4.1.jar" `
  -jar "C:\Users\VMAdmin2\IdeaProjects\Microbot\runelite-client\build\libs\microbot-2.6.20.jar"
```

## Building the watched JAR

Run the focused task once before launching the client:

```powershell
cd C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-autohunter
.\gradlew.bat AutoHunterPluginJar -PpluginList=AutoHunterPlugin --console=plain
```

For continuous rebuilds, use the same task with `--continuous`:

```powershell
.\gradlew.bat --continuous AutoHunterPluginJar -PpluginList=AutoHunterPlugin --console=plain
```

SSH is appropriate for the one-shot commands above, but not for the continuous
command. In the validated Bizza setup, SSH session teardown closed the Gradle
frontend or its standard input, so `Start-Process`/`cmd /k` detaches did not
leave a reliable watcher. A surviving Gradle daemon alone is not evidence that
continuous mode is still watching files.

Use this interactive guest scheduled task as the durable fallback instead:

```text
Task name: AutoHunter Continuous Build
User: clanker\VMAdmin2
Logon type: Interactive
Action: cmd.exe /d /k "cd /d C:\Users\VMAdmin2\IdeaProjects\Microbot-Hub-autohunter && gradlew.bat --continuous AutoHunterPluginJar -PpluginList=AutoHunterPlugin --console=plain"
```

Verify that task is running and that its session-1 Gradle process includes both
`--continuous` and `AutoHunterPluginJar`; then confirm the client log records a
reload after a source save.

## Safe loop and release boundary

1. Start the development client and the interactive continuous-build task.
2. Disable the target automation plugin before editing it.
3. Save source, then wait for the JAR timestamp/hash to change **and** the
   `HotReloadService - Reloaded` log entry.
4. Use Agent Server and `client.log` to confirm exactly one inactive instance
   before re-enabling the plugin.
5. Validate the changed behavior, then disable before the next edit.

Do not leave the normal sideloaded copy of the same plugin loaded. For
AutoHunter, the normal path is
`C:\Users\VMAdmin2\.runelite\microbot-plugins\AutoHunterPlugin.jar`; with the
client fully closed, archive that copy recoverably before the development run
and restore it only after the development client exits. Never move or replace a
JAR while a RuneLite/Microbot client has it loaded.

Hot-reloaded code is development evidence, not a release artifact. Reconcile a
proven guest change into the committed source branch, rebuild from a clean
worktree, then use the normal archive/install/hash-verification release flow.
