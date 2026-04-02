package com.ultimatetaskmaster.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import com.ultimatetaskmaster.data.LocationCluster;
import com.ultimatetaskmaster.data.NearbyTask;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import com.ultimatetaskmaster.data.SpatialTaskQuery;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskLocationService;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Main side panel for Ultimate Task Master.
 *
 * Two tabs (tasks-tracker toggle-button pattern):
 * 1. "All Tasks" — browse/search/filter all 1,589 tasks
 * 2. "Near Me"   — find tasks near the player's position using scraper location data
 *
 * Shared state: allTasks, completedTaskNames, sort criteria.
 * Each tab has its own scrollable task list and status label.
 */
public class UltimateTaskMasterPanel extends PluginPanel
{
	private static final String CARD_ALL = "ALL";
	private static final String CARD_NEARBY = "NEARBY";
	private static final String CARD_PLAN = "PLAN";

	// --- Shared controls ---
	private final JToggleButton allTasksTab = new JToggleButton("All Tasks");
	private final JToggleButton nearbyTab = new JToggleButton("Near Me");
	private final JToggleButton planTab = new JToggleButton("Plan");

	// --- "All Tasks" tab ---
	private final IconTextField allSearchField = new IconTextField();
	private final JToggleButton allHideCompletedToggle = new JToggleButton("Hide Done");
	private final JComboBox<SpatialTaskQuery.SortCriteria> allSortDropdown;
	private final JLabel allStatusLabel = new JLabel();
	private final JPanel allTaskListContainer = new JPanel();

	// --- "Near Me" tab ---
	private final JButton findNearbyButton = new JButton("Find Nearby Tasks");
	private final IconTextField nearbySearchField = new IconTextField();
	private final JToggleButton nearbyHideCompletedToggle = new JToggleButton("Hide Done");
	@Getter
	private final JComboBox<SpatialTaskQuery.SortCriteria> sortDropdown;
	private final JLabel nearbyStatusLabel = new JLabel();
	private final JPanel nearbyTaskListContainer = new JPanel();

	// --- "Current Plan" tab ---
	private final JLabel planStatusLabel = new JLabel();
	private final JPanel planListContainer = new JPanel();

	private final JLabel leagueInfoLabel = new JLabel();

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardPanel = new JPanel(cardLayout);

	private Runnable onFindNearby;
	private PlanService planService;
	private TaskLocationService locationService;
	private java.util.function.BiConsumer<String, LocationCluster> onPinCallback;
	private java.util.function.Consumer<String> onRemoveFromPlanCallback;
	private java.util.function.Consumer<TaskData> onAddToPlanCallback;

	/** All tasks from the data provider. Set once via {@link #setAllTasks}. */
	private List<TaskData> allTasks = Collections.emptyList();
	/** Names of completed tasks (for green background + hide filter). */
	private Set<String> completedTaskNames = Collections.emptySet();
	/** Current nearby results (null = no query run yet). */
	private List<NearbyTask> nearbyResults = null;

