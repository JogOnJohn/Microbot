package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreferredTeleportAssistantTest
{
	@Test
	public void originSpecificPohChoiceWinsOverOriginlessTeleportAtSameDestination()
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		WorldPoint destination = new WorldPoint(2440, 3090, 0);
		Transport poh = new Transport(origin, destination, "JewelleryBox -> CASTLE_WARS",
			TransportType.POH, true, 6);
		Transport item = new Transport(destination, "Ring of dueling: Castle Wars",
			TransportType.TELEPORTATION_ITEM, true, 30, Set.of(Set.of(2552)));
		Map<WorldPoint, Set<Transport>> transports = new HashMap<>();
		transports.put(origin, Set.of(poh));

		Transport result = PreferredTeleportAssistant.findPreferredTransport(
			List.of(origin, destination), origin, transports, Set.of(item));

		Assert.assertSame(poh, result);
	}

	@Test
	public void completedTeleportIsSkippedWhenPlayerIsAtItsDestination()
	{
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint firstDestination = new WorldPoint(3000, 3000, 0);
		WorldPoint secondDestination = new WorldPoint(2500, 3500, 0);
		Transport first = new Transport(firstDestination, "First teleport",
			TransportType.TELEPORTATION_SPELL, true, 20, Map.of());
		Transport second = new Transport(secondDestination, "Second teleport",
			TransportType.TELEPORTATION_SPELL, true, 20, Map.of());

		Transport result = PreferredTeleportAssistant.findPreferredTransport(
			List.of(start, firstDestination, secondDestination),
			firstDestination,
			Map.of(),
			Set.of(first, second));

		Assert.assertSame(second, result);
	}

	@Test
	public void farAwayInstanceTemplateDoesNotSkipTheInitialPohTeleport()
	{
		WorldPoint routingAnchor = new WorldPoint(2954, 3224, 0);
		WorldPoint destination = new WorldPoint(3210, 3424, 0);
		WorldPoint instanceTemplate = new WorldPoint(1997, 7105, 0);
		Transport poh = new Transport(routingAnchor, destination, "NexusTeleport -> Varrock",
			TransportType.POH, true, 6);

		Transport result = PreferredTeleportAssistant.findPreferredTransport(
			List.of(routingAnchor, destination),
			instanceTemplate,
			Map.of(routingAnchor, Set.of(poh)),
			Set.of());

		Assert.assertSame(poh, result);
	}

	@Test
	public void w330ChainKeepsPohDestinationAfterHouseEntry()
	{
		WorldPoint outside = new WorldPoint(2954, 3224, 0);
		WorldPoint routingAnchor = new WorldPoint(1877, 7052, 1);
		PohTransport nexus = new PohTransport(routingAnchor, NexusPortal.VARROCK);
		Map<WorldPoint, Set<Transport>> transports = Map.of(routingAnchor, Set.of(nexus));

		List<PohTransport> choices = PreferredTeleportAssistant.findUpcomingPohTransports(
			List.of(outside, routingAnchor, nexus.getDestination()),
			new WorldPoint(2011, 7106, 1),
			transports);

		Assert.assertEquals(List.of(nexus), choices);
	}

	@Test
	public void completedPohChoiceIsNotRetainedFromEarlierPathEdge()
	{
		WorldPoint routingAnchor = new WorldPoint(1877, 7052, 1);
		PohTransport nexus = new PohTransport(routingAnchor, NexusPortal.VARROCK);

		List<PohTransport> choices = PreferredTeleportAssistant.findUpcomingPohTransports(
			List.of(routingAnchor, nexus.getDestination(), new WorldPoint(3212, 3426, 0)),
			nexus.getDestination(),
			Map.of(routingAnchor, Set.of(nexus)));

		Assert.assertTrue(choices.isEmpty());
	}

	@Test
	public void jewelleryBoxStaticChildMatchesDestinationText()
	{
		Widget root = visibleWidget("");
		Widget castleWars = visibleWidget("Castle Wars");
		when(root.getStaticChildren()).thenReturn(new Widget[]{castleWars});

		Assert.assertSame(castleWars,
			PreferredTeleportAssistant.findBestTextWidget(root, "Castle Wars"));
	}

	@Test
	public void nexusShortcutMarkupStillMatchesDestinationText()
	{
		Widget root = visibleWidget("");
		Widget varrock = visibleWidget("<col=ffffff>A</col> Varrock");
		when(root.getStaticChildren()).thenReturn(new Widget[]{varrock});

		Assert.assertSame(varrock,
			PreferredTeleportAssistant.findBestTextWidget(root, "Varrock"));
	}

	@Test
	public void labelsSplitSourceAndDestinationWithoutLeakingFormatting()
	{
		Transport item = new Transport(new WorldPoint(2440, 3090, 0),
			"Ring of dueling: Castle_Wars", TransportType.TELEPORTATION_ITEM,
			true, 30, Set.of(Set.of(2552)));

		Assert.assertEquals("Ring of dueling", PreferredTeleportAssistant.sourceLabel(item));
		Assert.assertEquals("Castle Wars", PreferredTeleportAssistant.destinationLabel(item));
	}

	@Test
	public void jewelleryBoxUsesTheVisibleDestinationText()
	{
		PohTransport transport = new PohTransport(new WorldPoint(3200, 3200, 0), JewelleryBox.CASTLE_WARS);

		Assert.assertEquals(
			JewelleryBox.CASTLE_WARS.getLocation().getDestination(),
			PreferredTeleportAssistant.destinationLabel(transport));
	}

	@Test
	public void automaticSelectionDefaultsOff()
	{
		ShortestPathConfig config = new ShortestPathConfig()
		{
		};

		Assert.assertFalse(config.autoSelectPreferredTeleport());
		Assert.assertTrue(config.highlightPreferredTeleport());
	}

	private static Widget visibleWidget(String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getText()).thenReturn(text);
		return widget;
	}
}
