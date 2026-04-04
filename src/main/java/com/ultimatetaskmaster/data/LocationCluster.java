package com.ultimatetaskmaster.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A cluster of nearby world coordinates where a task can be completed.
 * Pre-computed from LeaguesMap data (monsters, items, scenery locations).
 *
 * The x,y coordinates are OSRS world tile coordinates.
 * Count indicates how many raw data points were clustered into this centroid.
 * structId links back to the task's game cache struct ID (used by crowdsourced data;
 * defaults to 0 for static task_locations.json where structId is the map key).
 */
@Data
@NoArgsConstructor
public class LocationCluster
{
	private int x;
	private int y;
	private int count;
	private int structId;
}