	public UltimateTaskMasterPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// --- Header: title + tab buttons ---
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(10, 10, 0, 10));

		JLabel title = new JLabel("Ultimate Task Master");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		header.add(Box.createVerticalStrut(6));

		// Tab buttons (tasks-tracker ButtonGroup pattern)
		JPanel tabRow = new JPanel();
		tabRow.setLayout(new BoxLayout(tabRow, BoxLayout.X_AXIS));
		tabRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabRow.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
		tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabRow.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 26));

		ButtonGroup tabGroup = new ButtonGroup();
		styleTabButton(allTasksTab);
		styleTabButton(nearbyTab);
		styleTabButton(planTab);
		tabGroup.add(allTasksTab);
		tabGroup.add(nearbyTab);
		tabGroup.add(planTab);

		allTasksTab.setSelected(true);
		allTasksTab.addActionListener(e -> cardLayout.show(cardPanel, CARD_ALL));
		nearbyTab.addActionListener(e -> cardLayout.show(cardPanel, CARD_NEARBY));
		planTab.addActionListener(e -> cardLayout.show(cardPanel, CARD_PLAN));

		tabRow.add(Box.createHorizontalGlue());
		tabRow.add(allTasksTab);
		tabRow.add(Box.createHorizontalGlue());
		tabRow.add(nearbyTab);
		tabRow.add(Box.createHorizontalGlue());
		tabRow.add(planTab);
		tabRow.add(Box.createHorizontalGlue());
		header.add(tabRow);
		header.add(Box.createVerticalStrut(4));

		leagueInfoLabel.setFont(FontManager.getRunescapeSmallFont());
		leagueInfoLabel.setForeground(new Color(200, 180, 120));
		leagueInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		leagueInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(leagueInfoLabel);

		add(header, BorderLayout.NORTH);

		// --- Card panel with both tabs ---
		cardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Init sort dropdowns
		allSortDropdown = new JComboBox<>(SpatialTaskQuery.SortCriteria.values());
		sortDropdown = new JComboBox<>(SpatialTaskQuery.SortCriteria.values());

		cardPanel.add(buildAllTasksCard(), CARD_ALL);
		cardPanel.add(buildNearbyCard(), CARD_NEARBY);
		cardPanel.add(buildPlanCard(), CARD_PLAN);
		cardLayout.show(cardPanel, CARD_ALL);

		add(cardPanel, BorderLayout.CENTER);
	}

	// ========== "All Tasks" card ==========

	private JPanel buildAllTasksCard()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Controls
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(6, 10, 4, 10));

		allSearchField.setIcon(IconTextField.Icon.SEARCH);
		allSearchField.setPreferredSize(new Dimension(0, 24));
		allSearchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		allSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		allSearchField.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyReleased(java.awt.event.KeyEvent e) { rebuildAllTasksList(); }
		});
		controls.add(allSearchField);
		controls.add(Box.createVerticalStrut(4));

		allHideCompletedToggle.setFont(FontManager.getRunescapeSmallFont());
		allHideCompletedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		allHideCompletedToggle.addActionListener(e -> rebuildAllTasksList());
		controls.add(allHideCompletedToggle);
		controls.add(Box.createVerticalStrut(4));

		JPanel allSortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		allSortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		allSortRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel allSortLbl = new JLabel("Sort: ");
		allSortLbl.setFont(FontManager.getRunescapeSmallFont());
		allSortLbl.setForeground(Color.WHITE);
		allSortRow.add(allSortLbl);
		allSortDropdown.setFont(FontManager.getRunescapeSmallFont());
		allSortDropdown.addActionListener(e -> rebuildAllTasksList());
		allSortRow.add(allSortDropdown);
		controls.add(allSortRow);
		controls.add(Box.createVerticalStrut(4));

		allStatusLabel.setFont(FontManager.getRunescapeSmallFont());
		allStatusLabel.setForeground(Color.GRAY);
		allStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		allStatusLabel.setText("Loading...");
		controls.add(allStatusLabel);

		card.add(controls, BorderLayout.NORTH);
		card.add(buildScrollableList(allTaskListContainer), BorderLayout.CENTER);
		return card;
	}

	// ========== "Near Me" card ==========

	private JPanel buildNearbyCard()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Controls
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(6, 10, 4, 10));

		findNearbyButton.setFont(FontManager.getRunescapeSmallFont());
		findNearbyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		findNearbyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		findNearbyButton.addActionListener(e ->
		{
			if (onFindNearby != null)
			{
				onFindNearby.run();
			}
		});
		controls.add(findNearbyButton);
		controls.add(Box.createVerticalStrut(4));

		nearbySearchField.setIcon(IconTextField.Icon.SEARCH);
		nearbySearchField.setPreferredSize(new Dimension(0, 24));
		nearbySearchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nearbySearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		nearbySearchField.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyReleased(java.awt.event.KeyEvent e) { rebuildNearbyList(); }
		});
		controls.add(nearbySearchField);
		controls.add(Box.createVerticalStrut(4));

		nearbyHideCompletedToggle.setFont(FontManager.getRunescapeSmallFont());
		nearbyHideCompletedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		nearbyHideCompletedToggle.addActionListener(e -> rebuildNearbyList());
		controls.add(nearbyHideCompletedToggle);
		controls.add(Box.createVerticalStrut(4));

		JPanel nearbySortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		nearbySortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		nearbySortRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel nearbySortLbl = new JLabel("Sort: ");
		nearbySortLbl.setFont(FontManager.getRunescapeSmallFont());
		nearbySortLbl.setForeground(Color.WHITE);
		nearbySortRow.add(nearbySortLbl);
		sortDropdown.setFont(FontManager.getRunescapeSmallFont());
		sortDropdown.addActionListener(e -> rebuildNearbyList());
		nearbySortRow.add(sortDropdown);
		controls.add(nearbySortRow);
		controls.add(Box.createVerticalStrut(4));

		nearbyStatusLabel.setFont(FontManager.getRunescapeSmallFont());
		nearbyStatusLabel.setForeground(Color.GRAY);
		nearbyStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		nearbyStatusLabel.setText("Click button to find nearby tasks");
		controls.add(nearbyStatusLabel);

		card.add(controls, BorderLayout.NORTH);
		card.add(buildScrollableList(nearbyTaskListContainer), BorderLayout.CENTER);
		return card;
	}

	// ========== "Current Plan" card ==========

	private JPanel buildPlanCard()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(6, 10, 4, 10));

		planStatusLabel.setFont(FontManager.getRunescapeSmallFont());
		planStatusLabel.setForeground(Color.GRAY);
		planStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		planStatusLabel.setText("Add tasks from the All Tasks tab");
		controls.add(planStatusLabel);

		card.add(controls, BorderLayout.NORTH);
		card.add(buildScrollableList(planListContainer), BorderLayout.CENTER);
		return card;
	}

	// ========== Shared scroll pane builder ==========

	private JScrollPane buildScrollableList(JPanel listContainer)
	{
		listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
		listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(listContainer, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(null);
		return scrollPane;
	}

	// ========== Public API (called from the plugin on the EDT) ==========

	public void setOnFindNearby(Runnable callback)
	{
		this.onFindNearby = callback;
	}

	public SpatialTaskQuery.SortCriteria getSelectedSort()
	{
		return (SpatialTaskQuery.SortCriteria) sortDropdown.getSelectedItem();
	}

	public void setLeagueInfo(String leagueName, int taskCount, String dataSource)
	{
		leagueInfoLabel.setText(leagueName + " | " + taskCount + " tasks | " + dataSource);
	}

	/** Set the full task list (called once on startup). Must be on EDT. */
	public void setAllTasks(List<TaskData> tasks)
	{
		this.allTasks = tasks != null ? tasks : Collections.emptyList();
		leagueInfoLabel.setText(allTasks.size() + " tasks loaded");
		rebuildAllTasksList();
	}

	/** Update which tasks are completed (green highlight + hide filter). Must be on EDT. */
	public void setCompletedTaskNames(Set<String> completed)
	{
		this.completedTaskNames = completed != null ? completed : Collections.emptySet();
		rebuildAllTasksList();
		rebuildNearbyList();
	}

	/** Update the nearby tab with query results. Must be on EDT. */
	public void updateResults(List<NearbyTask> results)
	{
		this.nearbyResults = results;
		rebuildNearbyList();
	}

	/** Show "not logged in" state on the nearby tab. */
	public void showNotLoggedIn()
	{
		nearbyResults = null;
		nearbyTaskListContainer.removeAll();
		nearbyStatusLabel.setText("Log in to find nearby tasks");
		addMessageLabel(nearbyTaskListContainer, "Log in to use this feature.");
		nearbyTaskListContainer.revalidate();
		nearbyTaskListContainer.repaint();
	}

	public void setPlanService(PlanService planService)
	{
		this.planService = planService;
	}

	public void setLocationService(TaskLocationService locationService)
	{
		this.locationService = locationService;
	}

	public void setOnPinCallback(java.util.function.BiConsumer<String, LocationCluster> callback)
	{
		this.onPinCallback = callback;
	}

	public void setOnRemoveFromPlanCallback(java.util.function.Consumer<String> callback)
	{
		this.onRemoveFromPlanCallback = callback;
	}

	public void setOnAddToPlan(java.util.function.Consumer<TaskData> callback)
	{
		this.onAddToPlanCallback = callback;
	}

	// ========== Rebuild: All Tasks tab ==========

	private void rebuildAllTasksList()
	{
		allTaskListContainer.removeAll();

		String searchText = getSearchText(allSearchField);
		boolean hideCompleted = allHideCompletedToggle.isSelected();

		List<TaskData> filtered = allTasks.stream()
			.filter(t -> searchText.isEmpty() || t.getName().toLowerCase().contains(searchText))
			.filter(t -> !hideCompleted || !completedTaskNames.contains(t.getName()))
			.sorted(getTaskComparator(allSortDropdown))
			.collect(Collectors.toList());

		if (filtered.isEmpty())
		{
			allStatusLabel.setText("No tasks match filter");
			addMessageLabel(allTaskListContainer, "No tasks match the current filters.");
		}
		else
		{
			int completedCount = (int) allTasks.stream()
				.filter(t -> completedTaskNames.contains(t.getName())).count();
			allStatusLabel.setText(String.format(
				"%d tasks shown (%d/%d completed)", filtered.size(), completedCount, allTasks.size()));

			for (int i = 0; i < filtered.size(); i++)
			{
				TaskData t = filtered.get(i);
				boolean done = completedTaskNames.contains(t.getName());
				TaskRowPanel row = new TaskRowPanel(t, done, null, i % 2 == 0);
				row.setOnAddToPlan(onAddToPlanCallback);
				allTaskListContainer.add(row);
			}
		}

		allTaskListContainer.revalidate();
		allTaskListContainer.repaint();
	}

	// ========== Rebuild: Nearby tab ==========

	private void rebuildNearbyList()
	{
		nearbyTaskListContainer.removeAll();

		if (nearbyResults == null)
		{
			nearbyStatusLabel.setText("Click button to find nearby tasks");
			addMessageLabel(nearbyTaskListContainer,
				"Press \"Find Nearby Tasks\" to search\nfor tasks near your position.");
			nearbyTaskListContainer.revalidate();
			nearbyTaskListContainer.repaint();
			return;
		}

		String searchText = getSearchText(nearbySearchField);
		boolean hideCompleted = nearbyHideCompletedToggle.isSelected();

		List<NearbyTask> filtered = nearbyResults.stream()
			.filter(nt -> searchText.isEmpty()
				|| nt.getTask().getName().toLowerCase().contains(searchText))
			.filter(nt -> !hideCompleted
				|| !completedTaskNames.contains(nt.getTask().getName()))
			.sorted(getNearbyComparator())
			.collect(Collectors.toList());

		if (filtered.isEmpty())
		{
			nearbyStatusLabel.setText("No nearby tasks match filter");
			addMessageLabel(nearbyTaskListContainer,
				"No nearby tasks found.\nAdjust search radius in plugin settings.");
		}
		else
		{
			nearbyStatusLabel.setText(filtered.size() + " nearby task"
				+ (filtered.size() != 1 ? "s" : "") + " found");

			for (int i = 0; i < filtered.size(); i++)
			{
				NearbyTask nt = filtered.get(i);
				boolean done = completedTaskNames.contains(nt.getTask().getName());
				TaskRowPanel nearbyRow = new TaskRowPanel(
					nt.getTask(), done, nt.getDistance(), i % 2 == 0);
				nearbyRow.setOnAddToPlan(onAddToPlanCallback);
				nearbyTaskListContainer.add(nearbyRow);
			}
		}

		nearbyTaskListContainer.revalidate();
		nearbyTaskListContainer.repaint();
	}

	// ========== Rebuild: Plan tab ==========

	public void rebuildPlanList()
	{
		planListContainer.removeAll();

		if (planService == null)
		{
			planStatusLabel.setText("Plan not available");
			planListContainer.revalidate();
			planListContainer.repaint();
			return;
		}

		java.util.List<PlanItem> items = planService.getItems();
		if (items.isEmpty())
		{
			planStatusLabel.setText("No tasks in plan. Add tasks from All Tasks tab.");
			addMessageLabel(planListContainer, "Click '+' on any task to add it to your plan.");
		}
		else
		{
			int totalPoints = 0;
			for (PlanItem item : items)
			{
				TaskData t = findTaskByName(item.getTaskName());
				totalPoints += t != null ? t.getPoints() : 0;
			}
			planStatusLabel.setText(items.size() + " tasks \u00b7 " + totalPoints + " pts");

			for (int i = 0; i < items.size(); i++)
			{
				PlanItem item = items.get(i);
				TaskData t = findTaskByName(item.getTaskName());
				java.util.List<LocationCluster> locs = locationService != null && t != null
					? locationService.getLocationsForTask(t.getStructId())
					: java.util.Collections.emptyList();
				planListContainer.add(new PlanItemPanel(item, t, locs, i % 2 == 0, onPinCallback, onRemoveFromPlanCallback));
			}
		}

		planListContainer.revalidate();
		planListContainer.repaint();
	}

	private TaskData findTaskByName(String name)
	{
		for (TaskData t : allTasks)
		{
			if (t.getName().equals(name))
			{
				return t;
			}
		}
		return null;
	}

	// ========== Comparators ==========

	private Comparator<NearbyTask> getNearbyComparator()
	{
		SpatialTaskQuery.SortCriteria sort = getSelectedSort();
		if (sort == null) sort = SpatialTaskQuery.SortCriteria.DISTANCE;

		switch (sort)
		{
			case POINTS:
				return Comparator.comparingInt((NearbyTask nt) -> nt.getTask().getPoints()).reversed();
			case COMPLETION_PCT:
				return Comparator.comparing(
					(NearbyTask nt) -> nt.getTask().getCompletionPct() != null
						? nt.getTask().getCompletionPct() : 0f
				).reversed();
			case TIER:
				return Comparator.comparingInt((NearbyTask nt) -> nt.getTask().getTier().ordinal());
			case DISTANCE:
			default:
				return Comparator.comparingInt(NearbyTask::getDistance);
		}
	}

	private static Comparator<TaskData> getTaskComparator(JComboBox<SpatialTaskQuery.SortCriteria> dropdown)
	{
		SpatialTaskQuery.SortCriteria sort = (SpatialTaskQuery.SortCriteria) dropdown.getSelectedItem();
		if (sort == null) sort = SpatialTaskQuery.SortCriteria.POINTS;

		switch (sort)
		{
			case POINTS:
				return Comparator.comparingInt(TaskData::getPoints).reversed();
			case COMPLETION_PCT:
				return Comparator.comparing(
					(TaskData t) -> t.getCompletionPct() != null ? t.getCompletionPct() : 0f
				).reversed();
			case TIER:
				return Comparator.comparingInt(t -> t.getTier().ordinal());
			case DISTANCE:
			default:
				return Comparator.comparing(TaskData::getName, String.CASE_INSENSITIVE_ORDER);
		}
	}

	// ========== Helpers ==========

	private static void styleTabButton(JToggleButton btn)
	{
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setPreferredSize(new Dimension(90, 22));
	}

	private static String getSearchText(IconTextField field)
	{
		return field.getText() != null ? field.getText().trim().toLowerCase() : "";
	}

	private static void addMessageLabel(JPanel container, String message)
	{
		JLabel label = new JLabel(
			"<html><center>" + message.replace("\n", "<br>") + "</center></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.GRAY);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(30, 10, 30, 10));
		container.add(label);
	}
}
