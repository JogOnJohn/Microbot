package net.runelite.client.plugins.microbot.actionrecorder.model;

import java.util.List;
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
	Integer widgetParentId;
	String widgetText;
	String widgetName;
	String widgetSemanticText;
	String[] widgetActions;
	List<WidgetTextSnapshot> widgetContext;
	boolean itemOperation;
	boolean forceLeftClick;
	boolean deprioritized;
}
