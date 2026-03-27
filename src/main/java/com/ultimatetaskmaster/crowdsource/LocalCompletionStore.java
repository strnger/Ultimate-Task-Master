package com.ultimatetaskmaster.crowdsource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Persists completion records locally in RuneLite's ConfigManager as JSON.
 *
 * <h3>Storage</h3>
 * ConfigManager stores key-value pairs per RuneLite profile, which means:
 * <ul>
 *   <li>Records survive client restarts</li>
 *   <li>Each profile (account) has its own data</li>
 *   <li>No external server needed for the MVP</li>
 * </ul>
 *
 * Layout: {@code Group: "ultimate-task-master", Key: "completion-records",
 * Value: JSON array of CompletionRecord objects}
 *
 * <h3>In-Memory Index</h3>
 * The {@code taskNameIndex} provides O(1) lookup by task name without
 * deserializing the JSON array on every query. Rebuilt on startup from
 * the persisted data.
 *
 * <h3>WikiSync Comparison</h3>
 * WikiSync stores player data as a single JSON blob per username+profile
 * in a MySQL table ({@code player_data_json}). We use the same pattern
 * locally: one JSON blob for all records in ConfigManager.
 *
 * <p>WikiSync's "hidden config item" pattern is relevant here too — their
 * {@code WikiSyncConfig.wikiSyncVersion()} stores internal state in a
 * hidden {@code @ConfigItem}. We could add a "last-synced-index" config
 * item to track which records have been pushed to the HTTP backend.</p>
 *
 * <p>When we add {@code HttpCompletionStore}, the sync flow would be:</p>
 * <ol>
 *   <li>On save(): append record locally AND queue for HTTP POST</li>
 *   <li>On @Schedule tick: POST un-synced records to backend</li>
 *   <li>On success: mark records as synced (store the synced index)</li>
 *   <li>On failure: quadratic backoff (WikiSync pattern)</li>
 * </ol>
 *
 * <p>Reference: {@code examples/WikiSync/WikiSyncPlugin.java} — see
 * {@code submitPlayerData()} and {@code cyclesSinceSuccessfulCall}.
 * Reference: {@code examples/wikisync-api/src/runelite/service.ts} — see
 * {@code parseAndSaveData()} for server-side JSON blob merge.</p>
 *
 * TODO: Add record cap / LRU eviction to prevent unbounded growth in ConfigManager.
 *       WikiSync doesn't have this problem because it only stores the latest values
 *       (not a history), but our append-only model could grow large over time.
 *
 * @see CompletionLocationStore
 * @see CompletionRecord
 */
@Singleton
@Slf4j
public class LocalCompletionStore implements CompletionLocationStore
{
	private static final String CONFIG_GROUP = "ultimate-task-master";
	private static final String CONFIG_KEY = "completion-records";
	private static final Type RECORD_LIST_TYPE =
		TypeToken.getParameterized(List.class, CompletionRecord.class).getType();

	private final ConfigManager configManager;
	private final Gson gson;

	/** All records in memory. */
	private final List<CompletionRecord> records = new ArrayList<>();

	/** Index: task name -> list of records for O(1) lookup. */
	private final Map<String, List<CompletionRecord>> taskNameIndex = new HashMap<>();

	@Inject
	public LocalCompletionStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		loadFromConfig();
	}

	@Override
	public synchronized void save(CompletionRecord record)
	{
		records.add(record);
		taskNameIndex.computeIfAbsent(record.getTaskName(), k -> new ArrayList<>()).add(record);
		persistToConfig();
	}

	@Override
	public synchronized List<CompletionRecord> getRecordsForTask(String taskName)
	{
		List<CompletionRecord> result = taskNameIndex.get(taskName);
		return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
	}

	@Override
	public synchronized List<CompletionRecord> getAllRecords()
	{
		return Collections.unmodifiableList(new ArrayList<>(records));
	}

	@Override
	public synchronized int getRecordCount()
	{
		return records.size();
	}

	private void loadFromConfig()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			log.debug("No existing completion records found in config.");
			return;
		}

		try
		{
			List<CompletionRecord> loaded = gson.fromJson(json, RECORD_LIST_TYPE);
			if (loaded != null)
			{
				records.addAll(loaded);
				rebuildIndex();
				log.info("Loaded {} completion records from config.", records.size());
			}
		}
		catch (Exception e)
		{
			log.error("Failed to parse completion records from config — starting fresh.", e);
		}
	}

	private void persistToConfig()
	{
		try
		{
			String json = gson.toJson(records, RECORD_LIST_TYPE);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
		}
		catch (Exception e)
		{
			log.error("Failed to persist completion records to config.", e);
		}
	}

	private void rebuildIndex()
	{
		taskNameIndex.clear();
		taskNameIndex.putAll(
			records.stream().collect(Collectors.groupingBy(CompletionRecord::getTaskName))
		);
	}
}
