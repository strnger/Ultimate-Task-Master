package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches task data from the full-task-scraper GitHub repository.
 *
 * Data priority: HTTP fresh fetch > ConfigManager cache > empty list.
 * The plugin should fall back to StaticTaskDataProvider if this returns empty.
 *
 * Flow:
 * 1. On startup: load from ConfigManager cache (instant, offline-safe)
 * 2. Call refreshFromRemote() (via @Schedule in plugin): GET leagues.json,
 *    find active league, GET full.json, parse, cache, update in-memory list
 * 3. getTasks() always returns the current in-memory list
 *
 * Periodic remote fetch with local ConfigManager cache for offline use.
 *
 * @see TaskDataProvider
 * @see StaticTaskDataProvider
 */
@Singleton
@Slf4j
public class HttpTaskDataProvider implements TaskDataProvider
{
	private static final String BASE_URL = "https://raw.githubusercontent.com/syrifgit/full-task-scraper/main/generated/";
	private static final String LEAGUES_URL = BASE_URL + "leagues.json";
	private static final String CONFIG_GROUP = "ultimate-task-master";
	private static final String CACHED_TASK_JSON_KEY = "cachedTaskJson";
	private static final String CACHED_LEAGUE_ID_KEY = "cachedLeagueId";
	private static final String CACHED_LEAGUE_NAME_KEY = "cachedLeagueName";

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final ConfigManager configManager;

	private volatile List<TaskData> tasks = Collections.emptyList();
	private volatile String currentLeagueName = "Unknown";

	/**
	 * League metadata from leagues.json.
	 */
	@Data
	static class LeagueInfo
	{
		int id;
		String name;
		String folder;
		String taskFile;
		boolean active;
	}

	@Inject
	public HttpTaskDataProvider(OkHttpClient okHttpClient, Gson gson, ConfigManager configManager)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.configManager = configManager;

