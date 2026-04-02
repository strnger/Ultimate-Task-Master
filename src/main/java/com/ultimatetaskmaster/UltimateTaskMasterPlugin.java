package com.ultimatetaskmaster;

import com.google.inject.Binder;
import com.google.inject.Provides;
import java.time.temporal.ChronoUnit;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.task.Schedule;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.ultimatetaskmaster.data.LocationCluster;
import com.ultimatetaskmaster.data.NearbyTask;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import com.ultimatetaskmaster.data.SpatialTaskQuery;
import com.ultimatetaskmaster.data.HttpTaskDataProvider;
import com.ultimatetaskmaster.data.StaticTaskDataProvider;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskDataProvider;
import com.ultimatetaskmaster.data.TaskLocationService;
import com.ultimatetaskmaster.detection.TaskCompletionEvent;
import com.ultimatetaskmaster.detection.TaskCompletionListener;
import com.ultimatetaskmaster.overlay.NearbyTaskMinimapOverlay;
import com.ultimatetaskmaster.overlay.NearbyTaskWorldOverlay;
import com.ultimatetaskmaster.panel.UltimateTaskMasterPanel;
import com.ultimatetaskmaster.worldmap.TaskWorldMapPoint;

/**
 * Ultimate Task Master — main plugin entry point.
 *
 * Feature 1: "What's Near Me?" — spatial query for nearby tasks using location data
 * from TaskData, populated by scraper coordinate data.
 *
 * Detection flow:
 *   ChatMessage -> TaskCompletionListener -> TaskCompletionEvent -> Plugin.onTaskCompletionEvent()
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
	private TaskDataProvider taskDataProvider;

	@Inject
	private HttpTaskDataProvider httpTaskDataProvider;

	@Inject
	private TaskCompletionListener completionListener;

	@Inject
	private PlanService planService;

	@Inject
	private TaskLocationService locationService;

	@Inject
	private NearbyTaskWorldOverlay worldOverlay;

	@Inject
	private NearbyTaskMinimapOverlay minimapOverlay;

	private UltimateTaskMasterPanel panel;
	private NavigationButton navButton;

	private final java.util.Map<String, java.util.List<TaskWorldMapPoint>> shownLocationPoints = new java.util.HashMap<>();

	private Set<String> completedTaskNames = Collections.emptySet();

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

		// Wire plan services
		panel.setPlanService(planService);
		panel.setLocationService(locationService);

		panel.setOnPinCallback((taskName, cluster) -> {
			planService.pinLocation(taskName, cluster.getX(), cluster.getY());
			SwingUtilities.invokeLater(() -> {
				panel.rebuildPlanList();
				updateWorldMapMarkers();
			});
		});

		panel.setOnRemoveFromPlanCallback(taskName -> {
			planService.removeTask(taskName);
			// Also hide locations from map if shown
			java.util.List<TaskWorldMapPoint> points = shownLocationPoints.remove(taskName);
			if (points != null) {
				for (TaskWorldMapPoint p : points) {
					worldMapPointManager.remove(p);
				}
			}
			panel.setTaskLocationsShown(taskName, false);
			SwingUtilities.invokeLater(() -> {
				panel.rebuildPlanList();
				panel.rebuildAllTasksList();
				panel.rebuildNearbyList();
				updateWorldMapMarkers();
			});
		});

		panel.setOnRemoveFromPlan(task -> {
			if (task != null && planService != null) {
				planService.removeTask(task.getName());
				// Also hide locations from map if shown
				java.util.List<TaskWorldMapPoint> points = shownLocationPoints.remove(task.getName());
				if (points != null) {
					for (TaskWorldMapPoint p : points) {
						worldMapPointManager.remove(p);
					}
				}
				panel.setTaskLocationsShown(task.getName(), false);
				SwingUtilities.invokeLater(() -> {
					panel.rebuildPlanList();
					panel.rebuildAllTasksList();
					panel.rebuildNearbyList();
					updateWorldMapMarkers();
				});
			}
		});

			panel.setOnAddToPlan(this::addTaskToPlan);

		panel.setOnToggleShowLocations((taskName, show) -> {
			if (show) {
				// Find the task data
				TaskData task = null;
				for (TaskData t : taskDataProvider.getTasks()) {
					if (t.getName().equals(taskName)) {
						task = t;
						break;
					}
				}
				if (task != null && locationService != null) {
					java.util.List<LocationCluster> locations = locationService.getLocationsForTask(task.getStructId());
					if (locations != null) {
						java.util.List<TaskWorldMapPoint> points = new java.util.ArrayList<>();
						for (LocationCluster loc : locations) {
							WorldPoint wp = new WorldPoint(loc.getX(), loc.getY(), 0);
							TaskWorldMapPoint point = new TaskWorldMapPoint(wp, task, new Color(255, 140, 0), 14);
							worldMapPointManager.add(point);
							points.add(point);
						}
						shownLocationPoints.put(taskName, points);
					}
				}
			} else {
				// Remove shown points for this task
				java.util.List<TaskWorldMapPoint> points = shownLocationPoints.remove(taskName);
				if (points != null) {
					for (TaskWorldMapPoint p : points) {
						worldMapPointManager.remove(p);
					}
				}
			}
			panel.setTaskLocationsShown(taskName, show);
		});

		SwingUtilities.invokeLater(() ->
		{
			panel.setAllTasks(taskDataProvider.getTasks());
			panel.setCompletedTaskNames(loadCompletedNames());
			panel.rebuildPlanList();
		});

		// Attempt HTTP refresh in background
		new Thread(() -> {
			httpTaskDataProvider.refreshFromRemote();
			List<TaskData> httpTasks = httpTaskDataProvider.getTasks();
			if (!httpTasks.isEmpty())
			{
				SwingUtilities.invokeLater(() -> {
					panel.setAllTasks(httpTasks);
					log.info("Refreshed tasks from remote: {} tasks for {}",
						httpTasks.size(), httpTaskDataProvider.getCurrentLeagueName());
				});
			}
		}, "utm-http-refresh").start();

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

		log.info("Ultimate Task Master started! {} tasks loaded.", taskDataProvider.getTasks().size());
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

	@Subscribe
	public void onTaskCompletionEvent(TaskCompletionEvent event)
	{
		completedTaskNames = new java.util.HashSet<>(completedTaskNames);
		completedTaskNames.add(event.getTaskName());

		client.addChatMessage(ChatMessageType.CONSOLE, CHAT_SENDER,
			"Task completed: " + event.getTaskName(), CHAT_SENDER);
		log.info("Task completed: {}", event.getTaskName());

		SwingUtilities.invokeLater(() -> panel.setCompletedTaskNames(completedTaskNames));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Only add menu entries when we have shown location points on the map
		if (shownLocationPoints.isEmpty())
		{
			return;
		}

		final Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
		if (map == null)
		{
			return;
		}

		if (!map.getBounds().contains(
			client.getMouseCanvasPosition().getX(),
			client.getMouseCanvasPosition().getY()))
		{
			return;
		}

		// Get the world point the mouse is hovering over on the map
		WorldPoint mouseWorldPoint = client.getRenderOverview().getMouseLocation();
		if (mouseWorldPoint == null)
		{
			return;
		}

		// Check if mouse is near any of our shown location dots
		for (java.util.Map.Entry<String, java.util.List<TaskWorldMapPoint>> entry : shownLocationPoints.entrySet())
		{
			String taskName = entry.getKey();
			for (TaskWorldMapPoint point : entry.getValue())
			{
				WorldPoint pointWp = point.getWorldPoint();
				// Within 5 tiles of a dot
				if (mouseWorldPoint.distanceTo2D(pointWp) <= 5)
				{
					client.createMenuEntry(0)
						.setOption("Pin location")
						.setTarget(ColorUtil.wrapWithColorTag(taskName, new java.awt.Color(255, 140, 0)))
						.setType(MenuAction.RUNELITE)
						.onClick(e -> {
							pinLocationForTask(taskName, pointWp.getX(), pointWp.getY());
						});
					return; // Only add one menu entry
				}
			}
		}
	}

	private void pinLocationForTask(String taskName, int x, int y)
	{
		if (planService == null)
		{
			return;
		}
		planService.pinLocation(taskName, x, y);
		SwingUtilities.invokeLater(() -> {
			panel.rebuildPlanList();
			updateWorldMapMarkers();
		});
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
			sort
		);

		SwingUtilities.invokeLater(() -> panel.updateResults(nearbyTasks));
		updateWorldMapMarkers();
	}

	private Set<String> loadCompletedNames()
	{
		// TODO: Load from ConfigManager when persistence is implemented
		return Collections.emptySet();
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

		// Show pinned plan locations
		for (PlanItem planItem : planService.getPinnedItems())
		{
			TaskData planTask = null;
			for (TaskData t : taskDataProvider.getTasks())
			{
				if (t.getName().equals(planItem.getTaskName()))
				{
					planTask = t;
					break;
				}
			}
			if (planTask != null && planItem.getPinnedX() != null && planItem.getPinnedY() != null)
			{
				WorldPoint planPoint = new WorldPoint(planItem.getPinnedX(), planItem.getPinnedY(), 0);
				worldMapPointManager.add(new TaskWorldMapPoint(planPoint, planTask));
			}
		}
	}

	private void clearWorldMapMarkers()
	{
		worldMapPointManager.removeIf(point -> point instanceof TaskWorldMapPoint);
	}

	@Schedule(
		period = 30,
		unit = ChronoUnit.MINUTES,
		asynchronous = true
	)
	public void refreshTaskData()
	{
		httpTaskDataProvider.refreshFromRemote();
		List<TaskData> httpTasks = httpTaskDataProvider.getTasks();
		if (!httpTasks.isEmpty())
		{
			SwingUtilities.invokeLater(() -> {
				panel.setAllTasks(httpTasks);
				panel.setCompletedTaskNames(loadCompletedNames());
				panel.rebuildPlanList();
			});
		}
	}

	public void addTaskToPlan(TaskData task)
	{
		if (task != null && planService.addTask(task.getName(), task.getStructId()))
		{
			SwingUtilities.invokeLater(() -> {
				panel.rebuildPlanList();
				updateWorldMapMarkers();
			});
		}
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
