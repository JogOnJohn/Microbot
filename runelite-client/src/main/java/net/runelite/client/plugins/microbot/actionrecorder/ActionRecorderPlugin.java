package net.runelite.client.plugins.microbot.actionrecorder;

import com.google.inject.Provides;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionPayloads;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecordType;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActorSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.GraphicSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemDelta;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.LocationSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.MenuEntrySnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.PlayerSnapshot;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Action Recorder",
	description = "Capture structured operator demonstrations for Microbot Hub script development",
	tags = {"action", "recorder", "operator", "agent", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class ActionRecorderPlugin extends Plugin
{
	public static final String CONFIG_GROUP = ActionRecorderConfig.CONFIG_GROUP;
	private static final Path RECORDINGS_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("action-recordings");

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ActionRecorderConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ActionRecorderOverlay overlay;

	private final Map<Integer, ContainerState> containerStates = new HashMap<>();
	private volatile ActionRecorderSession session;
	private volatile boolean pluginActive;
	private volatile boolean initialContainerSnapshotPending;

	@Provides
	ActionRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ActionRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		pluginActive = true;
		overlayManager.add(overlay);
		if (config.recordingEnabled())
		{
			startSession(config.sessionName(), config.sessionNotes());
		}
	}

	@Override
	protected void shutDown()
	{
		pluginActive = false;
		overlayManager.remove(overlay);
		requestStop("plugin_shutdown");
		containerStates.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("recordingEnabled".equals(event.getKey()))
		{
			if (config.recordingEnabled())
			{
				startSession(config.sessionName(), config.sessionNotes());
			}
			else
			{
				requestStop("config_toggle");
			}
		}
		else if ("addMarker".equals(event.getKey()))
		{
			addMarker(config.markerLabel(), "config_button");
		}
	}

	public synchronized RecorderStatus startSession(String requestedName, String requestedNotes)
	{
		ActionRecorderSession current = session;
		if (!pluginActive)
		{
			return getRecorderStatus("Action Recorder plugin is not active");
		}
		if (current != null && !current.isStopped())
		{
			return getRecorderStatus("A recording session is already active or finishing");
		}

		String name = requestedName == null || requestedName.trim().isEmpty()
			? "operator-session"
			: requestedName.trim();
		String notes = requestedNotes == null ? "" : requestedNotes.trim();
		try
		{
			session = new ActionRecorderSession(RECORDINGS_DIR, name, notes);
			initialContainerSnapshotPending = true;
			return getRecorderStatus(null);
		}
		catch (IOException e)
		{
			log.warn("Unable to start Action Recorder session", e);
			return getRecorderStatus("Unable to create the recording directory");
		}
	}

	public synchronized RecorderStatus requestStop(String reason)
	{
		ActionRecorderSession current = session;
		if (current != null)
		{
			current.requestStop(reason);
		}
		return getRecorderStatus(null);
	}

	public boolean addMarker(String label, String notes)
	{
		String marker = label == null ? "" : label.trim();
		if (marker.isEmpty())
		{
			return false;
		}
		return clientThread.runOnClientThreadOptional(() -> record(ActionRecordType.OPERATOR_MARKER,
			new ActionPayloads.OperatorMarker(sanitizeText(marker), sanitizeText(notes)))).orElse(false);
	}

	public RecorderStatus getRecorderStatus()
	{
		return getRecorderStatus(null);
	}

	private RecorderStatus getRecorderStatus(String message)
	{
		ActionRecorderSession current = session;
		return new RecorderStatus(
			pluginActive,
			current != null && current.isAccepting(),
			current != null && !current.isAccepting() && !current.isStopped(),
			current != null && current.isStopped(),
			current != null && current.isFailed(),
			current == null ? null : current.getSessionId(),
			current == null ? null : current.getName(),
			current == null ? null : current.getOutputDirectory().toString(),
			current == null ? 0 : current.getAcceptedCount(),
			current == null ? 0 : current.getDroppedCount(),
			current == null ? 0 : current.getPendingCount(),
			message);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!isRecording())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		Widget widget = entry.getWidget();
		String target = isPlayerAction(entry.getType()) ? "<player-target>" : sanitizeText(entry.getTarget());
		MenuEntrySnapshot menu = new MenuEntrySnapshot(
			sanitizeText(entry.getOption()),
			target,
			entry.getType().name(),
			entry.getIdentifier(),
			entry.getParam0(),
			entry.getParam1(),
			entry.getItemId(),
			entry.getItemOp(),
			entry.getWorldViewId(),
			widget == null ? null : widget.getId(),
			widget == null ? null : sanitizeText(widget.getText()),
			entry.isItemOp(),
			entry.isForceLeftClick(),
			entry.isDeprioritized());
		Point mouse = client.getMouseCanvasPosition();
		record(ActionRecordType.INTERACTION, new ActionPayloads.Interaction(
			menu,
			mouse.getX(),
			mouse.getY(),
			resolveInteractionTarget(entry),
			playerSnapshot()));
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (initialContainerSnapshotPending && isRecording())
		{
			initialContainerSnapshotPending = false;
			recordInitialContainer(InventoryID.INV);
			recordInitialContainer(InventoryID.WORN);
			recordInitialContainer(InventoryID.BANK);
		}
		if (config.captureGameTicks())
		{
			record(ActionRecordType.GAME_TICK, new ActionPayloads.GameTick(playerSnapshot()));
		}
	}

	private void recordInitialContainer(int containerId)
	{
		ItemContainer itemContainer = client.getItemContainer(containerId);
		if (itemContainer == null)
		{
			return;
		}
		ContainerState current = snapshotContainer(itemContainer.getItems());
		containerStates.put(containerId, current);
		List<ItemSnapshot> after = containerId == InventoryID.BANK
			? Collections.emptyList()
			: current.getItems();
		record(ActionRecordType.CONTAINER_CHANGE, new ActionPayloads.ContainerChange(
			containerId,
			containerName(containerId),
			true,
			current.getOccupiedSlots(),
			Collections.emptyList(),
			after));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (!isRecordedContainer(containerId))
		{
			return;
		}

		ContainerState previous = containerStates.get(containerId);
		ContainerState current = snapshotContainer(event.getItemContainer().getItems());
		containerStates.put(containerId, current);
		if (!isRecording())
		{
			return;
		}

		List<ItemDelta> deltas = ContainerState.diff(previous, current);
		if (previous != null && deltas.isEmpty())
		{
			return;
		}
		List<ItemSnapshot> after = containerId == InventoryID.BANK
			? Collections.emptyList()
			: current.getItems();
		record(ActionRecordType.CONTAINER_CHANGE, new ActionPayloads.ContainerChange(
			containerId,
			containerName(containerId),
			previous == null,
			current.getOccupiedSlots(),
			deltas,
			after));
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			Player player = client.getLocalPlayer();
			record(ActionRecordType.ANIMATION_CHANGE,
				new ActionPayloads.AnimationChange(player.getAnimation(), player.getPoseAnimation()));
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			record(ActionRecordType.GRAPHIC_CHANGE,
				new ActionPayloads.GraphicChange(graphicSnapshots(client.getLocalPlayer())));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		record(ActionRecordType.WIDGET_LOADED,
			new ActionPayloads.WidgetLifecycle(event.getGroupId(), null, null));
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		record(ActionRecordType.WIDGET_CLOSED,
			new ActionPayloads.WidgetLifecycle(event.getGroupId(), event.getModalMode(), event.isUnload()));
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (config.captureVarbits())
		{
			record(ActionRecordType.VARBIT_CHANGE,
				new ActionPayloads.VarbitChange(event.getVarpId(), event.getVarbitId(), event.getValue()));
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		record(ActionRecordType.STAT_CHANGE, new ActionPayloads.StatChange(
			event.getSkill().name(), event.getXp(), event.getLevel(), event.getBoostedLevel()));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (config.captureGameMessages() && isSafeGameMessage(event.getType()))
		{
			record(ActionRecordType.CHAT_MESSAGE,
				new ActionPayloads.ChatMessage(event.getType().name(), sanitizeText(event.getMessage())));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		record(ActionRecordType.GAME_STATE_CHANGE,
			new ActionPayloads.GameStateChange(event.getGameState().name()));
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		recordGameObject("spawned", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		recordGameObject("despawned", event.getGameObject());
	}

	private void recordGameObject(String change, GameObject object)
	{
		if (!isRecording() || client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint player = client.getLocalPlayer().getWorldLocation();
		WorldPoint location = object.getWorldLocation();
		if (player.getPlane() != location.getPlane() || player.distanceTo(location) > config.nearbyObjectRadius())
		{
			return;
		}
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		record(ActionRecordType.GAME_OBJECT_CHANGE, new ActionPayloads.GameObjectChange(
			change,
			object.getId(),
			composition == null ? null : sanitizeText(composition.getName()),
			locationSnapshot(location, object.getWorldView()),
			composition == null ? null : composition.getActions()));
	}

	private ContainerState snapshotContainer(Item[] items)
	{
		List<ItemSnapshot> snapshots = new ArrayList<>();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition composition = itemManager.getItemComposition(item.getId());
			snapshots.add(new ItemSnapshot(
				slot,
				item.getId(),
				sanitizeText(composition.getMembersName()),
				item.getQuantity(),
				composition.getNote() != -1,
				composition.getLinkedNoteId(),
				composition.getPlaceholderTemplateId() != -1,
				composition.getPlaceholderId(),
				composition.isStackable()));
		}
		return new ContainerState(snapshots);
	}

	private boolean record(ActionRecordType type, Object payload)
	{
		ActionRecorderSession current = session;
		return current != null && current.offer(type, client.getTickCount(), currentLocation(), payload);
	}

	private boolean isRecording()
	{
		ActionRecorderSession current = session;
		return current != null && current.isAccepting();
	}

	private LocationSnapshot currentLocation()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : locationSnapshot(player.getWorldLocation(), player.getWorldView());
	}

	private LocationSnapshot locationSnapshot(WorldPoint worldPoint, WorldView worldView)
	{
		if (worldPoint == null)
		{
			return null;
		}
		WorldView view = worldView == null ? client.getTopLevelWorldView() : worldView;
		int sceneX = worldPoint.getX() - view.getBaseX();
		int sceneY = worldPoint.getY() - view.getBaseY();
		WorldPoint template = worldPoint;
		if (view.isInstance())
		{
			LocalPoint local = LocalPoint.fromWorld(view, worldPoint);
			if (local != null)
			{
				WorldPoint resolvedTemplate = WorldPoint.fromLocalInstance(client, local, worldPoint.getPlane());
				if (resolvedTemplate != null)
				{
					template = resolvedTemplate;
				}
			}
		}
		return new LocationSnapshot(
			worldPoint.getX(),
			worldPoint.getY(),
			worldPoint.getPlane(),
			worldPoint.getRegionID(),
			sceneX,
			sceneY,
			view.getId(),
			view.isInstance(),
			template.getX(),
			template.getY(),
			template.getPlane(),
			template.getRegionID());
	}

	private PlayerSnapshot playerSnapshot()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		LocalPoint destinationLocal = client.getLocalDestinationLocation();
		LocationSnapshot destination = destinationLocal == null
			? null
			: locationSnapshot(WorldPoint.fromLocal(client, destinationLocal), client.getTopLevelWorldView());
		return new PlayerSnapshot(
			player.getAnimation(),
			player.getPoseAnimation(),
			graphicSnapshots(player),
			player.getOrientation(),
			client.getEnergy(),
			destination,
			actorSnapshot(player.getInteracting()));
	}

	private static List<GraphicSnapshot> graphicSnapshots(Actor actor)
	{
		List<GraphicSnapshot> graphics = new ArrayList<>();
		for (ActorSpotAnim graphic : actor.getSpotAnims())
		{
			graphics.add(new GraphicSnapshot(
				graphic.getId(),
				graphic.getStartCycle(),
				graphic.getHeight(),
				graphic.getFrame(),
				graphic.getCycle()));
		}
		return graphics;
	}

	private ActorSnapshot actorSnapshot(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}
		Integer id = actor instanceof NPC ? ((NPC) actor).getId() : actor instanceof Player ? ((Player) actor).getId() : null;
		String type = actor instanceof NPC ? "NPC" : actor instanceof Player ? "PLAYER" : actor.getClass().getSimpleName();
		String name = actor instanceof Player ? "<player>" : sanitizeText(actor.getName());
		return new ActorSnapshot(type, id, name, locationSnapshot(actor.getWorldLocation(), actor.getWorldView()));
	}

	private LocationSnapshot resolveInteractionTarget(MenuEntry entry)
	{
		if (isNpcAction(entry.getType()))
		{
			WorldView topLevel = client.getTopLevelWorldView();
			if (topLevel == null)
			{
				return null;
			}
			for (NPC npc : topLevel.npcs())
			{
				if (npc.getIndex() == entry.getIdentifier())
				{
					return locationSnapshot(npc.getWorldLocation(), npc.getWorldView());
				}
			}
		}
		if (isSceneAction(entry.getType()))
		{
			WorldView view = client.getWorldView(entry.getWorldViewId());
			if (view == null)
			{
				view = client.getTopLevelWorldView();
			}
			WorldPoint target = WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane());
			return locationSnapshot(target, view);
		}
		return null;
	}

	private String sanitizeText(String value)
	{
		if (value == null)
		{
			return null;
		}
		String sanitized = Text.removeTags(value).replace('\n', ' ').replace('\r', ' ').trim();
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getName() != null && !localPlayer.getName().isEmpty())
		{
			sanitized = sanitized.replaceAll("(?i)" + Pattern.quote(localPlayer.getName()), "<local-player>");
		}
		return sanitized;
	}

	private static boolean isRecordedContainer(int containerId)
	{
		return containerId == InventoryID.INV || containerId == InventoryID.WORN || containerId == InventoryID.BANK;
	}

	private static String containerName(int containerId)
	{
		if (containerId == InventoryID.INV)
		{
			return "inventory";
		}
		if (containerId == InventoryID.WORN)
		{
			return "equipment";
		}
		if (containerId == InventoryID.BANK)
		{
			return "bank";
		}
		return "container-" + containerId;
	}

	private static boolean isSafeGameMessage(ChatMessageType type)
	{
		return type == ChatMessageType.GAMEMESSAGE
			|| type == ChatMessageType.ENGINE
			|| type == ChatMessageType.SPAM
			|| type == ChatMessageType.PLAYERRELATED
			|| type == ChatMessageType.DIALOG
			|| type == ChatMessageType.MESBOX
			|| type == ChatMessageType.LEVELUPMESSAGE;
	}

	private static boolean isPlayerAction(MenuAction action)
	{
		return action.name().contains("PLAYER");
	}

	private static boolean isNpcAction(MenuAction action)
	{
		return action.name().contains("NPC");
	}

	private static boolean isSceneAction(MenuAction action)
	{
		String name = action.name();
		return action == MenuAction.WALK
			|| name.contains("GAME_OBJECT")
			|| name.contains("GROUND_ITEM");
	}

	public static void openRecordingsFolderStatic()
	{
		try
		{
			Files.createDirectories(RECORDINGS_DIR);
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
			{
				Desktop.getDesktop().open(RECORDINGS_DIR.toFile());
			}
			else
			{
				log.warn("Desktop OPEN action is unavailable for the Action Recorder folder");
			}
		}
		catch (IOException e)
		{
			log.warn("Unable to open Action Recorder folder", e);
		}
	}

	public static List<String> listSessionDirectories()
	{
		if (!Files.isDirectory(RECORDINGS_DIR))
		{
			return Collections.emptyList();
		}
		try (java.util.stream.Stream<Path> paths = Files.list(RECORDINGS_DIR))
		{
			List<String> sessions = new ArrayList<>();
			paths.filter(Files::isDirectory)
				.sorted((left, right) -> right.getFileName().toString().compareTo(left.getFileName().toString()))
				.limit(100)
				.forEach(path -> sessions.add(path.getFileName().toString()));
			return sessions;
		}
		catch (IOException e)
		{
			log.warn("Unable to list Action Recorder sessions", e);
			return Collections.emptyList();
		}
	}

	@Value
	public static class RecorderStatus
	{
		boolean pluginActive;
		boolean recording;
		boolean stopping;
		boolean stopped;
		boolean failed;
		String sessionId;
		String sessionName;
		String outputDirectory;
		long acceptedEventCount;
		long droppedEventCount;
		int pendingEventCount;
		String message;
	}
}
