package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads all 1,589 tasks from the bundled JSON resource (from full-task-scraper data).
 *
 * <h3>Bundled Data (MVP)</h3>
 * Task definitions are baked into the JAR as {@code tasks.json}. This is the
 * simplest approach — no server dependency, works offline, zero startup latency.
 *
 * <h3>Future: HTTP Task Provider (WikiSync Manifest Pattern)</h3>
 * Replace with {@code HttpTaskDataProvider} that follows the WikiSync manifest flow:
 * <ol>
 *   <li>On startup: {@code GET /api/v1/tasks/version} — check if cached data is stale</li>
 *   <li>If version differs: {@code GET /api/v1/tasks} — download full task list</li>
 *   <li>Cache response in ConfigManager (or local file) for offline fallback</li>
 *   <li>Use ETag/If-None-Match headers for efficient caching</li>
 * </ol>
 *
 * <p>This mirrors WikiSync's manifest check exactly:
 * {@code WikiSyncPlugin.checkManifest()} fetches the manifest periodically
 * (every 1200 seconds) and the plugin auto-adapts to server-side changes.
 * Reference: {@code examples/WikiSync/WikiSyncPlugin.java} — see
 * {@code checkManifest()} and {@code MANIFEST_URL}.</p>
 *
 * <p>Task locations are NOT part of the definition — they come from crowdsourced
 * completion data via {@link com.ultimatetaskmaster.crowdsource.TaskLocationResolver}.
 * All tasks have location=null here; the spatial query resolves locations at query time.</p>
 *
 * @see TaskDataProvider
 */
@Singleton
@Slf4j
public class StaticTaskDataProvider implements TaskDataProvider
{
	// Absolute classpath path — leading '/' because this class is in .data subpackage
	// but the resource lives at com/ultimatetaskmaster/tasks.json (one level up).
	private static final String TASKS_RESOURCE = "/com/ultimatetaskmaster/tasks.json";

	private final List<TaskData> tasks;

	@Inject
	public StaticTaskDataProvider(Gson gson)
	{
		this.tasks = Collections.unmodifiableList(loadTasks(gson));
		log.info("Loaded {} tasks from bundled resource", tasks.size());
	}

	@Override
	public List<TaskData> getTasks()
	{
		return tasks;
	}

	private static List<TaskData> loadTasks(Gson gson)
	{
		try (InputStream is = StaticTaskDataProvider.class.getResourceAsStream(TASKS_RESOURCE))
		{
			if (is == null)
			{
				log.error("Could not find {} resource! Falling back to empty task list.", TASKS_RESOURCE);
				return Collections.emptyList();
			}

			Type listType = TypeToken.getParameterized(List.class, JsonTaskDefinition.class).getType();
			List<JsonTaskDefinition> defs = gson.fromJson(
				new InputStreamReader(is, StandardCharsets.UTF_8), listType);

			if (defs == null)
			{
				log.error("Failed to parse tasks JSON — got null.");
				return Collections.emptyList();
			}

			List<TaskData> result = new ArrayList<>(defs.size());
			for (int i = 0; i < defs.size(); i++)
			{
				result.add(toTaskData(defs.get(i)));
			}
			return result;
		}
		catch (Exception e)
		{
			log.error("Error loading tasks from JSON resource", e);
			return Collections.emptyList();
		}
	}

	private static TaskData toTaskData(JsonTaskDefinition def)
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
			.location(null) // Locations come from crowdsourced data, not static definitions
			.completionPct(def.getCompletionPercent())
			.requirements(reqs)
			.build();
	}
}