		// Load cached data immediately (non-blocking, offline-safe)
		loadFromCache();
	}

	@Override
	public List<TaskData> getTasks()
	{
		return tasks;
	}

	/**
	 * @return the name of the currently loaded league (e.g., "Raging Echoes")
	 */
	public String getCurrentLeagueName()
	{
		return currentLeagueName;
	}

	/**
	 * Attempt to refresh task data from the remote GitHub source.
	 * Called by @Schedule in the plugin (asynchronous = true).
	 *
	 * Thread-safe: updates the volatile tasks list atomically.
	 */
	public void refreshFromRemote()
	{
		try
		{
			LeagueInfo league = fetchActiveLeague();
			if (league == null)
			{
				log.debug("No active league found with task data.");
				return;
			}

			String taskUrl = BASE_URL + league.getFolder() + "/" + league.getTaskFile();
			log.info("Fetching task data for {} from {}", league.getName(), taskUrl);

			String json = httpGet(taskUrl);
			if (json == null || json.isEmpty())
			{
				log.warn("Empty response from task data URL.");
				return;
			}

			List<TaskData> parsed = parseTaskJson(json);
			if (parsed.isEmpty())
			{
				log.warn("Parsed zero tasks from remote data — keeping existing data.");
				return;
			}

			// Atomically update
			this.tasks = Collections.unmodifiableList(parsed);
			this.currentLeagueName = league.getName();

			// Cache for offline use
			configManager.setConfiguration(CONFIG_GROUP, CACHED_TASK_JSON_KEY, json);
			configManager.setConfiguration(CONFIG_GROUP, CACHED_LEAGUE_ID_KEY, String.valueOf(league.getId()));
			configManager.setConfiguration(CONFIG_GROUP, CACHED_LEAGUE_NAME_KEY, league.getName());

			log.info("Refreshed {} tasks for league: {} (ID {})", parsed.size(), league.getName(), league.getId());
		}
		catch (Exception e)
		{
			log.warn("Failed to refresh tasks from remote — keeping existing data.", e);
		}
	}

	/**
	 * Fetch leagues.json and find the best league to use.
	 * Priority: active=true > highest ID with a taskFile.
	 */
	private LeagueInfo fetchActiveLeague()
	{
		String json = httpGet(LEAGUES_URL);
		if (json == null)
		{
			return null;
		}

		try
		{
			Type listType = TypeToken.getParameterized(List.class, LeagueInfo.class).getType();
			List<LeagueInfo> leagues = gson.fromJson(json, listType);
			if (leagues == null || leagues.isEmpty())
			{
				return null;
			}

			// First: look for an active league with task data
			LeagueInfo active = leagues.stream()
				.filter(l -> l.isActive() && l.getTaskFile() != null)
				.findFirst()
				.orElse(null);

			if (active != null)
			{
				return active;
			}

			// Fallback: highest ID league with a taskFile
			return leagues.stream()
				.filter(l -> l.getTaskFile() != null)
				.max(Comparator.comparingInt(LeagueInfo::getId))
				.orElse(null);
		}
		catch (JsonParseException e)
		{
			log.warn("Failed to parse leagues.json", e);
			return null;
		}
	}

	/**
	 * Load task data from ConfigManager cache (fast, offline-safe).
	 */
	private void loadFromCache()
	{
		String cachedJson = configManager.getConfiguration(CONFIG_GROUP, CACHED_TASK_JSON_KEY);
		String cachedName = configManager.getConfiguration(CONFIG_GROUP, CACHED_LEAGUE_NAME_KEY);

		if (cachedJson == null || cachedJson.isEmpty())
		{
			log.debug("No cached task data found.");
			return;
		}

		List<TaskData> parsed = parseTaskJson(cachedJson);
		if (!parsed.isEmpty())
		{
			this.tasks = Collections.unmodifiableList(parsed);
			this.currentLeagueName = cachedName != null ? cachedName : "Cached";
			log.info("Loaded {} tasks from cache (league: {})", parsed.size(), currentLeagueName);
		}
	}

	/**
	 * Parse a JSON string of task definitions into TaskData objects.
	 * Uses the same mapping logic as StaticTaskDataProvider.
	 */
	private List<TaskData> parseTaskJson(String json)
	{
		try
		{
			Type listType = TypeToken.getParameterized(List.class, JsonTaskDefinition.class).getType();
			List<JsonTaskDefinition> defs = gson.fromJson(json, listType);

			if (defs == null)
			{
				return Collections.emptyList();
			}

			List<TaskData> result = new ArrayList<>(defs.size());
			for (JsonTaskDefinition def : defs)
			{
				result.add(toTaskData(def));
			}
			return result;
		}
		catch (JsonParseException e)
		{
			log.error("Failed to parse task JSON", e);
			return Collections.emptyList();
		}
	}

	/**
	 * Convert a JSON task definition to a TaskData domain object.
	 * Identical logic to StaticTaskDataProvider.toTaskData().
	 */
	static TaskData toTaskData(JsonTaskDefinition def)
	{
		List<TaskSkillRequirement> reqs;
		if (def.getSkills() != null && !def.getSkills().isEmpty())
		{
			reqs = def.getSkills().stream()
				.map(r -> new TaskSkillRequirement(r.getSkill(), r.getLevel()))
				.collect(Collectors.toList());
		}
		else
		{
			reqs = Collections.emptyList();
		}

		// Map item requirements
		List<TaskItemRequirement> itemReqs;
		if (def.getItems() != null && !def.getItems().isEmpty())
		{
			itemReqs = def.getItems().stream()
				.map(i -> TaskItemRequirement.builder()
					.name(i.getName())
					.quantity(i.getQuantity())
					.itemId(i.getItemId())
					.alternateIds(i.getAlternateIds() != null ? i.getAlternateIds() : Collections.emptyList())
					.build())
				.collect(Collectors.toList());
		}
		else
		{
			itemReqs = Collections.emptyList();
		}

		TaskTier tier = TaskTier.fromInt(def.getTier());

		return TaskData.builder()
			.structId(def.getStructId())
			.sortId(def.getSortId())
			.name(def.getName())
			.description(def.getDescription() != null ? def.getDescription() : "")
			.area(TaskArea.fromString(def.getArea()))
			.tier(tier)
			.points(tier.getDefaultPoints())
			.category(def.getCategory())
			.skill(def.getSkill())
			.completionPct(def.getCompletionPercent())
			.location(null)
			.requirements(reqs)
			.itemRequirements(itemReqs)
			.build();
	}

	/**
	 * Simple synchronous HTTP GET. Returns the response body as a string, or null on failure.
	 * Uses synchronous call since this is invoked from @Schedule(asynchronous=true).
	 */
	private String httpGet(String url)
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", "Ultimate-Task-Master-RuneLite-Plugin")
			.build();

		try (Response response = okHttpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				log.debug("HTTP GET {} failed: {}", url, response.code());
				return null;
			}
			return response.body() != null ? response.body().string() : null;
		}
		catch (IOException e)
		{
			log.debug("HTTP GET {} error: {}", url, e.getMessage());
			return null;
		}
	}
}
