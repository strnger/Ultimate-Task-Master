package com.ultimatetaskmaster.data;

import java.util.List;
import lombok.Data;

/**
 * DTO matching the JSON structure in tasks.json (scraped from the OSRS wiki).
 * Used only for Gson deserialization — immediately converted to {@link TaskData}.
 *
 * JSON shape:
 * <pre>
 * {
 *   "area": "General",
 *   "name": "Achieve Your First Level 10",
 *   "description": "Reach level 10 in any skill",
 *   "requirements_text": "...",
 *   "requirements": [{"skill": "Fishing", "level": 10}],
 *   "points": 10,
 *   "tier": "Easy",
 *   "completion_pct": 93.4
 * }
 * </pre>
 */
@Data
class JsonTaskDefinition
{
	private String area;
	private String name;
	private String description;
	private String requirements_text;
	private List<JsonSkillReq> requirements;
	private int points;
	private String tier;
	private Float completion_pct;

	@Data
	static class JsonSkillReq
	{
		private String skill;
		private int level;
	}
}
