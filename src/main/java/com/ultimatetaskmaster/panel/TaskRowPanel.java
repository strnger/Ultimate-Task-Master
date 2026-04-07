package com.ultimatetaskmaster.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskItemRequirement;
import com.ultimatetaskmaster.data.TaskSkillRequirement;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

/**
 * A single task row in the task list panel.
 * Layout follows tasks-tracker's TaskPanel pattern exactly.
 *
 * Layout:
 *   [tier bar] [name          ] [+/- btn]
 *              [category · pts]
 */
public class TaskRowPanel extends JPanel
{
	static final Color COMPLETED_BG = new Color(0, 50, 0);

	private static final javax.swing.ImageIcon PLUS_ICON = new javax.swing.ImageIcon(
		net.runelite.client.util.ImageUtil.loadImageResource(
			com.ultimatetaskmaster.UltimateTaskMasterPlugin.class, "plus.png"));
	private static final javax.swing.ImageIcon MINUS_ICON = new javax.swing.ImageIcon(
		net.runelite.client.util.ImageUtil.loadImageResource(
			com.ultimatetaskmaster.UltimateTaskMasterPlugin.class, "minus.png"));

	@Getter
	private final TaskData task;
	@Getter
	private final boolean completed;
	private final Integer distance;
	private boolean isInPlan;
	private final boolean isHidden;
	private java.util.List<TaskItemRequirement> itemRequirements = java.util.Collections.emptyList();

	private Consumer<TaskData> onAddToPlan;
	private Consumer<TaskData> onRemoveFromPlan;
	private Consumer<TaskData> onMarkCompleted;
	private ItemManager itemManager;
	private Consumer<TaskData> onHideTask;
	private Consumer<TaskData> onUnhideTask;
	private java.util.Set<Integer> ownedItemIds;

	private final JPanel container;
	private final JPanel body;
	private final JPanel buttons;
	private Color baseBackground;
	private ItemIconPanel itemIconPanel;

	public TaskRowPanel(TaskData task, boolean completed, Integer distance, boolean isOddRow, boolean isInPlan, boolean isHidden)
	{
		super(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		this.task = task;
		this.completed = completed;
		this.distance = distance;
		this.isInPlan = isInPlan;
		this.isHidden = isHidden;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 7, 0));

		// --- Highlight wrapper (tasks-tracker pattern) ---
		JPanel highlightContainer = new JPanel(new BorderLayout());

		// --- Main container ---
		container = new JPanel(new BorderLayout());
		container.setBorder(new EmptyBorder(7, 7, 6, 0));

		// --- Tier color bar (WEST) - as a JLabel for consistency ---
		JLabel tierBar = new JLabel();
		tierBar.setMinimumSize(new Dimension(4, 0));
		tierBar.setPreferredSize(new Dimension(4, 0));
		tierBar.setOpaque(true);
		tierBar.setBackground(task.getTier().getColor());
		container.add(tierBar, BorderLayout.WEST);

		// --- Body (CENTER) - using BorderLayout like tasks-tracker ---
		body = new JPanel(new BorderLayout());
		body.setBorder(new EmptyBorder(0, 6, 0, 0));

		// Name (NORTH of body)
		JLabel nameLabel = new JLabel(task.getName());
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(Color.WHITE);
		body.add(nameLabel, BorderLayout.NORTH);

		// Subtitle: category + points + area (CENTER of body)
		String subtitle = buildSubtitle(task, distance);
		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subtitleLabel.setForeground(Color.GRAY);
		body.add(subtitleLabel, BorderLayout.CENTER);

		container.add(body, BorderLayout.CENTER);

		// --- Buttons (EAST) - following tasks-tracker pattern ---
		buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
		buttons.setBorder(new EmptyBorder(0, 0, 0, 7));

