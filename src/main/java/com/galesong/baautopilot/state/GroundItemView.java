package com.galesong.baautopilot.state;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@AllArgsConstructor
public class GroundItemView
{
	int itemId;
	WorldPoint tile;
	int quantity;
	/** Bait only: it matched the defender's call when it hit the ground, so runners eat it. */
	boolean good;
	int spawnTick;
	/**
	 * Another item is on top of this tile's pile: a left-click takes that one, so this needs a
	 * right-click and Take. False when it's the top item or alone (and in recordings, which don't know).
	 */
	boolean underneath;

	public GroundItemView(int itemId, WorldPoint tile, int quantity, boolean good, int spawnTick)
	{
		this(itemId, tile, quantity, good, spawnTick, false);
	}

	public GroundItemView withUnderneath(boolean underneath)
	{
		return new GroundItemView(itemId, tile, quantity, good, spawnTick, underneath);
	}
}
