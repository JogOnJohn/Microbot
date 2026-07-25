package net.runelite.client.plugins.microbot.actionrecorder;

import java.awt.event.KeyEvent;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.actionrecorder.model.KeyboardCaptureMode;
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
		assertEquals(null, ActionRecorderCapture.normalizedTarget(
			MenuAction.WALK, "Follow Billy", value -> value));
		assertEquals("<player-target>", ActionRecorderCapture.normalizedTarget(
			MenuAction.PLAYER_FIRST_OPTION, "Billy", value -> value));
		assertTrue(ActionRecorderCapture.usesSceneCoordinates(MenuAction.GAME_OBJECT_FIRST_OPTION));
		assertTrue(ActionRecorderCapture.usesSceneCoordinates(MenuAction.GROUND_ITEM_FIRST_OPTION));
	}

	@Test
	public void suppressesOnlyApprovedClockVariables()
	{
		assertTrue(ActionRecorderCapture.isClockNoise(VarPlayerID.MAP_CLOCK, -1));
		assertTrue(ActionRecorderCapture.isClockNoise(-1, VarbitID.DATE_MILLISECONDS_PAST_MINUTE));
		assertTrue(ActionRecorderCapture.isClockNoise(-1, VarbitID.DATE_SECONDS_PAST_MINUTE));
		assertFalse(ActionRecorderCapture.isClockNoise(-1, 12393));
	}

	@Test
	public void keyboardAllowlistUsesSemanticKeyNames()
	{
		assertTrue(ActionRecorderCapture.shouldCaptureKey(
			KeyboardCaptureMode.ALLOWLIST, "W, Space, F1", KeyEvent.VK_W));
		assertTrue(ActionRecorderCapture.shouldCaptureKey(
			KeyboardCaptureMode.ALLOWLIST, "W, Space, F1", KeyEvent.VK_SPACE));
		assertFalse(ActionRecorderCapture.shouldCaptureKey(
			KeyboardCaptureMode.ALLOWLIST, "W, Space, F1", KeyEvent.VK_Q));
		assertTrue(ActionRecorderCapture.shouldCaptureKey(
			KeyboardCaptureMode.ALL_KEYS, "", KeyEvent.VK_Q));
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
