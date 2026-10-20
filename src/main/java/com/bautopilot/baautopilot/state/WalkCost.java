package com.bautopilot.baautopilot.state;

import net.runelite.api.coords.WorldPoint;

/** Tiles to walk from you to a template tile on the collision map, -1 when unknown. */
public interface WalkCost
{
	int tilesTo(WorldPoint tile);
}
