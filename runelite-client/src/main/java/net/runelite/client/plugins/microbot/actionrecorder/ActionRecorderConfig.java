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
		keyName = "captureGameTicks",
		name = "Game tick snapshots",
		description = "Capture player state and location once per game tick",
		position = 0,
		section = captureSection
	)
	default boolean captureGameTicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureVarbits",
		name = "Varbit changes",
		description = "Capture varp and varbit changes for later state discovery",
		position = 1,
		section = captureSection
	)
	default boolean captureVarbits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureGameMessages",
		name = "Game messages",
		description = "Capture system and dialogue messages while excluding player chat",
		position = 2,
		section = captureSection
	)
	default boolean captureGameMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nearbyObjectRadius",
		name = "Object radius",
		description = "Maximum distance for recording spawned or despawned game objects",
		position = 3,
		section = captureSection
	)
	@Range(min = 1, max = 32)
	default int nearbyObjectRadius()
	{
		return 16;
	}
}
