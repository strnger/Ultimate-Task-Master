package com.ultimatetaskmaster.data;

import java.util.List;

/**
 * Provides the list of all known tasks.
 *
 * Implementations:
 * - StaticTaskDataProvider: Loads from bundled tasks.json (offline fallback)
 * - HttpTaskDataProvider: Fetches from full-task-scraper GitHub (primary)
 *
 * @see StaticTaskDataProvider
 * @see HttpTaskDataProvider
 */
public interface TaskDataProvider
{
	/**
	 * Get all known tasks. The returned list is immutable and never null.
	 * May be empty if loading failed.
	 */
	List<TaskData> getTasks();
}
