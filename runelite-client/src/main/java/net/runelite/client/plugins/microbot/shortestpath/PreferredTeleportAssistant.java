package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.util.Text;

/**
 * Resolves the next teleport selected by the displayed route to its currently visible UI control.
 * The same result drives both the blue hint overlay and the opt-in destination-choice automation.
 */
final class PreferredTeleportAssistant
{
	private static final Pattern NEXUS_SHORTCUT = Pattern.compile("<col=ffffff>(.*?)</col>");
	private static final int SPELLBOOK_SPELL_LIST_CHILD_ID = 3;
	private static final int GENERIC_MENU_CHILD_ID = 3;
	private static final int WORN_ITEMS_LAST_CHILD_ID = 30;
	private static final int MAX_PLAYER_TO_PATH_DISTANCE = 64;

	private final Client client;
	private String lastAutoActivationKey;
	private Set<WorldPoint> plannedTargets = Collections.emptySet();
	private List<PohTransport> plannedPohChoices = Collections.emptyList();
	private int cachedTick = Integer.MIN_VALUE;
	private Optional<HighlightTarget> cachedTarget = Optional.empty();

	@Inject
	PreferredTeleportAssistant(Client client)
	{
		this.client = client;
	}

	Optional<HighlightTarget> resolveCurrentTarget()
	{
		int tick = client.getTickCount();
		if (tick == cachedTick)
		{
			return cachedTarget;
		}
		cachedTick = tick;
		cachedTarget = computeCurrentTarget();
		return cachedTarget;
	}

	private Optional<HighlightTarget> computeCurrentTarget()
	{
		Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
		List<WorldPoint> path = pathfinder == null ? null : pathfinder.getPath();
		WorldPoint player = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		Set<WorldPoint> targets = pathfinder == null
			? Collections.emptySet()
			: pathfinder.getTargets();
		rememberPlannedPohChoices(targets, path != null && path.size() >= 2, findUpcomingPohTransports(
			path,
			player,
			ShortestPathPlugin.getTransports()));

		// A W330 route is a two-stage chain: enter the hosted house, then use a POH facility.
		// While outside, the first edge correctly highlights the house tab. Once a facility menu is
		// visible, prefer the later POH edge and keep it across the instance-load path recalculation.
		for (PohTransport pohChoice : plannedPohChoices)
		{
			HighlightTarget choice = resolveDestinationChoice(pohChoice);
			if (choice != null)
			{
				return Optional.of(choice);
			}
		}

		Transport transport = findPreferredTransport(
			path,
			player,
			ShortestPathPlugin.getTransports(),
			ShortestPathPlugin.getUsableTeleports());
		if (transport == null)
		{
			return Optional.empty();
		}

		HighlightTarget choice = resolveDestinationChoice(transport);
		if (choice != null)
		{
			return Optional.of(choice);
		}

		HighlightTarget source = resolveTeleportSource(transport);
		return Optional.ofNullable(source);
	}

	void autoActivateVisibleChoice(boolean enabled)
	{
		if (!enabled || Rs2Walker.getCurrentTarget() != null)
		{
			lastAutoActivationKey = null;
			return;
		}

		Optional<HighlightTarget> resolved = resolveCurrentTarget();
		if (resolved.isEmpty() || !resolved.get().isDestinationChoice())
		{
			lastAutoActivationKey = null;
			return;
		}

		HighlightTarget target = resolved.get();
		String activationKey = target.activationKey();
		if (activationKey.equals(lastAutoActivationKey))
		{
			return;
		}
		lastAutoActivationKey = activationKey;

		if (target.getShortcut() != null)
		{
			Rs2Keyboard.keyPress(target.getShortcut());
			return;
		}

		Rectangle bounds = target.getWidget().getBounds();
		if (hasArea(bounds))
		{
			Rs2Widget.clickWidget(target.getWidget());
		}
	}

	void reset()
	{
		lastAutoActivationKey = null;
		plannedTargets = Collections.emptySet();
		plannedPohChoices = Collections.emptyList();
		cachedTick = Integer.MIN_VALUE;
		cachedTarget = Optional.empty();
	}

