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
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads tasks from the bundled JSON resource. Serves as offline fallback.
 *
 * Task definitions are baked into the JAR as {@code tasks.json}.
 * No server dependency, works offline, zero startup latency.
 *
 * @see TaskDataProvider
 * @see HttpTaskDataProvider
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
			for (JsonTaskDefinition def : defs)
			{
				result.add(HttpTaskDataProvider.toTaskData(def));
			}
			return result;
		}
		catch (Exception e)
		{
			log.error("Error loading tasks from JSON resource", e);
			return Collections.emptyList();
		}
	}

	// Conversion logic shared with HttpTaskDataProvider — see HttpTaskDataProvider.toTaskData()
}
