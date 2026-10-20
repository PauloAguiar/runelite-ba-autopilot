package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenderAreaTest
{
	@Test
	public void occupiedHoldingTilesAreProtectedInBothArenas()
	{
		for (int wave : new int[]{3, 10})
		{
			WorldPoint food = ArenaTiles.trapFood(wave);
			Snapshot s = Snapshot.builder().inArena(true).role(Role.HEALER).wave(wave).build();
			assertEquals(food, DefenderArea.heldTile(s, List.of(food)));
			assertNull(DefenderArea.heldTile(s, List.of(food.dx(1))));
			assertNull(DefenderArea.heldTile(s.toBuilder().role(Role.DEFENDER).build(), List.of(food)));
			assertNull(DefenderArea.heldTile(s.toBuilder().inArena(false).build(), List.of(food)));
			assertNull(DefenderArea.heldTile(s.toBuilder().cleared(Collections.singleton(PenanceType.RUNNER)).build(), List.of(food)));
		}
	}

	@Test
	public void routesAndSmoothedLinesAvoidTheHoldingAreaWithoutChangingTheMap()
	{
		int[][] original = new int[11][11];
		int[][] protectedMap = DefenderArea.avoid(original, 5, 5, 1, 5, false);
		List<int[]> path = ArenaPath.find(protectedMap, 1, 5, (x, y) -> x == 9 && y == 5, 30);
		assertFalse(path.isEmpty());
		for (int[] tile : path)
		{
			assertTrue(Math.abs(tile[0] - 5) > 1 || Math.abs(tile[1] - 5) > 1);
		}
		assertTrue(ArenaPath.waypoints(protectedMap, path).size() > 2);
		for (int[] column : original)
		{
			for (int flag : column)
			{
				assertEquals(0, flag);
			}
		}
	}

	@Test
	public void healingCanApproachTheHolderWithoutSteppingOnItsTile()
	{
		int[][] map = DefenderArea.avoid(new int[11][11], 5, 5, 1, 5, true);
		List<int[]> path = ArenaPath.find(map, 1, 5,
			(x, y) -> Math.max(Math.abs(x - 5), Math.abs(y - 5)) == 1, 30);
		assertFalse(path.isEmpty());
		for (int[] tile : path)
		{
			assertFalse(tile[0] == 5 && tile[1] == 5);
		}
		assertTrue(ArenaPath.find(map, 1, 5, (x, y) -> x == 5 && y == 5, 30).isEmpty());
	}

	@Test
	public void someoneInsideTheMarginCanLeave()
	{
		for (int startX : new int[]{4, 5})
		{
			int[][] map = DefenderArea.avoid(new int[11][11], 5, 5, startX, 5, false);
			assertFalse(ArenaPath.find(map, startX, 5, (x, y) -> x == 9 && y == 5, 30).isEmpty());
		}
	}
}
