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
  "schemaVersion": 3,
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

Schema version 3 adds resolved object metadata, bounded semantic widget context, owned ground-item changes, and unambiguous recording totals. Capture settings are frozen when the session starts; configuration edits made during a recording apply to the next session rather than silently changing the current trace.

## Configuration

All capture categories default to enabled for a full operator demonstration. Disable categories only when a narrower trace is intentional.

| Setting | Default | Effect |
|---------|---------|--------|
| Interactions | on | Menu entry, target, canvas click, player state, and target location |
| Game tick snapshots | on | Periodic player, movement, animation, graphic, and location state |
| Game tick interval | `1` | Capture one snapshot every N game ticks |
| Inventory changes | on | Deltas plus complete inventory after-state |
| Equipment changes | on | Deltas plus complete equipment after-state |
| Bank changes | on | Deltas after a count-only baseline; never a full bank export |
| Animation changes | on | Local-player animation and pose changes |
| Graphic changes | on | Full local-player spot-animation details |
| Widget lifecycle | on | Widget group load and close events |
| Varbit changes | on | Varp/varbit ID and value changes |
| Stat changes | on | XP, real level, and boosted level changes |
| Game messages | on | System/dialogue categories only; player chat is excluded |
| Game state changes | on | Login, loading, hopping, and connection transitions |
| Nearby object changes | on | Radius-limited game-object spawn/despawn evidence |
| Actionable objects only | on | Resolve transformed definitions, then exclude scenery without actions such as vegetation |
| Ground-item changes | on | Ground-item spawn, despawn, and quantity evidence |
| Owned ground items only | on | Exclude unrelated public and static ground-item spawns |
| Nearby capture radius | `16` | Maximum same-plane distance for object and ground-item changes |
| Flush every N records | `25` | Periodic JSONL disk flush; session start and markers flush immediately |

## Interaction and effect evidence

An `INTERACTION` records the complete menu entry, canvas click, player state, event location, and target location when the target can be resolved. Widget interactions include the clicked component ID, parent ID, direct text/name/actions, and up to eight bounded text-bearing widgets from the clicked component's immediate context. `WALK` menu parameters are canvas coordinates, so the recorder deliberately leaves their interaction target location empty; subsequent tick locations and player destinations provide movement evidence. Canvas coordinates are diagnostic evidence only.

`CONTAINER_CHANGE` records inventory, equipment, and bank effects. Inventory and equipment include the after-state by slot. Bank events establish a private baseline without exporting the entire bank, then record item deltas. Item records include the exact ID, name, quantity, noted state and linked note ID, placeholder state and linked placeholder ID.

`GAME_OBJECT_CHANGE` resolves the active transformed object composition before recording the resolved ID, name, and actions. With the default actionable-only filter, decorative scenery without interactions is omitted. `GROUND_ITEM_CHANGE` records owned or group-owned item spawn, despawn, and quantity changes by default, including item metadata, ownership, visibility timing, and world location.

The Hub review should correlate each interaction with subsequent location, container, animation, graphic, widget, varbit, message, and object records. Correlation is evidence for a proposed state transition, not proof that every observed action belongs in the final script.

## Operator API

The Agent Server must be enabled, authenticated normally, and the Action Recorder plugin must be active.

| Method | Path | Body | Purpose |
|--------|------|------|---------|
| `GET` | `/action-recorder/status` | - | Current plugin and session state |
| `POST` | `/action-recorder/start` | `{"name":"farm-birdhouse","notes":"normal route"}` or empty | Start a session using the configured capture profile |
| `POST` | `/action-recorder/marker` | `{"label":"BANK_PREP","notes":"optional"}` | Add a candidate phase boundary |
| `POST` | `/action-recorder/stop` | `{"reason":"demonstration_complete"}` or empty | Request asynchronous stop and artifact finalization |
| `GET` | `/action-recorder/sessions` | - | List local session directories, newest first |

`POST /action-recorder/stop` returns `202` because disk finalization happens on the recorder writer thread. Poll status until `stopping` becomes false and `stopped` becomes true before handing off the directory.

Disabling the plugin also finalizes an active recording. That normal path writes `SESSION_END.reason` as `plugin_shutdown`; an explicit stop preserves the supplied operator reason.

## Recording totals

- `acceptedObservationCount` counts records accepted from the session start and subscribed capture events. It excludes the writer-generated `SESSION_END` record.
- `writtenRecordCount` counts every JSONL line, including `SESSION_END`.
- `droppedObservationCount` counts observations rejected because the bounded queue was full. Written sequence numbers remain contiguous when observations are dropped.
- `eventCounts` includes every written record type and therefore sums to `writtenRecordCount`.

## Capture and privacy boundaries

- Event subscribers only take small immutable snapshots; JSONL and handoff files are written on a bounded daemon writer queue.
- Event subscribers return before constructing payloads when no session or category is active, so leaving the plugin enabled but idle does not perform full tick snapshots.
- JSONL is flushed every configured record interval and immediately after session start or an operator marker, reducing loss during an abnormal client exit.
- Player chat is excluded. Captured system/dialogue text has the local display name replaced with `<local-player>`, and player interaction targets are replaced with `<player-target>`.
- The initial bank event establishes a count-only baseline. The recorder does not export the full bank contents.
- Nearby object records are actionable-only and radius-limited by default. Disable **Actionable objects only** only when non-interactive scenery changes are deliberately needed.
- Ground-item records are self/group-owned and radius-limited by default. Disable **Owned ground items only** only when public or static spawns are relevant.
- Exact repeated interactions remain in the lossless trace. Hub review should collapse retries and double-clicks according to observed effects rather than treating each click as a required script action.
- Queue saturation is reported as `droppedObservationCount`; a session with dropped observations should be treated as incomplete evidence.

## Microbot Hub handoff boundary

The client plugin owns capture fidelity, schema stability, artifact finalization, and operator session controls. The Hub project owns pruning accidental observations, grouping interactions into phases, choosing state entry/success/recovery conditions, and implementing the final state machine with Microbot query and interaction APIs.
