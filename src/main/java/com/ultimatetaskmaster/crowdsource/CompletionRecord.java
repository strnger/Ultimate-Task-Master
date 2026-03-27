package com.ultimatetaskmaster.crowdsource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single recorded task completion with its location.
 *
 * Stored in the local completion store as JSON. Each record says:
 * "Task X was completed at position (x, y, plane) at time T."
 *
 * Uses @Data (not @Value) because Gson needs a no-arg constructor for deserialization.
 * The plane=0 coordinates (x, y) are the most useful for spatial queries.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompletionRecord
{
	private String taskName;
	private int x;
	private int y;
	private int plane;
	private long timestamp;
}
