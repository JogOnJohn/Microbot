package net.runelite.client.plugins.microbot.actionrecorder;

import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.actionrecorder.model.WidgetTextSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionRecorderCaptureTest
{
	@Test
	public void resolvesActiveObjectCompositionBeforeFiltering()
	{
		ObjectComposition base = mock(ObjectComposition.class);
		ObjectComposition active = mock(ObjectComposition.class);
		when(base.getImpostorIds()).thenReturn(new int[]{100, 101});
		when(base.getImpostor()).thenReturn(active);

		assertSame(active, ActionRecorderCapture.resolveObjectComposition(base));
		assertFalse(ActionRecorderCapture.hasActionableAction(new String[]{null, ""}));
		assertTrue(ActionRecorderCapture.hasActionableAction(new String[]{null, "Empty"}));
	}

	@Test
	public void walkCanvasParametersAreNotTreatedAsSceneCoordinates()
	{
		assertFalse(ActionRecorderCapture.usesSceneCoordinates(MenuAction.WALK));
		assertTrue(ActionRecorderCapture.usesSceneCoordinates(MenuAction.GAME_OBJECT_FIRST_OPTION));
		assertTrue(ActionRecorderCapture.usesSceneCoordinates(MenuAction.GROUND_ITEM_FIRST_OPTION));
	}

	@Test
	public void derivesSemanticTextFromClickedWidgetChildren()
	{
		Widget button = mock(Widget.class);
		Widget label = mock(Widget.class);
		when(button.getNestedChildren()).thenReturn(new Widget[]{label});
		when(label.getId()).thenReturn(39845896);
		when(label.getParentId()).thenReturn(39845888);
		when(label.getText()).thenReturn("<col=ff981f>Fossil Island</col>");

		List<WidgetTextSnapshot> context = ActionRecorderCapture.widgetContext(button,
			value -> value == null ? null : value.replaceAll("<[^>]+>", ""));

		assertEquals(1, context.size());
		assertEquals("Fossil Island", context.get(0).getText());
		assertEquals("Fossil Island", ActionRecorderCapture.semanticWidgetText(context));
	}
}
