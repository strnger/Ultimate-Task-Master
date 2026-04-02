package com.ultimatetaskmaster.worldmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import com.ultimatetaskmaster.data.TaskData;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

/**
 * A clickable world map marker for a task location.
 *
 * Used for both 'Near Me' nearby task markers and 'Current Plan' pinned location markers.
 *
 * Uses jump-on-click so players can quickly navigate to task locations
 * on the world map. Color-coded by task tier.
 *
 * Follows the WorldMapPointManager pattern from Leagues Planner:
 * - Add via worldMapPointManager.add()
 * - Remove via worldMapPointManager.removeIf(p -> p instanceof TaskWorldMapPoint)
 *
 * TODO: Replace programmatic icons with proper PNG sprites per tier
 *       (see Leagues Planner's taskMarkerEasy.png etc.).
 */
public class TaskWorldMapPoint extends WorldMapPoint
{
	private static final int ICON_SIZE = 10;

	@Getter
	private final TaskData taskData;

	public TaskWorldMapPoint(WorldPoint worldPoint, TaskData taskData)
	{
		super(worldPoint, createMarkerIcon(taskData));
		this.taskData = taskData;
		setName(taskData.getName());
		setTarget(worldPoint);
		setJumpOnClick(true);
		setSnapToEdge(true);
	}

	/**
	 * Creates a location marker with a specific color (for "Show on map" dots).
	 */
	public TaskWorldMapPoint(WorldPoint worldPoint, TaskData taskData, Color markerColor, int size)
	{
		super(worldPoint, createColoredIcon(markerColor, size));
		this.taskData = taskData;
		setName(taskData.getName());
		setTarget(worldPoint);
		setJumpOnClick(true);
		setSnapToEdge(true);
	}

	/**
	 * Creates a simple colored circle icon for the map marker.
	 * Color matches the task's tier.
	 */
	private static BufferedImage createMarkerIcon(TaskData taskData)
	{
		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();

		Color tierColor = taskData.getTier().getColor();

		// Filled circle with darker outline
		g.setColor(tierColor);
		g.fillOval(1, 1, ICON_SIZE - 2, ICON_SIZE - 2);
		g.setColor(tierColor.darker());
		g.drawOval(1, 1, ICON_SIZE - 2, ICON_SIZE - 2);

		g.dispose();
		return img;
	}

	/**
	 * Creates a colored circle icon of a specific size.
	 * Used by the "Show on map" location markers.
	 */
	private static BufferedImage createColoredIcon(Color color, int size)
	{
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillOval(1, 1, size - 2, size - 2);
		g.setColor(color.darker());
		g.drawOval(1, 1, size - 2, size - 2);
		g.dispose();
		return img;
	}
}
