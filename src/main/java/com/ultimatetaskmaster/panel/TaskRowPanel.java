package com.ultimatetaskmaster.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskSkillRequirement;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

/**
 * A single task row in the task list panel.
 * Layout follows tasks-tracker's TaskPanel pattern exactly.
 *
 * Layout:
 *   [tier bar] [name          ] [+ btn]
 *              [category · pts]
 */
public class TaskRowPanel extends JPanel
{
	static final Color COMPLETED_BG = new Color(0, 50, 0);

	@Getter
	private final TaskData task;
	@Getter
	private final boolean completed;
	private final Integer distance;

	private Consumer<TaskData> onAddToPlan;

	private final JPanel container;
	private final JPanel body;
	private final JPanel buttons;
	private Color baseBackground;

	public TaskRowPanel(TaskData task, boolean completed, Integer distance, boolean isOddRow)
	{
		super(new BorderLayout());
		this.task = task;
		this.completed = completed;
		this.distance = distance;

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

		JToggleButton addBtn = new JToggleButton();
		addBtn.setPreferredSize(new Dimension(10, 10));
		addBtn.setFont(FontManager.getRunescapeBoldFont());
		addBtn.setText("+");
		addBtn.setForeground(new Color(0, 200, 83));
		addBtn.setToolTipText("Add to plan");
		addBtn.setBorder(new EmptyBorder(3, 0, 3, 0));
		addBtn.addActionListener(e -> {
			if (onAddToPlan != null)
			{
				onAddToPlan.accept(task);
			}
			addBtn.setSelected(false);
		});
		SwingUtil.removeButtonDecorations(addBtn);
		buttons.add(addBtn);

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

		sb.append("<br><span style='color:gray;font-size:9px'>Struct ID: ").append(task.getStructId()).append("</span><br>");
		sb.append("</body></html>");
		return sb.toString();
	}
}
