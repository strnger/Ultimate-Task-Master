package com.ultimatetaskmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Plugin configuration.
 *
 * Each @ConfigItem becomes a setting in the RuneLite plugin config panel.
 */
@ConfigGroup(UltimateTaskMasterPlugin.CONFIG_GROUP)
public interface UltimateTaskMasterConfig extends Config
{
	// --- Near Me Settings ---

	@ConfigSection(
		name = "Near Me",
		description = "Settings for the 'What's Near Me?' spatial query feature",
		position = 0
	)
	String nearMeSettings = "nearMeSettings";

	@ConfigItem(
		position = 0,
		keyName = "searchRadius",
		name = "Search Radius",
		description = "How many tiles away to search for nearby tasks (Chebyshev distance)",
		section = nearMeSettings
	)
	@Range(min = 10, max = 500)
	default int searchRadius()
	{
		return 100;
	}

	@ConfigItem(
		position = 1,
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Display nearby task highlights in the game world and minimap",
		section = nearMeSettings
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "showWorldMapMarkers",
		name = "Show Map Markers",
		description = "Display nearby task markers on the world map (click to jump)",
		section = nearMeSettings
	)
	default boolean showWorldMapMarkers()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "filterBySkillLevel",
		name = "Filter by Level",
		description = "Hide nearby tasks you don't have the skill levels for",
		section = nearMeSettings
	)
	default boolean filterBySkillLevel()
	{
		return true;
	}

	// --- Beta Settings ---

	@ConfigSection(
		name = "Beta",
		description = "Beta access settings",
		position = 2
	)
	String betaSettings = "betaSettings";

	@ConfigItem(
		position = 0,
		keyName = "betaUnlocked",
		name = "Beta Unlocked",
		description = "Whether the beta key has been entered",
		section = betaSettings,
		hidden = true
	)
	default boolean betaUnlocked()
	{
		return false;
	}

	// --- Notification Settings ---

	@ConfigSection(
		name = "Notifications",
		description = "Notification settings",
		position = 1
	)
	String notificationSettings = "notificationSettings";

	@ConfigItem(
		position = 0,
		keyName = "notifyOnComplete",
		name = "Notify on Completion",
		description = "Send a notification when a nearby task is completed",
		section = notificationSettings
	)
	default boolean notifyOnComplete()
	{
		return true;
	}
}
