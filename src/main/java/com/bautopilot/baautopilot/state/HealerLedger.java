package com.bautopilot.baautopilot.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-wave record of penance healers: spawn order and the poisoned food that landed on each.
 * Spawn order is NPC-index order within a tick (indexes are handed out in spawn order).
 */
public class HealerLedger
{
	public static final class Entry
	{
		final int npcIndex;
		int order;
		int foods;
		int lastFoodTick = -1;
		int lastPoisonDamage = -1;
		int lastPoisonTick = -1;
		final Map<Integer, Integer> foodsByPeriod = new HashMap<>();

		Entry(int npcIndex)
		{
			this.npcIndex = npcIndex;
		}

		public int getOrder()
		{
			return order;
		}

		public int getFoods()
		{
			return foods;
		}

		public int getLastFoodTick()
		{
			return lastFoodTick;
		}

		public int getLastPoisonDamage()
		{
			return lastPoisonDamage;
		}

		public int getLastPoisonTick()
		{
			return lastPoisonTick;
		}

		public int foodsIn(int period)
		{
			return foodsByPeriod.getOrDefault(period, 0);
		}
	}

	private final Map<Integer, Entry> entries = new HashMap<>();
	private final List<Integer> pendingSpawns = new ArrayList<>();
	private int nextOrder = 1;

	public void reset()
	{
		entries.clear();
		pendingSpawns.clear();
		nextOrder = 1;
	}

	/** The index now belongs to a new NPC. */
	public void forget(int npcIndex)
	{
		entries.remove(npcIndex);
		pendingSpawns.remove(Integer.valueOf(npcIndex));
	}

	public void spawned(int npcIndex)
	{
		if (!entries.containsKey(npcIndex))
		{
			entries.put(npcIndex, new Entry(npcIndex));
			pendingSpawns.add(npcIndex);
		}
	}

	/** Assigns orders to this tick's spawns, lowest NPC index first. */
	public void endTick()
	{
		if (pendingSpawns.isEmpty())
		{
			return;
		}
		pendingSpawns.sort(Integer::compare);
		for (int idx : pendingSpawns)
		{
			entries.get(idx).order = nextOrder++;
		}
		pendingSpawns.clear();
	}

	/** Records one correct food on a healer. */
	public void fed(int npcIndex, int tick, int period)
	{
		Entry e = entries.get(npcIndex);
		if (e == null)
		{
			spawned(npcIndex);
			endTick();
			e = entries.get(npcIndex);
		}
		e.foods++;
		e.lastFoodTick = tick;
		e.foodsByPeriod.merge(period, 1, Integer::sum);
	}

	/** Poison damage is evidence of active poison, not evidence that another food landed. */
	public void poisonHit(int npcIndex, int amount, int tick)
	{
		Entry e = entries.get(npcIndex);
		if (e != null && amount > 0 && amount <= 4)
		{
			e.lastPoisonDamage = amount;
			e.lastPoisonTick = tick;
		}
	}

	public Entry get(int npcIndex)
	{
		return entries.get(npcIndex);
	}
}
