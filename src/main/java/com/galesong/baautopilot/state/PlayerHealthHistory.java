package com.galesong.baautopilot.state;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recent observed net HP loss. Healing never counts as damage; missing observations are unknown. */
public final class PlayerHealthHistory
{
	public static final int WINDOW_TICKS = 20;
	private final History self = new History();
	private final Map<String, History> team = new LinkedHashMap<>();

	public void reset() { self.clear(); team.clear(); }
	public void observeSelf(int tick, int hp, int max) { self.observe(tick, hp, max); }
	public void observeTeam(int tick, List<TeammateView> players)
	{
		for (TeammateView p : players)
			team.computeIfAbsent(p.getName(), name -> new History()).observe(tick, p.getHp(), p.getMaxHp());
	}
	public double selfRate(int tick) { return self.rate(tick); }
	public Map<String, Double> teamRates(int tick)
	{
		Map<String, Double> rates = new LinkedHashMap<>();
		team.forEach((name, history) -> rates.put(name, history.rate(tick)));
		return ImmutableMap.copyOf(rates);
	}

	private static final class History
	{
		private final Deque<int[]> intervals = new ArrayDeque<>(); // tick, observed loss
		private int lastTick = -1, lastHp, lastMax;
		void clear() { intervals.clear(); lastTick = -1; }
		void observe(int tick, int hp, int max)
		{
			if (max <= 0 || hp <= 0) { lastTick = -1; return; }
			if (tick <= lastTick) return;
			if (lastTick == tick - 1 && max == lastMax)
				intervals.addLast(new int[]{tick, Math.max(0, lastHp - hp)});
			lastTick = tick;
			lastHp = hp;
			lastMax = max;
			expire(tick);
		}
		private void expire(int tick)
		{
			while (!intervals.isEmpty() && intervals.peekFirst()[0] <= tick - WINDOW_TICKS) intervals.removeFirst();
		}
		double rate(int tick)
		{
			expire(tick);
			return intervals.stream().mapToInt(interval -> interval[1]).average().orElse(0);
		}
	}
}
