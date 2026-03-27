package com.ultimatetaskmaster.crowdsource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Resolves the "best guess" WorldPoint for a task based on crowdsourced completion data.
 *
 * <h3>Algorithm: Componentwise Median</h3>
 * For each task, collects all completion locations from the store and returns the
 * median X, median Y, and median plane. The median is robust to outliers — if 9/10
 * players complete "Mine copper ore" at Al Kharid mine and 1 does it at Lumbridge,
 * the resolved location is Al Kharid.
 *
 * <h3>Cache</h3>
 * Results are cached in a HashMap and invalidated explicitly when new records arrive.
 * The cache is rebuilt lazily on the next call to {@link #getLocation(String)}.
 *
 * <h3>Future: Server-Side Aggregation</h3>
 * When we add the HTTP backend, the server can pre-compute median locations and serve
 * them via {@code GET /api/v1/locations}. The resolver would then just read the response
 * instead of computing medians locally. This follows the WikiSync pattern where the
 * server does the heavy transformation (see {@code LeagueTransformer.getLeagueTasks()})
 * and the client just consumes the result.
 *
 * <h3>Future: Mode-Based Clustering</h3>
 * Some tasks can be done at multiple distinct locations (e.g., "Mine iron ore" could
 * be at Al Kharid, Varrock East, or Mining Guild). A simple median may land between
 * clusters. A future improvement would use DBSCAN or similar to find clusters, then
 * return the centroid of the largest cluster. For MVP, the median is good enough.
 *
 * @see CompletionLocationStore
 */
@Singleton
@Slf4j
public class TaskLocationResolver
{
	private final CompletionLocationStore store;
	private Map<String, WorldPoint> cache = Collections.emptyMap();
	private boolean cacheValid = false;

	@Inject
	public TaskLocationResolver(CompletionLocationStore store)
	{
		this.store = store;
	}

	/**
	 * Resolve the best-guess location for a task.
	 *
	 * @param taskName the task name (must match CompletionRecord.taskName exactly)
	 * @return the median location, or null if no completion records exist for this task
	 */
	public WorldPoint getLocation(String taskName)
	{
		ensureCache();
		return cache.get(taskName);
	}

	/**
	 * Check if we have any location data for a task.
	 */
	public boolean hasLocation(String taskName)
	{
		return getLocation(taskName) != null;
	}

	/**
	 * Mark the cache as stale. Called when new completion records are added.
	 * The cache will be rebuilt lazily on the next getLocation() call.
	 */
	public void invalidateCache()
	{
		cacheValid = false;
	}

	private void ensureCache()
	{
		if (cacheValid)
		{
			return;
		}

		List<CompletionRecord> allRecords = store.getAllRecords();
		Map<String, List<CompletionRecord>> byTask = allRecords.stream()
			.collect(Collectors.groupingBy(CompletionRecord::getTaskName));

		Map<String, WorldPoint> newCache = new HashMap<>();
		for (Map.Entry<String, List<CompletionRecord>> entry : byTask.entrySet())
		{
			WorldPoint median = computeMedian(entry.getValue());
			if (median != null)
			{
				newCache.put(entry.getKey(), median);
			}
		}

		cache = newCache;
		cacheValid = true;
		log.debug("Location cache rebuilt: {} tasks with locations from {} records",
			cache.size(), allRecords.size());
	}

	/**
	 * Compute the componentwise median of a list of completion records.
	 * Each component (x, y, plane) is computed independently.
	 */
	private static WorldPoint computeMedian(List<CompletionRecord> records)
	{
		if (records == null || records.isEmpty())
		{
			return null;
		}

		List<Integer> xs = records.stream().map(CompletionRecord::getX)
			.sorted().collect(Collectors.toList());
		List<Integer> ys = records.stream().map(CompletionRecord::getY)
			.sorted().collect(Collectors.toList());
		List<Integer> planes = records.stream().map(CompletionRecord::getPlane)
			.sorted().collect(Collectors.toList());

		int mid = records.size() / 2;
		return new WorldPoint(xs.get(mid), ys.get(mid), planes.get(mid));
	}
}
