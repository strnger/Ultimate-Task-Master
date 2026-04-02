package com.ultimatetaskmaster.data;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single task in the user's plan.
 *
 * Tracks which task was selected, its order in the plan, and an optional
 * pinned location (chosen by the user from suggested locations).
 *
 * Uses @Data (not @Value) because Gson needs a no-arg constructor for deserialization.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanItem
{
	/** The task name (matches TaskData.name). */
	private String taskName;

	/** The game cache struct ID (matches TaskData.structId). */
	private int structId;

	/** Position in the plan (0-based, user's selection order). */
	private int order;

	/** Pinned location X coordinate (OSRS world tile), or null if not pinned. */
	@Nullable
	private Integer pinnedX;

	/** Pinned location Y coordinate (OSRS world tile), or null if not pinned. */
	@Nullable
	private Integer pinnedY;
}
