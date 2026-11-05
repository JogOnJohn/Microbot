# Development Plugin Reload

The custom single-JAR watcher has been retired in favor of upstream's live
plugin manager. See [Live plugins](live-plugins.md) for the supported workflow.

Remove the old `-Dmicrobot.hotreload.devjar=...` launch argument. Build the
standalone plugin outside the installed plugin directory, then use Local
Plugins to update the JAR or enable its folder watcher. Keep only one installed
JAR per plugin. Do not run a second watcher or a separate sideload of the same
plugin.

Existing normal sideload JARs remain in place. The manager keeps a runtime copy
to avoid locking their source files. Settings are retained; external enable
keys are migrated when their legacy identity is unambiguous.

Shared client APIs, the walker, and built-in plugins still require a client
rebuild and restart. Updating the client release JAR does not update a running
client or enable the folder watcher automatically.
