package com.galesong.baautopilot.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * Shortest walking route on a scene collision grid ({@code flags[x][y]}, as from
 * {@code CollisionData.getFlags()}): 8-directional breadth-first search that respects walls on tile
 * edges and only cuts a corner when both sides of it are open. Client-free, so it is unit tested
 * on hand-made grids.
 */
public final class ArenaPath
{
	/** Tiles you can't stand on at all. */
	private static final int SOLID = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	/** A goal test on scene coordinates. */
	public interface Goal
	{
		boolean reached(int x, int y);
	}

	private ArenaPath()
	{
	}

	/**
	 * Route from (sx, sy) to the nearest tile passing {@code goal}, start and goal included; empty
	 * when there is none within {@code maxSteps}.
	 */
	public static List<int[]> find(int[][] flags, int sx, int sy, Goal goal, int maxSteps)
	{
		int w = flags.length;
		int h = w == 0 ? 0 : flags[0].length;
		if (sx < 0 || sy < 0 || sx >= w || sy >= h)
		{
			return Collections.emptyList();
		}
		int[] prev = new int[w * h];
		int[] dist = new int[w * h];
		Arrays.fill(dist, -1);
		int[] queue = new int[w * h];
		int head = 0;
		int tail = 0;
		int start = sx * h + sy;
		dist[start] = 0;
		prev[start] = start;
		queue[tail++] = start;
		while (head < tail)
		{
			int cur = queue[head++];
			int cx = cur / h;
			int cy = cur % h;
			if (goal.reached(cx, cy))
			{
				return straightTrace(flags, dist, cur, start, h);
			}
			if (dist[cur] >= maxSteps)
			{
				continue;
			}
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if ((dx != 0 || dy != 0) && canMove(flags, cx, cy, dx, dy))
					{
						int next = (cx + dx) * h + (cy + dy);
						if (dist[next] < 0)
						{
							dist[next] = dist[cur] + 1;
							prev[next] = cur;
							queue[tail++] = next;
						}
					}
				}
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Waypoints for drawing a route: join the furthest visible tiles directly instead of showing
	 * every arbitrary grid step. Check every crossed tile edge, including both sides of a corner,
	 * so a straight segment never hides a wall or cuts through an obstacle.
	 */
	public static List<int[]> waypoints(int[][] flags, List<int[]> route)
	{
		if (route.size() <= 2)
		{
			return route;
		}
		List<int[]> out = new ArrayList<>();
		int at = 0;
		out.add(route.get(at));
		while (at < route.size() - 1)
		{
			int next = route.size() - 1;
			while (next > at + 1 && !clearLine(flags, route.get(at), route.get(next)))
			{
				next--;
			}
			out.add(route.get(next));
			at = next;
		}
		return out;
	}

	/** Traverse the cells intersected by a line between tile centres, in edge-crossing order. */
	private static boolean clearLine(int[][] flags, int[] from, int[] to)
	{
		int x = from[0];
		int y = from[1];
		int dx = Integer.signum(to[0] - x);
		int dy = Integer.signum(to[1] - y);
		int width = Math.abs(to[0] - x);
		int height = Math.abs(to[1] - y);
		int crossedX = 0;
		int crossedY = 0;
		while (x != to[0] || y != to[1])
		{
			// Compare the next vertical/horizontal boundary times without rounding to a tile.
			long vertical = (2L * crossedX + 1) * height;
			long horizontal = (2L * crossedY + 1) * width;
			int stepX = vertical <= horizontal ? dx : 0;
			int stepY = horizontal <= vertical ? dy : 0;
			if (!canMove(flags, x, y, stepX, stepY))
			{
				return false;
			}
			x += stepX;
			y += stepY;
			crossedX += stepX == 0 ? 0 : 1;
			crossedY += stepY == 0 ? 0 : 1;
		}
		return true;
	}

	/**
	 * Steps from (sx, sy) to every tile within {@code maxSteps} ({@code [x][y]}, -1 where unreached):
	 * one search per tick gives the planner a walking cost for every candidate.
	 */
	public static int[][] distances(int[][] flags, int sx, int sy, int maxSteps)
	{
		int w = flags.length;
		int h = w == 0 ? 0 : flags[0].length;
		int[][] dist = new int[w][h];
		for (int[] column : dist)
		{
			Arrays.fill(column, -1);
		}
		if (sx < 0 || sy < 0 || sx >= w || sy >= h)
		{
			return dist;
		}
		int[] queue = new int[w * h];
		int head = 0;
		int tail = 0;
		dist[sx][sy] = 0;
		queue[tail++] = sx * h + sy;
		while (head < tail)
		{
			int cur = queue[head++];
			int cx = cur / h;
			int cy = cur % h;
			if (dist[cx][cy] >= maxSteps)
			{
				continue;
			}
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if ((dx != 0 || dy != 0) && canMove(flags, cx, cy, dx, dy) && dist[cx + dx][cy + dy] < 0)
					{
						dist[cx + dx][cy + dy] = dist[cx][cy] + 1;
						queue[tail++] = (cx + dx) * h + cy + dy;
					}
				}
			}
		}
		return dist;
	}

	/** Steps to stand on (x, y), or next to it when it is solid (an object); -1 when unreached. */
	public static int stepsTo(int[][] dist, int x, int y)
	{
		if (x < 0 || y < 0 || x >= dist.length || y >= dist[0].length)
		{
			return -1;
		}
		if (dist[x][y] >= 0)
		{
			return dist[x][y];
		}
		int best = -1;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				int nx = x + dx;
				int ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < dist.length && ny < dist[0].length && dist[nx][ny] >= 0
					&& (best < 0 || dist[nx][ny] < best))
				{
					best = dist[nx][ny];
				}
			}
		}
		return best;
	}

	/**
	 * Among the equally short routes, the one with the fewest turns: walked back from the goal,
	 * keep the last direction while it still leads home, else take a diagonal before a straight
	 * step. From the start that reads as one straight run, then the diagonal into the target
	 * (reported 2026-10-17: the search's arbitrary tie-break drew zigzags across the arena).
	 */
	private static List<int[]> straightTrace(int[][] flags, int[] dist, int end, int start, int h)
	{
		List<int[]> out = new ArrayList<>();
		int at = end;
		int ldx = 0;
		int ldy = 0;
		out.add(new int[]{at / h, at % h});
		while (at != start)
		{
			int cx = at / h;
			int cy = at % h;
			int best = -1;
			int bdx = 0;
			int bdy = 0;
			int bestScore = Integer.MAX_VALUE;
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int px = cx - dx;
					int py = cy - dy;
					if ((dx == 0 && dy == 0) || px < 0 || py < 0 || px >= flags.length || py >= h)
					{
						continue;
					}
					int p = px * h + py;
					if (dist[p] != dist[at] - 1 || !canMove(flags, px, py, dx, dy))
					{
						continue;
					}
					int score = (dx == ldx && dy == ldy) ? 0 : (dx != 0 && dy != 0) ? 1 : 2;
					if (score < bestScore)
					{
						bestScore = score;
						best = p;
						bdx = dx;
						bdy = dy;
					}
				}
			}
			if (best < 0)
			{
				break;
			}
			at = best;
			ldx = bdx;
			ldy = bdy;
			out.add(new int[]{at / h, at % h});
		}
		Collections.reverse(out);
		return out;
	}

	private static List<int[]> trace(int[] prev, int end, int start, int h)
	{
		List<int[]> out = new ArrayList<>();
		for (int at = end; ; at = prev[at])
		{
			out.add(new int[]{at / h, at % h});
			if (at == start)
			{
				break;
			}
		}
		Collections.reverse(out);
		return out;
	}

	/** One step from (x, y) by (dx, dy), each -1..1. */
	static boolean canMove(int[][] flags, int x, int y, int dx, int dy)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (nx < 0 || ny < 0 || nx >= flags.length || ny >= flags[0].length || (flags[nx][ny] & SOLID) != 0)
		{
			return false;
		}
		if (dx != 0 && dy != 0)
		{
			// A diagonal needs both cardinal steps around the corner, from either side
			return canMove(flags, x, y, dx, 0) && canMove(flags, x, y, 0, dy)
				&& canMove(flags, x + dx, y, 0, dy) && canMove(flags, x, y + dy, dx, 0);
		}
		if (dx == 1)
		{
			return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
				&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
		}
		if (dx == -1)
		{
			return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
				&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
		}
		if (dy == 1)
		{
			return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
				&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
		}
		return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
			&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
	}
}