		// Create toggle button (icons are static constants - loaded once, shared by all rows)
		JToggleButton planBtn = new JToggleButton();
		planBtn.setIcon(PLUS_ICON);
		planBtn.setSelectedIcon(MINUS_ICON);
		planBtn.setSelected(isInPlan);
		planBtn.setPreferredSize(new Dimension(16, 16));
		planBtn.setToolTipText(isInPlan ? "Remove from plan" : "Add to plan");
		planBtn.setBorder(new EmptyBorder(5, 0, 5, 0));
		planBtn.addActionListener(e -> {
			boolean nowSelected = planBtn.isSelected();
			if (nowSelected && onAddToPlan != null)
			{
				onAddToPlan.accept(task);
				planBtn.setToolTipText("Remove from plan");
			}
			else if (!nowSelected && onRemoveFromPlan != null)
			{
				onRemoveFromPlan.accept(task);
				planBtn.setToolTipText("Add to plan");
			}
		});
		SwingUtil.removeButtonDecorations(planBtn);
		buttons.add(planBtn);

		// "Mark as Completed" button — sends completion to crowdsourcing server
		if (!completed)
		{
			JToggleButton markDoneBtn = new JToggleButton();
			// Use a checkmark Unicode character as a simple icon
			markDoneBtn.setText("\u2713");
			markDoneBtn.setFont(FontManager.getRunescapeSmallFont());
			markDoneBtn.setForeground(new Color(100, 255, 100));
			markDoneBtn.setPreferredSize(new Dimension(16, 16));
			markDoneBtn.setToolTipText("Mark as Completed (submit to crowdsourcing)");
			markDoneBtn.setBorder(new EmptyBorder(2, 0, 2, 0));
			SwingUtil.removeButtonDecorations(markDoneBtn);
			markDoneBtn.addActionListener(e -> {
				if (onMarkCompleted != null)
				{
					onMarkCompleted.accept(task);
					markDoneBtn.setEnabled(false);
					markDoneBtn.setForeground(Color.GRAY);
					markDoneBtn.setToolTipText("Submitted!");
				}
			});
			buttons.add(markDoneBtn);
		}

		// Hide/Show button
		if (isHidden)
		{
			// Task is hidden — show "unhide" button
			JToggleButton showBtn = new JToggleButton("\u25CB");
			showBtn.setFont(FontManager.getRunescapeSmallFont());
			showBtn.setForeground(new Color(100, 200, 100));
			showBtn.setPreferredSize(new Dimension(16, 16));
			showBtn.setToolTipText("Unhide this task");
			showBtn.setBorder(new EmptyBorder(2, 0, 2, 0));
			SwingUtil.removeButtonDecorations(showBtn);
			showBtn.addActionListener(e -> {
				if (onUnhideTask != null)
				{
					onUnhideTask.accept(task);
				}
			});
			buttons.add(showBtn);
		}
		else
		{
			// Normal — show "hide" button
			JToggleButton hideBtn = new JToggleButton("\u2715");
			hideBtn.setFont(FontManager.getRunescapeSmallFont());
			hideBtn.setForeground(Color.GRAY);
			hideBtn.setPreferredSize(new Dimension(16, 16));
			hideBtn.setToolTipText("Hide this task");
			hideBtn.setBorder(new EmptyBorder(2, 0, 2, 0));
			SwingUtil.removeButtonDecorations(hideBtn);
			hideBtn.addActionListener(e -> {
				if (onHideTask != null)
				{
					onHideTask.accept(task);
				}
			});
			buttons.add(hideBtn);
		}

		container.add(buttons, BorderLayout.EAST);

		// --- Wire it all together (tasks-tracker pattern) ---
		highlightContainer.add(container, BorderLayout.NORTH);
		add(highlightContainer, BorderLayout.NORTH);

		// --- Background ---
		baseBackground = completed ? COMPLETED_BG
			: isOddRow ? new Color(44, 44, 44)
			: ColorScheme.DARKER_GRAY_COLOR;
		setBackgroundColor(baseBackground);

