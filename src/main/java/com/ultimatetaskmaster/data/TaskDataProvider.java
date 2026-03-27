package com.ultimatetaskmaster.data;

import java.util.List;

/**
 * Provides the list of all known tasks. Abstracted as an interface for
 * easy swapping between data sources via Guice binding.
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@link StaticTaskDataProvider} — MVP. Loads from bundled {@code tasks.json}.</li>
 *   <li>{@code HttpTaskDataProvider} — Future. Fetches from our API server with
 *       version-check caching (WikiSync manifest pattern).</li>
 * </ul>
 *
 * <h3>WikiSync Manifest Pattern</h3>
 * WikiSync separates "what to collect" (manifest) from "stored data" (player JSON blob).
 * The manifest is fetched periodically and drives the plugin's behavior.
 *
 * <p>Our task list is analogous to WikiSync's manifest — it defines what the plugin
 * knows about. When the server updates the task list (new league, new tasks),
 * the plugin auto-fetches and adapts. No plugin update needed.</p>
 *
 * <p>Reference: {@code examples/WikiSync/WikiSyncPlugin.java} — see
 * {@code checkManifest()} for the periodic fetch pattern, and {@code Manifest.java}
 * for the data model.</p>
 *
 * @see StaticTaskDataProvider
 */
public interface TaskDataProvider
{
	/**
	 * Get all known tasks. The returned list is immutable and never null.
	 * May be empty if loading failed.
	 */
	List<TaskData> getTasks();
}
