package net.runelite.client.plugins.microbot.actionrecorder.model;

import java.util.List;
import java.util.Map;
import lombok.Value;

/** Reusable payload objects written inside the common {@link ActionRecord} envelope. */
public final class ActionPayloads
{
	private ActionPayloads()
	{
	}

	@Value
	public static class SessionStart
	{
		String name;
		String notes;
		long startedAtEpochMs;
		String outputDirectory;
	}

	@Value
	public static class SessionEnd
	{
		String reason;
		long stoppedAtEpochMs;
		long acceptedEventCount;
		long droppedEventCount;
	}

	@Value
	public static class OperatorMarker
	{
		String label;
		String notes;
	}

	@Value
	public static class Interaction
	{
		MenuEntrySnapshot menuEntry;
		int canvasX;
		int canvasY;
		LocationSnapshot targetLocation;
		PlayerSnapshot player;
	}

	@Value
	public static class GameTick
	{
		PlayerSnapshot player;
	}

	@Value
	public static class ContainerChange
	{
		int containerId;
		String containerName;
		boolean baselineEstablished;
		int occupiedSlots;
		List<ItemDelta> deltas;
		List<ItemSnapshot> itemsAfter;
	}

	@Value
	public static class AnimationChange
	{
		int animationId;
		int poseAnimationId;
	}

	@Value
	public static class GraphicChange
	{
		List<GraphicSnapshot> graphics;
	}

	@Value
	public static class WidgetLifecycle
	{
		int groupId;
		Integer modalMode;
		Boolean unload;
	}

	@Value
	public static class VarbitChange
	{
		int varpId;
		int varbitId;
		int value;
	}

	@Value
	public static class StatChange
	{
		String skill;
		int xp;
		int level;
		int boostedLevel;
	}

	@Value
	public static class ChatMessage
	{
		String messageType;
		String message;
	}

	@Value
	public static class GameStateChange
	{
		String gameState;
	}

	@Value
	public static class GameObjectChange
	{
		String change;
		int objectId;
		String name;
		LocationSnapshot objectLocation;
		String[] actions;
	}

	@Value
	public static class Manifest
	{
		int schemaVersion;
		String sessionId;
		String name;
		String notes;
		long startedAtEpochMs;
		long stoppedAtEpochMs;
		long acceptedEventCount;
		long droppedEventCount;
		Map<ActionRecordType, Long> eventCounts;
		List<String> operatorMarkers;
		String eventsFile;
		String handoffFile;
	}
}
