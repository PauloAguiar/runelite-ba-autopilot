package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened in one wave, for "how am I doing" feedback: your call reaction times, how quickly
 * your partner called you, role counters, and the team's numbers from the wave-complete screen.
 * Mutable while the wave runs; {@link #copy()} hands out a stable view.
 */
public class WaveStats
{
	public static final double SECONDS_PER_TICK = 0.6;

	int wave;
	Role role;
	int startTick = -1;
	int endTick = -1;
	boolean finished;

	/** Ticks from your call becoming owed to you making it. */
	final List<Integer> callReactions = new ArrayList<>();
	/** Calls you never made before the next call came. */
	int callsMissed;
	/** Horn calls that didn't match what the HUD asked for. */
	int callsWrong;
	/** Ticks from a call change to your partner's new call reaching you. */
	final List<Integer> partnerDelays = new ArrayList<>();
	/** Calls your partner never made. */
	int partnerMissed;

	int eggsPicked;
	int eggsWrong;
	int loads;
	int shots;
	int foods;
	int wrongFoods;
	/** Healer: teammates' hitpoints restored ("You healed N hitpoints."). */
	int hpHealed;
	final List<Integer> healerLifetimes = new ArrayList<>();
	int fighterRangerKills;
	int runnerKills;
	int immuneHits;

	/** From the wave-complete screen, null until it is seen. */
	Integer teamFailedAttacks;
	Integer teamRunnersPassed;
	Integer teamEggs;
	Integer teamHpHealed;
	Integer teamWrongPoison;

	WaveStats(int wave, Role role, int startTick)
	{
		this.wave = wave;
		this.role = role;
		this.startTick = startTick;
	}

	public WaveStats copy()
	{
		WaveStats c = new WaveStats(wave, role, startTick);
		c.endTick = endTick;
		c.finished = finished;
		c.callReactions.addAll(callReactions);
		c.callsMissed = callsMissed;
		c.callsWrong = callsWrong;
		c.partnerDelays.addAll(partnerDelays);
		c.partnerMissed = partnerMissed;
		c.eggsPicked = eggsPicked;
		c.eggsWrong = eggsWrong;
		c.loads = loads;
		c.shots = shots;
		c.foods = foods;
		c.wrongFoods = wrongFoods;
		c.hpHealed = hpHealed;
		c.healerLifetimes.addAll(healerLifetimes);
		c.fighterRangerKills = fighterRangerKills;
		c.runnerKills = runnerKills;
		c.immuneHits = immuneHits;
		c.teamFailedAttacks = teamFailedAttacks;
		c.teamRunnersPassed = teamRunnersPassed;
		c.teamEggs = teamEggs;
		c.teamHpHealed = teamHpHealed;
		c.teamWrongPoison = teamWrongPoison;
		return c;
	}

	public int getWave()
	{
		return wave;
	}

	public Role getRole()
	{
		return role;
	}

	public boolean isFinished()
	{
		return finished;
	}

	/** Wave length so far (or in total once finished), in ticks. */
	public int durationTicks(int now)
	{
		if (startTick < 0)
		{
			return 0;
		}
		return Math.max(0, (endTick >= 0 ? endTick : now) - startTick);
	}

	public List<Integer> getCallReactions()
	{
		return Collections.unmodifiableList(callReactions);
	}

	public int getCallsMissed()
	{
		return callsMissed;
	}

	public int getCallsWrong()
	{
		return callsWrong;
	}

	public List<Integer> getPartnerDelays()
	{
		return Collections.unmodifiableList(partnerDelays);
	}

	public int getPartnerMissed()
	{
		return partnerMissed;
	}

	public int getHpHealed()
	{
		return hpHealed;
	}

	public int getEggsPicked()
	{
		return eggsPicked;
	}

	public int getEggsWrong()
	{
		return eggsWrong;
	}

	public int getLoads()
	{
		return loads;
	}

	public int getShots()
	{
		return shots;
	}

	public int getFoods()
	{
		return foods;
	}

	public int getWrongFoods()
	{
		return wrongFoods;
	}

	public List<Integer> getHealerLifetimes()
	{
		return Collections.unmodifiableList(healerLifetimes);
	}

	public int getFighterRangerKills()
	{
		return fighterRangerKills;
	}

	public int getRunnerKills()
	{
		return runnerKills;
	}

	public int getImmuneHits()
	{
		return immuneHits;
	}

	public Integer getTeamFailedAttacks()
	{
		return teamFailedAttacks;
	}

	public Integer getTeamRunnersPassed()
	{
		return teamRunnersPassed;
	}

	public Integer getTeamEggs()
	{
		return teamEggs;
	}

	public Integer getTeamHpHealed()
	{
		return teamHpHealed;
	}

	public Integer getTeamWrongPoison()
	{
		return teamWrongPoison;
	}

	public boolean hasTeamStats()
	{
		return teamRunnersPassed != null;
	}

	/** Mean of a tick list in seconds, or -1 when empty. */
	public static double meanSeconds(List<Integer> ticks)
	{
		if (ticks.isEmpty())
		{
			return -1;
		}
		return ticks.stream().mapToInt(Integer::intValue).average().orElse(0) * SECONDS_PER_TICK;
	}

	/** Worst of a tick list in seconds, or -1 when empty. */
	public static double worstSeconds(List<Integer> ticks)
	{
		return ticks.stream().mapToInt(Integer::intValue).max().orElse(-1) * (ticks.isEmpty() ? 1 : SECONDS_PER_TICK);
	}

	/** "m:ss". */
	public static String clock(int ticks)
	{
		int s = (int) Math.round(ticks * SECONDS_PER_TICK);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}
