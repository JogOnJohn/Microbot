package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

/** Bounded semantic text associated with a clicked widget or its immediate context. */
@Value
public class WidgetTextSnapshot
{
	int widgetId;
	int parentId;
	String text;
	String name;
}
