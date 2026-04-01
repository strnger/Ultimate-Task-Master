package com.ultimatetaskmaster.data;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Task difficulty tiers, matching OSRS Leagues tier system.
 * Points and colors derived from Raging Echoes League data.
 * Tier IDs (1-5) match the scraper's integer mapping.
 */
@Getter
public enum TaskTier
{
	EASY("Easy", 1, 10, new Color(190, 190, 190)),
	MEDIUM("Medium", 2, 40, new Color(161, 255, 132)),
	HARD("Hard", 3, 80, new Color(70, 255, 207)),
	ELITE("Elite", 4, 200, new Color(6, 160, 255)),
	MASTER("Master", 5, 400, new Color(255, 0, 0));

	private static final Map<Integer, TaskTier> BY_ID = new HashMap<>();
	private static final Map<String, TaskTier> BY_NAME = new HashMap<>();

	static
	{
		for (TaskTier tier : values())
		{
			BY_ID.put(tier.tierId, tier);
			BY_NAME.put(tier.displayName.toLowerCase(), tier);
		}
	}

	private final String displayName;
	private final int tierId;
	private final int defaultPoints;
	private final Color color;

	TaskTier(String displayName, int tierId, int defaultPoints, Color color)
	{
		this.displayName = displayName;
		this.tierId = tierId;
		this.defaultPoints = defaultPoints;
		this.color = color;
	}

	/**
	 * Look up tier by integer ID from scraper data (1=Easy ... 5=Master).
	 */
	public static TaskTier fromInt(int tierId)
	{
		return BY_ID.getOrDefault(tierId, EASY);
	}

	/**
	 * Parse tier name from JSON data (case-insensitive).
	 */
	public static TaskTier fromString(String name)
	{
		if (name == null)
		{
			return EASY;
		}
		return BY_NAME.getOrDefault(name.toLowerCase(), EASY);
	}
}
