package com.ultimatetaskmaster.detection;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Fired on RuneLite's EventBus when a task completion is detected.
 *
 * This is the ONLY contract between detection and the rest of the plugin.
 * Any detection method (chat messages, varbits, widgets) can fire this event.
 * The plugin subscribes to it and doesn't care how the completion was detected.
 *
 * To add a new detection method:
 * 1. Create a new class (like VarbitCompletionDetector)
 * 2. Inject EventBus and call eventBus.post(new TaskCompletionEvent(...))
 * 3. Register it in the plugin's startUp(), unregister in shutDown()
 * That's it. No other code changes needed.
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
