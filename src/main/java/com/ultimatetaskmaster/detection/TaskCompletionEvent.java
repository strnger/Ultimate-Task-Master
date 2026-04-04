package com.ultimatetaskmaster.detection;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Fired on RuneLite's EventBus when a task completion is detected.
 * Any detection method can fire this event.
 *
 * @see TaskCompletionListener
 */
@Value
public class TaskCompletionEvent
{
	/** The task name as parsed from the detection source. */
	String taskName;

	/**
	 * The difficulty tier as a lowercase string (e.g., "easy", "medium", "hard", "elite", "master").
	 * Null if the tier could not be determined (e.g., from a varbit-only detection).
	 */
	@Nullable
	String tier;

	/** Where the player was standing when they completed the task. */
	WorldPoint playerLocation;

	/** System.currentTimeMillis() when the completion was detected. */
	long timestamp;
}