	private void rememberPlannedPohChoices(Set<WorldPoint> targets, boolean hasComputedPath,
		List<PohTransport> choices)
	{
		Set<WorldPoint> currentTargets = targets == null
			? Collections.emptySet()
			: new HashSet<>(targets);
		if (!currentTargets.equals(plannedTargets))
		{
			plannedTargets = currentTargets;
			plannedPohChoices = Collections.emptyList();
		}
		if (hasComputedPath)
		{
			plannedPohChoices = new ArrayList<>(choices);
		}
	}

	private HighlightTarget resolveDestinationChoice(Transport transport)
	{
		String destination = destinationLabel(transport);
		if (destination.isEmpty())
		{
			return null;
		}

		List<Widget> roots = new ArrayList<>(4);
		if (transport instanceof PohTransport)
		{
			Object pohTeleport = ((PohTransport) transport).getTeleport();
			if (pohTeleport instanceof JewelleryBox)
			{
				addVisible(roots, client.getWidget(InterfaceID.POH_JEWELLERY_BOX, 0));
			}
			else if (pohTeleport instanceof NexusPortal)
			{
				addVisible(roots, client.getWidget(InterfaceID.TELENEXUS_TELEPORT, 0));
			}
			addVisible(roots, client.getWidget(InterfaceID.MENU, GENERIC_MENU_CHILD_ID));
		}
		else if (transport.getDisplayInfo() != null && transport.getDisplayInfo().contains(":"))
		{
			addVisible(roots, client.getWidget(InterfaceID.MENU, GENERIC_MENU_CHILD_ID));
		}

		addVisible(roots, client.getWidget(InterfaceID.Chatmenu.OPTIONS));
		for (Widget root : roots)
		{
			Widget widget = findBestTextWidget(root, destination);
			if (widget == null || containsStrikeThrough(widget))
			{
				continue;
			}

			Character shortcut = null;
			if (transport instanceof PohTransport
				&& ((PohTransport) transport).getTeleport() instanceof NexusPortal)
			{
				Matcher matcher = NEXUS_SHORTCUT.matcher(nullToEmpty(widget.getText()));
				if (matcher.find() && !matcher.group(1).isEmpty())
				{
					shortcut = matcher.group(1).charAt(0);
				}
			}
			return new HighlightTarget(transport, widget, root, destination, true, shortcut);
		}
		return null;
	}

	private HighlightTarget resolveTeleportSource(Transport transport)
	{
		if (transport.getType() == TransportType.TELEPORTATION_SPELL)
		{
			Widget root = client.getWidget(InterfaceID.MAGIC_SPELLBOOK, SPELLBOOK_SPELL_LIST_CHILD_ID);
			if (!isVisible(root))
			{
				return null;
			}
			String spell = sourceLabel(transport);
			Widget widget = findBestTextWidget(root, spell);
			return widget == null ? null
				: new HighlightTarget(transport, widget, root, spell, false, null);
		}

		if (transport.getType() != TransportType.TELEPORTATION_ITEM)
		{
			return null;
		}

		Set<Integer> itemIds = flattenItemIds(transport);
		if (itemIds.isEmpty())
		{
			return null;
		}

		Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
		Widget item = findItemWidget(inventory, itemIds);
		if (item != null)
		{
			return new HighlightTarget(transport, item, inventory, sourceLabel(transport), false, null);
		}

		for (int child = 0; child <= WORN_ITEMS_LAST_CHILD_ID; child++)
		{
			Widget equipment = client.getWidget(InterfaceID.WORNITEMS, child);
			if (isVisible(equipment) && itemIds.contains(equipment.getItemId()))
			{
				return new HighlightTarget(transport, equipment, equipment,
					sourceLabel(transport), false, null);
			}
		}
		return null;
	}

