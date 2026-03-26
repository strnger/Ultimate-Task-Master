package com.ultimatetaskmaster;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Ultimate Task Master",
	description = "Track and manage in-game tasks and goals",
	tags = {"tasks", "goals", "tracker"}
)
public class UltimateTaskMasterPlugin extends Plugin
{
	static final String CONFIG_GROUP = "ultimate-task-master";

	@Inject
	private Client client;

	@Inject
	private UltimateTaskMasterConfig config;

	@Provides
	UltimateTaskMasterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateTaskMasterConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Ultimate Task Master started!");
	}

	@Override
	protected void shutDown()
	{
		log.info("Ultimate Task Master stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		log.debug("Game state changed to {}", gameStateChanged.getGameState());
	}
}
