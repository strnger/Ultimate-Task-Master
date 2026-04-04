package com.ultimatetaskmaster.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
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
		if (nearbyTasks != null)
		{
			for (NearbyTask nearbyTask : nearbyTasks)
			{
				renderTaskTile(graphics, nearbyTask);
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

	private void renderTaskTile(Graphics2D graphics, NearbyTask nearbyTask)
	{
		WorldPoint taskPoint = nearbyTask.getResolvedLocation();
		if (taskPoint == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		Scene scene = worldView.getScene();

		// Handle instanced areas (e.g., POH, minigames)
		for (WorldPoint point : WorldPoint.toLocalInstance(scene, taskPoint))
		{
			if (point.getPlane() != client.getTopLevelWorldView().getPlane())
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

			// Outline
			graphics.setColor(tileColor);
			graphics.setStroke(new BasicStroke(STROKE_WIDTH));
			graphics.draw(poly);

			// Semi-transparent fill
			graphics.setColor(new Color(
				tileColor.getRed(), tileColor.getGreen(), tileColor.getBlue(), FILL_ALPHA));
			graphics.fill(poly);

			// Render task name above tile
			Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, nearbyTask.getTask().getName(), 0);
			if (canvasTextLocation != null)
			{
				OverlayUtil.renderTextLocation(graphics, canvasTextLocation, nearbyTask.getTask().getName(), tileColor);
			}
		}
	}
}
