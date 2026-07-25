package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class ItemSnapshot
{
	int slot;
	int itemId;
	String name;
	int quantity;
	boolean noted;
	int linkedNoteId;
	boolean placeholder;
	int linkedPlaceholderId;
	boolean stackable;
}
