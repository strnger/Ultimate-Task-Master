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
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

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
 * TODO: Add task name text above tile (OverlayUtil.renderTextLocation).
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
		if (nearbyTasks == null || nearbyTasks.isEmpty())
		{
			return null;
		}

		for (NearbyTask nearbyTask : nearbyTasks)
		{
			renderTaskTile(graphics, nearbyTask);
		}

		return null;
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
		}
	}
}
