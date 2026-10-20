package com.galesong.baautopilot.overlay;

import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Template (instance) coordinates back to the live scene. */
public final class Scenes
{
	private Scenes()
	{
	}

	/**
	 * The live tile for a template tile. An instance can use the same template chunk more than once,
	 * so of all its copies take the one in the loaded scene nearest to you (taking the first copy
	 * could land outside the scene, and then nothing was drawn).
	 */
	public static WorldPoint toWorld(Client client, WorldPoint template)
	{
		if (template == null)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		Collection<WorldPoint> live = WorldPoint.toLocalInstance(wv, template);
		Player me = client.getLocalPlayer();
		WorldPoint here = me == null ? null : me.getWorldLocation();
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (WorldPoint w : live)
		{
			if (LocalPoint.fromWorld(wv, w) == null)
			{
				continue;
			}
			int d = here == null ? 0 : here.distanceTo2D(w);
			if (d < bestDistance)
			{
				best = w;
				bestDistance = d;
			}
		}
		return best;
	}

	public static LocalPoint toLocal(Client client, WorldPoint template)
	{
		WorldPoint world = toWorld(client, template);
		return world == null ? null : LocalPoint.fromWorld(client.getTopLevelWorldView(), world);
	}
}
