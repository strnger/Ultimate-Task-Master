package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides pre-computed location clusters for league tasks.
 *
 * Loads task_locations.json (generated from LeaguesMap data files) which maps
 * task structIds to lists of coordinate clusters where the task can be completed.
 *
 * Each cluster represents a group of nearby world coordinates (within 10 tiles)
 * that have been merged into a single centroid point.
 */
@Singleton
@Slf4j
public class TaskLocationService
{
	private static final String LOCATIONS_RESOURCE = "/com/ultimatetaskmaster/task_locations.json";

	private final Map<Integer, List<LocationCluster>> locationsByStructId;

	@Inject
	public TaskLocationService(Gson gson)
	{
		this.locationsByStructId = loadLocations(gson);
		log.info("Loaded location data for {} tasks", locationsByStructId.size());
	}

	/**
	 * Get suggested location clusters for a task.
	 *
	 * @param structId the task's game cache struct ID
	 * @return list of location clusters, or empty list if no locations known
	 */
	public List<LocationCluster> getLocationsForTask(int structId)
	{
		List<LocationCluster> locs = locationsByStructId.get(structId);
		return locs != null ? locs : Collections.emptyList();
	}

	/**
	 * Check if we have any location data for a task.
	 */
	public boolean hasLocations(int structId)
	{
		return locationsByStructId.containsKey(structId);
	}

	/**
	 * Total number of tasks that have location data.
	 */
	public int getTaskCount()
	{
		return locationsByStructId.size();
	}

	private static Map<Integer, List<LocationCluster>> loadLocations(Gson gson)
	{
		try (InputStream is = TaskLocationService.class.getResourceAsStream(LOCATIONS_RESOURCE))
		{
			if (is == null)
			{
				log.error("Could not find {} resource!", LOCATIONS_RESOURCE);
				return Collections.emptyMap();
			}

			// JSON shape: { "structId": [{"x":int,"y":int,"count":int}, ...], ... }
			Type mapType = TypeToken.getParameterized(
				Map.class, String.class,
				TypeToken.getParameterized(List.class, LocationCluster.class).getType()
			).getType();

			Map<String, List<LocationCluster>> raw = gson.fromJson(
				new InputStreamReader(is, StandardCharsets.UTF_8), mapType);

			if (raw == null)
			{
				log.error("Failed to parse task locations JSON.");
				return Collections.emptyMap();
			}

			// Convert string keys to integer keys
			Map<Integer, List<LocationCluster>> result = new HashMap<>();
			for (Map.Entry<String, List<LocationCluster>> entry : raw.entrySet())
			{
				try
				{
					int structId = Integer.parseInt(entry.getKey());
					result.put(structId, Collections.unmodifiableList(entry.getValue()));
				}
				catch (NumberFormatException e)
				{
					log.warn("Invalid struct ID in locations JSON: {}", entry.getKey());
				}
			}

			return Collections.unmodifiableMap(result);
		}
		catch (Exception e)
		{
			log.error("Error loading task locations", e);
			return Collections.emptyMap();
		}
	}
}
