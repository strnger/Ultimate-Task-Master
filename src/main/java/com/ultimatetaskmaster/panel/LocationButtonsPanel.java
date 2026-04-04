package com.ultimatetaskmaster.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.LocationCluster;
import net.runelite.client.ui.FontManager;

/**
 * Compact panel with a "Show on map" toggle for task locations.
 * Replaces the old coordinate buttons that overflowed the panel.
 */
public class LocationButtonsPanel extends JPanel
{
	private static final Color SHOWN_COLOR = new Color(255, 140, 0);

	public LocationButtonsPanel(String taskName, List<LocationCluster> locations,
								BiConsumer<String, Boolean> onToggleShowLocations,
								boolean isShownOnMap)
	{
		setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(0, 10, 3, 7));
		setAlignmentX(LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

		if (locations == null || locations.isEmpty())
		{
			return;
		}

		JLabel icon = new JLabel("\uD83D\uDCCD");
		icon.setFont(FontManager.getRunescapeSmallFont());
		add(icon);

		JToggleButton showBtn = new JToggleButton(locations.size() + " locations");
		showBtn.setSelected(isShownOnMap);
		showBtn.setFont(FontManager.getRunescapeSmallFont());
		showBtn.setForeground(isShownOnMap ? Color.WHITE : Color.LIGHT_GRAY);
		showBtn.setBackground(isShownOnMap ? SHOWN_COLOR : new Color(60, 60, 60));
		showBtn.setPreferredSize(new Dimension(110, 18));
		showBtn.setBorder(new EmptyBorder(2, 6, 2, 6));
		showBtn.setFocusPainted(false);
		showBtn.setToolTipText(isShownOnMap ? "Hide locations from map" : "Show all locations on world map");
		showBtn.addActionListener(e -> {
			boolean nowShown = showBtn.isSelected();
			showBtn.setForeground(nowShown ? Color.WHITE : Color.LIGHT_GRAY);
			showBtn.setBackground(nowShown ? SHOWN_COLOR : new Color(60, 60, 60));
			showBtn.setToolTipText(nowShown ? "Hide locations from map" : "Show all locations on world map");
			if (onToggleShowLocations != null)
			{
				onToggleShowLocations.accept(taskName, nowShown);
			}
		});
		add(showBtn);
	}
}
