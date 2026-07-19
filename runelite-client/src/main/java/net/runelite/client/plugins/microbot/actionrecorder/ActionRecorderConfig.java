package net.runelite.client.plugins.microbot.actionrecorder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ActionRecorderConfig.CONFIG_GROUP)
public interface ActionRecorderConfig extends Config
{
	String CONFIG_GROUP = "actionRecorder";

	@ConfigSection(
		name = "Session",
		description = "Operator recording session controls",
		position = 0
	)
	String sessionSection = "session";

	@ConfigSection(
		name = "Capture",
		description = "Structured event categories to capture",
		position = 1
	)
	String captureSection = "capture";

	@ConfigSection(
		name = "Output",
		description = "Recording durability and output behavior",
		position = 2
	)
	String outputSection = "output";

	@ConfigItem(
		keyName = "recordingEnabled",
		name = "Recording enabled",
		description = "Start or stop a structured recording session",
		position = 0,
		section = sessionSection
	)
	default boolean recordingEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sessionName",
		name = "Session name",
		description = "Short operator-facing name for the recording",
		position = 1,
		section = sessionSection
	)
	default String sessionName()
	{
		return "operator-session";
	}

	@ConfigItem(
		keyName = "sessionNotes",
		name = "Session notes",
		description = "Context that should accompany the Microbot Hub handoff",
		position = 2,
		section = sessionSection
	)
	default String sessionNotes()
	{
		return "";
	}

	@ConfigItem(
		keyName = "markerLabel",
		name = "Marker label",
		description = "Label used by the Add marker button",
		position = 3,
		section = sessionSection
	)
	default String markerLabel()
	{
		return "phase";
	}

	@ConfigItem(
		keyName = "addMarker",
		name = "Add marker",
		description = "Insert the configured phase marker into the active session",
		position = 4,
		section = sessionSection
	)
	default ConfigButton addMarker()
	{
		return new ConfigButton();
	}

	@ConfigItem(
		keyName = "openRecordingsFolder",
		name = "Open recordings folder",
		description = "Open the folder containing Action Recorder session bundles",
		position = 5,
		section = sessionSection
	)
	default ConfigButton openRecordingsFolder()
	{
		return new ConfigButton();
	}

	@ConfigItem(
		keyName = "captureInteractions",
		name = "Interactions",
		description = "Capture clicked menu entries, targets, canvas coordinates, and player state",
		position = 0,
		section = captureSection
	)
	default boolean captureInteractions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGameTicks",
		name = "Game tick snapshots",
		description = "Capture periodic player state and location snapshots",
		position = 1,
		section = captureSection
	)
	default boolean captureGameTicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameTickInterval",
		name = "Game tick interval",
		description = "Record one game tick snapshot every N ticks",
		position = 2,
		section = captureSection
	)
	@Range(min = 1, max = 20)
	default int gameTickInterval()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "captureInventory",
		name = "Inventory changes",
		description = "Capture inventory deltas and complete after-state snapshots",
		position = 3,
		section = captureSection
	)
	default boolean captureInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureEquipment",
		name = "Equipment changes",
		description = "Capture equipment deltas and complete after-state snapshots",
		position = 4,
		section = captureSection
	)
	default boolean captureEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureBank",
		name = "Bank changes",
		description = "Capture bank deltas after a count-only baseline; never export the full bank",
		position = 5,
		section = captureSection
	)
	default boolean captureBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureAnimations",
		name = "Animation changes",
		description = "Capture local-player animation and pose-animation changes",
		position = 6,
		section = captureSection
	)
	default boolean captureAnimations()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGraphics",
		name = "Graphic changes",
		description = "Capture local-player spot-animation details such as teleport graphics",
		position = 7,
		section = captureSection
	)
	default boolean captureGraphics()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureWidgets",
		name = "Widget lifecycle",
		description = "Capture widget group load and close events",
		position = 8,
		section = captureSection
	)
	default boolean captureWidgets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureVarbits",
		name = "Varbit changes",
		description = "Capture varp and varbit changes for later state discovery",
		position = 9,
		section = captureSection
	)
	default boolean captureVarbits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureStats",
		name = "Stat changes",
		description = "Capture XP, real level, and boosted level changes",
		position = 10,
		section = captureSection
	)
	default boolean captureStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGameMessages",
		name = "Game messages",
		description = "Capture system and dialogue messages while excluding player chat",
		position = 11,
		section = captureSection
	)
	default boolean captureGameMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGameState",
		name = "Game state changes",
		description = "Capture login, loading, hopping, and connection state transitions",
		position = 12,
		section = captureSection
	)
	default boolean captureGameState()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGameObjects",
		name = "Nearby object changes",
		description = "Capture nearby game-object spawn and despawn evidence",
		position = 13,
		section = captureSection
	)
	default boolean captureGameObjects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "actionableObjectsOnly",
		name = "Actionable objects only",
		description = "Exclude scenery without actions, such as vegetation; transformed object definitions are resolved first",
		position = 14,
		section = captureSection
	)
	default boolean actionableObjectsOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGroundItems",
		name = "Ground-item changes",
		description = "Capture nearby ground-item spawn, despawn, and quantity changes",
		position = 15,
		section = captureSection
	)
	default boolean captureGroundItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ownedGroundItemsOnly",
		name = "Owned ground items only",
		description = "Exclude unrelated public ground items and static item spawns",
		position = 16,
		section = captureSection
	)
	default boolean ownedGroundItemsOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nearbyObjectRadius",
		name = "Nearby capture radius",
		description = "Maximum same-plane distance for recording objects and ground items",
		position = 17,
		section = captureSection
	)
	@Range(min = 1, max = 32)
	default int nearbyObjectRadius()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "flushEveryRecords",
		name = "Flush every N records",
		description = "Flush JSONL to disk after this many records; lower values improve crash durability at a small I/O cost",
		position = 0,
		section = outputSection
	)
	@Range(min = 1, max = 250)
	default int flushEveryRecords()
	{
		return 25;
	}
}
