package com.ultimatetaskmaster.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import com.ultimatetaskmaster.UltimateTaskMasterConfig;
import com.ultimatetaskmaster.UltimateTaskMasterPlugin;
import com.ultimatetaskmaster.data.NearbyTask;
import com.ultimatetaskmaster.data.PlanItem;
import com.ultimatetaskmaster.data.PlanService;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Renders colored tile highlights at nearby task locations in the 3D game world.
 *
 * Follows the PathTileOverlay pattern from Leagues Planner:
 * - ABOVE_SCENE layer for world tiles
 * - Perspective.getCanvasTilePoly() for world-to-screen tile rendering
 * - Color coded by task tier
 *
 * Only renders tiles within the loaded scene (~128 tile radius from player).
 *
 * TODO: Add directional arrows for tasks outside visible range (Quest Helper pattern).
 */
public class NearbyTaskWorldOverlay extends Overlay
{
	private static final int FILL_ALPHA = 50;
	private static final float STROKE_WIDTH = 2f;

	private final Client client;
	private final UltimateTaskMasterPlugin plugin;
	private final UltimateTaskMasterConfig config;

	@Inject
	private PlanService planService;

	@Inject
	private NearbyTaskWorldOverlay(Client client, UltimateTaskMasterPlugin plugin, UltimateTaskMasterConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		List<NearbyTask> nearbyTasks = plugin.getNearbyTasks();
		if (nearbyTasks != null && !nearbyTasks.isEmpty())
		{
			java.util.List<ResolvedTask> resolved = resolveCollisions(nearbyTasks);
			for (ResolvedTask rt : resolved)
			{
				renderTaskTileAt(graphics, rt.task, rt.displayLocation);
			}
		}

		// Render pinned plan locations (orange color)
		if (planService != null)
		{
			List<PlanItem> pinned = planService.getPinnedItems();
			for (PlanItem item : pinned)
			{
				if (item.getPinnedX() != null && item.getPinnedY() != null)
				{
					WorldPoint planPoint = new WorldPoint(item.getPinnedX(), item.getPinnedY(), 0);
					renderPlanTile(graphics, planPoint, item.getTaskName());
				}
			}
		}

		return null;
	}

