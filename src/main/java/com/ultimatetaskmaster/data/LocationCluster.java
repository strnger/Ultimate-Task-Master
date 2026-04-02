package com.ultimatetaskmaster.data;

import lombok.Value;

/**
 * A cluster of nearby world coordinates where a task can be completed.
 * Pre-computed from LeaguesMap data (monsters, items, scenery locations).
 *
 * The x,y coordinates are OSRS world tile coordinates.
 * Count indicates how many raw data points were clustered into this centroid.
 */
@Value
public class LocationCluster
{
	int x;
	int y;
	int count;
}
