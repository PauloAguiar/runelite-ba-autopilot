package com.galesong.baautopilot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.state.ArenaPath;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

public class ArenaPathTest
{
	private static int[][] open(int size)
	{
		return new int[size][size];
	}

	@Test
	public void straightAndDiagonalOnOpenGround()
	{
		List<int[]> path = ArenaPath.find(open(10), 1, 1, (x, y) -> x == 4 && y == 4, 50);
		assertEquals(4, path.size());
		assertArrayEquals(new int[]{1, 1}, path.get(0));
		assertArrayEquals(new int[]{4, 4}, path.get(3));
	}

	@Test
	public void goesAroundABlockedColumn()
	{
		int[][] flags = open(10);
		for (int y = 0; y < 8; y++)
		{
			flags[5][y] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		}
		List<int[]> path = ArenaPath.find(flags, 2, 2, (x, y) -> x == 8 && y == 2, 60);
		assertTrue(path.size() > 7);
		for (int[] p : path)
		{
			assertTrue("walked through the wall at " + p[0] + "," + p[1], p[0] != 5 || p[1] >= 8);
		}
	}

	@Test
	public void respectsAWallOnATileEdge()
	{
		int[][] flags = open(6);
		// A wall on the east side of (2,y) for every y: you can't step from x=2 to x=3 anywhere
		for (int y = 0; y < 6; y++)
		{
			flags[2][y] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
			flags[3][y] |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		assertTrue(ArenaPath.find(flags, 1, 1, (x, y) -> x == 4 && y == 1, 40).isEmpty());
	}

	@Test
	public void noCornerCutting()
	{
		int[][] flags = open(5);
		flags[2][1] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		flags[1][2] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		// (1,1) -> (2,2) diagonally would squeeze between two blocked tiles
		List<int[]> path = ArenaPath.find(flags, 1, 1, (x, y) -> x == 2 && y == 2, 20);
		assertTrue(path.isEmpty() || path.size() > 2);
	}

	@Test
	public void drawsDirectSegmentsInEveryDirectionOnOpenGround()
	{
		int[][] flags = open(15);
		for (int x = 1; x < 14; x++)
		{
			for (int y = 1; y < 14; y++)
			{
				if (x == 7 && y == 7)
				{
					continue;
				}
				int tx = x;
				int ty = y;
				List<int[]> route = ArenaPath.find(flags, 7, 7, (px, py) -> px == tx && py == ty, 50);
				List<int[]> drawn = ArenaPath.waypoints(flags, route);
				assertEquals("unnecessary bend toward " + x + "," + y, 2, drawn.size());
				assertArrayEquals(new int[]{7, 7}, drawn.get(0));
				assertArrayEquals(new int[]{x, y}, drawn.get(1));
			}
		}
	}

	@Test
	public void drawnSegmentsStillGoAroundObstacles()
	{
		int[][] flags = open(10);
		for (int y = 0; y < 8; y++)
		{
			flags[5][y] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		}
		List<int[]> drawn = ArenaPath.waypoints(flags,
			ArenaPath.find(flags, 2, 2, (x, y) -> x == 8 && y == 2, 60));
		assertTrue("drew straight through the blocked column", drawn.size() > 2);
		assertArrayEquals(new int[]{2, 2}, drawn.get(0));
		assertArrayEquals(new int[]{8, 2}, drawn.get(drawn.size() - 1));
		for (int i = 1; i < drawn.size(); i++)
		{
			int[] a = drawn.get(i - 1);
			int[] b = drawn.get(i);
			if (Math.min(a[0], b[0]) <= 5 && Math.max(a[0], b[0]) >= 5 && a[0] != b[0])
			{
				double crossingY = a[1] + (5.0 - a[0]) * (b[1] - a[1]) / (b[0] - a[0]);
				assertTrue("segment crosses the blocked column", crossingY >= 7.5);
			}
		}
	}

	@Test
	public void drawnSegmentsRespectTileEdgeWalls()
	{
		int[][] flags = open(7);
		for (int y = 1; y <= 3; y++)
		{
			flags[2][y] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
			flags[3][y] |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		List<int[]> drawn = ArenaPath.waypoints(flags,
			ArenaPath.find(flags, 1, 2, (x, y) -> x == 4 && y == 2, 40));
		assertTrue("smoothed through an edge wall", drawn.size() > 2);
	}

	@Test
	public void drawnSegmentsDoNotCutCornersOrSkipIntersectedTiles()
	{
		int[][] flags = open(7);
		flags[2][1] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		flags[1][2] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		List<int[]> corner = ArenaPath.waypoints(flags,
			ArenaPath.find(flags, 1, 1, (x, y) -> x == 2 && y == 2, 40));
		assertTrue("drew diagonally between blocked tiles", corner.size() > 2);

		flags[1][2] = 0;
		List<int[]> shallow = ArenaPath.waypoints(flags,
			ArenaPath.find(flags, 1, 1, (x, y) -> x == 5 && y == 3, 40));
		assertTrue("shallow line crossed a blocked tile between sampled centres", shallow.size() > 2);
	}
}
