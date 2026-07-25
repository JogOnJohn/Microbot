package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

/**
 * Stable JSONL envelope shared by every Action Recorder event.
 *
 * <p>The client targets Java 11, so this deliberately uses an immutable Lombok
 * value object instead of the Java {@code record} language feature.</p>
 */
@Value
public class ActionRecord
{
	int schemaVersion;
	String sessionId;
	long sequence;
	ActionRecordType type;
	long occurredAtEpochMs;
	long offsetMs;
	int gameTick;
	LocationSnapshot location;
	Object payload;
}
