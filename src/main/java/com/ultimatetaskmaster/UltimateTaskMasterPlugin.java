package com.ultimatetaskmaster;

import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.ultimatetaskmaster.crowdsource.CompletionLocationStore;
import com.ultimatetaskmaster.crowdsource.CompletionRecord;
import com.ultimatetaskmaster.crowdsource.LocalCompletionStore;
import com.ultimatetaskmaster.crowdsource.TaskLocationResolver;
import com.ultimatetaskmaster.data.NearbyTask;
import com.ultimatetaskmaster.data.SpatialTaskQuery;
import com.ultimatetaskmaster.data.StaticTaskDataProvider;
import com.ultimatetaskmaster.data.TaskDataProvider;
import com.ultimatetaskmaster.detection.TaskCompletionEvent;
import com.ultimatetaskmaster.detection.TaskCompletionListener;
import com.ultimatetaskmaster.overlay.NearbyTaskMinimapOverlay;
import com.ultimatetaskmaster.overlay.NearbyTaskWorldOverlay;
import com.ultimatetaskmaster.panel.UltimateTaskMasterPanel;
import com.ultimatetaskmaster.worldmap.TaskWorldMapPoint;

/**
 * Ultimate Task Master — main plugin entry point.
 *
 * Feature 1: "What's Near Me?" — spatial query for nearby tasks using crowdsourced
 * completion locations. Players complete tasks -> we record their position -> that builds
 * a heatmap of where tasks are typically done.
 *
 * Detection flow:
 *   ChatMessage -> TaskCompletionListener -> TaskCompletionEvent -> Plugin.onTaskCompletionEvent()
 *     -> CompletionLocationStore.save() -> TaskLocationResolver cache invalidated
 *     -> auto-refresh nearby query -> overlays + panel update immediately
 *
 * Lifecycle follows the standard RuneLite pattern (see docs/plugin-api/lifecycle.md):
 * - startUp():  register overlays, panel, load data, wire detection
 * - shutDown(): unregister EVERYTHING registered in startUp()
 */
@Slf4j
@PluginDescriptor(
	name = "Ultimate Task Master",
	description = "Find nearby tasks, manage task lists, and plan efficient routes",
	tags = {"tasks", "goals", "tracker", "leagues", "near me"}
)
public class UltimateTaskMasterPlugin extends Plugin
{
	static final String CONFIG_GROUP = "ultimate-task-master";
	private static final String CHAT_SENDER = "UTM";

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private UltimateTaskMasterConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private TaskDataProvider taskDataProvider;

	@Inject
	private TaskLocationResolver locationResolver;

	@Inject
	private CompletionLocationStore completionStore;

	@Inject
	private TaskCompletionListener completionListener;

	@Inject
	private NearbyTaskWorldOverlay worldOverlay;

	@Inject
	private NearbyTaskMinimapOverlay minimapOverlay;

	private UltimateTaskMasterPanel panel;
	private NavigationButton navButton;

	/**
	 * The current "near me" results. Shared with overlays via getter.
	 * Empty list (never null) when no query has been run.
	 */
	@Getter
	private List<NearbyTask> nearbyTasks = Collections.emptyList();

	@Override
	public void configure(Binder binder)
	{
		binder.bind(TaskDataProvider.class).to(StaticTaskDataProvider.class);
		binder.bind(CompletionLocationStore.class).to(LocalCompletionStore.class);
	}

