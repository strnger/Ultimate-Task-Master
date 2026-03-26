package com.ultimatetaskmaster;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UltimateTaskMasterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UltimateTaskMasterPlugin.class);
		RuneLite.main(args);
	}
}
