package com.ultimatetaskmaster;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.time.temporal.ChronoUnit;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.task.Schedule;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.ultimatetaskmaster.data.CrowdsourcingService;
import com.ultimatetaskmaster.data.LocalCompletionStore;
import com.ultimatetaskmaster.data.LocationCluster;
import com.ultimatetaskmaster.data.NearbyTask;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import com.ultimatetaskmaster.data.SpatialTaskQuery;
import com.ultimatetaskmaster.data.HttpTaskDataProvider;
import com.ultimatetaskmaster.data.StaticTaskDataProvider;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskSkillRequirement;
import com.ultimatetaskmaster.data.TaskDataProvider;
import com.ultimatetaskmaster.data.TaskItemService;
import com.ultimatetaskmaster.data.RouteGenerator;
import com.ultimatetaskmaster.data.TaskLocationService;

import net.runelite.client.game.ItemManager;
import com.ultimatetaskmaster.detection.TaskCompletionEvent;
import com.ultimatetaskmaster.detection.TaskCompletionListener;
import com.ultimatetaskmaster.overlay.UtmBankTab;
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
 * TODO: Add varp/varbit-based detection for bulk reading all completed league tasks
 *       from game memory. This would be the most reliable method — knows ALL completed
 *       tasks, not just ones completed while the plugin was active. Needs research into
 *       the correct varps for Raging Echoes league.
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
	private ConfigManager configManager;

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
	private TaskItemService taskItemService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private CrowdsourcingService crowdsourcingService;

	@Inject
	private LocalCompletionStore localCompletionStore;

	@Inject
	private NearbyTaskWorldOverlay worldOverlay;

	@Inject
	private NearbyTaskMinimapOverlay minimapOverlay;

	@Inject
	private UtmBankTab utmBankTab;

	private UltimateTaskMasterPanel panel;
	private NavigationButton navButton;
	private Widget utmBankButton;

	private final Map<String, List<TaskWorldMapPoint>> shownLocationPoints = new HashMap<>();

	private volatile WorldPoint cachedPlayerPosition;
	private volatile int[] cachedPlayerSkills;

	private Set<String> completedTaskNames = Collections.emptySet();

	private List<TaskWorldMapPoint> routeMapPoints = new ArrayList<>();

	/**
	 * The current "near me" results. Shared with overlays via getter.
	 * Empty list (never null) when no query has been run.
	 */
	@Getter
	private List<NearbyTask> nearbyTasks = Collections.emptyList();

	/**
	 * Cached enriched task list with location data populated from TaskLocationService.
	 * Used by refreshNearbyQuery() and panel so that SpatialTaskQuery can find nearby tasks.
	 */
	@Getter
	private List<TaskData> enrichedTasks = Collections.emptyList();

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

		// Beta lock check
		boolean unlocked = config.betaUnlocked();
		if (unlocked)
		{
			panel.setBetaUnlocked(true);
		}
		else
		{
			panel.showBetaLock();
		}

		panel.setOnBetaUnlocked(() -> {
			configManager.setConfiguration(CONFIG_GROUP, "betaUnlocked", true);
		});

		// Load locally stored completed task names
		completedTaskNames = localCompletionStore.getCompletedNames();

		// Show pending count if any
		int pendingCount = localCompletionStore.getPendingCount();
		if (pendingCount > 0)
		{
			panel.setSyncStatus(pendingCount + " pending", new Color(255, 200, 100));
		}

		// Wire plan services
		panel.setPlanService(planService);
		panel.setLocationService(locationService);
		panel.setTaskItemService(taskItemService);
		panel.setItemManager(itemManager);

		panel.setOnRemoveFromPlan(task -> {
			if (task != null && planService != null) {
				planService.removeTask(task.getName());
				removeShownLocationPoints(task.getName());
				refreshAllPanelViews();
			}
		});

			panel.setOnAddToPlan(this::addTaskToPlan);

		panel.setOnMarkCompleted(task -> {
			// Save locally — instant, no network, no freeze
			WorldPoint pos = cachedPlayerPosition;
			int x = pos != null ? pos.getX() : 0;
			int y = pos != null ? pos.getY() : 0;
			int plane = pos != null ? pos.getPlane() : 0;

			localCompletionStore.addPending(task.getName(), task.getStructId(), x, y, plane);

			// Update in-memory completed set
			completedTaskNames = new HashSet<>(completedTaskNames);
			completedTaskNames.add(task.getName());

			// Update sync status to show pending count
			int pending = localCompletionStore.getPendingCount();
			panel.setSyncStatus(pending + " pending", new Color(255, 200, 100));

			// Remove from nearby results and refresh overlay immediately
			removeFromNearbyTasks(task.getName());
			updateWorldMapMarkers();

			// Refresh panel lists to show completed state
			panel.setCompletedTaskNames(completedTaskNames);
			panel.rebuildNearbyList();
		});

		// Load hidden task names
		String hiddenJson = configManager.getConfiguration(CONFIG_GROUP, "hiddenTaskNames");
		if (hiddenJson != null && !hiddenJson.isEmpty())
		{
			try
			{
				Type type = new TypeToken<List<String>>(){}.getType();
				List<String> names = new Gson().fromJson(hiddenJson, type);
				if (names != null)
				{
					panel.setHiddenTaskNames(new HashSet<>(names));
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load hidden task names", e);
			}
		}

		panel.setOnHideTask(taskName -> {
			String json = configManager.getConfiguration(CONFIG_GROUP, "hiddenTaskNames");
			Set<String> hidden = new HashSet<>();
			if (json != null && !json.isEmpty())
			{
				try
				{
					Type type = new TypeToken<List<String>>(){}.getType();
					List<String> names = new Gson().fromJson(json, type);
					if (names != null) hidden.addAll(names);
				}
				catch (Exception e) { /* ignore */ }
			}
			hidden.add(taskName);
			saveHiddenTaskNames(hidden);
			// Remove from current nearby results and refresh overlay immediately
			removeFromNearbyTasks(taskName);
			updateWorldMapMarkers();
		});

		panel.setOnUnhideTask(taskName -> {
			String json = configManager.getConfiguration(CONFIG_GROUP, "hiddenTaskNames");
			Set<String> hidden = new HashSet<>();
			if (json != null && !json.isEmpty())
			{
				try
				{
					Type type = new TypeToken<List<String>>(){}.getType();
					List<String> names = new Gson().fromJson(json, type);
					if (names != null) hidden.addAll(names);
				}
				catch (Exception e) { /* ignore */ }
			}
			hidden.remove(taskName);
			saveHiddenTaskNames(hidden);
		});

		panel.setOnSync(() -> {
			performSync(true);
		});

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
					List<LocationCluster> locations = locationService.getLocationsForTask(task.getStructId());
					if (locations != null) {
						List<TaskWorldMapPoint> points = new ArrayList<>();
						for (LocationCluster loc : locations) {
							WorldPoint wp = new WorldPoint(loc.getX(), loc.getY(), 0);
							TaskWorldMapPoint point = new TaskWorldMapPoint(wp, task, new Color(255, 140, 0), 14);
							worldMapPointManager.add(point);
							points.add(point);
						}
						shownLocationPoints.put(taskName, points);
					}
				}
				panel.setTaskLocationsShown(taskName, true);
			} else {
				removeShownLocationPoints(taskName);
			}
		});

		panel.setOnGenerateRoute(() -> {
			// Run on background thread to avoid blocking EDT
			new Thread(() -> {
				WorldPoint playerPos = cachedPlayerPosition;
				if (playerPos == null)
				{
					SwingUtilities.invokeLater(() -> {
						panel.displayRoute(Collections.emptyList());
						showRouteOnMap(Collections.emptyList());
					});
					return;
				}

				Set<String> hidden = getHiddenTaskNames();

				List<RouteGenerator.RouteStep> route = RouteGenerator.generateRoute(
					enrichedTasks,
					locationService,
					playerPos,
					completedTaskNames,
					hidden,
					cachedPlayerSkills,
					0,  // no max radius (find all reachable tasks)
					50  // max 50 tasks in route
				);

				SwingUtilities.invokeLater(() -> {
					panel.displayRoute(route);
					showRouteOnMap(route);
				});
			}, "utm-route-gen").start();
		});

		List<TaskData> allTasks = enrichTasksWithLocations(taskDataProvider.getTasks());
		enrichedTasks = allTasks;
		SwingUtilities.invokeLater(() ->
		{
			panel.setAllTasks(allTasks);
			panel.setCompletedTaskNames(completedTaskNames);
			panel.rebuildPlanList();
		});

		// Attempt HTTP refresh in background
		new Thread(() -> {
			httpTaskDataProvider.refreshFromRemote();
			List<TaskData> httpTasks = httpTaskDataProvider.getTasks();
			if (!httpTasks.isEmpty())
			{
				List<TaskData> enrichedHttpTasks = enrichTasksWithLocations(httpTasks);
				enrichedTasks = enrichedHttpTasks;
				SwingUtilities.invokeLater(() -> {
					panel.setAllTasks(enrichedHttpTasks);
					log.info("Refreshed tasks from remote: {} tasks for {}",
						enrichedHttpTasks.size(), httpTaskDataProvider.getCurrentLeagueName());
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
		// Bank item overlay is toggled on/off by the UTM bank button — not added at startup

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
		if (utmBankTab.isActive())
		{
			utmBankTab.deactivate();
		}
		clearRouteMarkers();
		clearWorldMapMarkers();
		nearbyTasks = Collections.emptyList();

		if (utmBankButton != null)
		{
			utmBankButton.setHidden(true);
			utmBankButton = null;
		}

		log.info("Ultimate Task Master stopped!");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getLocalPlayer() != null)
		{
			cachedPlayerPosition = client.getLocalPlayer().getWorldLocation();
		}

		// Deactivate UTM bank tab if bank was closed
		if (utmBankTab.isActive())
		{
			Widget bankWidget = client.getWidget(ComponentID.BANK_CONTAINER);
			if (bankWidget == null || bankWidget.isHidden())
			{
				utmBankTab.deactivate();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				cachedPlayerSkills = client.getRealSkillLevels();
				// Auto-sync on login: push pending completions + pull latest locations
				performSync(true);
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				// Push any pending completions before leaving
				if (localCompletionStore.getPendingCount() > 0)
				{
					performSync(false);
				}
				nearbyTasks = Collections.emptyList();
				clearWorldMapMarkers();
				SwingUtilities.invokeLater(() -> panel.showNotLoggedIn());
				break;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		cachedPlayerSkills = client.getRealSkillLevels();
	}

	@Subscribe
	public void onTaskCompletionEvent(TaskCompletionEvent event)
	{
		completedTaskNames = new HashSet<>(completedTaskNames);
		completedTaskNames.add(event.getTaskName());

		// Persist so it survives restart
		localCompletionStore.addCompletedName(event.getTaskName());

		client.addChatMessage(ChatMessageType.CONSOLE, CHAT_SENDER,
			"Task completed: " + event.getTaskName(), CHAT_SENDER);
		log.info("Task completed: {}", event.getTaskName());

		SwingUtilities.invokeLater(() -> panel.setCompletedTaskNames(completedTaskNames));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Shift-right-click on game world: "Add to Plan" for nearby tasks
		if (client.isKeyPressed(KeyCode.KC_SHIFT)
			&& event.getType() == MenuAction.WALK.getId()
			&& nearbyTasks != null && !nearbyTasks.isEmpty())
		{
			// Get the tile the player is hovering
			if (client.getSelectedSceneTile() != null)
			{
				WorldPoint hoveredTile = client.getSelectedSceneTile().getWorldLocation();
				if (hoveredTile != null)
				{
					for (NearbyTask nearbyTask : nearbyTasks)
					{
						WorldPoint taskPoint = nearbyTask.getResolvedLocation();
						if (taskPoint != null && hoveredTile.distanceTo(taskPoint) <= 1)
						{
							TaskData task = nearbyTask.getTask();
							// Check if already in plan
							boolean inPlan = planService.getItems().stream()
								.anyMatch(item -> item.getTaskName().equals(task.getName()));

							if (!inPlan)
							{
								client.createMenuEntry(-1)
									.setOption("Add to Plan")
									.setTarget(ColorUtil.wrapWithColorTag(task.getName(), task.getTier().getColor()))
									.setType(MenuAction.RUNELITE)
									.onClick(e -> {
										addTaskToPlan(task);
									});
							}
							else
							{
								client.createMenuEntry(-1)
									.setOption("Remove from Plan")
									.setTarget(ColorUtil.wrapWithColorTag(task.getName(), task.getTier().getColor()))
									.setType(MenuAction.RUNELITE)
									.onClick(e -> {
										planService.removeTask(task.getName());
										refreshAllPanelViews();
									});
							}
							// Always offer "Hide Task" option
							client.createMenuEntry(-2)
								.setOption("Hide Task")
								.setTarget(ColorUtil.wrapWithColorTag(task.getName(), Color.GRAY))
								.setType(MenuAction.RUNELITE)
								.onClick(e -> {
									// Add to hidden set and persist
									String json = configManager.getConfiguration(CONFIG_GROUP, "hiddenTaskNames");
									Set<String> hidden = new HashSet<>();
									if (json != null && !json.isEmpty())
									{
										try
										{
											Type type = new TypeToken<List<String>>(){}.getType();
											List<String> names = new Gson().fromJson(json, type);
											if (names != null) hidden.addAll(names);
										}
										catch (Exception ex) { /* ignore */ }
									}
									hidden.add(task.getName());
									saveHiddenTaskNames(hidden);
									
									// Remove from current nearby results and refresh
									removeFromNearbyTasks(task.getName());
									
									SwingUtilities.invokeLater(() -> {
										panel.setHiddenTaskNames(hidden);
										panel.rebuildAllTasksList();
										panel.rebuildNearbyList();
									});
								});

							break;
						}
					}
				}
			}
		}

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

		// Calculate the world point the mouse is hovering over on the map
		WorldPoint mouseWorldPoint = getMouseWorldPointOnMap();
		if (mouseWorldPoint == null)
		{
			return;
		}

		// Check if mouse is near any of our shown location dots
		for (Map.Entry<String, List<TaskWorldMapPoint>> entry : shownLocationPoints.entrySet())
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

	/**
	 * Converts mouse canvas position to a world point on the world map.
	 * Adapted from shortest-path plugin's calculateMapPoint approach.
	 */
	private WorldPoint getMouseWorldPointOnMap()
	{
		Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
		if (map == null)
		{
			return null;
		}

		WorldMap worldMap = client.getWorldMap();
		float zoom = worldMap.getWorldMapZoom();
		net.runelite.api.Point center = worldMap.getWorldMapPosition();
		Rectangle bounds = map.getBounds();

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();

		int dx = (int) ((mouse.getX() - bounds.getCenterX()) / zoom);
		int dy = (int) ((-(mouse.getY() - bounds.getCenterY())) / zoom);

		return new WorldPoint(center.getX() + dx, center.getY() + dy, 0);
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
	 * Run a sync on a background thread: push pending completions, pull latest locations.
	 * Safe to call from any thread (spawns its own background thread).
	 *
	 * @param updateUi if true, updates sync button status on EDT
	 */
	private void performSync(boolean updateUi)
	{
		new Thread(() -> {
			try
			{
				List<LocalCompletionStore.PendingCompletion> pending = localCompletionStore.getPending();
				int pushed = 0;

				if (!pending.isEmpty())
				{
					if (updateUi)
					{
						SwingUtilities.invokeLater(() -> panel.setSyncStatus("Pushing " + pending.size() + "...", Color.YELLOW));
					}
					pushed = crowdsourcingService.pushPending(pending);
					if (pushed > 0)
					{
						localCompletionStore.removePending(pending.subList(0, Math.min(pushed, pending.size())));
					}
				}

				if (updateUi)
				{
					SwingUtilities.invokeLater(() -> panel.setSyncStatus("Pulling locations...", Color.YELLOW));
				}
				List<CrowdsourcingService.ServerLocation> serverLocations = crowdsourcingService.pullLocations();

				// Merge server locations into TaskLocationService
				if (!serverLocations.isEmpty())
				{
					List<LocationCluster> clusters = new ArrayList<>();
					for (CrowdsourcingService.ServerLocation sl : serverLocations)
					{
						LocationCluster lc = new LocationCluster();
						lc.setStructId(sl.getStruct_id());
						lc.setX(sl.getX());
						lc.setY(sl.getY());
						lc.setCount(sl.getHits());
						clusters.add(lc);
					}
					locationService.mergeServerLocations(clusters);

					// Re-enrich tasks with merged location data
					enrichedTasks = enrichTasksWithLocations(taskDataProvider.getTasks());
				}

				final int finalPushed = pushed;
				final int pulled = serverLocations.size();
				int remaining = localCompletionStore.getPendingCount();

				if (updateUi)
				{
					SwingUtilities.invokeLater(() -> {
						String status = "Pushed " + finalPushed + ", pulled " + pulled + " locations";
						if (remaining > 0)
						{
							status += " (" + remaining + " still pending)";
						}
						panel.setSyncStatus(status, new Color(100, 255, 100));
						panel.setSyncEnabled(true);
					});
					refreshAllPanelViews();
				}

				log.debug("Sync complete: pushed {}, pulled {} locations, {} remaining", finalPushed, pulled, remaining);
			}
			catch (Exception e)
			{
				log.error("Sync failed", e);
				if (updateUi)
				{
					SwingUtilities.invokeLater(() -> {
						panel.setSyncStatus("Sync failed: " + e.getMessage(), new Color(255, 80, 80));
						panel.setSyncEnabled(true);
					});
				}
			}
		}, "utm-sync").start();
	}

	/**
	 * Check if the player meets all skill requirements for a task.
	 * Returns true if requirements are met or if no requirements/skills data available.
	 */
	private boolean meetsSkillRequirements(TaskData task)
	{
		if (cachedPlayerSkills == null)
		{
			return true; // Can't check, show the task
		}

		List<TaskSkillRequirement> reqs = task.getRequirements();
		if (reqs == null || reqs.isEmpty())
		{
			return true;
		}

		for (TaskSkillRequirement req : reqs)
		{
			try
			{
				Skill skill = Skill.valueOf(req.getSkill().toUpperCase());
				if (cachedPlayerSkills[skill.ordinal()] < req.getLevel())
				{
					return false;
				}
			}
			catch (IllegalArgumentException e)
			{
				// Unknown skill name (e.g. OVERALL) - skip this requirement
				log.debug("Unknown skill in requirement: {}", req.getSkill());
			}
		}
		return true;
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
		SpatialTaskQuery.SortCriteria sort = SpatialTaskQuery.SortCriteria.DISTANCE;

		nearbyTasks = SpatialTaskQuery.findNearby(
			enrichedTasks.isEmpty() ? taskDataProvider.getTasks() : enrichedTasks,
			playerLocation,
			config.searchRadius(),
			sort,
			locationService
		);

		// Filter out hidden tasks
		Set<String> hiddenNames = getHiddenTaskNames();
		if (!hiddenNames.isEmpty())
		{
			nearbyTasks = nearbyTasks.stream()
				.filter(nt -> !hiddenNames.contains(nt.getTask().getName()))
				.collect(Collectors.toList());
		}

		// Filter out completed tasks
		if (!completedTaskNames.isEmpty())
		{
			nearbyTasks = nearbyTasks.stream()
				.filter(nt -> !completedTaskNames.contains(nt.getTask().getName()))
				.collect(Collectors.toList());
		}

		// Filter out tasks the player doesn't have the levels for
		if (config.filterBySkillLevel())
		{
			nearbyTasks = nearbyTasks.stream()
				.filter(nt -> meetsSkillRequirements(nt.getTask()))
				.collect(Collectors.toList());
		}

		SwingUtilities.invokeLater(() -> panel.updateResults(nearbyTasks));
		updateWorldMapMarkers();
	}

	private Set<String> getHiddenTaskNames()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, "hiddenTaskNames");
		if (json == null || json.isEmpty())
		{
			return Collections.emptySet();
		}
		try
		{
			Type type = new TypeToken<List<String>>(){}.getType();
			List<String> names = new Gson().fromJson(json, type);
			return names != null ? new HashSet<>(names) : Collections.emptySet();
		}
		catch (Exception e)
		{
			return Collections.emptySet();
		}
	}

	private void showRouteOnMap(List<RouteGenerator.RouteStep> route)
	{
		// Clear previous route markers
		clearRouteMarkers();

		if (route == null || route.isEmpty())
		{
			return;
		}

		// Add world map markers for each step
		for (RouteGenerator.RouteStep step : route)
		{
			TaskWorldMapPoint point = new TaskWorldMapPoint(
				step.getLocation(), step.getTask(), new Color(0, 200, 255), 14);
			worldMapPointManager.add(point);
			routeMapPoints.add(point);
		}

		// Also add route steps to nearbyTasks so the tile overlay renders them
		List<NearbyTask> routeNearby = new ArrayList<>();
		for (RouteGenerator.RouteStep step : route)
		{
			routeNearby.add(new NearbyTask(step.getTask(), step.getLocation(), step.getDistanceFromPrevious()));
		}
		nearbyTasks = routeNearby;

		log.debug("Showing {} route steps on map", route.size());
	}

	private void clearRouteMarkers()
	{
		for (TaskWorldMapPoint point : routeMapPoints)
		{
			worldMapPointManager.remove(point);
		}
		routeMapPoints.clear();
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
			List<TaskData> enrichedHttpTasks = enrichTasksWithLocations(httpTasks);
			enrichedTasks = enrichedHttpTasks;
			SwingUtilities.invokeLater(() -> {
				panel.setAllTasks(enrichedHttpTasks);
				panel.setCompletedTaskNames(completedTaskNames);
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

	private void saveHiddenTaskNames(Set<String> hidden)
	{
		configManager.setConfiguration(CONFIG_GROUP, "hiddenTaskNames",
			new Gson().toJson(new ArrayList<>(hidden)));
	}

	private void removeShownLocationPoints(String taskName)
	{
		List<TaskWorldMapPoint> points = shownLocationPoints.remove(taskName);
		if (points != null)
		{
			for (TaskWorldMapPoint p : points)
			{
				worldMapPointManager.remove(p);
			}
		}
		panel.setTaskLocationsShown(taskName, false);
	}

	private void removeFromNearbyTasks(String taskName)
	{
		nearbyTasks = nearbyTasks.stream()
			.filter(nt -> !nt.getTask().getName().equals(taskName))
			.collect(Collectors.toList());
	}

	private void refreshAllPanelViews()
	{
		SwingUtilities.invokeLater(() -> {
			panel.rebuildAllTasksList();
			panel.rebuildNearbyList();
			panel.rebuildPlanList();
		});
		updateWorldMapMarkers();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANK)
		{
			// Create the UTM button on the bank interface
			clientThread.invokeLater(this::createBankButton);
		}
	}

	private void createBankButton()
	{
		Widget parent = client.getWidget(ComponentID.BANK_CONTAINER);
		if (parent == null)
		{
			return;
		}

		// Background button
		utmBankButton = parent.createChild(-1, WidgetType.GRAPHIC);
		utmBankButton.setSpriteId(SpriteID.TAB_INVENTORY);
		utmBankButton.setOriginalWidth(25);
		utmBankButton.setOriginalHeight(25);
		utmBankButton.setOriginalX(434);
		utmBankButton.setOriginalY(5);
		utmBankButton.setName("UTM Plan");
		utmBankButton.setHasListener(true);

		// Left-click action
		utmBankButton.setAction(0, "View UTM Plan Items");
		utmBankButton.setOnOpListener((JavaScriptCallback) e -> {
			onUtmBankButtonClicked();
		});

		utmBankButton.revalidate();
		log.info("UTM bank button created");
	}

	private void onUtmBankButtonClicked()
	{
		if (utmBankTab.isActive())
		{
			utmBankTab.deactivate();
		}
		else
		{
			utmBankTab.activate();
		}

		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	/**
	 * Enriches tasks with location data from TaskLocationService.
	 * Sets each task's location to the highest-count cluster centroid.
	 * Since TaskData is @Value (immutable), we rebuild each task with the location set.
	 */
	private List<TaskData> enrichTasksWithLocations(List<TaskData> tasks)
	{
		if (locationService == null)
		{
			return tasks;
		}

		List<TaskData> enriched = new ArrayList<>(tasks.size());
		int located = 0;

		for (TaskData task : tasks)
		{
			List<LocationCluster> clusters = locationService.getLocationsForTask(task.getStructId());
			if (clusters != null && !clusters.isEmpty())
			{
				// Use highest-count cluster as primary location
				LocationCluster best = clusters.get(0);
				for (LocationCluster c : clusters)
				{
					if (c.getCount() > best.getCount())
					{
						best = c;
					}
				}

				TaskData enrichedTask = TaskData.builder()
					.structId(task.getStructId())
					.sortId(task.getSortId())
					.name(task.getName())
					.description(task.getDescription())
					.area(task.getArea())
					.tier(task.getTier())
					.points(task.getPoints())
					.category(task.getCategory())
					.skill(task.getSkill())
					.completionPct(task.getCompletionPct())
					.location(new WorldPoint(best.getX(), best.getY(), 0))
					.requirements(task.getRequirements())
					.build();
				enriched.add(enrichedTask);
				located++;
			}
			else
			{
				enriched.add(task);
			}
		}

		log.info("Enriched {} of {} tasks with location data", located, tasks.size());
		return enriched;
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
