package com.ultimatetaskmaster.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.LocationCluster;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.TaskData;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A card in the Current Plan tab showing one planned task.
 *
 * Layout:
 *   [tier bar 4px] [order. name       ] [pts ] [X]
 *                  [category/skill     ]
 *                  [Suggested: [loc1] [loc2]  ]
 *                  [Pinned: x, y              ]
 */
public class PlanItemPanel extends JPanel
{
	private static final Color ODD_ROW_BG = new Color(44, 44, 44);
	private static final Color PINNED_COLOR = new Color(255, 140, 0);
	private static final Color SUGGESTION_COLOR = new Color(100, 100, 100);
	private static final Color SUGGESTION_HOVER = new Color(70, 70, 70);

	@Getter
	private final PlanItem planItem;
	@Getter
	private final TaskData task;

	private Color baseBackground;

	/**
	 * @param planItem         the plan item data
	 * @param task             the full task data (for name, tier, points, category)
	 * @param locations        suggested location clusters for this task
	 * @param isOddRow         for alternating row backgrounds
	 * @param onPin            callback when user pins a location: (taskName, cluster)
	 * @param onRemove         callback when user removes task from plan: (taskName)
	 */
	public PlanItemPanel(
		PlanItem planItem,
		TaskData task,
		List<LocationCluster> locations,
		boolean isOddRow,
		BiConsumer<String, LocationCluster> onPin,
		Consumer<String> onRemove)
	{
		super(new BorderLayout());
		this.planItem = planItem;
		this.task = task;

		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(new EmptyBorder(0, 0, 1, 0));

		JPanel container = new JPanel(new BorderLayout());
		container.setBorder(new EmptyBorder(4, 0, 4, 0));

		// --- Tier color bar (WEST) ---
		JPanel tierBar = new JPanel();
		tierBar.setPreferredSize(new Dimension(4, 0));
		tierBar.setBackground(task != null ? task.getTier().getColor() : Color.GRAY);
		container.add(tierBar, BorderLayout.WEST);

		// --- Body (CENTER) ---
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setBorder(new EmptyBorder(0, 6, 0, 4));

		// Row 1: order number + task name
		String orderStr = (planItem.getOrder() + 1) + ". ";
		String name = task != null ? task.getName() : planItem.getTaskName();
		JLabel nameLabel = new JLabel(orderStr + name);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(Color.WHITE);
		body.add(nameLabel);

		// Row 2: category/skill
		if (task != null)
		{
			String catSkill = buildCategorySkill(task);
			if (!catSkill.isEmpty())
			{
				JLabel catLabel = new JLabel(catSkill);
				catLabel.setFont(FontManager.getRunescapeSmallFont());
				catLabel.setForeground(new Color(200, 180, 120));
				body.add(catLabel);
			}
		}

		// Row 3: Suggested locations
		if (locations != null && !locations.isEmpty())
		{
			JPanel suggestRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
			suggestRow.setOpaque(false);

			JLabel sugLabel = new JLabel("Locations: ");
			sugLabel.setFont(FontManager.getRunescapeSmallFont());
			sugLabel.setForeground(Color.GRAY);
			suggestRow.add(sugLabel);

			int maxShow = Math.min(locations.size(), 5);
			for (int i = 0; i < maxShow; i++)
			{
				LocationCluster cluster = locations.get(i);
				boolean isPinned = planItem.getPinnedX() != null
					&& Math.abs(planItem.getPinnedX() - cluster.getX()) < 2
					&& planItem.getPinnedY() != null
					&& Math.abs(planItem.getPinnedY() - cluster.getY()) < 2;

				String btnText = cluster.getX() + "," + cluster.getY();
				if (cluster.getCount() > 1)
				{
					btnText += " (" + cluster.getCount() + ")";
				}

				JButton locBtn = new JButton(btnText);
				locBtn.setFont(FontManager.getRunescapeSmallFont());
				locBtn.setForeground(isPinned ? PINNED_COLOR : Color.LIGHT_GRAY);
				locBtn.setBackground(isPinned ? SUGGESTION_HOVER : SUGGESTION_COLOR);
				locBtn.setBorder(new EmptyBorder(1, 4, 1, 4));
				locBtn.setFocusPainted(false);
				locBtn.setToolTipText(isPinned ? "Currently pinned" : "Click to pin this location");

				final LocationCluster c = cluster;
				locBtn.addActionListener(e ->
				{
					if (onPin != null)
					{
						onPin.accept(planItem.getTaskName(), c);
					}
				});
				suggestRow.add(locBtn);
			}

			if (locations.size() > maxShow)
			{
				JLabel moreLabel = new JLabel("+" + (locations.size() - maxShow) + " more");
				moreLabel.setFont(FontManager.getRunescapeSmallFont());
				moreLabel.setForeground(Color.GRAY);
				suggestRow.add(moreLabel);
			}

			body.add(suggestRow);
		}

		// Row 4: Pinned location indicator
		if (planItem.getPinnedX() != null && planItem.getPinnedY() != null)
		{
			JLabel pinnedLabel = new JLabel("\uD83D\uDCCD " + planItem.getPinnedX() + ", " + planItem.getPinnedY());
			pinnedLabel.setFont(FontManager.getRunescapeSmallFont());
			pinnedLabel.setForeground(PINNED_COLOR);
			body.add(pinnedLabel);
		}

		container.add(body, BorderLayout.CENTER);

		// --- Right side: points + remove button (EAST) ---
		JPanel rightSide = new JPanel();
		rightSide.setLayout(new BoxLayout(rightSide, BoxLayout.Y_AXIS));
		rightSide.setOpaque(false);
		rightSide.setBorder(new EmptyBorder(0, 4, 0, 7));

		if (task != null)
		{
			JLabel pointsLabel = new JLabel(task.getPoints() + " pts");
			pointsLabel.setFont(FontManager.getRunescapeSmallFont());
			pointsLabel.setForeground(task.getTier().getColor());
			pointsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			pointsLabel.setAlignmentX(RIGHT_ALIGNMENT);
			rightSide.add(pointsLabel);
		}

		JButton removeBtn = new JButton("\u2715");
		removeBtn.setFont(FontManager.getRunescapeSmallFont());
		removeBtn.setForeground(Color.RED);
		removeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeBtn.setBorder(new EmptyBorder(1, 4, 1, 4));
		removeBtn.setFocusPainted(false);
		removeBtn.setToolTipText("Remove from plan");
		removeBtn.setAlignmentX(RIGHT_ALIGNMENT);
		removeBtn.addActionListener(e ->
		{
			if (onRemove != null)
			{
				onRemove.accept(planItem.getTaskName());
			}
		});
		rightSide.add(removeBtn);

		container.add(rightSide, BorderLayout.EAST);

		// --- Background ---
		baseBackground = isOddRow ? ODD_ROW_BG : ColorScheme.DARKER_GRAY_COLOR;
		container.setBackground(baseBackground);
		body.setBackground(baseBackground);

		add(container, BorderLayout.CENTER);

		// Hover effect
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				container.setBackground(baseBackground.brighter());
				body.setBackground(baseBackground.brighter());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				container.setBackground(baseBackground);
				body.setBackground(baseBackground);
			}
		});
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private static String buildCategorySkill(TaskData task)
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
}
