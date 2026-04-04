package com.ultimatetaskmaster.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Game areas/regions for task categorization.
 * Matches OSRS Leagues region system.
 *
 * Supports lookup by display name and scraper aliases
 * (e.g. "Global", "Fremennik Province", "Kharidian Desert", "Kourend & Kebos").
 */
@Getter
public enum TaskArea
{
	GENERAL("General"),
	MISTHALIN("Misthalin"),
	KARAMJA("Karamja"),
	ASGARNIA("Asgarnia"),
	FREMENNIK("Fremennik"),
	KANDARIN("Kandarin"),
	DESERT("Desert"),
	MORYTANIA("Morytania"),
	TIRANNWN("Tirannwn"),
	WILDERNESS("Wilderness"),
	KOUREND("Kourend"),
	VARLAMORE("Varlamore");

	private static final Map<String, TaskArea> LOOKUP = new HashMap<>();

	static
	{
		// Register each enum value by its display name (lower-cased)
		for (TaskArea area : values())
		{
			LOOKUP.put(area.displayName.toLowerCase(), area);
		}

		// Scraper aliases for backward compatibility
		LOOKUP.put("global", GENERAL);
		LOOKUP.put("fremennik province", FREMENNIK);
		LOOKUP.put("kharidian desert", DESERT);
		LOOKUP.put("kourend & kebos", KOUREND);
	}

	private final String displayName;

	TaskArea(String displayName)
	{
		this.displayName = displayName;
	}

	public static TaskArea fromString(String name)
	{
		if (name == null)
		{
			return GENERAL;
		}
		return LOOKUP.getOrDefault(name.toLowerCase(), GENERAL);
	}
}
