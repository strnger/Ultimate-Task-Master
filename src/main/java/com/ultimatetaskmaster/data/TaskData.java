package com.ultimatetaskmaster.data;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Immutable task data object - the core domain model for a single task.
 *
 * All task data flows through this POJO, whether loaded from static JSON,
 * a remote API, or OSRS game structs. Keep it lean. No behavior, just data.
 *
 * Fields sourced from the full-task-scraper output:
 * - structId: game cache struct ID (canonical task identifier)
 * - sortId: in-game UI sort order
 * - category/skill: new filter dimensions from scraper
 *
 * WorldPoint may be null for locationless tasks.
 * CompletionPct may be null if data is unavailable.
 */
@Value
@Builder
public class TaskData
{
	/** Game cache struct ID - the canonical task identifier. */
	int structId;

	/** In-game UI sort order. */
	int sortId;

	String name;
	String description;
	TaskArea area;
	TaskTier tier;
	int points;

	/** Task category. Null if unknown. */
	@Nullable
	String category;

	/** Primary skill group. Null if unknown. */
	@Nullable
	String skill;

	/** Task primary location. Null for locationless tasks. */
	@Nullable
	WorldPoint location;

	/** Completion percentage (0.0 - 100.0). Null if unknown. */
	@Nullable
	Float completionPct;

	/** Skill requirements. May be empty, never null after build. */
	List<TaskSkillRequirement> requirements;
}
