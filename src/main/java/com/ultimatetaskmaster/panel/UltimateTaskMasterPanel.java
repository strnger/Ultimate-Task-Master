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

	// --- Beta lock ---
	private boolean betaUnlocked = false;
	private JPanel lockPanel;
	private Runnable onBetaUnlocked;

	private Runnable onFindNearby;
	private Runnable onSyncCallback;
	private JLabel syncStatusLabel;
	private JButton syncButton;
	private PlanService planService;
	private TaskLocationService locationService;
	private java.util.function.BiConsumer<String, LocationCluster> onPinCallback;
	private java.util.function.Consumer<String> onRemoveFromPlanCallback;
	private java.util.function.Consumer<TaskData> onAddToPlanCallback;
	private java.util.function.Consumer<TaskData> onRemoveFromPlanTaskCallback;
	private java.util.function.BiConsumer<String, Boolean> onToggleShowLocationsCallback;
	private java.util.function.Consumer<TaskData> onMarkCompletedCallback;
	private java.util.Set<String> shownLocationTasks = new java.util.HashSet<>();
	private java.util.Set<String> hiddenTaskNames = new java.util.HashSet<>();
	private java.util.function.Consumer<String> onHideTaskCallback;
	private java.util.function.Consumer<String> onUnhideTaskCallback;
	private boolean showHidden = false;

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

		header.add(Box.createVerticalStrut(4));

		// Sync row
		JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		syncRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		syncRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		syncButton = new JButton("\u21BB Sync");
		syncButton.setFont(FontManager.getRunescapeSmallFont());
		syncButton.setForeground(Color.WHITE);
		syncButton.setBackground(new Color(60, 60, 60));
		syncButton.setPreferredSize(new Dimension(70, 20));
		syncButton.setBorder(new EmptyBorder(2, 8, 2, 8));
		syncButton.setFocusPainted(false);
		syncButton.setToolTipText("Push local completions to server & pull latest locations");
		syncButton.addActionListener(e -> {
			if (onSyncCallback != null)
			{
				syncButton.setEnabled(false);
				syncButton.setText("Syncing...");
				onSyncCallback.run();
			}
		});
		syncRow.add(syncButton);

		syncStatusLabel = new JLabel("");
		syncStatusLabel.setFont(FontManager.getRunescapeSmallFont());
		syncStatusLabel.setForeground(Color.GRAY);
		syncRow.add(syncStatusLabel);

		header.add(syncRow);

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

		// Build lock panel (shown until beta key entered)
		lockPanel = buildLockPanel();

		add(cardPanel, BorderLayout.CENTER);
	}

	// ========== Beta Lock ==========

	public void setOnBetaUnlocked(Runnable callback)
	{
		this.onBetaUnlocked = callback;
	}

	public void setBetaUnlocked(boolean unlocked)
	{
		this.betaUnlocked = unlocked;
		if (unlocked && lockPanel != null)
		{
			remove(lockPanel);
			lockPanel = null;
			add(cardPanel, BorderLayout.CENTER);
			revalidate();
			repaint();
		}
	}

	public void showBetaLock()
	{
		if (lockPanel == null) return;
		remove(cardPanel);
		add(lockPanel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private JPanel buildLockPanel()
	{
		JPanel lock = new JPanel();
		lock.setLayout(new BoxLayout(lock, BoxLayout.Y_AXIS));
		lock.setBackground(ColorScheme.DARK_GRAY_COLOR);
		lock.setBorder(new EmptyBorder(40, 20, 40, 20));

		JLabel lockIcon = new JLabel("\uD83D\uDD12");
		lockIcon.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN, 32));
		lockIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
		lock.add(lockIcon);
		lock.add(Box.createVerticalStrut(12));

		JLabel lockTitle = new JLabel("Beta Access Required");
		lockTitle.setFont(FontManager.getRunescapeBoldFont());
		lockTitle.setForeground(ColorScheme.BRAND_ORANGE);
		lockTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		lock.add(lockTitle);
		lock.add(Box.createVerticalStrut(8));

		JLabel lockDesc = new JLabel("<html><center>Enter the beta key to<br>unlock the plugin.</center></html>");
		lockDesc.setFont(FontManager.getRunescapeSmallFont());
		lockDesc.setForeground(Color.GRAY);
		lockDesc.setAlignmentX(Component.CENTER_ALIGNMENT);
		lock.add(lockDesc);
		lock.add(Box.createVerticalStrut(16));

		javax.swing.JTextField keyField = new javax.swing.JTextField(12);
		keyField.setMaximumSize(new Dimension(160, 28));
		keyField.setAlignmentX(Component.CENTER_ALIGNMENT);
		keyField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
		keyField.setToolTipText("Enter beta key");
		lock.add(keyField);
		lock.add(Box.createVerticalStrut(8));

		JButton unlockBtn = new JButton("Unlock");
		unlockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		unlockBtn.setFont(FontManager.getRunescapeSmallFont());
		lock.add(unlockBtn);
		lock.add(Box.createVerticalStrut(8));

		JLabel errorLabel = new JLabel(" ");
		errorLabel.setFont(FontManager.getRunescapeSmallFont());
		errorLabel.setForeground(new Color(255, 80, 80));
		errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		lock.add(errorLabel);

		Runnable tryUnlock = () -> {
			String input = keyField.getText().trim().toLowerCase();
			if ("fat cat".equals(input))
			{
				if (onBetaUnlocked != null)
				{
					onBetaUnlocked.run();
				}
				setBetaUnlocked(true);
			}
			else
			{
				errorLabel.setText("Incorrect beta key");
				keyField.setText("");
			}
		};

		unlockBtn.addActionListener(e -> tryUnlock.run());
		keyField.addActionListener(e -> tryUnlock.run());

		return lock;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, super.getPreferredSize().height);
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

		JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		toggleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		toggleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		allHideCompletedToggle.setFont(FontManager.getRunescapeSmallFont());
		allHideCompletedToggle.addActionListener(e -> rebuildAllTasksList());
		toggleRow.add(allHideCompletedToggle);

		JToggleButton showHiddenToggle = new JToggleButton("Show Hidden");
		showHiddenToggle.setFont(FontManager.getRunescapeSmallFont());
		showHiddenToggle.setPreferredSize(new Dimension(85, 22));
		showHiddenToggle.addActionListener(e -> {
			showHidden = showHiddenToggle.isSelected();
			rebuildAllTasksList();
		});
		toggleRow.add(showHiddenToggle);

		controls.add(toggleRow);
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

	public void setOnSync(Runnable callback)
	{
		this.onSyncCallback = callback;
	}

	public void setSyncStatus(String text, Color color)
	{
		if (syncStatusLabel != null)
		{
			syncStatusLabel.setText(text);
			syncStatusLabel.setForeground(color);
		}
	}

	public void setSyncEnabled(boolean enabled)
	{
		if (syncButton != null)
		{
			syncButton.setEnabled(enabled);
			if (enabled)
			{
				syncButton.setText("\u21BB Sync");
			}
		}
	}

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

	public void setOnRemoveFromPlan(java.util.function.Consumer<TaskData> callback)
	{
		this.onRemoveFromPlanTaskCallback = callback;
	}

	public void setOnToggleShowLocations(java.util.function.BiConsumer<String, Boolean> callback)
	{
		this.onToggleShowLocationsCallback = callback;
	}

	public void setOnMarkCompleted(java.util.function.Consumer<TaskData> callback)
	{
		this.onMarkCompletedCallback = callback;
	}

	public void setTaskLocationsShown(String taskName, boolean shown)
	{
		if (shown)
		{
			shownLocationTasks.add(taskName);
		}
		else
		{
			shownLocationTasks.remove(taskName);
		}
	}

	public void setOnHideTask(java.util.function.Consumer<String> callback)
	{
		this.onHideTaskCallback = callback;
	}

	public void setOnUnhideTask(java.util.function.Consumer<String> callback)
	{
		this.onUnhideTaskCallback = callback;
	}

	public void setHiddenTaskNames(java.util.Set<String> names)
	{
		this.hiddenTaskNames = names != null ? names : new java.util.HashSet<>();
	}

	// ========== Rebuild: All Tasks tab ==========

	public void rebuildAllTasksList()
	{
		allTaskListContainer.removeAll();

		String searchText = getSearchText(allSearchField);
		boolean hideCompleted = allHideCompletedToggle.isSelected();

		List<TaskData> filtered = allTasks.stream()
			.filter(t -> searchText.isEmpty() || t.getName().toLowerCase().contains(searchText))
			.filter(t -> !hideCompleted || !completedTaskNames.contains(t.getName()))
			.filter(t -> showHidden || !hiddenTaskNames.contains(t.getName()))
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

			Set<String> planNames = getPlanTaskNames();
			for (int i = 0; i < filtered.size(); i++)
			{
				TaskData t = filtered.get(i);
				boolean done = completedTaskNames.contains(t.getName());
				boolean inPlan = planNames.contains(t.getName());
				boolean isHidden = hiddenTaskNames.contains(t.getName());
				TaskRowPanel row = new TaskRowPanel(t, done, null, i % 2 == 0, inPlan, isHidden);
				row.setOnAddToPlan(onAddToPlanCallback);
				row.setOnRemoveFromPlan(onRemoveFromPlanTaskCallback);
				row.setOnMarkCompleted(onMarkCompletedCallback);
				row.setOnHideTask(td -> {
					if (onHideTaskCallback != null)
					{
						onHideTaskCallback.accept(td.getName());
					}
					hiddenTaskNames.add(td.getName());
					rebuildAllTasksList();
				});
				row.setOnUnhideTask(td -> {
					hiddenTaskNames.remove(td.getName());
					if (onUnhideTaskCallback != null)
					{
						onUnhideTaskCallback.accept(td.getName());
					}
					rebuildAllTasksList();
				});
				allTaskListContainer.add(row);
			}
		}

		allTaskListContainer.revalidate();
		allTaskListContainer.repaint();
	}

	// ========== Rebuild: Nearby tab ==========

	public void rebuildNearbyList()
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
			.filter(nt -> !hiddenTaskNames.contains(nt.getTask().getName()))
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

			Set<String> planNames = getPlanTaskNames();
			for (int i = 0; i < filtered.size(); i++)
			{
				NearbyTask nt = filtered.get(i);
				boolean done = completedTaskNames.contains(nt.getTask().getName());
				boolean inPlan = planNames.contains(nt.getTask().getName());
				TaskRowPanel nearbyRow = new TaskRowPanel(
					nt.getTask(), done, nt.getDistance(), i % 2 == 0, inPlan, false);
				nearbyRow.setOnAddToPlan(onAddToPlanCallback);
				nearbyRow.setOnRemoveFromPlan(onRemoveFromPlanTaskCallback);
				nearbyRow.setOnMarkCompleted(onMarkCompletedCallback);
				nearbyRow.setOnHideTask(td -> {
					if (onHideTaskCallback != null)
					{
						onHideTaskCallback.accept(td.getName());
					}
					hiddenTaskNames.add(td.getName());
					rebuildNearbyList();
				});
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
				PlanItem planItem = items.get(i);
				
				// Find TaskData for this plan item
				TaskData taskData = allTasks.stream()
					.filter(t -> t.getName().equals(planItem.getTaskName()))
					.findFirst()
					.orElse(null);
				
				if (taskData != null) {
					boolean done = completedTaskNames.contains(taskData.getName());
					
					// Wrapper panel to stack TaskRowPanel + LocationButtonsPanel
					JPanel itemWrapper = new JPanel();
					itemWrapper.setLayout(new BoxLayout(itemWrapper, BoxLayout.Y_AXIS));
					itemWrapper.setOpaque(false);
					itemWrapper.setAlignmentX(LEFT_ALIGNMENT);
					
					// Task row (same as All Tasks, but isInPlan=true always)
					TaskRowPanel taskRow = new TaskRowPanel(taskData, done, null, i % 2 == 0, true, false);
					taskRow.setOnAddToPlan(onAddToPlanCallback);
					taskRow.setOnRemoveFromPlan(onRemoveFromPlanTaskCallback);
					taskRow.setOnMarkCompleted(onMarkCompletedCallback);
					itemWrapper.add(taskRow);
					
					// Location buttons panel (the extra piece for Plan tab)
					if (locationService != null) {
						List<LocationCluster> locations = locationService.getLocationsForTask(taskData.getStructId());
						if (locations != null && !locations.isEmpty()) {
							boolean isShown = shownLocationTasks.contains(planItem.getTaskName());
							LocationButtonsPanel locationPanel = new LocationButtonsPanel(
								planItem.getTaskName(),
								locations,
								onToggleShowLocationsCallback,
								isShown
							);
							itemWrapper.add(locationPanel);
						}
					}
					
					planListContainer.add(itemWrapper);
				}
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

	private Set<String> getPlanTaskNames()
	{
		if (planService == null)
		{
			return Collections.emptySet();
		}
		return planService.getItems().stream()
			.map(PlanItem::getTaskName)
			.collect(Collectors.toSet());
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