	private void renderPlanTile(Graphics2D graphics, WorldPoint point, String label)
	{
		WorldView worldView = client.getTopLevelWorldView();
		for (WorldPoint wp : WorldPoint.toLocalInstance(worldView.getScene(), point))
		{
			if (wp.getPlane() != worldView.getPlane())
			{
				continue;
			}

			LocalPoint lp = LocalPoint.fromWorld(worldView, wp);
			if (lp == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, lp, 0);
			if (poly == null)
			{
				continue;
			}

			Color planColor = new Color(255, 140, 0);
			graphics.setColor(planColor);
			graphics.setStroke(new BasicStroke(STROKE_WIDTH));
			graphics.draw(poly);
			graphics.setColor(new Color(255, 140, 0, FILL_ALPHA));
			graphics.fill(poly);

			// Render plan item name above tile
			if (label != null)
			{
				Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
				if (canvasTextLocation != null)
				{
					OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, planColor);
				}
			}
		}
	}

	/**
	 * Resolves tile collisions by spreading overlapping tasks to adjacent tiles.
	 *
	 * When multiple tasks share the same tile:
	 * 1. Sort by completion percentage (desc), then alphabetical
	 * 2. First task keeps the original tile
	 * 3. Others shift to N, NE, E, SE, S, SW, W, NW (in order)
	 * 4. Shifted tiles are checked for existing occupants
	 */
	private java.util.List<ResolvedTask> resolveCollisions(java.util.List<NearbyTask> tasks)
	{
		if (tasks == null || tasks.isEmpty())
		{
			return java.util.Collections.emptyList();
		}

		// Direction offsets: N, NE, E, SE, S, SW, W, NW
		int[][] offsets = {
			{0, 1}, {1, 1}, {1, 0}, {1, -1},
			{0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
		};

		// Group tasks by their tile coordinate
		Map<String, java.util.List<NearbyTask>> byTile = new HashMap<>();
		for (NearbyTask nt : tasks)
		{
			WorldPoint wp = nt.getResolvedLocation();
			if (wp == null) continue;
			String key = wp.getX() + "," + wp.getY();
			byTile.computeIfAbsent(key, k -> new ArrayList<>()).add(nt);
		}

		// Track all occupied tiles
		java.util.Set<String> occupied = new java.util.HashSet<>(byTile.keySet());

		java.util.List<ResolvedTask> result = new ArrayList<>();

		for (Map.Entry<String, java.util.List<NearbyTask>> entry : byTile.entrySet())
		{
			java.util.List<NearbyTask> group = entry.getValue();

			// Sort: higher completion % first, then alphabetical
			group.sort(Comparator
				.comparing((NearbyTask nt) -> nt.getTask().getCompletionPct() != null
					? nt.getTask().getCompletionPct() : 0f)
				.reversed()
				.thenComparing(nt -> nt.getTask().getName()));

			// First task keeps original tile
			result.add(new ResolvedTask(group.get(0), group.get(0).getResolvedLocation()));

			// Remaining tasks get shifted to adjacent tiles
			int offsetIdx = 0;
			for (int i = 1; i < group.size(); i++)
			{
				NearbyTask nt = group.get(i);
				WorldPoint original = nt.getResolvedLocation();
				WorldPoint shifted = null;

				// Find an unoccupied adjacent tile
				while (offsetIdx < offsets.length)
				{
					int nx = original.getX() + offsets[offsetIdx][0];
					int ny = original.getY() + offsets[offsetIdx][1];
					String newKey = nx + "," + ny;
					if (!occupied.contains(newKey))
					{
						shifted = new WorldPoint(nx, ny, original.getPlane());
						occupied.add(newKey);
						offsetIdx++;
						break;
					}
					offsetIdx++;
				}

				if (shifted == null)
				{
					// All 8 adjacent tiles occupied — just offset by index
					shifted = new WorldPoint(
						original.getX() + i,
						original.getY() + i,
						original.getPlane());
				}

				result.add(new ResolvedTask(nt, shifted));
			}
		}

		return result;
	}

	private void renderTaskTileAt(Graphics2D graphics, NearbyTask nearbyTask, WorldPoint renderPoint)
	{
		if (renderPoint == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		Scene scene = worldView.getScene();

		for (WorldPoint point : WorldPoint.toLocalInstance(scene, renderPoint))
		{
			if (point.getPlane() != worldView.getPlane())
			{
				continue;
			}

			LocalPoint lp = LocalPoint.fromWorld(worldView, point);
			if (lp == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, lp, 0);
			if (poly == null)
			{
				continue;
			}

			Color tileColor = nearbyTask.getTask().getTier().getColor();

			graphics.setColor(tileColor);
			graphics.setStroke(new BasicStroke(STROKE_WIDTH));
			graphics.draw(poly);

			graphics.setColor(new Color(
				tileColor.getRed(), tileColor.getGreen(), tileColor.getBlue(), FILL_ALPHA));
			graphics.fill(poly);

			Point canvasTextLocation = Perspective.getCanvasTextLocation(
				client, graphics, lp, nearbyTask.getTask().getName(), 0);
			if (canvasTextLocation != null)
			{
				OverlayUtil.renderTextLocation(graphics, canvasTextLocation,
					nearbyTask.getTask().getName(), tileColor);
			}
		}
	}

	/** A task with its resolved (possibly shifted) display location */
	private static class ResolvedTask
	{
		final NearbyTask task;
		final WorldPoint displayLocation;

		ResolvedTask(NearbyTask task, WorldPoint displayLocation)
		{
			this.task = task;
			this.displayLocation = displayLocation;
		}
	}
}
