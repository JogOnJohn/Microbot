# Development Plugin Hot Reload

This feature is a local development aid. It reloads one external plugin JAR with
a fresh classloader after that JAR has been rebuilt. It does not reload client
classes, mixins, shared API classes, or built-in Microbot plugins.

## Starting the client

Start the development client once with the absolute path to the external plugin
JAR to watch:

```text
-Dmicrobot.hotreload.devjar=C:\path\to\your-plugin.jar
```

The built-in **Microbot Hot Reload** plugin starts automatically but is inert
unless that property is set. A stable replacement JAR causes its old plugin
generation to stop, unregister, unload, and be replaced by a fresh classloader.

## Safety rules

- Use only for an allowlisted development plugin JAR.
- Stop automation scripts before rebuilding their JAR. A plugin must release
  executors, overlays, event subscriptions, and other resources in `shutDown`.
- Build or copy to a staging filename and atomically replace the watched JAR
  where possible. The watcher waits for two matching size/timestamp samples,
  but staging avoids partial JAR reads.
- A failed replacement restores the prior in-memory plugin generation; it does
  not reload the client or change the protected playable branch.
