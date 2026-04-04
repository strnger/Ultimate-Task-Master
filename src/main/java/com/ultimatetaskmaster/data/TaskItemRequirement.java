package com.ultimatetaskmaster.data;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * An item requirement for a task.
 * Immutable value object — (name, quantity, itemId, alternateIds).
 *
 * Example: "Any axe" → name="Any axe", itemId=1351 (bronze axe),
 *          alternateIds=[1349, 1353, ...] (iron, steel, etc.)
 *
 * itemId may be -1 if the item ID is unknown (display-only).
 */
@Value
@Builder
public class TaskItemRequirement
{
	/** Display name of the item (e.g. "Tinderbox", "Any axe", "Coins") */
	String name;

	/** Required quantity (default 1) */
	@Builder.Default
	int quantity = 1;

	/** RuneLite ItemID constant, or -1 if unknown */
	@Builder.Default
	int itemId = -1;

	/** Alternative item IDs that also satisfy this requirement */
	@Nullable
	@Builder.Default
	List<Integer> alternateIds = Collections.emptyList();
}
