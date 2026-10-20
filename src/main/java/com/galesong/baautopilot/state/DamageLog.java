package com.galesong.baautopilot.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Damage dealt to each penance NPC this wave, from its hitsplats: totals (yours, poison, anyone's)
 * and a short rolling window for damage per second. Client-free.
 */
public class DamageLog
{
	/** Rolling window for damage per second: 10 ticks = 6 s. */
	public static final int WINDOW_TICKS = 10;

	private static final class Hit
	{
		final int tick;
		final int amount;
		final boolean mine;

		Hit(int tick, int amount, boolean mine)
		{
			this.tick = tick;
			this.amount = amount;
			this.mine = mine;
		}
	}

	/** Per NPC: [total, yours, poison]. */
	private final Map<Integer, int[]> totals = new HashMap<>();
	private final Map<Integer, Deque<Hit>> recent = new HashMap<>();

	public void reset()
	{
		totals.clear();
		recent.clear();
	}

	/** The index now belongs to a new NPC. */
	public void forget(int npcIndex)
	{
		totals.remove(npcIndex);
		recent.remove(npcIndex);
	}

	public void hit(int npcIndex, int tick, int amount, boolean mine, boolean poison)
	{
		if (amount <= 0)
		{
			return;
		}
		int[] t = totals.computeIfAbsent(npcIndex, k -> new int[3]);
		t[0] += amount;
		if (mine)
		{
			t[1] += amount;
		}
		if (poison)
		{
			t[2] += amount;
		}
		recent.computeIfAbsent(npcIndex, k -> new ArrayDeque<>()).addLast(new Hit(tick, amount, mine));
	}

	public int total(int npcIndex)
	{
		int[] t = totals.get(npcIndex);
		return t == null ? 0 : t[0];
	}

	public int mine(int npcIndex)
	{
		int[] t = totals.get(npcIndex);
		return t == null ? 0 : t[1];
	}

	public int poison(int npcIndex)
	{
		int[] t = totals.get(npcIndex);
		return t == null ? 0 : t[2];
	}

	/** Damage per second on one NPC over the last {@link #WINDOW_TICKS}. */
	public double dps(int npcIndex, int tick)
	{
		Deque<Hit> hits = recent.get(npcIndex);
		return hits == null ? 0 : perSecond(sum(hits, tick, false));
	}

	/** Your damage per second over the last window, all penance together. */
	public double myDps(int tick)
	{
		int sum = 0;
		for (Deque<Hit> hits : recent.values())
		{
			sum += sum(hits, tick, true);
		}
		return perSecond(sum);
	}

	/** Everyone's damage per second over the last window, all penance together. */
	public double teamDps(int tick)
	{
		int sum = 0;
		for (Deque<Hit> hits : recent.values())
		{
			sum += sum(hits, tick, false);
		}
		return perSecond(sum);
	}

	/** Damage you've dealt this wave, all penance together. */
	public int myTotal()
	{
		return totals.values().stream().mapToInt(t -> t[1]).sum();
	}

	private static int sum(Deque<Hit> hits, int tick, boolean mineOnly)
	{
		while (!hits.isEmpty() && hits.peekFirst().tick <= tick - WINDOW_TICKS)
		{
			hits.removeFirst();
		}
		int sum = 0;
		for (Hit h : hits)
		{
			if (!mineOnly || h.mine)
			{
				sum += h.amount;
			}
		}
		return sum;
	}

	private static double perSecond(int damage)
	{
		return damage / (WINDOW_TICKS * WaveStats.SECONDS_PER_TICK);
	}
}
