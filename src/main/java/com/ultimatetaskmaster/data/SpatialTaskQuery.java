package com.ultimatetaskmaster.data;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.ultimatetaskmaster.crowdsource.TaskLocationResolver;
import net.runelite.api.coords.WorldPoint;

/**
 * Spatial query engine for "What's Near Me?".
 *
 * Finds tasks within a tile radius of the player, using 2D Chebyshev distance
 * (ignoring plane) so tasks on different floors are still discoverable.
 *
 * Location data is resolved via {@link TaskLocationResolver} at query time.
 * Tasks without a known location won't appear in results.
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

	/**
	 * Find all tasks within the given radius of the origin point.
	 *
	 * @param allTasks         all known tasks
	 * @param origin           player's current position
	 * @param radius           search radius in tiles (Chebyshev distance)
	 * @param sortBy           how to sort results
	 * @param locationResolver resolves locations per task
	 * @return sorted list of nearby tasks with distances and resolved locations
	 */
	public static List<NearbyTask> findNearby(
		List<TaskData> allTasks,
		WorldPoint origin,
		int radius,
		SortCriteria sortBy,
		TaskLocationResolver locationResolver)
	{
		return allTasks.stream()
			.map(task ->
			{
				WorldPoint loc = locationResolver.getLocation(task.getName());
				if (loc == null)
				{
					return null;
				}
				return new NearbyTask(task, loc, distance2D(origin, loc));
			})
			.filter(Objects::nonNull)
			.filter(nt -> nt.getDistance() <= radius)
			.sorted(getSorter(sortBy))
			.collect(Collectors.toList());
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
