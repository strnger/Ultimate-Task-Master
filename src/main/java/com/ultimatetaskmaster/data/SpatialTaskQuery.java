package com.ultimatetaskmaster.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Spatial query engine for "What's Near Me?".
 *
 * Finds tasks within a tile radius of the player, using 2D Chebyshev distance
 * (ignoring plane) so tasks on different floors are still discoverable.
 *
 * Location data comes from TaskData.location, populated by scraper coordinate data.
 *
 * TODO: Add tier/area/skill filters.
 * TODO: Add "requirements met" filter using client.getRealSkillLevel().
 */
public final class SpatialTaskQuery
{
	private SpatialTaskQuery()
	{
		// Utility class — no instances
	}


	public static List<NearbyTask> findNearby(
		List<TaskData> allTasks,
		WorldPoint origin,
		int radius,
		SortCriteria sortBy,
		TaskLocationService locationService)
	{
		List<NearbyTask> results = new ArrayList<>();

		for (TaskData task : allTasks)
		{
			// Check all location clusters for this task
			WorldPoint bestLocation = null;
			int bestDistance = Integer.MAX_VALUE;

			if (locationService != null)
			{
				List<LocationCluster> clusters = locationService.getLocationsForTask(task.getStructId());
				if (clusters != null)
				{
					for (LocationCluster cluster : clusters)
					{
						WorldPoint clusterPoint = new WorldPoint(cluster.getX(), cluster.getY(), 0);
						int dist = distance2D(origin, clusterPoint);
						if (dist <= radius && dist < bestDistance)
						{
							bestDistance = dist;
							bestLocation = clusterPoint;
						}
					}
				}
			}

			// Fall back to TaskData.location if no cluster matches
			if (bestLocation == null && task.getLocation() != null)
			{
				int dist = distance2D(origin, task.getLocation());
				if (dist <= radius)
				{
					bestDistance = dist;
					bestLocation = task.getLocation();
				}
			}

			if (bestLocation != null)
			{
				results.add(new NearbyTask(task, bestLocation, bestDistance));
			}
		}

		results.sort(getSorter(sortBy));
		return results;
	}

	/**
	 * Backward-compatible overload without TaskLocationService.
	 * Falls back to using only TaskData.location.
	 */
	public static List<NearbyTask> findNearby(
		List<TaskData> allTasks,
		WorldPoint origin,
		int radius,
		SortCriteria sortBy)
	{
		return findNearby(allTasks, origin, radius, sortBy, null);
	}

	/**
	 * 2D Chebyshev distance, ignoring plane.
	 * This way a task on the ground floor is still "nearby" if the player is upstairs.
	 */
	private static int distance2D(WorldPoint a, WorldPoint b)
	{
		return Math.max(
			Math.abs(a.getX() - b.getX()),
			Math.abs(a.getY() - b.getY())
		);
	}

	private static Comparator<NearbyTask> getSorter(SortCriteria sortBy)
	{
		switch (sortBy)
		{
			case DISTANCE:
				return Comparator.comparingInt(NearbyTask::getDistance);
			case POINTS:
				return Comparator.comparingInt((NearbyTask nt) -> nt.getTask().getPoints()).reversed();
			case COMPLETION_PCT:
				return Comparator.comparing(
					(NearbyTask nt) -> nt.getTask().getCompletionPct() != null
						? nt.getTask().getCompletionPct()
						: 0f
				).reversed();
			case TIER:
				return Comparator.comparingInt(
					(NearbyTask nt) -> nt.getTask().getTier().ordinal()
				);
			default:
				return Comparator.comparingInt(NearbyTask::getDistance);
		}
	}

	/**
	 * How to sort "Near Me" results.
	 */
	public enum SortCriteria
	{
		DISTANCE,
		POINTS,
		COMPLETION_PCT,
		TIER
	}
}
