package com.ultimatetaskmaster.data;

import java.util.List;
import lombok.Data;

/**
 * DTO matching the JSON structure produced by the OSRS Leagues scraper.
 * Used only for Gson deserialization — immediately converted to {@link TaskData}.
 *
 * <p>Each element in {@code LEAGUE_5.full.json} has this shape:</p>
 * <pre>{@code
 * {
 *   "structId": 2631,
 *   "sortId": 0,
 *   "name": "Defeat a Moss Giant",
 *   "description": "Defeat a Moss Giant",
 *   "area": "Global",
 *   "category": "Combat",
 *   "skill": "All",
 *   "tier": 1,
 *   "tierName": "Easy",
 *   "completionPercent": 84.8,
 *   "skills": [
 *     { "skill": "DEFENCE", "level": 40 },
 *     { "skill": "RANGED", "level": 70 }
 *   ],
 *   "wikiNotes": "70 Ranged,  40 Defence"
 * }
 * }</pre>
 *
 * @see StaticTaskDataProvider
 */
@Data
class JsonTaskDefinition
{
	private int structId;
	private int sortId;
	private String name;
	private String description;
	private String area;
	private String category;
	private String skill;
	private int tier;
	private String tierName;
	private Float completionPercent;
	private List<JsonSkillReq> skills;
	private String wikiNotes;

	/**
	 * A single skill requirement entry.
	 * Skill names use uppercase enum-style values (e.g. {@code "DEFENCE"}, {@code "RANGED"}).
	 */
	@Data
	static class JsonSkillReq
	{
		private String skill;
		private int level;
	}
}
