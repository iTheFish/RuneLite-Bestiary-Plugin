package net.runelite.client.plugins.bestiary;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher: starts a from-source RuneLite client with this plugin loaded.
 * Run via {@code ./gradlew run} (passes --developer-mode).
 */
public class BestiaryPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BestiaryPlugin.class);
		RuneLite.main(args);
	}
}
