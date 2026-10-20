package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import net.runelite.api.coords.WorldPoint;

/**
 * How well a penance runner is held by the defender's food trail, worst first when it matters:
 * escaping south past the trap, loose (no bait in range: it wanders, mostly south), bad bait only
 * (it gets pulled along, not killed), lured to good bait, or stacked on the trap food.
 */
public enum RunnerState
{
	ESCAPING,
	LOOSE,
	BAD_ONLY,
	LURED,
	STACKED;

	/**
	 * Bait in range uses the defender's lure range (4 tiles at level 1, 5 from level 2), as a
	 * square around the runner (OSRS Wiki, Advanced strategies).
	 */
	public static RunnerState of(Snapshot s, PenanceView runner)
	{
		WorldPoint tile = runner.getTile();
		if (tile == null)
		{
			return LOOSE;
		}
		WorldPoint trapFood = ArenaTiles.trapFood(s.getWave());
		if (ArenaTiles.distance(tile, trapFood) <= DefenderPlanner.STACK_RADIUS)
		{
			return STACKED;
		}
		if (tile.getY() <= trapFood.getY() - DefenderPlanner.RELURE_SOUTH)
		{
			return ESCAPING;
		}
		boolean good = false;
		boolean bad = false;
		for (GroundItemView bait : s.getGroundItems())
		{
			if (bait.getTile() == null || !BaIds.BAIT.contains(bait.getItemId())
				|| ArenaTiles.distance(tile, bait.getTile()) > s.baitRange())
			{
				continue;
			}
			good |= bait.isGood();
			bad |= !bait.isGood();
		}
		return good ? LURED : bad ? BAD_ONLY : LOOSE;
	}
}
