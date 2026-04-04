package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Manages the user's task plan — an ordered list of tasks they want to complete.
 *
 * Each plan item can optionally have a pinned location (chosen from suggested
 * locations provided by TaskLocationService).
 *
 * Plan data is persisted in ConfigManager and survives client restarts.
 */
@Singleton
@Slf4j
public class PlanService
{
	private static final String CONFIG_GROUP = "ultimate-task-master";
	private static final String CONFIG_KEY = "plan-items";
	private static final Type PLAN_LIST_TYPE =
		TypeToken.getParameterized(List.class, PlanItem.class).getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<PlanItem> items = new ArrayList<>();

	@Inject
	public PlanService(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		loadFromConfig();
	}

	/** Get all plan items in order. Returns an unmodifiable view. */
	public synchronized List<PlanItem> getItems()
	{
		return Collections.unmodifiableList(new ArrayList<>(items));
	}

	/** Number of items in the plan. */
	public synchronized int size()
	{
		return items.size();
	}

	/** Check if a task is already in the plan. */
	public synchronized boolean containsTask(String taskName)
	{
		return items.stream().anyMatch(i -> i.getTaskName().equals(taskName));
	}

	/** Add a task to the end of the plan. Returns false if already present. */
	public synchronized boolean addTask(String taskName, int structId)
	{
		if (containsTask(taskName))
		{
			return false;
		}
		PlanItem item = new PlanItem(taskName, structId, items.size(), null, null);
		items.add(item);
		persist();
		log.debug("Added to plan: {} (structId={})", taskName, structId);
		return true;
	}

	/** Remove a task from the plan by name. */
	public synchronized boolean removeTask(String taskName)
	{
		boolean removed = items.removeIf(i -> i.getTaskName().equals(taskName));
		if (removed)
		{
			reindex();
			persist();
			log.debug("Removed from plan: {}", taskName);
		}
		return removed;
	}

	/** Move a task to a new position in the plan. */
	public synchronized void moveTask(int fromIndex, int toIndex)
	{
		if (fromIndex < 0 || fromIndex >= items.size()
			|| toIndex < 0 || toIndex >= items.size()
			|| fromIndex == toIndex)
		{
			return;
		}
		PlanItem item = items.remove(fromIndex);
		items.add(toIndex, item);
		reindex();
		persist();
	}

	/** Pin a location for a task. */
	public synchronized void pinLocation(String taskName, int x, int y)
	{
		for (PlanItem item : items)
		{
			if (item.getTaskName().equals(taskName))
			{
				item.setPinnedX(x);
				item.setPinnedY(y);
				persist();
				log.debug("Pinned location for {}: ({}, {})", taskName, x, y);
				return;
			}
		}
	}

	/** Unpin the location for a task. */
	public synchronized void unpinLocation(String taskName)
	{
		for (PlanItem item : items)
		{
			if (item.getTaskName().equals(taskName))
			{
				item.setPinnedX(null);
				item.setPinnedY(null);
				persist();
				return;
			}
		}
	}

	/** Clear the entire plan. */
	public synchronized void clear()
	{
		items.clear();
		persist();
	}

	/** Get all items that have a pinned location. */
	public synchronized List<PlanItem> getPinnedItems()
	{
		List<PlanItem> pinned = new ArrayList<>();
		for (PlanItem item : items)
		{
			if (item.getPinnedX() != null && item.getPinnedY() != null)
			{
				pinned.add(item);
			}
		}
		return pinned;
	}

	private void reindex()
	{
		for (int i = 0; i < items.size(); i++)
		{
			items.get(i).setOrder(i);
		}
	}

	private void persist()
	{
		try
		{
			String json = gson.toJson(items, PLAN_LIST_TYPE);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
		}
		catch (Exception e)
		{
			log.error("Failed to persist plan items", e);
		}
	}

	private void loadFromConfig()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			List<PlanItem> loaded = gson.fromJson(json, PLAN_LIST_TYPE);
			if (loaded != null)
			{
				items.addAll(loaded);
				log.info("Loaded {} plan items from config", items.size());
			}
		}
		catch (Exception e)
		{
			log.error("Failed to parse plan items — starting fresh.", e);
		}
	}
}