	static Transport findPreferredTransport(List<WorldPoint> path, WorldPoint player,
		Map<WorldPoint, Set<Transport>> transports, Set<Transport> usableTeleports)
	{
		if (path == null || path.size() < 2)
		{
			return null;
		}

		int startIndex = closestPathIndex(path, player);
		for (int i = startIndex; i + 1 < path.size(); i++)
		{
			WorldPoint from = path.get(i);
			WorldPoint to = path.get(i + 1);
			if (from == null || to == null || from.distanceTo(to) <= 1)
			{
				continue;
			}

			Transport transport = transports == null ? null
				: matchTeleportByDestination(transports.get(from), to);
			if (transport == null)
			{
				transport = matchTeleportByDestination(usableTeleports, to);
			}
			if (transport != null)
			{
				return transport;
			}
		}
		return null;
	}

	static List<PohTransport> findUpcomingPohTransports(List<WorldPoint> path, WorldPoint player,
		Map<WorldPoint, Set<Transport>> transports)
	{
		if (path == null || path.size() < 2 || transports == null)
		{
			return Collections.emptyList();
		}

		List<PohTransport> choices = new ArrayList<>();
		int startIndex = closestPathIndex(path, player);
		for (int i = startIndex; i + 1 < path.size(); i++)
		{
			WorldPoint from = path.get(i);
			WorldPoint to = path.get(i + 1);
			if (from == null || to == null || from.distanceTo(to) <= 1)
			{
				continue;
			}
			Transport transport = matchTeleportByDestination(transports.get(from), to);
			if (transport instanceof PohTransport && !choices.contains(transport))
			{
				choices.add((PohTransport) transport);
			}
		}
		return choices;
	}

	static Transport matchTeleportByDestination(Set<Transport> candidates, WorldPoint destination)
	{
		if (candidates == null || destination == null)
		{
			return null;
		}
		for (Transport transport : candidates)
		{
			if (!destination.equals(transport.getDestination()))
			{
				continue;
			}
			if (transport.getType() == TransportType.POH
				|| TransportType.isTeleport(transport.getType(), transport.getOrigin()))
			{
				return transport;
			}
		}
		return null;
	}

	static String destinationLabel(Transport transport)
	{
		if (transport == null)
		{
			return "";
		}
		if (transport instanceof PohTransport
			&& ((PohTransport) transport).getTeleport() instanceof JewelleryBox)
		{
			JewelleryBox jewelleryBox = (JewelleryBox) ((PohTransport) transport).getTeleport();
			return jewelleryBox.getLocation().getDestination();
		}

		String displayInfo = nullToEmpty(transport.getDisplayInfo()).trim();
		int arrow = displayInfo.indexOf("->");
		if (arrow >= 0)
		{
			return displayInfo.substring(arrow + 2).trim().replace('_', ' ');
		}
		int colon = displayInfo.indexOf(':');
		if (colon >= 0)
		{
			return displayInfo.substring(colon + 1).trim().replace('_', ' ');
		}
		return "";
	}

	static String sourceLabel(Transport transport)
	{
		String displayInfo = transport == null ? "" : nullToEmpty(transport.getDisplayInfo()).trim();
		int colon = displayInfo.indexOf(':');
		return (colon >= 0 ? displayInfo.substring(0, colon) : displayInfo).trim();
	}

