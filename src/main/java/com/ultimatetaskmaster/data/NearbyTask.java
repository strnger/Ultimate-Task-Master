package com.ultimatetaskmaster.data;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A task that was found within the spatial search radius, with its resolved location.
 *
 * The resolved location comes from TaskData.location, populated by scraper coordinate data.
 *
 * Overlays and world map markers should use {@link #getResolvedLocation()} for rendering.
 */
@Value
public class NearbyTask
{
	TaskData task;
	WorldPoint resolvedLocation;
	int distance;
}
