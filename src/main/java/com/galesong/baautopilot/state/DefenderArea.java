package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;

/** Keep teammates' suggested walks away from an occupied runner holding tile. */
public final class DefenderArea
{
	private DefenderArea()
	{
	}

	public static WorldPoint heldTile(Snapshot s, Iterable<WorldPoint> otherPlayers)
	{
		if (s == null || !s.isInArena() || s.getRole() == null || s.getRole() == Role.DEFENDER
			|| s.getWave() < 1 || s.getWave() > 10
			|| s.getCleared().contains(PenanceType.RUNNER))
		{
			return null;
		}
		WorldPoint food = ArenaTiles.trapFood(s.getWave());
		for (WorldPoint player : otherPlayers)
		{
			if (food.equals(player))
			{
				return food;
			}
		}
		return null;
	}

	/** Copy only affected columns; never change the client's collision map. */
	public static int[][] avoid(int[][] flags, int x, int y, int startX, int startY, boolean approachPlayer)
	{
		// A player interaction needs an adjacent tile. Someone already inside the margin must
		// also be able to leave it; in both cases protect the holding tile without the margin.
		int radius = approachPlayer || Math.max(Math.abs(startX - x), Math.abs(startY - y)) <= 1 ? 0 : 1;
		int[][] out = flags.clone();
		for (int cx = Math.max(0, x - radius); cx <= Math.min(flags.length - 1, x + radius); cx++)
		{
			out[cx] = flags[cx].clone();
			for (int cy = Math.max(0, y - radius); cy <= Math.min(flags[cx].length - 1, y + radius); cy++)
			{
				if (cx != startX || cy != startY)
				{
					out[cx][cy] |= CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				}
			}
		}
		return out;
	}
}
