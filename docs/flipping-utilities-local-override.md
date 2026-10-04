# Flipping Utilities local override

## Source and build

This is the external RuneLite Flipping Utilities plugin, separate from the Microbot Hub GE Flipper.
The RuneLite Plugin Hub manifest inspected on 5 October 2026 pins
`https://github.com/Flipping-Utilities/rl-plugin.git` at
`4420ad641bad2c9505dd979b138e716654db3436`. The local fix is based on that revision;
later upstream packaging changes were not imported.

`flipping-utilities-startup.patch` reproduces local source commit
`d3899bb581565bcd9b54a22a2a2e7fbe75f0fce3`. Apply it with `git am` in a checkout
of the pinned upstream revision, then build:

```powershell
.\gradlew.bat test jar "-PmicrobotClientPath=<absolute path to microbot-2.6.28.jar>" --console=plain
```

Compilation uses the exact prepared Microbot client JAR rather than a floating
RuneLite dependency. Tests use Java 11, matching the plugin's target; running its
existing Gson/Instant persistence tests directly on Java 17 requires module
access that their fixture does not configure. All 60 tests pass with Java 11,
including the new early-varbit regression and existing long-price tests.

Built plugin SHA-256:
`F10171BF89CE4689E84EC9472EEA1803B768016EED99F8FB91AA340E25540B6B`.
Prepared client SHA-256:
`4DA94BD1D2C9FFB49178B7C7657B573027041336C28D8DFB4AC7CC0F1C6C5038`
(binary built from client source `fcb9c374c5`).

## Fix

The UI handler registered with the event bus inside its constructor, before
FlippingPlugin created FlippingPanel. Varbit events arriving on the client
thread could dereference that missing panel during EDT startup. Registration
now happens after all panels exist. An early-varbit guard also handles direct
calls before initialization. Shutdown unregisters the UI handler and slot
drawer so restarting the plugin does not retain their old subscriptions.

## Bizza installation and validation

The built JAR is installed as
`C:\Users\VMAdmin2\.runelite\microbot-plugins\FlippingPlugin.jar`.
The `flipping-utilities` entry was removed from RuneLite's `externalPlugins`
configuration to prevent the managed Plugin Hub copy loading alongside it.
The original JAR and configuration are archived in
`C:\Users\VMAdmin2\operator-work\output\archive\flipping-lifecycle-20261005`.
Do not reinstall the managed copy while this override is present.

Live validation confirmed plugin loading, successful startup, and a stop/start
cycle without the prior GameUiChangesHandler exception. The visible client is
in desktop session 1. Agility resumed and completed a Rellekka lap. This does
not validate actual GE trading or every offer-editor action.

To return to the managed plugin, close the client, archive the override outside
`microbot-plugins`, restore the saved `externalPlugins` value (preserving any
subsequent plugin-list edits), and relaunch. The custom JAR hash warning is
expected; its installed SHA-256 was verified against the built artifact.

## Inventory Setups follow-up

The upstream v1.26 update, existing-instance public module, and missing-layout
filter fix remain integrated. Its old overlapping custom bank icon is removed;
use the setup-panel filter control or native Show worn items menu.
All four bank-filter regressions passed again on 5 October. The full prior
client audit also passed plugin injection and golden-route/converter checks.
Bank counter placement and actual filtered contents remain the user's planned
manual bank UI check; no bank visit was performed during this agility run.
