package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for submitting task completions to the crowdsourcing server.
 * 
 * Follows the same pattern as tog-crowdsourcing's CrowdsourcingManager:
 * - Uses OkHttpClient (injected by RuneLite)
 * - Async POST via enqueue (non-blocking)
 * - JSON payload with task info + player position
 *
 * Server endpoint: POST /api/submit
 * Payload: { task_name, struct_id, x, y, plane }
 */
@Singleton
@Slf4j
public class CrowdsourcingService
{
	// Local dev server — change to production URL when deployed
	private static final String SERVER_BASE_URL = "http://localhost:3847";
	private static final String SUBMIT_URL = SERVER_BASE_URL + "/api/submit";
	private static final String LOCATIONS_URL = SERVER_BASE_URL + "/api/locations/clustered";
	private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient okHttpClient;
	private final Gson gson;

	/**
	 * Payload sent to the server on task completion.
	 */
	@Data
	public static class CompletionPayload
	{
		private final String task_name;
		private final int struct_id;
		private final int x;
		private final int y;
		private final int plane;
	}

	/**
	 * Location data returned from the server's clustered endpoint.
	 */
	@Data
	public static class ServerLocation
	{
		private String task_name;
		private int struct_id;
		private int x;
		private int y;
		private int plane;
		private int hits;
		private int total_hits;
		private int percentage;
		private int point_count;
	}

	@Inject
	public CrowdsourcingService(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Submit a task completion to the crowdsourcing server.
	 * Non-blocking — fires and forgets via OkHttp async callback.
	 *
	 * @param taskName  display name of the task
	 * @param structId  unique task struct ID
	 * @param x         player world X coordinate
	 * @param y         player world Y coordinate
	 * @param plane     player world plane (0 = surface)
	 */
	public void submitCompletion(String taskName, int structId, int x, int y, int plane)
	{
		CompletionPayload payload = new CompletionPayload(taskName, structId, x, y, plane);
		String json = gson.toJson(payload);

		Request request = new Request.Builder()
			.url(SUBMIT_URL)
			.post(RequestBody.create(JSON_TYPE, json))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to submit task completion to crowdsourcing server: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (response.isSuccessful())
					{
						log.debug("Task completion submitted: {} at ({}, {}, {})", taskName, x, y, plane);
					}
					else
					{
						log.debug("Crowdsourcing server returned {}: {}", response.code(), response.message());
					}
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	/**
	 * Push a batch of pending completions to the server.
	 * Synchronous — MUST be called from a background thread, not EDT or game thread.
	 *
	 * @param pending list of completions to push
	 * @return number of successfully pushed items
	 */
	public int pushPending(List<LocalCompletionStore.PendingCompletion> pending)
	{
		int pushed = 0;
		for (LocalCompletionStore.PendingCompletion item : pending)
		{
			try
			{
				CompletionPayload payload = new CompletionPayload(
					item.getTaskName(), item.getStructId(),
					item.getX(), item.getY(), item.getPlane()
				);
				Request request = new Request.Builder()
					.url(SUBMIT_URL)
					.post(RequestBody.create(JSON_TYPE, gson.toJson(payload)))
					.build();
				try (Response response = okHttpClient.newCall(request).execute())
				{
					if (response.isSuccessful())
					{
						pushed++;
					}
					else
					{
						log.debug("Push failed for {}: {}", item.getTaskName(), response.code());
					}
				}
			}
			catch (IOException e)
			{
				log.debug("Push failed for {}: {}", item.getTaskName(), e.getMessage());
			}
		}
		return pushed;
	}

	/**
	 * Pull all crowdsourced locations from the server.
	 * Synchronous — MUST be called from a background thread.
	 *
	 * @return list of server locations, or empty list on failure
	 */
	public List<ServerLocation> pullLocations()
	{
		try
		{
			Request request = new Request.Builder()
				.url(LOCATIONS_URL)
				.build();
			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (response.isSuccessful() && response.body() != null)
				{
					Type listType = new TypeToken<List<ServerLocation>>(){}.getType();
					List<ServerLocation> locations = gson.fromJson(response.body().string(), listType);
					return locations != null ? locations : Collections.emptyList();
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Failed to pull locations: {}", e.getMessage());
		}
		return Collections.emptyList();
	}

	/**
	 * Check if the server is reachable (for UI status indicator).
	 * Synchronous — call from background thread only.
	 */
	public boolean isServerReachable()
	{
		try
		{
			Request request = new Request.Builder()
				.url(SERVER_BASE_URL + "/api/stats")
				.build();
			try (Response response = okHttpClient.newCall(request).execute())
			{
				return response.isSuccessful();
			}
		}
		catch (IOException e)
		{
			return false;
		}
	}
}
