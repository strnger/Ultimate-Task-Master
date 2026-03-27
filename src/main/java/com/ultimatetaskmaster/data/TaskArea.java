package com.ultimatetaskmaster.data;

import lombok.Getter;

/**
 * Game areas/regions for task categorization.
 * Matches OSRS Leagues region system.
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

	private final String displayName;

	TaskArea(String displayName)
	{
		this.displayName = displayName;
	}

	public static TaskArea fromString(String name)
	{
		for (TaskArea area : values())
		{
			if (area.displayName.equalsIgnoreCase(name))
			{
				return area;
			}
		}
		return GENERAL;
	}
}
