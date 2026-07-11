# DevTools copy-to-clipboard tooling

The RuneLite devtools overlays are canvas-rendered and can't be selected with the mouse.
These additions let you copy the data as text instead of transcribing it from screenshots.
All of them live in the Developer Tools side panel (`net.runelite.client.plugins.devtools`).

## Copy Location (button)

Next to the **Location** toggle there is a **Copy Location** button. It copies the same block
the Location overlay renders, as plain text:

```
Local: 6208, 6720
World: 2953, 3224, 0
Region: 25, 24, 11825
Scene: 48, 52
Chunk 6,6: 0 2952 3224
Base: 2905, 3172
Map regions: 46, 49 (11825)
```

Inside an instance the block starts with `Instance` and the world/region coordinates are the
instance-translated (template) coordinates — the ones you want for transports.tsv.

## Grid Capture (toggle)

For defining teleport landing areas and transport rectangles:

1. Enable **Grid Capture** in the devtools panel.
2. **Shift + right-click** a tile → *Set grid corner 1*.
3. Shift + right-click the opposite corner → *Set grid corner 2*.

When the second corner is set, the rectangle is copied to the clipboard as:

```
2953,3224,0 -> 2956,3226,0
2953,3224,0
2954,3224,0
...
```

First line is the normalized bounds (`minX,minY,plane -> maxX,maxY,plane`), followed by one
line per tile. The selection renders as a cyan rectangle in the world. Corners are
instance-translated. *Clear grid corners* is available on the same shift right-click menu;
setting a new corner after a completed rectangle starts a fresh selection.

## Inventory Inspector: copy item IDs

In the Inventory Inspector's item grid (added/removed/current), right-click any item:

- **Copy item ID** — just the numeric ID.
- **Copy item info** — `Name id=... qty=... slot=...`.

## Widget Inspector: copy widget data

- **Copy** button (bottom bar) — copies the currently selected widget.
- Right-click a node in the widget tree → **Copy widget info**.

Copied text includes the identifier (`group.child[index]` + InterfaceID name when known),
packed id, bounds, hidden flag, text, name, actions, item id/quantity and sprite id.

## Shell (existing button — investigated)

The **Shell** button opens a full **JShell REPL** (module `runelite-jshell`, wired up in
`ShellFrame`) bound to the Microbot injector. Its prelude (`prelude.jsh`) pre-imports
`net.runelite.api.*`, gameval IDs, and most Microbot utils (`Rs2Player`, `Rs2Walker`,
`Rs2Inventory`, `Rs2GameObject`, `Rs2Bank`, ...) and predefines:

- `client`, `clientThread`, `configManager` — injected instances
- `log` — logger that echoes to the shell console
- `inject(Class)` — pull anything else from the injector
- `subscribe(EventClass.class, ev -> ...)` — eventbus subscription, auto-cleaned per run
- `cleanup(Runnable)` — register cleanup run before the next execution

Example — print the player's instance-translated position:

```java
clientThread.invoke(() -> log.info("{}",
    WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation())));
```

It has autocomplete (Ctrl+Space) and is the best ad-hoc scripting/inspection surface in the
client — anything not covered by the copy buttons above can be extracted here. Note: each Run
drops previous snippets and re-evaluates the editor content.

## Agent server

`http://127.0.0.1:8081/state` already returns the player position for agents; the clipboard
tooling above is aimed at manual bug-report workflows.
