# Action Recorder

Action Recorder is a Microbot client plugin for capturing structured operator demonstrations. Its output is an observation bundle for review and handoff to a Microbot Hub script project; it does not replay mouse coordinates or generate a client-side automation script.

## Session workflow

1. Enable **Action Recorder** in the Microbot plugin list.
2. Start a session from the plugin configuration or `POST /action-recorder/start`.
3. Add operator markers at useful phase boundaries such as `BANK_PREP`, `FALADOR_PATCH`, or `BIRDHOUSE_1`.
4. Perform the demonstration normally.
5. Stop the session and wait until `/action-recorder/status` reports `stopped: true`.
6. Hand the completed session directory to the owning Microbot Hub project for review and state-machine development.

Sessions are written under `~/.runelite/action-recordings/<timestamp>-<name>-<session>/`:

- `events.jsonl` contains the lossless ordered event stream.
- `manifest.json` contains counts, timestamps, marker order, and schema metadata.
- `handoff.md` describes the client-to-Hub boundary and candidate flow guidance.

## Record contract

Every JSONL line uses the same envelope:

```json
{
  "schemaVersion": 1,
  "sessionId": "...",
  "sequence": 42,
  "type": "INTERACTION",
  "occurredAtEpochMs": 1784412345678,
  "offsetMs": 15320,
  "gameTick": 48120,
  "location": {
    "worldX": 3052,
    "worldY": 3307,
    "plane": 0,
    "regionId": 12083,
    "sceneX": 48,
    "sceneY": 51,
    "worldViewId": 0,
    "instanced": false,
    "templateX": 3052,
    "templateY": 3307,
    "templatePlane": 0,
    "templateRegionId": 12083
  },
  "payload": {}
}
```

The client targets Java 11, so the implementation uses immutable value objects rather than the Java `record` keyword. Payloads are typed objects for session lifecycle, operator markers, interactions, game ticks, container changes, animations, graphics, widgets, varbits, stats, game messages, game state, and nearby game-object changes.

## Interaction and effect evidence

An `INTERACTION` records the complete menu entry, canvas click, player state, event location, and target location when the target can be resolved. Canvas coordinates are diagnostic evidence only.

`CONTAINER_CHANGE` records inventory, equipment, and bank effects. Inventory and equipment include the after-state by slot. Bank events establish a private baseline without exporting the entire bank, then record item deltas. Item records include the exact ID, name, quantity, noted state and linked note ID, placeholder state and linked placeholder ID.

The Hub review should correlate each interaction with subsequent location, container, animation, graphic, widget, varbit, message, and object records. Correlation is evidence for a proposed state transition, not proof that every observed action belongs in the final script.

## Operator API

The Agent Server must be enabled, authenticated normally, and the Action Recorder plugin must be active.

| Method | Path | Body | Purpose |
|--------|------|------|---------|
| `GET` | `/action-recorder/status` | - | Current plugin and session state |
| `POST` | `/action-recorder/start` | `{"name":"farm-birdhouse","notes":"normal route"}` | Start a session |
| `POST` | `/action-recorder/marker` | `{"label":"BANK_PREP","notes":"optional"}` | Add a candidate phase boundary |
| `POST` | `/action-recorder/stop` | `{"reason":"demonstration_complete"}` | Request asynchronous stop and artifact finalization |
| `GET` | `/action-recorder/sessions` | - | List local session directories, newest first |

`POST /action-recorder/stop` returns `202` because disk finalization happens on the recorder writer thread. Poll status until `stopping` becomes false and `stopped` becomes true before handing off the directory.

## Capture and privacy boundaries

- Event subscribers only take small immutable snapshots; JSONL and handoff files are written on a bounded daemon writer queue.
- Player chat is excluded. Captured system/dialogue text has the local display name replaced with `<local-player>`, and player interaction targets are replaced with `<player-target>`.
- The initial bank event establishes a count-only baseline. The recorder does not export the full bank contents.
- Nearby object spawn/despawn records are radius-limited to avoid unrelated scene noise.
- Queue saturation is reported as `droppedEventCount`; a session with dropped records should be treated as incomplete evidence.

## Microbot Hub handoff boundary

The client plugin owns capture fidelity, schema stability, artifact finalization, and operator session controls. The Hub project owns pruning accidental observations, grouping interactions into phases, choosing state entry/success/recovery conditions, and implementing the final state machine with Microbot query and interaction APIs.
