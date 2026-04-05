package com.ultimatetaskmaster.overlay;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.ultimatetaskmaster.UltimateTaskMasterPlugin;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskItemRequirement;
import com.ultimatetaskmaster.data.TaskItemService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * Manages a custom bank tab view showing items needed per planned task.
 * Simplified adaptation of quest-helper's QuestBankTab pattern.
 *
 * When activated, hides normal bank items and displays plan items
 * organized by task with section headers.
 */
@Singleton
@Slf4j
public class UtmBankTab
{
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int TEXT_HEIGHT = 15;
	private static final int SECTION_SPACING = 5;

	private static final Color HEADER_COLOR = new Color(228, 216, 162);
	private static final Color TASK_HEADER_COLOR = new Color(255, 140, 0);

	@Inject
	private Client client;

	@Inject
	private PlanService planService;

	@Inject
	private TaskItemService taskItemService;

	@Inject
	private UltimateTaskMasterPlugin plugin;

	private final List<Widget> addedWidgets = new ArrayList<>();

	@Getter
	@Setter
	private boolean active = false;

	/**
	 * Activate the UTM bank tab view.
	 * Hides normal bank items and shows plan items organized by task.
	 */
	public void activate()
	{
		Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		if (itemContainer == null)
		{
			log.debug("Bank item container not found");
			return;
		}

		active = true;

		// Hide all existing bank items
		Widget[] children = itemContainer.getDynamicChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				child.setHidden(true);
			}
		}

		// Build our custom layout
		buildPlanItemLayout(itemContainer);

		log.info("UTM bank tab activated");
	}

	/**
	 * Deactivate the UTM bank tab view.
	 * Shows normal bank items and removes custom widgets.
	 */
	public void deactivate()
	{
		// Remove our added widgets
		for (Widget w : addedWidgets)
		{
			w.setHidden(true);
		}
		addedWidgets.clear();

		// Show all bank items again
		Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		if (itemContainer != null)
		{
			Widget[] children = itemContainer.getDynamicChildren();
			if (children != null)
			{
				for (Widget child : children)
				{
					child.setHidden(false);
				}
			}
		}

		active = false;
		log.info("UTM bank tab deactivated");
	}

	private void buildPlanItemLayout(Widget container)
	{
		// Remove any previous custom widgets
		for (Widget w : addedWidgets)
		{
			w.setHidden(true);
		}
		addedWidgets.clear();

		List<TaskData> allTasks = plugin.getEnrichedTasks();
		ItemContainer bank = client.getItemContainer(net.runelite.api.InventoryID.BANK);

		int yOffset = 0;

		for (PlanItem planItem : planService.getItems())
		{
			// Find the task
			TaskData task = null;
			for (TaskData t : allTasks)
			{
				if (t.getName().equals(planItem.getTaskName()))
				{
					task = t;
					break;
				}
			}
			if (task == null) continue;

			// Get item requirements
			List<TaskItemRequirement> items = taskItemService.getItemRequirements(task);
			if (items.isEmpty()) continue;

			// Add task header
			Widget header = createText(container, task.getName(),
				TASK_HEADER_COLOR.getRGB(),
				ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING,
				TEXT_HEIGHT,
				ITEM_ROW_START, yOffset + SECTION_SPACING);
			addedWidgets.add(header);
			yOffset += TEXT_HEIGHT + SECTION_SPACING;

			// Add items for this task
			int itemsInRow = 0;
			for (TaskItemRequirement itemReq : items)
			{
				// Try to find the item ID
				int itemId = itemReq.getItemId();
				if (itemId <= 0)
				{
					itemId = findItemIdByName(itemReq.getName());
				}

				if (itemId <= 0)
				{
					// Can't display without an item ID — show as text instead
					Widget textItem = createText(container, "\u2022 " + itemReq.getName(),
						Color.LIGHT_GRAY.getRGB(),
						ITEM_HORIZONTAL_SPACING * 2,
						TEXT_HEIGHT,
						ITEM_ROW_START + (itemsInRow % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING,
						yOffset);
					addedWidgets.add(textItem);
					itemsInRow++;
					if (itemsInRow % ITEMS_PER_ROW == 0)
					{
						yOffset += ITEM_VERTICAL_SPACING;
					}
					continue;
				}

				// Create item graphic widget
				int x = ITEM_ROW_START + (itemsInRow % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING;
				int y = yOffset;

				Widget itemWidget = container.createChild(-1, WidgetType.GRAPHIC);
				itemWidget.setItemId(itemId);
				int bankQty = bank != null ? bank.count(itemId) : 0;
				itemWidget.setItemQuantity(bankQty);
				itemWidget.setItemQuantityMode(ItemQuantityMode.ALWAYS);
				itemWidget.setOriginalWidth(36);
				itemWidget.setOriginalHeight(32);
				itemWidget.setOriginalX(x);
				itemWidget.setOriginalY(y);
				itemWidget.setBorderType(1);

				ItemComposition def = client.getItemDefinition(itemId);
				if (def != null)
				{
					itemWidget.setName("<col=ff9040>" + def.getName() + "</col>");
				}

				// Dim if player doesn't have it
				if (bankQty <= 0)
				{
					itemWidget.setOpacity(150);
				}

				itemWidget.revalidate();
				addedWidgets.add(itemWidget);

				// Add quantity text (have/need)
				String qtyText = bankQty + "/" + itemReq.getQuantity();
				Widget qtyWidget = createText(container, qtyText,
					bankQty >= itemReq.getQuantity() ? Color.GREEN.getRGB() : Color.RED.getRGB(),
					ITEM_HORIZONTAL_SPACING,
					TEXT_HEIGHT - 3,
					x + 2, y + 24);
				addedWidgets.add(qtyWidget);

				itemsInRow++;
				if (itemsInRow % ITEMS_PER_ROW == 0)
				{
					yOffset += ITEM_VERTICAL_SPACING;
				}
			}

			// Move to next row if partial row
			if (itemsInRow % ITEMS_PER_ROW != 0)
			{
				yOffset += ITEM_VERTICAL_SPACING;
			}

			yOffset += SECTION_SPACING;
		}

		// Update scroll height
		container.setScrollHeight(Math.max(yOffset, container.getHeight()));
		log.info("Built UTM bank layout: {} widgets added", addedWidgets.size());
	}

	/**
	 * Try to find an item ID by searching bank contents for a matching name.
	 */
	private int findItemIdByName(String name)
	{
		if (name == null) return -1;

		ItemContainer bank = client.getItemContainer(net.runelite.api.InventoryID.BANK);
		if (bank == null) return -1;

		String lower = name.toLowerCase();
		for (net.runelite.api.Item item : bank.getItems())
		{
			if (item.getId() <= 0) continue;
			ItemComposition def = client.getItemDefinition(item.getId());
			if (def != null && def.getName() != null && def.getName().toLowerCase().equals(lower))
			{
				return item.getId();
			}
		}
		return -1;
	}

	private Widget createText(Widget container, String text, int color, int width, int height, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setText(text);
		widget.setFontId(FontID.PLAIN_11);
		widget.setTextColor(color);
		widget.setTextShadowed(true);
		widget.revalidate();
		return widget;
	}
}
