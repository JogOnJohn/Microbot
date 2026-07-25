package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

/** Effective capture profile frozen at session start and persisted with the handoff. */
@Value
public class CaptureSettingsSnapshot
{
	boolean captureInteractions;
	boolean captureGameTicks;
	int gameTickInterval;
	boolean captureInventory;
	boolean captureEquipment;
	boolean captureBank;
	boolean captureAnimations;
	boolean captureGraphics;
	boolean captureWidgets;
	boolean captureVarbits;
	boolean captureStats;
	boolean captureGameMessages;
	boolean captureGameState;
	boolean includeClockVariables;
	ObjectCaptureMode objectCaptureMode;
	boolean captureGroundItems;
	boolean ownedGroundItemsOnly;
	int nearbyObjectRadius;
	boolean captureKeyboardContext;
	KeyboardCaptureMode keyboardCaptureMode;
	String keyboardAllowlist;
	boolean captureCameraChanges;
	int flushEveryRecords;
}
