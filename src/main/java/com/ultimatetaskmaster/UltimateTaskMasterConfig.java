package com.ultimatetaskmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(UltimateTaskMasterPlugin.CONFIG_GROUP)
public interface UltimateTaskMasterConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "General settings",
		position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigItem(
		position = 0,
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Display the task progress overlay in-game",
		section = generalSettings
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 1,
		keyName = "notifyOnComplete",
		name = "Notify on Completion",
		description = "Send a notification when a tracked task is completed",
		section = generalSettings
	)
	default boolean notifyOnComplete()
	{
		return true;
	}
}
