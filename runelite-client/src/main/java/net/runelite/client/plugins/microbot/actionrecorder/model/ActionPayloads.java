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
		CaptureSettingsSnapshot captureSettings;
	}

	@Value
	public static class SessionEnd
	{
		String reason;
		long stoppedAtEpochMs;
		long acceptedObservationCount;
		long writtenRecordCount;
		long droppedObservationCount;
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
		long interactionId;
		MenuEntrySnapshot menuEntry;
		int canvasX;
		int canvasY;
		LocationSnapshot targetLocation;
		ObjectTargetSnapshot objectTarget;
		PlayerSnapshot player;
	}

	@Value
	public static class WalkDestination
	{
		long interactionId;
		String resolution;
		int clientTicksElapsed;
		LocationSnapshot destination;
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
		Integer resolvedObjectId;
		String name;
		LocationSnapshot objectLocation;
		String[] actions;
	}

	@Value
	public static class ObjectContextSnapshot
	{
		String change;
		long observedAtEpochMs;
		int gameTick;
		int objectId;
		Integer resolvedObjectId;
		String name;
		LocationSnapshot objectLocation;
		String[] actions;
	}

	@Value
	public static class ObjectTargetSnapshot
	{
		int objectId;
		Integer resolvedObjectId;
		String name;
		LocationSnapshot objectLocation;
		String[] actions;
		int transformVarbitId;
		int transformVarpId;
		Integer transformValue;
		List<ObjectContextSnapshot> recentContext;
	}

	@Value
	public static class ObjectTargetState
	{
		long interactionId;
		String phase;
		int gameTicksElapsed;
		ObjectTargetSnapshot object;
	}

	@Value
	public static class GroundItemChange
	{
		String change;
		int itemId;
		String name;
		int beforeQuantity;
		int afterQuantity;
		LocationSnapshot itemLocation;
		boolean noted;
		int linkedNoteId;
		boolean placeholder;
		int linkedPlaceholderId;
		int ownership;
		boolean privateItem;
		int visibleTime;
		int despawnTime;
	}

	@Value
	public static class KeyboardInput
	{
		String eventType;
		int keyCode;
		String keyText;
		int modifiersEx;
		String modifiersText;
		int keyLocation;
		boolean autoRepeat;
		long eventWhenEpochMs;
	}

	@Value
	public static class CameraChange
	{
		boolean baselineEstablished;
		int yaw;
		int pitch;
		int yawTarget;
		int pitchTarget;
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
		long acceptedObservationCount;
		long writtenRecordCount;
		long droppedObservationCount;
		Map<ActionRecordType, Long> eventCounts;
		List<String> operatorMarkers;
		CaptureSettingsSnapshot captureSettings;
		String eventsFile;
		String handoffFile;
	}
}
