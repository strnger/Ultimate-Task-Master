package com.ultimatetaskmaster.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskSkillRequirement;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A single task row in the task list panel.
 *
 * Visual pattern copied from tasks-tracker's TaskPanel:
 * - BorderLayout container with tier icon/bar on WEST, body in CENTER, metadata on EAST
 * - Background color encodes state: normal, completed (green tint), unqualified (red tint)
 * - RunescapeSmallFont for consistency with other RuneLite panels
 * - Hover highlight for interactivity feedback
 * - Rich tooltip on hover with full task details
 *
 * Layout:
 *   [tier bar 4px] [name          ] [points]
 *                  [category/skill] [area  ]
 */
public class TaskRowPanel extends JPanel
{
	/** Background for completed tasks — green tint, matches tasks-tracker. */
	static final Color COMPLETED_BG = new Color(0, 50, 0);
	/** Background for odd rows to create visual banding. */
	private static final Color ODD_ROW_BG = new Color(44, 44, 44);

	@Getter
	private final TaskData task;
	@Getter
	private final boolean completed;
	private final Integer distance;

	private final JPanel container;
	private final JPanel body;
	private Color baseBackground;

	/**
	 * @param task      the task to display
	 * @param completed whether this task has been completed by the player
	 * @param distance  tiles from player (null if no location / not in "nearby" mode)
	 * @param isOddRow  for alternating row backgrounds
	 */
	public TaskRowPanel(TaskData task, boolean completed, Integer distance, boolean isOddRow)
	{
		super(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		this.task = task;
		this.completed = completed;
		this.distance = distance;

		setBorder(new EmptyBorder(0, 0, 1, 0));

		// --- Container with padding ---
		container = new JPanel(new BorderLayout());
		container.setBorder(new EmptyBorder(4, 0, 3, 0));

		// --- Tier color bar (WEST) ---
		JPanel tierBar = new JPanel();
		tierBar.setPreferredSize(new Dimension(4, 0));
		tierBar.setBackground(task.getTier().getColor());
		container.add(tierBar, BorderLayout.WEST);

		// --- Body: name + category/skill (CENTER) ---
		body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setBorder(new EmptyBorder(0, 6, 0, 4));

		JLabel nameLabel = new JLabel(task.getName());
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(Color.WHITE);
		body.add(nameLabel);

		String categorySkill = buildCategorySkillText(task);
		if (!categorySkill.isEmpty())
		{
			JLabel categoryLabel = new JLabel(categorySkill);
			categoryLabel.setFont(FontManager.getRunescapeSmallFont());
			categoryLabel.setForeground(new Color(200, 180, 120)); // gold-ish color for category
			body.add(categoryLabel);
		}

		container.add(body, BorderLayout.CENTER);

		// --- Right side: points + area/distance (EAST) ---
		JPanel rightSide = new JPanel();
		rightSide.setLayout(new BoxLayout(rightSide, BoxLayout.Y_AXIS));
		rightSide.setOpaque(false);
		rightSide.setBorder(new EmptyBorder(0, 4, 0, 7));

		JLabel pointsLabel = new JLabel(task.getPoints() + " pts");
		pointsLabel.setFont(FontManager.getRunescapeSmallFont());
		pointsLabel.setForeground(task.getTier().getColor());
		pointsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		pointsLabel.setAlignmentX(RIGHT_ALIGNMENT);
		rightSide.add(pointsLabel);

		if (distance != null)
		{
			JLabel distLabel = new JLabel(distance + " tiles");
			distLabel.setFont(FontManager.getRunescapeSmallFont());
			distLabel.setForeground(getDistanceColor(distance));
			distLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			distLabel.setAlignmentX(RIGHT_ALIGNMENT);
			rightSide.add(distLabel);
		}
		else
		{
			JLabel areaLabel = new JLabel(task.getArea().getDisplayName());
			areaLabel.setFont(FontManager.getRunescapeSmallFont());
			areaLabel.setForeground(Color.GRAY);
			areaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			areaLabel.setAlignmentX(RIGHT_ALIGNMENT);
			rightSide.add(areaLabel);
		}

		container.add(rightSide, BorderLayout.EAST);

		// --- Background color ---
		baseBackground = completed ? COMPLETED_BG
			: isOddRow ? ODD_ROW_BG
			: ColorScheme.DARKER_GRAY_COLOR;
		setBackgroundColor(baseBackground);

		add(container, BorderLayout.CENTER);

		// --- Hover effect (tasks-tracker pattern) ---
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
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void setBackgroundColor(Color color)
	{
		container.setBackground(color);
		body.setBackground(color);
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
			sb.append("<br><b style='color:#22b14d'>✔ Completed</b><br>");
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
				sb.append("&bull; ").append(req.getLevel()).append(" ")
					.append(req.getSkill()).append("<br>");
			}
		}

		sb.append("<br><span style='color:gray;font-size:9px'>Struct ID: ").append(task.getStructId()).append("</span><br>");
		sb.append("</body></html>");
		return sb.toString();
	}

	private static String buildCategorySkillText(TaskData task)
	{
		StringBuilder sb = new StringBuilder();
		if (task.getCategory() != null && !task.getCategory().isEmpty())
		{
			sb.append(task.getCategory());
		}
		if (task.getSkill() != null && !task.getSkill().isEmpty() && !"All".equals(task.getSkill()))
		{
			if (sb.length() > 0)
			{
				sb.append(" \u2022 ");
			}
			sb.append(task.getSkill());
		}
		return sb.toString();
	}

	private static Color getDistanceColor(int distance)
	{
		if (distance <= 10)
		{
			return new Color(34, 177, 77);
		}
		if (distance <= 50)
		{
			return new Color(210, 193, 53);
		}
		return Color.WHITE;
	}

}
