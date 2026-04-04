package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Stores pending task completions locally in RuneLite's ConfigManager.
 * 
 * Flow:
 * 1. Player clicks "Mark Completed" → addPending() saves instantly (no network)
 * 2. Player clicks "Sync" (or login/logout) → getPending() returns queue
 * 3. After successful server push → clearPending() empties the queue
 *
 * Data is persisted across client restarts via ConfigManager.
 */
@Singleton
@Slf4j
public class LocalCompletionStore
{
	private static final String CONFIG_GROUP = "ultimate-task-master";
	private static final String PENDING_KEY = "pendingCompletions";
	private static final String COMPLETED_KEY = "completedTaskNames";
	private static final Type PENDING_LIST_TYPE = new TypeToken<List<PendingCompletion>>(){}.getType();
	private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>(){}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	/**
	 * A pending completion waiting to be synced to the server.
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PendingCompletion
	{
		private String taskName;
		private int structId;
		private int x;
		private int y;
		private int plane;
		private long timestamp;
	}

	@Inject
	public LocalCompletionStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Add a completion to the pending queue. Instant, no network.
	 */
	public void addPending(String taskName, int structId, int x, int y, int plane)
	{
		List<PendingCompletion> pending = getPendingMutable();
		pending.add(new PendingCompletion(taskName, structId, x, y, plane, System.currentTimeMillis()));
		savePending(pending);
		
		// Also add to completed names set
		addCompletedName(taskName);
		
		log.debug("Queued completion: {} at ({}, {}, {}). Queue size: {}", taskName, x, y, plane, pending.size());
	}

	/**
	 * Get all pending completions (for sync push).
	 */
	public List<PendingCompletion> getPending()
	{
		return Collections.unmodifiableList(getPendingMutable());
	}

	/**
	 * Remove specific items from pending after successful push.
	 */
	public void removePending(List<PendingCompletion> pushed)
	{
		List<PendingCompletion> pending = getPendingMutable();
		pending.removeAll(pushed);
		savePending(pending);
		log.debug("Removed {} pushed items. Queue size: {}", pushed.size(), pending.size());
	}

	/**
	 * Clear all pending completions.
	 */
	public void clearPending()
	{
		configManager.unsetConfiguration(CONFIG_GROUP, PENDING_KEY);
		log.debug("Cleared all pending completions");
	}

	/**
	 * Get the count of pending completions.
	 */
	public int getPendingCount()
	{
		return getPendingMutable().size();
	}

	/**
	 * Get all locally completed task names (persisted across restarts).
	 */
	public java.util.Set<String> getCompletedNames()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, COMPLETED_KEY);
		if (json == null || json.isEmpty())
		{
			return new java.util.HashSet<>();
		}
		try
		{
			List<String> names = gson.fromJson(json, STRING_LIST_TYPE);
			return names != null ? new java.util.HashSet<>(names) : new java.util.HashSet<>();
		}
		catch (Exception e)
		{
			log.warn("Failed to parse completed names", e);
			return new java.util.HashSet<>();
		}
	}

	/**
	 * Add a task name to the completed set.
	 */
	public void addCompletedName(String taskName)
	{
		java.util.Set<String> names = getCompletedNames();
		names.add(taskName);
		configManager.setConfiguration(CONFIG_GROUP, COMPLETED_KEY, gson.toJson(new ArrayList<>(names)));
	}

	/**
	 * Replace the completed names set (e.g. after sync pull).
	 */
	public void setCompletedNames(java.util.Set<String> names)
	{
		configManager.setConfiguration(CONFIG_GROUP, COMPLETED_KEY, gson.toJson(new ArrayList<>(names)));
	}

	private List<PendingCompletion> getPendingMutable()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, PENDING_KEY);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<PendingCompletion> list = gson.fromJson(json, PENDING_LIST_TYPE);
			return list != null ? list : new ArrayList<>();
		}
		catch (Exception e)
		{
			log.warn("Failed to parse pending completions", e);
			return new ArrayList<>();
		}
	}

	private void savePending(List<PendingCompletion> pending)
	{
		configManager.setConfiguration(CONFIG_GROUP, PENDING_KEY, gson.toJson(pending));
	}
}