	@Provides
	UltimateTaskMasterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateTaskMasterConfig.class);
	}

	@Override
	protected void startUp()
	{
		// 1. Build the side panel and populate with all tasks
		panel = new UltimateTaskMasterPanel();
		panel.setOnFindNearby(this::onFindNearbyTasks);

		SwingUtilities.invokeLater(() ->
		{
			panel.setAllTasks(taskDataProvider.getTasks());
			panel.setCompletedTaskNames(getCompletedTaskNames());
		});

		// 2. Register navigation button
		final BufferedImage icon = createPlaceholderIcon();
		navButton = NavigationButton.builder()
			.tooltip("Ultimate Task Master")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// 3. Register overlays
		overlayManager.add(worldOverlay);
		overlayManager.add(minimapOverlay);

		// 4. Wire up completion detection
		completionListener.register();

		log.info("Ultimate Task Master started! {} tasks loaded, {} completion records.",
			taskDataProvider.getTasks().size(), completionStore.getRecordCount());
	}

	@Override
	protected void shutDown()
	{
		completionListener.unregister();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(worldOverlay);
		overlayManager.remove(minimapOverlay);
		clearWorldMapMarkers();
		nearbyTasks = Collections.emptyList();

		log.info("Ultimate Task Master stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			nearbyTasks = Collections.emptyList();
			clearWorldMapMarkers();
			SwingUtilities.invokeLater(() -> panel.showNotLoggedIn());
		}
	}

	/**
	 * Debug command: type  ::utmtest easy Catch a Shrimp  in chat to simulate
	 * a task completion at the player's current location.
	 *
	 * Fires a TaskCompletionEvent directly, exercising the full pipeline:
	 * detection -> storage -> resolver -> spatial query -> overlays.
	 *
	 * Usage:   ::utmtest <tier> <task name>
	 * Example: ::utmtest easy Catch a Shrimp
	 * Example: ::utmtest master Complete the Inferno
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!event.getCommand().equalsIgnoreCase("utmtest"))
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		String[] args = event.getArguments();
		if (args.length < 2)
		{
			client.addChatMessage(ChatMessageType.CONSOLE, CHAT_SENDER,
				"Usage: ::utmtest <tier> <task name>  (e.g. ::utmtest easy Catch a Shrimp)", CHAT_SENDER);
			return;
		}

		String tier = args[0].toLowerCase();
		StringBuilder taskNameBuilder = new StringBuilder(args[1]);
		for (int i = 2; i < args.length; i++)
		{
			taskNameBuilder.append(" ").append(args[i]);
		}
		String taskName = taskNameBuilder.toString();

		WorldPoint location = client.getLocalPlayer().getWorldLocation();
		TaskCompletionEvent completionEvent = new TaskCompletionEvent(
			taskName, tier, location, System.currentTimeMillis());
		eventBus.post(completionEvent);
	}

	/**
	 * Handle a detected task completion.
	 * Records the location, gives the user visual feedback in chat,
	 * updates the panel's completed-task set, and auto-refreshes overlays.
	 */
	@Subscribe
	public void onTaskCompletionEvent(TaskCompletionEvent event)
	{
		CompletionRecord record = new CompletionRecord(
			event.getTaskName(),
			event.getPlayerLocation().getX(),
			event.getPlayerLocation().getY(),
			event.getPlayerLocation().getPlane(),
			event.getTimestamp()
		);

		completionStore.save(record);
		locationResolver.invalidateCache();

		// Visual feedback in chat
		String feedback = String.format("Recorded: \"%s\" at (%d, %d) — %d record(s) total",
			event.getTaskName(),
			event.getPlayerLocation().getX(),
			event.getPlayerLocation().getY(),
			completionStore.getRecordCount());

		client.addChatMessage(ChatMessageType.CONSOLE, CHAT_SENDER, feedback, CHAT_SENDER);
		log.info("[{}] {}", CHAT_SENDER, feedback);

		// Update panel: mark task as completed + auto-refresh nearby query
		SwingUtilities.invokeLater(() -> panel.setCompletedTaskNames(getCompletedTaskNames()));
		refreshNearbyQuery();
	}

	/**
	 * Triggered by the "Find Nearby Tasks" button in the panel.
	 */
	private void onFindNearbyTasks()
	{
		// Button click arrives on Swing EDT; client API requires the game client thread.
		clientThread.invokeLater(this::refreshNearbyQuery);
	}

	/**
	 * Re-runs the spatial query using the player's current position.
	 * Updates the panel, overlays, and world map markers.
	 */
	private void refreshNearbyQuery()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		SpatialTaskQuery.SortCriteria sort = panel.getSelectedSort();

		nearbyTasks = SpatialTaskQuery.findNearby(
			taskDataProvider.getTasks(),
			playerLocation,
			config.searchRadius(),
			sort,
			locationResolver
		);

		SwingUtilities.invokeLater(() -> panel.updateResults(nearbyTasks));
		updateWorldMapMarkers();

		log.debug("Found {} nearby tasks within {} tiles of {} ({} tasks have locations)",
			nearbyTasks.size(), config.searchRadius(), playerLocation,
			taskDataProvider.getTasks().stream()
				.filter(t -> locationResolver.hasLocation(t.getName()))
				.count());
	}

	/**
	 * Build the set of task names that have at least one completion record.
	 * Used to mark tasks as "completed" in the panel (green background).
	 */
	private Set<String> getCompletedTaskNames()
	{
		return completionStore.getAllRecords().stream()
			.map(CompletionRecord::getTaskName)
			.collect(Collectors.toSet());
	}

	private void updateWorldMapMarkers()
	{
		clearWorldMapMarkers();

		if (!config.showWorldMapMarkers())
		{
			return;
		}

		for (NearbyTask nearbyTask : nearbyTasks)
		{
			WorldPoint location = nearbyTask.getResolvedLocation();
			if (location != null)
			{
				worldMapPointManager.add(new TaskWorldMapPoint(location, nearbyTask.getTask()));
			}
		}
	}

	private void clearWorldMapMarkers()
	{
		worldMapPointManager.removeIf(point -> point instanceof TaskWorldMapPoint);
	}

	/**
	 * Creates a simple "T" icon for the navigation button.
	 * TODO: Replace with a proper icon PNG resource file (src/main/resources/).
	 */
	private static BufferedImage createPlaceholderIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(255, 152, 0));
		g.fillRoundRect(1, 1, 14, 14, 4, 4);
		g.setColor(Color.WHITE);
		g.setFont(new Font("Arial", Font.BOLD, 11));
		g.drawString("T", 4, 13);
		g.dispose();
		return icon;
	}
}
