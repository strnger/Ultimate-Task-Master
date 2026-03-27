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
}
