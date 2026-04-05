package com.ultimatetaskmaster.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Generates an optimized task completion route using a greedy nearest-neighbor algorithm.
 *
 * Algorithm:
 * 1. Start from player's current position
 * 2. Find all "doable" tasks (has location, not completed, not hidden, meets skill reqs)
 * 3. Pick the closest unvisited task, add to route, move cursor to that task's location
 * 4. Repeat until no more tasks within maxRadius or route reaches maxTasks
 *
 * Uses 2D Chebyshev distance (same as SpatialTaskQuery) as a proxy for travel distance.
 * Future: integrate with shortest-path for actual walk distance + teleport awareness.
 */
@Slf4j
public final class RouteGenerator
{
	private RouteGenerator() {}

	/** A single step in the generated route. */
	@Value
	public static class RouteStep
	{
		/** The task to complete at this step */
		TaskData task;

		/** The location to travel to */
		WorldPoint location;

		/** Distance from the previous step (or player start) in tiles */
		int distanceFromPrevious;

		/** Cumulative distance from the start */
		int cumulativeDistance;

		/** Step number (1-based) */
		int stepNumber;
	}

	/**
	 * Generate a route starting from the given origin.
	 *
	 * @param allTasks         all available tasks (enriched with locations)
	 * @param locationService  for looking up all location clusters per task
	 * @param origin           player's starting position
	 * @param completedNames   tasks already completed (excluded)
	 * @param hiddenNames      tasks hidden by user (excluded)
	 * @param playerSkills     player's real skill levels (from client.getRealSkillLevels()), or null to skip skill check
	 * @param maxRadius        maximum Chebyshev distance to search for next task (0 = unlimited)
	 * @param maxTasks         maximum number of tasks in the route (0 = unlimited)
	 * @return ordered list of RouteSteps forming the route
	 */
	public static List<RouteStep> generateRoute(
		List<TaskData> allTasks,
		TaskLocationService locationService,
		WorldPoint origin,
		Set<String> completedNames,
		Set<String> hiddenNames,
		int[] playerSkills,
		int maxRadius,
		int maxTasks)
	{
		if (allTasks == null || allTasks.isEmpty() || origin == null)
		{
			return Collections.emptyList();
		}

		// Build candidate pool: tasks with locations that are doable
		List<Candidate> candidates = new ArrayList<>();
		for (TaskData task : allTasks)
		{
			// Skip completed/hidden
			if (completedNames != null && completedNames.contains(task.getName()))
			{
				continue;
			}
			if (hiddenNames != null && hiddenNames.contains(task.getName()))
			{
				continue;
			}

			// Skip if player doesn't meet skill requirements
			if (playerSkills != null && !meetsSkillRequirements(task, playerSkills))
			{
				continue;
			}

			// Get all locations for this task
			List<WorldPoint> locations = getTaskLocations(task, locationService);
			if (locations.isEmpty())
			{
				continue;
			}

			candidates.add(new Candidate(task, locations));
		}

		if (candidates.isEmpty())
		{
			return Collections.emptyList();
		}

		log.debug("Route generation: {} candidate tasks from {} total", candidates.size(), allTasks.size());

		// Greedy nearest-neighbor
		List<RouteStep> route = new ArrayList<>();
		WorldPoint cursor = origin;
		int cumulativeDistance = 0;
		int effectiveMaxTasks = maxTasks > 0 ? maxTasks : Integer.MAX_VALUE;
		int effectiveMaxRadius = maxRadius > 0 ? maxRadius : Integer.MAX_VALUE;

		while (!candidates.isEmpty() && route.size() < effectiveMaxTasks)
		{
			// Find closest candidate to cursor
			int bestIndex = -1;
			int bestDistance = Integer.MAX_VALUE;
			WorldPoint bestLocation = null;

			for (int i = 0; i < candidates.size(); i++)
			{
				Candidate c = candidates.get(i);
				for (WorldPoint loc : c.locations)
				{
					int dist = chebyshev(cursor, loc);
					if (dist < bestDistance)
					{
						bestDistance = dist;
						bestIndex = i;
						bestLocation = loc;
					}
				}
			}

			// Stop if no task within max radius
			if (bestIndex < 0 || bestDistance > effectiveMaxRadius)
			{
				break;
			}

			// Add to route
			Candidate chosen = candidates.remove(bestIndex);
			cumulativeDistance += bestDistance;

			route.add(new RouteStep(
				chosen.task,
				bestLocation,
				bestDistance,
				cumulativeDistance,
				route.size() + 1
			));

			// Move cursor to chosen task's location
			cursor = bestLocation;
		}

		log.info("Generated route: {} tasks, {} total tiles", route.size(), cumulativeDistance);
		return route;
	}

	private static int chebyshev(WorldPoint a, WorldPoint b)
	{
		return Math.max(
			Math.abs(a.getX() - b.getX()),
			Math.abs(a.getY() - b.getY())
		);
	}

	private static boolean meetsSkillRequirements(TaskData task, int[] playerSkills)
	{
		List<TaskSkillRequirement> reqs = task.getRequirements();
		if (reqs == null || reqs.isEmpty())
		{
			return true;
		}
		for (TaskSkillRequirement req : reqs)
		{
			try
			{
				net.runelite.api.Skill skill = net.runelite.api.Skill.valueOf(req.getSkill().toUpperCase());
				if (playerSkills[skill.ordinal()] < req.getLevel())
				{
					return false;
				}
			}
			catch (IllegalArgumentException e)
			{
				// Unknown skill — skip
			}
		}
		return true;
	}

	private static List<WorldPoint> getTaskLocations(TaskData task, TaskLocationService locationService)
	{
		List<WorldPoint> result = new ArrayList<>();

		if (locationService != null)
		{
			List<LocationCluster> clusters = locationService.getLocationsForTask(task.getStructId());
			if (clusters != null)
			{
				for (LocationCluster c : clusters)
				{
					result.add(new WorldPoint(c.getX(), c.getY(), 0));
				}
			}
		}

		if (result.isEmpty() && task.getLocation() != null)
		{
			result.add(task.getLocation());
		}

		return result;
	}

	/** Internal candidate during route building */
	private static class Candidate
	{
		final TaskData task;
		final List<WorldPoint> locations;

		Candidate(TaskData task, List<WorldPoint> locations)
		{
			this.task = task;
			this.locations = locations;
		}
	}
}
