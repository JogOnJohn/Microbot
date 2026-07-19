package net.runelite.client.plugins.microbot.actionrecorder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.actionrecorder.model.WidgetTextSnapshot;

/** Small, client-thread-only capture helpers kept separate for focused regression testing. */
final class ActionRecorderCapture
{
	private static final int MAX_OBJECT_TRANSFORM_DEPTH = 4;
	private static final int MAX_WIDGET_CONTEXT_ITEMS = 8;
	private static final int MAX_WIDGET_TEXT_LENGTH = 160;

	private ActionRecorderCapture()
	{
	}

	static ObjectComposition resolveObjectComposition(ObjectComposition composition)
	{
		ObjectComposition resolved = composition;
		for (int depth = 0; depth < MAX_OBJECT_TRANSFORM_DEPTH
			&& resolved != null && resolved.getImpostorIds() != null; depth++)
		{
			ObjectComposition impostor = resolved.getImpostor();
			if (impostor == null || impostor == resolved)
			{
				break;
			}
			resolved = impostor;
		}
		return resolved;
	}

	static boolean hasActionableAction(String[] actions)
	{
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (isSemanticText(action))
			{
				return true;
			}
		}
		return false;
	}

	static boolean usesSceneCoordinates(MenuAction action)
	{
		String name = action.name();
		return name.contains("GAME_OBJECT") || name.contains("GROUND_ITEM");
	}

	static List<WidgetTextSnapshot> widgetContext(Widget widget, UnaryOperator<String> sanitizer)
	{
		List<WidgetTextSnapshot> context = new ArrayList<>();
		if (widget == null)
		{
			return context;
		}

		addWidget(context, widget, sanitizer);
		addWidgets(context, widget.getNestedChildren(), sanitizer);
		if (context.isEmpty())
		{
			Widget parent = widget.getParent();
			addWidget(context, parent, sanitizer);
			if (parent != null)
			{
				addWidgets(context, parent.getNestedChildren(), sanitizer);
			}
		}
		return context;
	}

	static String semanticWidgetText(List<WidgetTextSnapshot> context)
	{
		Set<String> values = new LinkedHashSet<>();
		for (WidgetTextSnapshot snapshot : context)
		{
			if (isSemanticText(snapshot.getText()))
			{
				values.add(snapshot.getText());
			}
			if (isSemanticText(snapshot.getName()))
			{
				values.add(snapshot.getName());
			}
		}
		return values.isEmpty() ? null : String.join(" | ", values);
	}

	private static void addWidgets(List<WidgetTextSnapshot> context, Widget[] widgets,
		UnaryOperator<String> sanitizer)
	{
		if (widgets == null)
		{
			return;
		}
		for (Widget child : widgets)
		{
			if (context.size() >= MAX_WIDGET_CONTEXT_ITEMS)
			{
				return;
			}
			addWidget(context, child, sanitizer);
		}
	}

	private static void addWidget(List<WidgetTextSnapshot> context, Widget widget,
		UnaryOperator<String> sanitizer)
	{
		if (widget == null || context.size() >= MAX_WIDGET_CONTEXT_ITEMS)
		{
			return;
		}
		String text = sanitizeWidgetText(widget.getText(), sanitizer);
		String name = sanitizeWidgetText(widget.getName(), sanitizer);
		if (isSemanticText(text) || isSemanticText(name))
		{
			context.add(new WidgetTextSnapshot(widget.getId(), widget.getParentId(), text, name));
		}
	}

	private static String sanitizeWidgetText(String value, UnaryOperator<String> sanitizer)
	{
		String sanitized = sanitizer.apply(value);
		if (!isSemanticText(sanitized))
		{
			return null;
		}
		return sanitized.length() <= MAX_WIDGET_TEXT_LENGTH
			? sanitized : sanitized.substring(0, MAX_WIDGET_TEXT_LENGTH);
	}

	private static boolean isSemanticText(String value)
	{
		return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
	}
}
