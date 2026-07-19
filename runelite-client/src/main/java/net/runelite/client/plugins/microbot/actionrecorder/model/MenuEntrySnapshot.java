package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class MenuEntrySnapshot
{
	String option;
	String target;
	String action;
	int identifier;
	int param0;
	int param1;
	int itemId;
	int itemOp;
	int worldViewId;
	Integer widgetId;
	String widgetText;
	boolean itemOperation;
	boolean forceLeftClick;
	boolean deprioritized;
}
