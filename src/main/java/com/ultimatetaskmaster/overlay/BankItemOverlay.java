package com.ultimatetaskmaster.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import com.ultimatetaskmaster.UltimateTaskMasterPlugin;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import com.ultimatetaskmaster.data.TaskData;
import com.ultimatetaskmaster.data.TaskItemRequirement;
import com.ultimatetaskmaster.data.TaskItemService;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Overlay that appears on top of the bank interface showing
 * item requirements for planned tasks.
 *
 * Only renders when the bank is open and the plan has tasks with item data.
 * Simple overlay approach — no dependency on RuneLite internal bank tag APIs.
 */
public class BankItemOverlay extends Overlay
{
	private static final Color TITLE_COLOR = new Color(255, 140, 0);
	private static final Color ITEM_COLOR = new Color(200, 200, 200);

	private final Client client;
	private final UltimateTaskMasterPlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	private PlanService planService;

	@Inject
	private TaskItemService taskItemService;

	@Inject
	public BankItemOverlay(Client client, UltimateTaskMasterPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only show when bank is open
		Widget bankWidget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (bankWidget == null || bankWidget.isHidden())
		{
			return null;
		}

		if (planService == null || taskItemService == null)
		{
			return null;
		}

		// Collect items needed for planned tasks
		Set<String> itemDisplays = new LinkedHashSet<>();
		List<TaskData> tasks = plugin.getEnrichedTasks();

		for (PlanItem planItem : planService.getItems())
		{
			TaskData task = null;
			if (tasks != null)
			{
				for (TaskData t : tasks)
				{
					if (t.getName().equals(planItem.getTaskName()))
					{
						task = t;
						break;
					}
				}
			}
			if (task != null)
			{
				List<TaskItemRequirement> items = taskItemService.getItemRequirements(task);
				for (TaskItemRequirement item : items)
				{
					String display = item.getName();
					if (item.getQuantity() > 1)
					{
						display += " x" + item.getQuantity();
					}
					itemDisplays.add(display);
				}
			}
		}

		if (itemDisplays.isEmpty())
		{
			return null;
		}

		// Build the overlay panel
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(200, 0));

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("UTM Plan Items")
			.color(TITLE_COLOR)
			.build());

		for (String item : itemDisplays)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(item)
				.leftColor(ITEM_COLOR)
				.build());
		}

		return panelComponent.render(graphics);
	}
}
