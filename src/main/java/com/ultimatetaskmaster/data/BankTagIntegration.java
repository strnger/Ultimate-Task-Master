package com.ultimatetaskmaster.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * Integrates with RuneLite's bank tags system to show a "UTM Plan" tab
 * in the bank that filters to show items needed for planned tasks.
 *
 * Uses TagManager.registerTag() to create a virtual bank tag backed by
 * our planned item list, and BankTagsService.openBankTag() to activate it.
 */
@Singleton
@Slf4j
public class BankTagIntegration
{
	private static final String TAG_NAME = "UTM Plan";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PlanService planService;

	@Inject
	private TaskItemService taskItemService;

	private Set<Integer> planItemIds = new HashSet<>();
	private boolean registered = false;

	/**
	 * Register the UTM Plan bank tag. Call from plugin startUp().
	 */
	public void register()
	{
		BankTag utmTag = itemId -> planItemIds.contains(itemId);
		tagManager.registerTag(TAG_NAME, utmTag);
		registered = true;
		log.info("Registered UTM Plan bank tag");
	}

	/**
	 * Unregister the UTM Plan bank tag. Call from plugin shutDown().
	 */
	public void unregister()
	{
		if (registered)
		{
			tagManager.unregisterTag(TAG_NAME);
			registered = false;
		}
	}

	/**
	 * Rebuild the set of item IDs needed for the plan.
	 * Call whenever the plan changes or tasks are loaded.
	 *
	 * @param allTasks the enriched task list for looking up TaskData by name
	 */
	public void refreshPlanItems(List<TaskData> allTasks)
	{
		Set<Integer> newIds = new HashSet<>();

		for (PlanItem planItem : planService.getItems())
		{
			TaskData task = null;
			for (TaskData t : allTasks)
			{
				if (t.getName().equals(planItem.getTaskName()))
				{
					task = t;
					break;
				}
			}
			if (task == null)
			{
				continue;
			}

			List<TaskItemRequirement> items = taskItemService.getItemRequirements(task);
			for (TaskItemRequirement item : items)
			{
				if (item.getItemId() > 0)
				{
					newIds.add(item.getItemId());
				}
				else
				{
					// Look up item ID by name using ItemManager
					int id = lookupItemId(item.getName());
					if (id > 0)
					{
						newIds.add(id);
					}
				}
			}
		}

		planItemIds = newIds;
		log.debug("UTM Plan bank tag: {} item IDs", planItemIds.size());
	}

	/**
	 * Open the UTM Plan bank tag to filter the bank.
	 * Must be called on the client thread when the bank is open.
	 */
	public void openPlanTag()
	{
		clientThread.invokeLater(() -> {
			bankTagsService.openBankTag(TAG_NAME, 0);
		});
	}

	/**
	 * Close the UTM Plan bank tag filter.
	 */
	public void closePlanTag()
	{
		clientThread.invokeLater(() -> {
			bankTagsService.closeBankTag();
		});
	}

	/**
	 * Check if the UTM Plan tag is currently active.
	 */
	public boolean isActive()
	{
		String activeTag = bankTagsService.getActiveTag();
		return TAG_NAME.equals(activeTag);
	}

	/**
	 * Look up an item ID by name. Returns -1 if not found.
	 * Uses RuneLite's ItemManager search.
	 */
	private int lookupItemId(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return -1;
		}

		try
		{
			// Try exact name search via item compositions
			List<net.runelite.http.api.item.ItemPrice> results = itemManager.search(itemName);
			if (results != null && !results.isEmpty())
			{
				// Find exact match first
				for (net.runelite.http.api.item.ItemPrice result : results)
				{
					if (result.getName().equalsIgnoreCase(itemName))
					{
						return result.getId();
					}
				}
				// Fall back to first result
				return results.get(0).getId();
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to look up item: {}", itemName);
		}

		return -1;
	}
}
