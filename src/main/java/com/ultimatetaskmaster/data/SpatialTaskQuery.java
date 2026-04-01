package com.ultimatetaskmaster.data;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Spatial query engine for "What's Near Me?" (Feature 1).
 *
 * Finds tasks within a tile radius of the player, using 2D Chebyshev distance
 * (ignoring plane) so tasks on different floors are still discoverable.
 *
 * Location data comes from TaskData.location, populated by scraper coordinate data.
 *
 * TODO: Add tier/area/skill filters (OverallDesign.md Feature 1 spec).
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
		SortCriteria sortBy)
	{
		return allTasks.stream()
			.filter(task -> task.getLocation() != null)
			.map(task -> new NearbyTask(task, task.getLocation(), distance2D(origin, task.getLocation())))
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
	 * TODO: Add AFK_RATING sort when focus rating system is implemented (OverallDesign.md §Focus).
	 */
	public enum SortCriteria
	{
		DISTANCE,
		POINTS,
		COMPLETION_PCT,
		TIER
	}
}
