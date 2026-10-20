package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Collects {@link WaveStats} from the same events {@link RoundLogic} sees. Client-free, so the
 * replay harness can recompute a recorded round's numbers.
 */
public class Performance
{
	static final int HISTORY = 40;

	private static final String PUT_IN_BAG = "You put the egg in the bag.";
	/** Healer: "You healed 10 hitpoints." (exact; the BA Minigame plugin counts the same line). */
	private static final java.util.regex.Pattern HEALED = java.util.regex.Pattern.compile("^You healed (\\d+) hitpoints?\\.");
	private static final String EXPLODED = "The egg exploded.";
	private static final String LOADED = "You load the hopper.";
	private static final String IMMUNE = "These Penance are immune to your attacks.";
	private static final String WRONG_FOOD = "wrong type of poisoned food";

	private final List<WaveStats> history = new ArrayList<>();
	private WaveStats current;

	private int period = -1;
	private boolean heardThisPeriod;
	private boolean owedThisPeriod;
	private boolean calledThisPeriod;
	private int owedSince = -1;

	public void reset()
	{
		current = null;
		period = -1;
		owedSince = -1;
	}

	/** Called every arena tick once role and wave are known. */
	public void tick(int tick, int wave, Role role, int callPeriod, int periodStart, boolean heardFresh,
		boolean callOwed, boolean callDone)
	{
		tick(tick, wave, role, callPeriod, periodStart, heardFresh, callOwed, callDone, false);
	}

	/** @param covered a teammate of your role made your call: neither timed nor counted as missed */
	public void tick(int tick, int wave, Role role, int callPeriod, int periodStart, boolean heardFresh,
		boolean callOwed, boolean callDone, boolean covered)
	{
		if (wave <= 0 || role == null)
		{
			return;
		}
		if (current == null || current.wave != wave || current.finished)
		{
			current = new WaveStats(wave, role, periodStart >= 0 ? periodStart : tick);
			period = -1;
		}
		if (callPeriod != period)
		{
			closePeriod();
			period = callPeriod;
			heardThisPeriod = false;
			owedThisPeriod = false;
			calledThisPeriod = false;
			owedSince = -1;
		}
		if (heardFresh && !heardThisPeriod && periodStart >= 0)
		{
			heardThisPeriod = true;
			current.partnerDelays.add(Math.max(0, tick - periodStart));
		}
		if (covered && !callDone)
		{
			owedSince = -1;
			owedThisPeriod = false;
			return;
		}
		if (callOwed && owedSince < 0 && !calledThisPeriod)
		{
			owedSince = tick;
			owedThisPeriod = true;
		}
		if (callDone && owedSince >= 0)
		{
			current.callReactions.add(tick - owedSince);
			owedSince = -1;
			calledThisPeriod = true;
		}
	}

	private void closePeriod()
	{
		if (current == null || period < 0)
		{
			return;
		}
		if (!heardThisPeriod)
		{
			current.partnerMissed++;
		}
		if (owedThisPeriod && !calledThisPeriod)
		{
			current.callsMissed++;
		}
	}

	/** The wave ended (left the arena or the next wave began). */
	public void finish(int tick)
	{
		if (current == null || current.finished)
		{
			return;
		}
		// The last call is cut short by the wave end, so it can't count as missed
		current.endTick = tick;
		current.finished = true;
		history.add(current);
		if (history.size() > HISTORY)
		{
			history.remove(0);
		}
	}

	public void chat(String msg)
	{
		if (current == null || current.finished)
		{
			return;
		}
		java.util.regex.Matcher healed = HEALED.matcher(msg);
		if (healed.find())
		{
			current.hpHealed += Integer.parseInt(healed.group(1));
		}
		if (PUT_IN_BAG.equals(msg))
		{
			current.eggsPicked++;
		}
		else if (EXPLODED.equals(msg))
		{
			current.eggsWrong++;
		}
		else if (LOADED.equals(msg))
		{
			current.loads++;
		}
		else if (IMMUNE.equals(msg))
		{
			current.immuneHits++;
		}
		else if (msg.toLowerCase().contains(WRONG_FOOD))
		{
			current.wrongFoods++;
		}
	}

	public void menu(String option, String target)
	{
		if (current != null && !current.finished && "Shoot".equals(option) && target != null
			&& target.toLowerCase().contains("launcher"))
		{
			current.shots++;
		}
	}

	/** Eggs that appeared in the inventory since last tick (picked up past the bag). */
	public void inventory(Map<Integer, Integer> before, Map<Integer, Integer> now)
	{
		if (current == null || current.finished)
		{
			return;
		}
		for (int id : new int[]{BaIds.EGG_RED, BaIds.EGG_GREEN, BaIds.EGG_BLUE})
		{
			int gained = now.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (gained > 0)
			{
				current.eggsPicked += gained;
			}
		}
	}

	public void wrongCall()
	{
		if (current != null && !current.finished)
		{
			current.callsWrong++;
		}
	}

	public void ownFeed()
	{
		if (current != null && !current.finished)
		{
			current.foods++;
		}
	}

	/** A death counted for an NPC that was only out of view. */
	public void undoDeath(PenanceType type)
	{
		if (current == null || current.finished || type == null)
		{
			return;
		}
		if (type == PenanceType.FIGHTER || type == PenanceType.RANGER)
		{
			current.fighterRangerKills = Math.max(0, current.fighterRangerKills - 1);
		}
		else if (type == PenanceType.RUNNER)
		{
			current.runnerKills = Math.max(0, current.runnerKills - 1);
		}
	}

	public void death(PenanceType type, int lifetimeTicks)
	{
		if (current == null || current.finished || type == null)
		{
			return;
		}
		switch (type)
		{
			case FIGHTER:
			case RANGER:
				current.fighterRangerKills++;
				break;
			case RUNNER:
				current.runnerKills++;
				break;
			case HEALER:
				if (lifetimeTicks >= 0)
				{
					current.healerLifetimes.add(lifetimeTicks);
				}
				break;
			default:
				break;
		}
	}

	/** The wave-complete screen's five team numbers, for the wave that just ended. */
	public void teamStats(int failedAttacks, int runnersPassed, int eggs, int hpHealed, int wrongPoison)
	{
		WaveStats last = history.isEmpty() ? null : history.get(history.size() - 1);
		if (last == null || last.hasTeamStats())
		{
			return;
		}
		last.teamFailedAttacks = failedAttacks;
		last.teamRunnersPassed = runnersPassed;
		last.teamEggs = eggs;
		last.teamHpHealed = hpHealed;
		last.teamWrongPoison = wrongPoison;
	}

	/** This wave so far, or null outside a wave. */
	public WaveStats current()
	{
		return current == null || current.finished ? null : current.copy();
	}

	/** Finished waves, oldest first. */
	public List<WaveStats> history()
	{
		List<WaveStats> out = new ArrayList<>(history.size());
		history.forEach(w -> out.add(w.copy()));
		return Collections.unmodifiableList(out);
	}
}
