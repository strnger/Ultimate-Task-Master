package com.ultimatetaskmaster.crowdsource;

import java.util.List;

/**
 * Persistence interface for task completion location records.
 *
 * <h3>Design: Interface-based for swappability</h3>
 * Bound via Guice in the plugin's {@code configure(Binder)} method.
 * Swap implementations without touching business logic:
 * <ul>
 *   <li>{@link LocalCompletionStore} — MVP. ConfigManager JSON persistence. Single-player only.</li>
 *   <li>{@code HttpCompletionStore} — Future. POSTs records to our backend API, aggregates
 *       across all players. Based on the WikiSync submission pattern.</li>
 * </ul>
 *
 * <h3>WikiSync Pattern Reference</h3>
 * Our future HTTP store should follow the WikiSync submission flow:
 * <ol>
 *   <li>Plugin detects completion -> creates {@link CompletionRecord}</li>
 *   <li>Record POSTed to {@code /api/v1/locations/submit} (async via OkHttpClient.enqueue())</li>
 *   <li>Server appends record to DB (see OverallDesign.md "Backend Architecture Reference")</li>
 *   <li>On success, mark record as synced locally</li>
 *   <li>Use quadratic backoff on failure (WikiSync pattern: retry on perfect squares)</li>
 * </ol>
 *
 * <p>Unlike WikiSync's delta-only submission (which merges varps), our data is append-only —
 * each completion is a new record, not an update to an existing value. This is simpler:
 * no diffing needed, just POST new records that haven't been synced yet.</p>
 *
 * <p>Reference: {@code examples/WikiSync/WikiSyncPlugin.java} — see {@code submitPlayerData()},
 * {@code subtract()}, and {@code merge()} for the delta submission pattern.
 * Reference: {@code examples/wikisync-api/src/runelite/service.ts} — see {@code parseAndSaveData()}
 * for the server-side merge logic.</p>
 *
 * @see LocalCompletionStore
 * @see CompletionRecord
 */
public interface CompletionLocationStore
{
	/**
	 * Persist a single completion record.
	 * Implementations must be thread-safe (called from game thread).
	 */
	void save(CompletionRecord record);

	/**
	 * Get all records for a specific task name.
	 * Used by {@link TaskLocationResolver} to compute the median location.
	 */
	List<CompletionRecord> getRecordsForTask(String taskName);

	/**
	 * Get ALL stored records (all tasks).
	 * Used for bulk operations like HTTP sync or cache warming.
	 */
	List<CompletionRecord> getAllRecords();

	/**
	 * Total number of stored records across all tasks.
	 * Used for logging and diagnostics.
	 */
	int getRecordCount();
}
