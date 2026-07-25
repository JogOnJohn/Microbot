package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class ItemDelta
{
	int itemId;
	String name;
	long beforeQuantity;
	long afterQuantity;
	long delta;
	boolean noted;
	int linkedNoteId;
	boolean placeholder;
	int linkedPlaceholderId;
}
