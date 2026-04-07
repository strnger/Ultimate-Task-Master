package com.ultimatetaskmaster.panel;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import com.ultimatetaskmaster.data.ItemIdMapping;
import com.ultimatetaskmaster.data.TaskItemRequirement;

/**
 * A compact horizontal row of item sprite icons for displaying task requirements.
 *
 * Pattern copied from Quest Helper / Inventory Setups:
 * - Full-color icon when player HAS the item
 * - Greyed-out (desaturated + alpha) when they DON'T
 * - Small quantity badge when quantity > 1
 * - Tooltip on hover showing item name + "x5" quantity
 *
 * Uses ItemManager.getImage(itemId) → AsyncBufferedImage for sprite loading.
 * Uses ImageUtil.grayscaleImage() + ImageUtil.alphaOffset() for greyed-out effect.
 */
public class ItemIconPanel extends JPanel
{
	private static final Dimension ICON_SIZE = new Dimension(20, 18);
	private static final int MAX_PANEL_HEIGHT = 40;
	private static final int GREY_ALPHA_OFFSET = -100;

	private final List<TaskItemRequirement> items;
	private final ItemManager itemManager;
	private Set<Integer> ownedItemIds;

	/**
	 * Creates a compact row of item sprite icons.
	 *
	 * @param items        the item requirements to display
	 * @param itemManager  RuneLite's ItemManager for getting item sprites
	 * @param ownedItemIds set of item IDs the player currently has (null = all greyed)
	 */
	public ItemIconPanel(List<TaskItemRequirement> items, ItemManager itemManager, Set<Integer> ownedItemIds)
	{
		super(new FlowLayout(FlowLayout.LEFT, 2, 0));
		setOpaque(false);
		setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, MAX_PANEL_HEIGHT));

		this.items = items != null ? items : Collections.emptyList();
		this.itemManager = itemManager;
		this.ownedItemIds = ownedItemIds;

		if (itemManager == null || this.items.isEmpty())
		{
			setVisible(false);
			return;
		}

		buildIcons();
	}

	/**
	 * Rebuilds the panel with a new set of owned item IDs.
	 *
	 * @param ownedItemIds the updated set of item IDs the player owns (null = all greyed)
	 */
	public void updateOwnedItems(Set<Integer> ownedItemIds)
	{
		this.ownedItemIds = ownedItemIds;
		removeAll();
		buildIcons();
		revalidate();
		repaint();
	}

	private void buildIcons()
	{
		int iconsAdded = 0;

		for (TaskItemRequirement item : items)
		{
			int itemId = resolveItemId(item);
			if (itemId == -1)
			{
				continue;
			}

			int quantity = item.getQuantity();
			boolean showStack = quantity > 1;
			AsyncBufferedImage image = itemManager.getImage(itemId, quantity, showStack);

			JLabel label = new JLabel();
			label.setPreferredSize(ICON_SIZE);
			label.setToolTipText(buildTooltip(item));

			boolean owned = isOwned(itemId, item);
			if (owned)
			{
				image.addTo(label);
			}
			else
			{
				// Start with the async image for immediate display, then grey it out
				image.addTo(label);
				image.onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						BufferedImage grey = ImageUtil.grayscaleImage(image);
						grey = ImageUtil.alphaOffset(grey, GREY_ALPHA_OFFSET);
						label.setIcon(new ImageIcon(grey));
					})
				);
			}

			add(label);
			iconsAdded++;
		}

		if (iconsAdded == 0)
		{
			setVisible(false);
		}
		else
		{
			setVisible(true);
		}
	}

	/**
	 * Resolves the RuneLite ItemID for a requirement.
	 * Prefers {@link TaskItemRequirement#getItemId()}, falls back to {@link ItemIdMapping}.
	 */
	private static int resolveItemId(TaskItemRequirement item)
	{
		int id = item.getItemId();
		if (id != -1)
		{
			return id;
		}
		return ItemIdMapping.getItemId(item.getName());
	}

	/**
	 * Checks whether the player owns an item, considering alternate IDs.
	 */
	private boolean isOwned(int primaryId, TaskItemRequirement item)
	{
		if (ownedItemIds == null)
		{
			return false;
		}

		if (ownedItemIds.contains(primaryId))
		{
			return true;
		}

		// Check alternate IDs from the requirement itself
		List<Integer> reqAlts = item.getAlternateIds();
		if (reqAlts != null)
		{
			for (int altId : reqAlts)
			{
				if (ownedItemIds.contains(altId))
				{
					return true;
				}
			}
		}

		// Check alternate IDs from the static mapping
		List<Integer> mappingAlts = ItemIdMapping.getAlternateIds(item.getName());
		for (int altId : mappingAlts)
		{
			if (ownedItemIds.contains(altId))
			{
				return true;
			}
		}

		return false;
	}

	private static String buildTooltip(TaskItemRequirement item)
	{
		if (item.getQuantity() > 1)
		{
			return item.getName() + " x" + item.getQuantity();
		}
		return item.getName();
	}
}