	private static int closestPathIndex(List<WorldPoint> path, WorldPoint player)
	{
		if (player == null)
		{
			return 0;
		}
		int closest = 0;
		int closestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			WorldPoint point = path.get(i);
			if (point == null || point.getPlane() != player.getPlane())
			{
				continue;
			}
			int distance = point.distanceTo2D(player);
			if (distance < closestDistance)
			{
				closest = i;
				closestDistance = distance;
			}
		}
		// POH and some quest instances expose template coordinates while the route deliberately starts
		// from a synthetic routing anchor. Never use a far-away template tile to skip the first edge.
		return closestDistance <= MAX_PLAYER_TO_PATH_DISTANCE ? closest : 0;
	}

	private static Set<Integer> flattenItemIds(Transport transport)
	{
		Set<Integer> ids = new HashSet<>();
		if (transport.getItemIdRequirements() != null)
		{
			for (Set<Integer> alternatives : transport.getItemIdRequirements())
			{
				if (alternatives != null)
				{
					ids.addAll(alternatives);
				}
			}
		}
		return ids;
	}

	private static Widget findItemWidget(Widget root, Set<Integer> itemIds)
	{
		if (!isVisible(root))
		{
			return null;
		}
		for (Widget widget : flattenWidgets(root))
		{
			if (isVisible(widget) && itemIds.contains(widget.getItemId()))
			{
				return widget;
			}
		}
		return null;
	}

	static Widget findBestTextWidget(Widget root, String needle)
	{
		if (!isVisible(root) || needle == null || needle.isEmpty())
		{
			return null;
		}
		String wanted = normalize(needle);
		Widget partial = null;
		for (Widget widget : flattenWidgets(root))
		{
			if (!isVisible(widget))
			{
				continue;
			}
			for (String candidate : widgetStrings(widget))
			{
				String normalized = normalize(candidate);
				if (normalized.equals(wanted))
				{
					return widget;
				}
				if (partial == null && normalized.contains(wanted))
				{
					partial = widget;
				}
			}
		}
		return partial;
	}

	private static List<Widget> flattenWidgets(Widget root)
	{
		if (root == null)
		{
			return Collections.emptyList();
		}
		List<Widget> widgets = new ArrayList<>();
		Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		collectWidgets(root, widgets, seen);
		return widgets;
	}

	private static void collectWidgets(Widget widget, List<Widget> widgets, Set<Widget> seen)
	{
		if (widget == null || !seen.add(widget))
		{
			return;
		}
		widgets.add(widget);
		Widget[][] groups = {
			widget.getChildren(),
			widget.getNestedChildren(),
			widget.getDynamicChildren(),
			widget.getStaticChildren()
		};
		for (Widget[] group : groups)
		{
			if (group == null)
			{
				continue;
			}
			for (Widget child : group)
			{
				collectWidgets(child, widgets, seen);
			}
		}
	}

	private static List<String> widgetStrings(Widget widget)
	{
		List<String> values = new ArrayList<>();
		values.add(widget.getText());
		values.add(widget.getName());
		if (widget.getActions() != null)
		{
			Collections.addAll(values, widget.getActions());
		}
		return values;
	}

	private static String normalize(String value)
	{
		return Text.removeTags(nullToEmpty(value))
			.replace('_', ' ')
			.trim()
			.toLowerCase(java.util.Locale.ROOT);
	}

	private static boolean containsStrikeThrough(Widget widget)
	{
		return nullToEmpty(widget.getText()).toLowerCase(java.util.Locale.ROOT).contains("<str>");
	}

	private static void addVisible(List<Widget> widgets, Widget widget)
	{
		if (isVisible(widget) && !widgets.contains(widget))
		{
			widgets.add(widget);
		}
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	static boolean hasArea(Rectangle bounds)
	{
		return bounds != null && bounds.width > 0 && bounds.height > 0;
	}

	private static String nullToEmpty(String value)
	{
		return value == null ? "" : value;
	}

	static final class HighlightTarget
	{
		private final Transport transport;
		private final Widget widget;
		private final Widget root;
		private final String label;
		private final boolean destinationChoice;
		private final Character shortcut;

		private HighlightTarget(Transport transport, Widget widget, Widget root, String label,
			boolean destinationChoice, Character shortcut)
		{
			this.transport = transport;
			this.widget = widget;
			this.root = root;
			this.label = label;
			this.destinationChoice = destinationChoice;
			this.shortcut = shortcut;
		}

		Transport getTransport()
		{
			return transport;
		}

		Widget getWidget()
		{
			return widget;
		}

		Widget getRoot()
		{
			return root;
		}

		String getLabel()
		{
			return label;
		}

		boolean isDestinationChoice()
		{
			return destinationChoice;
		}

		Character getShortcut()
		{
			return shortcut;
		}

		private String activationKey()
		{
			WorldPoint destination = transport.getDestination();
			return transport.getType() + ":" + destination + ":" + widget.getId() + ":"
				+ widget.getIndex() + ":" + label;
		}
	}
}
