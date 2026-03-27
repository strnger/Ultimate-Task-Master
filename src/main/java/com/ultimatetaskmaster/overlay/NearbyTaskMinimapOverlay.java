package com.ultimatetaskmaster.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.List;
import javax.inject.Inject;
import com.ultimatetaskmaster.UltimateTaskMasterConfig;
import com.ultimatetaskmaster.UltimateTaskMasterPlugin;
import com.ultimatetaskmaster.data.NearbyTask;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Varbits;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Renders colored dots on the minimap for nearby task locations.
 *
 * Follows the PathMinimapOverlay pattern from Leagues Planner:
 * - ABOVE_WIDGETS layer
 * - Elliptical clip area matching the minimap's circular shape
 * - Perspective.localToMinimap() for world-to-minimap coordinate conversion
 * - Rotation-aware rendering via camera yaw
 *
 * Only renders tasks within the loaded scene (minimap visibility range).
 */
public class NearbyTaskMinimapOverlay extends Overlay
{
	private static final int DOT_WIDTH = 4;
	private static final int DOT_HEIGHT = 4;

	private final Client client;
	private final UltimateTaskMasterPlugin plugin;
	private final UltimateTaskMasterConfig config;

	@Inject
	private NearbyTaskMinimapOverlay(
		Client client,
		UltimateTaskMasterPlugin plugin,
		UltimateTaskMasterConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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

		Shape minimapClip = getMinimapClipArea();
		if (minimapClip == null)
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setClip(minimapClip);

		for (NearbyTask nearbyTask : nearbyTasks)
		{
			drawTaskDot(graphics, nearbyTask);
		}

		return null;
	}

	private void drawTaskDot(Graphics2D graphics, NearbyTask nearbyTask)
	{
		WorldPoint taskPoint = nearbyTask.getResolvedLocation();
		if (taskPoint == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		Scene scene = worldView.getScene();

		// Handle instanced areas
		for (WorldPoint point : WorldPoint.toLocalInstance(scene, taskPoint))
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

			Point posOnMinimap = Perspective.localToMinimap(client, lp);
			if (posOnMinimap == null)
			{
				continue;
			}

			// Rotation-aware rendering (Leagues Planner pattern)
			Color dotColor = nearbyTask.getTask().getTier().getColor();
			renderMinimapRect(graphics, posOnMinimap, dotColor);
		}
	}

	/**
	 * Render a rotation-aware rectangle on the minimap.
	 * Rotates to match camera yaw so the dot stays correctly positioned.
	 * Pattern borrowed from Leagues Planner PathMinimapOverlay.
	 */
	private void renderMinimapRect(Graphics2D graphics, Point center, Color color)
	{
		double angle = client.getCameraYawTarget() * Math.PI / 1024.0d;

		graphics.setColor(color);
		graphics.rotate(angle, center.getX(), center.getY());
		graphics.fillRect(
			center.getX() - DOT_WIDTH / 2,
			center.getY() - DOT_HEIGHT / 2,
			DOT_WIDTH, DOT_HEIGHT);
		graphics.rotate(-angle, center.getX(), center.getY());
	}

	/**
	 * Get the minimap's clip area as an ellipse.
	 * Handles fixed, resizable, and bottom-line viewport modes.
	 */
	private Shape getMinimapClipArea()
	{
		Widget minimapWidget = getMinimapDrawWidget();
		if (minimapWidget == null || minimapWidget.isHidden())
		{
			return null;
		}

		Rectangle bounds = minimapWidget.getBounds();
		return new Ellipse2D.Double(
			bounds.getX(), bounds.getY(),
			bounds.getWidth(), bounds.getHeight());
	}

	private Widget getMinimapDrawWidget()
	{
		if (client.isResized())
		{
			if (client.getVarbitValue(Varbits.SIDE_PANELS) == 1)
			{
				return client.getWidget(
					ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
			}
			return client.getWidget(
				ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
		}
		return client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
	}
}
