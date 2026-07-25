package net.runelite.client.plugins.microbot.actionrecorder;

import com.google.inject.Provides;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
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
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionPayloads;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecordType;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActorSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.CaptureSettingsSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.GraphicSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemDelta;
import net.runelite.client.plugins.microbot.actionrecorder.model.ItemSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.LocationSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.MenuEntrySnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.ObjectCaptureMode;
import net.runelite.client.plugins.microbot.actionrecorder.model.PlayerSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.WidgetTextSnapshot;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Action Recorder",
	description = "Capture structured operator demonstrations for Microbot Hub script development",
	tags = {"action", "recorder", "operator", "agent", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class ActionRecorderPlugin extends Plugin implements KeyListener
{
	public static final String CONFIG_GROUP = ActionRecorderConfig.CONFIG_GROUP;
	private static final Path RECORDINGS_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("action-recordings");
	private static final int MAX_PENDING_WALK_CLIENT_TICKS = 5;
	private static final int OBJECT_STATE_WATCH_TICKS = 5;
	private static final int MAX_OBJECT_CONTEXT = 512;
	private static final long OBJECT_CONTEXT_WINDOW_MS = 15_000L;
	private static final int OBJECT_CONTEXT_RADIUS = 2;

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

	@Inject
	private KeyManager keyManager;

	private final Map<Integer, ContainerState> containerStates = new HashMap<>();
	private final Deque<PendingWalk> pendingWalks = new ArrayDeque<>();
	private final List<PendingObjectWatch> pendingObjectWatches = new ArrayList<>();
	private final Deque<ActionPayloads.ObjectContextSnapshot> objectContext = new ArrayDeque<>();
	private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<>());
	private volatile ActionRecorderSession session;
	private volatile CaptureSettingsSnapshot captureSettings;
	private volatile boolean pluginActive;
	private volatile boolean initialContainerSnapshotPending;
	private long nextInteractionId;
	private CameraState previousCameraState;

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
		keyManager.registerKeyListener(this);
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
		keyManager.unregisterKeyListener(this);
		requestStop("plugin_shutdown");
		clearTransientState();
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
			CaptureSettingsSnapshot nextSettings = captureSettingsFromConfig();
			ActionRecorderSession nextSession = new ActionRecorderSession(RECORDINGS_DIR, name, notes, nextSettings);
			clearTransientState();
			nextInteractionId = 0;
			captureSettings = nextSettings;
			initialContainerSnapshotPending = true;
			session = nextSession;
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
			captureSettings,
			message);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || !settings.isCaptureInteractions())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		long interactionId = ++nextInteractionId;
		Widget widget = entry.getWidget();
		List<WidgetTextSnapshot> widgetContext = ActionRecorderCapture.widgetContext(widget, this::sanitizeText);
		String target = ActionRecorderCapture.normalizedTarget(entry.getType(), entry.getTarget(), this::sanitizeText);
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
			widget == null ? null : widget.getParentId(),
			widget == null ? null : sanitizeText(widget.getText()),
			widget == null ? null : sanitizeText(widget.getName()),
			ActionRecorderCapture.semanticWidgetText(widgetContext),
			widget == null ? null : sanitizeActions(widget.getActions()),
			widgetContext,
			entry.isItemOp(),
			entry.isForceLeftClick(),
			entry.isDeprioritized());
		Point mouse = client.getMouseCanvasPosition();
		ActionPayloads.ObjectTargetSnapshot objectTarget = snapshotInteractionObject(entry, settings, true);
		boolean accepted = record(ActionRecordType.INTERACTION, new ActionPayloads.Interaction(
			interactionId,
			menu,
			mouse.getX(),
			mouse.getY(),
			resolveInteractionTarget(entry),
			objectTarget,
			playerSnapshot()));
		if (!accepted)
		{
			return;
		}
		if (entry.getType() == MenuAction.WALK)
		{
			pendingWalks.addLast(new PendingWalk(interactionId, client.getLocalDestinationLocation()));
		}
		if (objectTarget != null)
		{
			pendingObjectWatches.add(new PendingObjectWatch(interactionId, objectTarget));
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (activeCaptureSettings() == null || pendingWalks.isEmpty())
		{
			return;
		}
		LocalPoint destination = client.getLocalDestinationLocation();
		Iterator<PendingWalk> iterator = pendingWalks.iterator();
		while (iterator.hasNext())
		{
			PendingWalk pending = iterator.next();
			pending.incrementClientTicksElapsed();
			boolean changed = destination != null && !destination.equals(pending.getBaselineDestination());
			boolean timedOut = pending.getClientTicksElapsed() >= MAX_PENDING_WALK_CLIENT_TICKS;
			if (!changed && !timedOut)
			{
				continue;
			}
			LocationSnapshot resolved = destination == null ? null : locationSnapshot(
				WorldPoint.fromLocal(client, destination), client.getWorldView(destination.getWorldView()));
			record(ActionRecordType.WALK_DESTINATION, new ActionPayloads.WalkDestination(
				pending.getInteractionId(),
				resolved == null ? "unresolved" : changed ? "destination_changed" : "destination_unchanged",
				pending.getClientTicksElapsed(),
				resolved));
			iterator.remove();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null)
		{
			return;
		}
		if (initialContainerSnapshotPending)
		{
			initialContainerSnapshotPending = false;
			if (settings.isCaptureInventory())
			{
				recordInitialContainer(InventoryID.INV);
			}
			if (settings.isCaptureEquipment())
			{
				recordInitialContainer(InventoryID.WORN);
			}
			if (settings.isCaptureBank())
			{
				recordInitialContainer(InventoryID.BANK);
			}
		}
		if (settings.isCaptureGameTicks() && client.getTickCount() % settings.getGameTickInterval() == 0)
		{
			record(ActionRecordType.GAME_TICK, new ActionPayloads.GameTick(playerSnapshot()));
		}
		captureCamera(settings);
		captureObjectWatchStates();
	}

	private void recordInitialContainer(int containerId)
	{
		if (containerStates.containsKey(containerId))
		{
			return;
		}
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
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || !isContainerCaptureEnabled(settings, containerId))
		{
			return;
		}
		ContainerState previous = containerStates.get(containerId);
		ContainerState current = snapshotContainer(event.getItemContainer().getItems());
		containerStates.put(containerId, current);

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
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		Player player = client.getLocalPlayer();
		if (settings != null && settings.isCaptureAnimations() && player != null && event.getActor() == player)
		{
			record(ActionRecordType.ANIMATION_CHANGE,
				new ActionPayloads.AnimationChange(player.getAnimation(), player.getPoseAnimation()));
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		Player player = client.getLocalPlayer();
		if (settings != null && settings.isCaptureGraphics() && player != null && event.getActor() == player)
		{
			record(ActionRecordType.GRAPHIC_CHANGE,
				new ActionPayloads.GraphicChange(graphicSnapshots(player)));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureWidgets())
		{
			record(ActionRecordType.WIDGET_LOADED,
				new ActionPayloads.WidgetLifecycle(event.getGroupId(), null, null));
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureWidgets())
		{
			record(ActionRecordType.WIDGET_CLOSED,
				new ActionPayloads.WidgetLifecycle(event.getGroupId(), event.getModalMode(), event.isUnload()));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureVarbits()
			&& (settings.isIncludeClockVariables()
				|| !ActionRecorderCapture.isClockNoise(event.getVarpId(), event.getVarbitId())))
		{
			record(ActionRecordType.VARBIT_CHANGE,
				new ActionPayloads.VarbitChange(event.getVarpId(), event.getVarbitId(), event.getValue()));
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureStats())
		{
			record(ActionRecordType.STAT_CHANGE, new ActionPayloads.StatChange(
				event.getSkill().name(), event.getXp(), event.getLevel(), event.getBoostedLevel()));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureGameMessages() && isSafeGameMessage(event.getType()))
		{
			record(ActionRecordType.CHAT_MESSAGE,
				new ActionPayloads.ChatMessage(event.getType().name(), sanitizeText(event.getMessage())));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings != null && settings.isCaptureGameState())
		{
			record(ActionRecordType.GAME_STATE_CHANGE,
				new ActionPayloads.GameStateChange(event.getGameState().name()));
		}
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

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		recordGroundItem("spawned", event.getTile(), event.getItem(), 0, event.getItem().getQuantity());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		recordGroundItem("despawned", event.getTile(), event.getItem(), event.getItem().getQuantity(), 0);
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		recordGroundItem("quantity_changed", event.getTile(), event.getItem(),
			event.getOldQuantity(), event.getNewQuantity());
	}

	private void recordGameObject(String change, GameObject object)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || settings.getObjectCaptureMode() == ObjectCaptureMode.OFF
			|| client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint player = client.getLocalPlayer().getWorldLocation();
		WorldPoint location = object.getWorldLocation();
		if (player.getPlane() != location.getPlane() || player.distanceTo(location) > settings.getNearbyObjectRadius())
		{
			return;
		}
		ObjectComposition composition = ActionRecorderCapture.resolveObjectComposition(
			client.getObjectDefinition(object.getId()));
		String[] actions = composition == null ? null : sanitizeActions(composition.getActions());
		ActionPayloads.ObjectContextSnapshot contextSnapshot = new ActionPayloads.ObjectContextSnapshot(
			change,
			System.currentTimeMillis(),
			client.getTickCount(),
			object.getId(),
			composition == null ? null : composition.getId(),
			composition == null ? null : sanitizeSemanticText(composition.getName()),
			locationSnapshot(location, object.getWorldView()),
			actions);
		bufferObjectContext(contextSnapshot);
		observeObjectWatches(contextSnapshot);
		if (settings.getObjectCaptureMode() == ObjectCaptureMode.INTERACTION_FOCUSED)
		{
			return;
		}
		if (settings.getObjectCaptureMode() == ObjectCaptureMode.ACTIONABLE_NEARBY
			&& !ActionRecorderCapture.hasActionableAction(actions))
		{
			return;
		}
		record(ActionRecordType.GAME_OBJECT_CHANGE, new ActionPayloads.GameObjectChange(
			change,
			object.getId(),
			composition == null ? null : composition.getId(),
			composition == null ? null : sanitizeSemanticText(composition.getName()),
			locationSnapshot(location, object.getWorldView()),
			actions));
	}

	private void bufferObjectContext(ActionPayloads.ObjectContextSnapshot snapshot)
	{
		objectContext.addLast(snapshot);
		long cutoff = System.currentTimeMillis() - OBJECT_CONTEXT_WINDOW_MS;
		while (!objectContext.isEmpty()
			&& (objectContext.size() > MAX_OBJECT_CONTEXT
				|| objectContext.peekFirst().getObservedAtEpochMs() < cutoff))
		{
			objectContext.removeFirst();
		}
	}

	private void observeObjectWatches(ActionPayloads.ObjectContextSnapshot snapshot)
	{
		LocationSnapshot observedLocation = snapshot.getObjectLocation();
		if (observedLocation == null)
		{
			return;
		}
		for (PendingObjectWatch watch : pendingObjectWatches)
		{
			LocationSnapshot targetLocation = watch.getBefore().getObjectLocation();
			if (targetLocation != null
				&& targetLocation.getWorldX() == observedLocation.getWorldX()
				&& targetLocation.getWorldY() == observedLocation.getWorldY()
				&& targetLocation.getPlane() == observedLocation.getPlane())
			{
				watch.observe(snapshot.getChange(), snapshot.getObjectId());
			}
		}
	}

	private ActionPayloads.ObjectTargetSnapshot snapshotInteractionObject(MenuEntry entry,
		CaptureSettingsSnapshot settings, boolean includeRecentContext)
	{
		if (settings.getObjectCaptureMode() == ObjectCaptureMode.OFF
			|| !ActionRecorderCapture.isGameObjectAction(entry.getType()))
		{
			return null;
		}
		WorldView view = client.getWorldView(entry.getWorldViewId());
		if (view == null)
		{
			view = client.getTopLevelWorldView();
		}
		if (view == null)
		{
			return null;
		}
		WorldPoint worldPoint = WorldPoint.fromScene(view, entry.getParam0(), entry.getParam1(), view.getPlane());
		return snapshotObjectTarget(entry.getIdentifier(), locationSnapshot(worldPoint, view), includeRecentContext);
	}

	private ActionPayloads.ObjectTargetSnapshot snapshotObjectTarget(int objectId,
		LocationSnapshot location, boolean includeRecentContext)
	{
		ObjectComposition base = client.getObjectDefinition(objectId);
		ObjectComposition resolved = ActionRecorderCapture.resolveObjectComposition(base);
		int transformVarbitId = base == null ? -1 : base.getVarbitId();
		int transformVarpId = base == null ? -1 : base.getVarPlayerId();
		Integer transformValue = transformVarbitId >= 0 ? client.getVarbitValue(transformVarbitId)
			: transformVarpId >= 0 ? client.getVarpValue(transformVarpId) : null;
		List<ActionPayloads.ObjectContextSnapshot> context = includeRecentContext
			? recentObjectContext(location) : Collections.emptyList();
		return new ActionPayloads.ObjectTargetSnapshot(
			objectId,
			resolved == null ? null : resolved.getId(),
			resolved == null ? null : sanitizeSemanticText(resolved.getName()),
			location,
			resolved == null ? null : sanitizeActions(resolved.getActions()),
			transformVarbitId,
			transformVarpId,
			transformValue,
			context);
	}

	private List<ActionPayloads.ObjectContextSnapshot> recentObjectContext(LocationSnapshot target)
	{
		if (target == null)
		{
			return Collections.emptyList();
		}
		long cutoff = System.currentTimeMillis() - OBJECT_CONTEXT_WINDOW_MS;
		List<ActionPayloads.ObjectContextSnapshot> context = new ArrayList<>();
		for (ActionPayloads.ObjectContextSnapshot snapshot : objectContext)
		{
			LocationSnapshot location = snapshot.getObjectLocation();
			if (snapshot.getObservedAtEpochMs() >= cutoff && location != null
				&& location.getPlane() == target.getPlane()
				&& Math.abs(location.getWorldX() - target.getWorldX()) <= OBJECT_CONTEXT_RADIUS
				&& Math.abs(location.getWorldY() - target.getWorldY()) <= OBJECT_CONTEXT_RADIUS)
			{
				context.add(snapshot);
			}
		}
		return context;
	}

	private void captureObjectWatchStates()
	{
		if (pendingObjectWatches.isEmpty())
		{
			return;
		}
		Iterator<PendingObjectWatch> iterator = pendingObjectWatches.iterator();
		while (iterator.hasNext())
		{
			PendingObjectWatch watch = iterator.next();
			watch.incrementGameTicksElapsed();
			int currentObjectId = watch.getObservedObjectId() == null
				? watch.getBefore().getObjectId() : watch.getObservedObjectId();
			ActionPayloads.ObjectTargetSnapshot current = snapshotObjectTarget(
				currentObjectId, watch.getBefore().getObjectLocation(), false);
			boolean changed = watch.getObservedChange() != null
				|| !java.util.Objects.equals(watch.getBefore().getResolvedObjectId(), current.getResolvedObjectId())
				|| !java.util.Objects.equals(watch.getBefore().getTransformValue(), current.getTransformValue());
			boolean timedOut = watch.getGameTicksElapsed() >= OBJECT_STATE_WATCH_TICKS;
			if (!changed && !timedOut)
			{
				continue;
			}
			record(ActionRecordType.OBJECT_TARGET_STATE, new ActionPayloads.ObjectTargetState(
				watch.getInteractionId(), watch.getObservedChange() != null
					? "event_" + watch.getObservedChange() : changed ? "changed" : "unchanged_timeout",
				watch.getGameTicksElapsed(), current));
			iterator.remove();
		}
	}

	private void recordGroundItem(String change, Tile tile, TileItem item, int beforeQuantity, int afterQuantity)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || !settings.isCaptureGroundItems() || client.getLocalPlayer() == null
			|| tile == null || item == null)
		{
			return;
		}
		if (settings.isOwnedGroundItemsOnly() && item.getOwnership() != TileItem.OWNERSHIP_SELF
			&& item.getOwnership() != TileItem.OWNERSHIP_GROUP)
		{
			return;
		}
		WorldPoint player = client.getLocalPlayer().getWorldLocation();
		WorldPoint location = tile.getWorldLocation();
		if (player.getPlane() != location.getPlane() || player.distanceTo(location) > settings.getNearbyObjectRadius())
		{
			return;
		}
		WorldView view = client.getWorldView(tile.getLocalLocation().getWorldView());
		ItemComposition composition = itemManager.getItemComposition(item.getId());
		record(ActionRecordType.GROUND_ITEM_CHANGE, new ActionPayloads.GroundItemChange(
			change,
			item.getId(),
			sanitizeText(composition.getMembersName()),
			beforeQuantity,
			afterQuantity,
			locationSnapshot(location, view),
			composition.getNote() != -1,
			composition.getLinkedNoteId(),
			composition.getPlaceholderTemplateId() != -1,
			composition.getPlaceholderId(),
			item.getOwnership(),
			item.isPrivate(),
			item.getVisibleTime(),
			item.getDespawnTime()));
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

	private CaptureSettingsSnapshot activeCaptureSettings()
	{
		return isRecording() ? captureSettings : null;
	}

	private CaptureSettingsSnapshot captureSettingsFromConfig()
	{
		return new CaptureSettingsSnapshot(
			config.captureInteractions(),
			config.captureGameTicks(),
			config.gameTickInterval(),
			config.captureInventory(),
			config.captureEquipment(),
			config.captureBank(),
			config.captureAnimations(),
			config.captureGraphics(),
			config.captureWidgets(),
			config.captureVarbits(),
			config.captureStats(),
			config.captureGameMessages(),
			config.captureGameState(),
			config.includeClockVariables(),
			config.objectCaptureMode(),
			config.captureGroundItems(),
			config.ownedGroundItemsOnly(),
			config.nearbyObjectRadius(),
			config.captureKeyboardContext(),
			config.keyboardCaptureMode(),
			config.keyboardAllowlist(),
			config.captureCameraChanges(),
			config.flushEveryRecords());
	}

	private void captureCamera(CaptureSettingsSnapshot settings)
	{
		if (!settings.isCaptureCameraChanges())
		{
			previousCameraState = null;
			return;
		}
		CameraState current = new CameraState(
			client.getCameraYaw(),
			client.getCameraPitch(),
			client.getCameraYawTarget(),
			client.getCameraPitchTarget());
		if (current.equals(previousCameraState))
		{
			return;
		}
		record(ActionRecordType.CAMERA_CHANGE, new ActionPayloads.CameraChange(
			previousCameraState == null,
			current.getYaw(),
			current.getPitch(),
			current.getYawTarget(),
			current.getPitchTarget()));
		previousCameraState = current;
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		boolean autoRepeat = !pressedKeys.add(event.getKeyCode());
		queueKeyboardInput("pressed", event, autoRepeat);
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		pressedKeys.remove(event.getKeyCode());
		queueKeyboardInput("released", event, false);
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
		// Deliberately excluded: typed characters may contain chat, search text, or credentials.
	}

	@Override
	public void focusLost()
	{
		pressedKeys.clear();
	}

	private void queueKeyboardInput(String eventType, KeyEvent event, boolean autoRepeat)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || !settings.isCaptureKeyboardContext()
			|| !ActionRecorderCapture.shouldCaptureKey(
				settings.getKeyboardCaptureMode(), settings.getKeyboardAllowlist(), event.getKeyCode()))
		{
			return;
		}
		int keyCode = event.getKeyCode();
		String keyText = KeyEvent.getKeyText(keyCode);
		int modifiersEx = event.getModifiersEx();
		String modifiersText = KeyEvent.getModifiersExText(modifiersEx);
		int keyLocation = event.getKeyLocation();
		long eventWhen = event.getWhen();
		clientThread.invokeLater(() -> captureKeyboardInput(eventType, keyCode, keyText,
			modifiersEx, modifiersText, keyLocation, autoRepeat, eventWhen));
	}

	private void captureKeyboardInput(String eventType, int keyCode, String keyText,
		int modifiersEx, String modifiersText, int keyLocation, boolean autoRepeat, long eventWhen)
	{
		CaptureSettingsSnapshot settings = activeCaptureSettings();
		if (settings == null || !settings.isCaptureKeyboardContext()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		record(ActionRecordType.KEYBOARD_INPUT, new ActionPayloads.KeyboardInput(
			eventType,
			keyCode,
			keyText,
			modifiersEx,
			modifiersText,
			keyLocation,
			autoRepeat,
			eventWhen));
	}

	private void clearTransientState()
	{
		containerStates.clear();
		pendingWalks.clear();
		pendingObjectWatches.clear();
		objectContext.clear();
		pressedKeys.clear();
		previousCameraState = null;
	}

	private static boolean isContainerCaptureEnabled(CaptureSettingsSnapshot settings, int containerId)
	{
		return containerId == InventoryID.INV && settings.isCaptureInventory()
			|| containerId == InventoryID.WORN && settings.isCaptureEquipment()
			|| containerId == InventoryID.BANK && settings.isCaptureBank();
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
		WorldView destinationView = destinationLocal == null ? null : client.getWorldView(destinationLocal.getWorldView());
		LocationSnapshot destination = destinationLocal == null
			? null
			: locationSnapshot(WorldPoint.fromLocal(client, destinationLocal), destinationView);
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
		WorldView view = client.getWorldView(entry.getWorldViewId());
		if (view == null)
		{
			view = client.getTopLevelWorldView();
		}
		if (isNpcAction(entry.getType()))
		{
			if (view == null)
			{
				return null;
			}
			for (NPC npc : view.npcs())
			{
				if (npc.getIndex() == entry.getIdentifier())
				{
					return locationSnapshot(npc.getWorldLocation(), npc.getWorldView());
				}
			}
		}
		if (ActionRecorderCapture.usesSceneCoordinates(entry.getType()))
		{
			if (view == null)
			{
				return null;
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

	private String sanitizeSemanticText(String value)
	{
		String sanitized = sanitizeText(value);
		return sanitized == null || sanitized.isEmpty() || "null".equalsIgnoreCase(sanitized) ? null : sanitized;
	}

	private String[] sanitizeActions(String[] actions)
	{
		if (actions == null)
		{
			return null;
		}
		String[] sanitized = actions.clone();
		for (int index = 0; index < sanitized.length; index++)
		{
			sanitized[index] = sanitizeSemanticText(sanitized[index]);
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
			|| type == ChatMessageType.DIALOG
			|| type == ChatMessageType.MESBOX
			|| type == ChatMessageType.LEVELUPMESSAGE;
	}

	private static boolean isNpcAction(MenuAction action)
	{
		return action.name().contains("NPC");
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
		long acceptedObservationCount;
		long droppedObservationCount;
		int pendingObservationCount;
		CaptureSettingsSnapshot captureSettings;
		String message;
	}

	@Value
	private static class CameraState
	{
		int yaw;
		int pitch;
		int yawTarget;
		int pitchTarget;
	}

	private static class PendingWalk
	{
		private final long interactionId;
		private final LocalPoint baselineDestination;
		private int clientTicksElapsed;

		private PendingWalk(long interactionId, LocalPoint baselineDestination)
		{
			this.interactionId = interactionId;
			this.baselineDestination = baselineDestination;
		}

		private long getInteractionId()
		{
			return interactionId;
		}

		private LocalPoint getBaselineDestination()
		{
			return baselineDestination;
		}

		private int getClientTicksElapsed()
		{
			return clientTicksElapsed;
		}

		private void incrementClientTicksElapsed()
		{
			clientTicksElapsed++;
		}
	}

	private static class PendingObjectWatch
	{
		private final long interactionId;
		private final ActionPayloads.ObjectTargetSnapshot before;
		private int gameTicksElapsed;
		private String observedChange;
		private Integer observedObjectId;

		private PendingObjectWatch(long interactionId, ActionPayloads.ObjectTargetSnapshot before)
		{
			this.interactionId = interactionId;
			this.before = before;
		}

		private long getInteractionId()
		{
			return interactionId;
		}

		private ActionPayloads.ObjectTargetSnapshot getBefore()
		{
			return before;
		}

		private int getGameTicksElapsed()
		{
			return gameTicksElapsed;
		}

		private String getObservedChange()
		{
			return observedChange;
		}

		private Integer getObservedObjectId()
		{
			return observedObjectId;
		}

		private void observe(String change, int objectId)
		{
			observedChange = change;
			observedObjectId = objectId;
		}

		private void incrementGameTicksElapsed()
		{
			gameTicksElapsed++;
		}
	}
}
