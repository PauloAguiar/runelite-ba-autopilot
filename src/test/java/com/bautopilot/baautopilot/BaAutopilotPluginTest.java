package com.bautopilot.baautopilot;

import com.bautopilot.baautopilot.dev.BaRecorderPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BaAutopilotPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// The round recorder writes ~/.runelite/ba-autopilot/recordings/*.jsonl (see README)
		ExternalPluginManager.loadBuiltin(BaAutopilotPlugin.class, BaRecorderPlugin.class);
		RuneLite.main(args);
	}
}
