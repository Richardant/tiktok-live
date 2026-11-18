package com.tiktoklive;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TikTokLivePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TikTokLivePlugin.class);
		RuneLite.main(args);
	}
}
