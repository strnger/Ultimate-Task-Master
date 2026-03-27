package com.ultimatetaskmaster.data;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Immutable task data object — the core domain model for a single task.
 *
 * All task data flows through this POJO, whether loaded from static stubs,
 * a JSON API, or OSRS game structs. Keep it lean. No behavior, just data.
 *
 * WorldPoint may be null for locationless tasks (e.g., "level up any skill").
 * CompletionPct may be null if wiki data is unavailable.
 */
@Value
@Builder
public class TaskData
{
	int id;
	String name;
	String description;
	TaskArea area;
	TaskTier tier;
	int points;

	/** Task's primary location. Null for locationless tasks. */
	@Nullable
	WorldPoint location;

	/** Wiki completion percentage (0.0 - 100.0). Null if unknown. */
	@Nullable
	Float completionPct;

	/** Skill requirements. May be empty, never null after build. */
	List<TaskSkillRequirement> requirements;
}
