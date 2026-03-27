package com.ultimatetaskmaster.data;

import java.awt.Color;
import lombok.Getter;

/**
 * Task difficulty tiers, matching OSRS Leagues tier system.
 * Points and colors derived from official game data.
 */
@Getter
public enum TaskTier
{
	EASY("Easy", 10, new Color(190, 190, 190)),
	MEDIUM("Medium", 30, new Color(161, 255, 132)),
	HARD("Hard", 80, new Color(70, 255, 207)),
	ELITE("Elite", 200, new Color(6, 160, 255)),
	MASTER("Master", 400, new Color(255, 0, 0));

	private final String displayName;
	private final int defaultPoints;
	private final Color color;

	TaskTier(String displayName, int defaultPoints, Color color)
	{
		this.displayName = displayName;
		this.defaultPoints = defaultPoints;
		this.color = color;
	}

	/**
	 * Parse tier name from JSON data (case-insensitive).
	 */
	public static TaskTier fromString(String name)
	{
		for (TaskTier tier : values())
		{
			if (tier.displayName.equalsIgnoreCase(name))
			{
				return tier;
			}
		}
		return EASY;
	}
}
