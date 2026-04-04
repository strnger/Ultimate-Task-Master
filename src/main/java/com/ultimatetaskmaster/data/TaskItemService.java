package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides item requirements for tasks.
 *
 * Data sources (in priority order):
 * 1. Scraper JSON items[] array (when available in TaskData.itemRequirements)
 * 2. strategy.json seed data (198 tasks with comma-separated item names)
 *
 * strategy.json serves as a bridge until the scraper provides structured item data.
 */
@Singleton
@Slf4j
public class TaskItemService
{
	private static final String STRATEGY_RESOURCE = "/com/ultimatetaskmaster/strategy.json";

	/** Map of structId -> list of item requirements from strategy.json */
	private final Map<Integer, List<TaskItemRequirement>> strategyItems;

	@Data
	private static class StrategyEntry
	{
		private String taskName;
		private String search;
	}

	@Inject
	public TaskItemService(Gson gson)
	{
		this.strategyItems = loadStrategy(gson);
		log.info("Loaded item data for {} tasks from strategy.json", strategyItems.size());
	}

	/**
	 * Get item requirements for a task.
	 * Checks TaskData.itemRequirements first (from scraper), falls back to strategy.json.
	 *
	 * @param task the task to get requirements for
	 * @return list of item requirements, or empty list if none known
	 */
	public List<TaskItemRequirement> getItemRequirements(TaskData task)
	{
		// Prefer scraper data if available
		if (task.getItemRequirements() != null && !task.getItemRequirements().isEmpty())
		{
			return task.getItemRequirements();
		}

		// Fall back to strategy.json seed data
		List<TaskItemRequirement> items = strategyItems.get(task.getStructId());
		return items != null ? items : Collections.emptyList();
	}

	/**
	 * Check if we have any item data for a task (from any source).
	 */
	public boolean hasItemData(TaskData task)
	{
		return !getItemRequirements(task).isEmpty();
	}

	/**
	 * Total tasks with item data across all sources.
	 */
	public int getCoverage()
	{
		return strategyItems.size();
	}

	private static Map<Integer, List<TaskItemRequirement>> loadStrategy(Gson gson)
	{
		try (InputStream is = TaskItemService.class.getResourceAsStream(STRATEGY_RESOURCE))
		{
			if (is == null)
			{
				log.warn("Could not find strategy.json resource");
				return Collections.emptyMap();
			}

			Type mapType = new TypeToken<Map<String, StrategyEntry>>(){}.getType();
			Map<String, StrategyEntry> raw = gson.fromJson(
				new InputStreamReader(is, StandardCharsets.UTF_8), mapType);

			if (raw == null)
			{
				return Collections.emptyMap();
			}

			Map<Integer, List<TaskItemRequirement>> result = new HashMap<>();
			for (Map.Entry<String, StrategyEntry> entry : raw.entrySet())
			{
				try
				{
					int structId = Integer.parseInt(entry.getKey());
					StrategyEntry strategy = entry.getValue();

					if (strategy.getSearch() == null || strategy.getSearch().isEmpty())
					{
						continue;
					}

					List<TaskItemRequirement> items = new ArrayList<>();
					for (String itemName : strategy.getSearch().split(","))
					{
						String trimmed = itemName.trim();
						if (!trimmed.isEmpty())
						{
							items.add(TaskItemRequirement.builder()
								.name(trimmed)
								.build());
						}
					}

					if (!items.isEmpty())
					{
						result.put(structId, Collections.unmodifiableList(items));
					}
				}
				catch (NumberFormatException e)
				{
					log.warn("Invalid struct ID in strategy.json: {}", entry.getKey());
				}
			}

			return Collections.unmodifiableMap(result);
		}
		catch (Exception e)
		{
			log.error("Error loading strategy.json", e);
			return Collections.emptyMap();
		}
	}
}
