package com.ultimatetaskmaster.data;

import com.google.gson.Gson;
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