		// --- Hover ---
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackgroundColor(baseBackground.brighter());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackgroundColor(baseBackground);
			}
		});

		// --- Tooltip ---
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	public void setOnAddToPlan(Consumer<TaskData> callback)
	{
		this.onAddToPlan = callback;
	}

	public void setOnRemoveFromPlan(Consumer<TaskData> callback)
	{
		this.onRemoveFromPlan = callback;
	}

	public void setOnMarkCompleted(Consumer<TaskData> callback)
	{
		this.onMarkCompleted = callback;
	}

	public void setOnHideTask(Consumer<TaskData> callback)
	{
		this.onHideTask = callback;
	}

	public void setOnUnhideTask(Consumer<TaskData> callback)
	{
		this.onUnhideTask = callback;
	}

	public void setOwnedItemIds(java.util.Set<Integer> ids)
	{
		this.ownedItemIds = ids;
	}

	public void setItemRequirements(java.util.List<TaskItemRequirement> items)
	{
		this.itemRequirements = items != null ? items : java.util.Collections.emptyList();

		// Build the item icon panel if we have items and an item manager
		if (!this.itemRequirements.isEmpty() && this.itemManager != null)
		{
			if (itemIconPanel != null)
			{
				body.remove(itemIconPanel);
			}
			itemIconPanel = new ItemIconPanel(this.itemRequirements, this.itemManager, this.ownedItemIds);
			body.add(itemIconPanel, java.awt.BorderLayout.SOUTH);
			body.revalidate();
			body.repaint();
		}
	}

	public void setItemManager(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		return buildTooltip();
	}

	@Override
	public JToolTip createToolTip()
	{
		JToolTip tip = new JToolTip();
		tip.setFont(FontManager.getRunescapeSmallFont());
		return tip;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, getPreferredSize().height);
	}

	private void setBackgroundColor(Color color)
	{
		container.setBackground(color);
		body.setBackground(color);
		buttons.setBackground(color);
	}

	private static String buildSubtitle(TaskData task, Integer distance)
	{
		StringBuilder sb = new StringBuilder();

		// Category
		if (task.getCategory() != null && !task.getCategory().isEmpty())
		{
			sb.append(task.getCategory());
		}

		// Points
		sb.append(sb.length() > 0 ? " \u00b7 " : "");
		sb.append(task.getPoints()).append(" pts");

		// Area or distance
		if (distance != null)
		{
			sb.append(" \u00b7 ").append(distance).append(" tiles");
		}
		else
		{
			sb.append(" \u00b7 ").append(task.getArea().getDisplayName());
		}

		return sb.toString();
	}

	private String buildTooltip()
	{
		StringBuilder sb = new StringBuilder("<html><body style='width:200px'>");
		sb.append("<b>").append(task.getName()).append("</b><br>");
		sb.append(task.getDescription()).append("<br><br>");
		sb.append("Area: ").append(task.getArea().getDisplayName()).append("<br>");
		if (task.getCategory() != null)
		{
			sb.append("Category: ").append(task.getCategory()).append("<br>");
		}
		if (task.getSkill() != null)
		{
			sb.append("Skill: ").append(task.getSkill()).append("<br>");
		}
		sb.append("Tier: ").append(task.getTier().getDisplayName())
			.append(" (").append(task.getPoints()).append(" pts)").append("<br>");

		if (distance != null)
		{
			sb.append("Distance: ").append(distance).append(" tiles<br>");
		}

		if (completed)
		{
			sb.append("<br><b style='color:#22b14d'>\u2714 Completed</b><br>");
		}

		if (task.getCompletionPct() != null)
		{
			sb.append("Players completed: ")
				.append(String.format("%.1f%%", task.getCompletionPct())).append("<br>");
		}

		if (task.getRequirements() != null && !task.getRequirements().isEmpty())
		{
			sb.append("<br>Requirements:<br>");
			for (TaskSkillRequirement req : task.getRequirements())
			{
				sb.append("\u2022 ").append(req.getLevel()).append(" ")
					.append(req.getSkill()).append("<br>");
			}
		}

		if (!itemRequirements.isEmpty())
		{
			sb.append("<br><b>Items Needed:</b><br>");
			for (TaskItemRequirement item : itemRequirements)
			{
				sb.append("\u2022 ").append(item.getName());
				if (item.getQuantity() > 1)
				{
					sb.append(" x").append(item.getQuantity());
				}
				sb.append("<br>");
			}
		}

		sb.append("<br><span style='color:gray;font-size:9px'>Struct ID: ").append(task.getStructId()).append("</span><br>");
		sb.append("</body></html>");
		return sb.toString();
	}
}
