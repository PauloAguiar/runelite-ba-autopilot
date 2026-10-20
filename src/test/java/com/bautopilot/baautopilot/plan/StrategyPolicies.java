package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Experimental decision policies for replay comparisons only; never part of the plugin jar. */
public final class StrategyPolicies
{
	private StrategyPolicies()
	{
	}

	public static BiFunction<Snapshot, Settings, Instruction> proposal(boolean strictCalls, boolean trapFirst, boolean ordinaryKills)
	{
		Planner planner = new Planner();
		DefenderPlanner defender = new DefenderPlanner();
		return (snapshot, settings) ->
		{
			Snapshot s = strictCalls && !snapshot.isListenFresh()
				? snapshot.toBuilder().listenCall(null).build() : snapshot;
			Instruction result = planner.plan(s, settings);
			boolean unclassifiedDrop = strictCalls && !snapshot.isListenFresh() && !result.getItems().isEmpty();
			if (result.getStep() == Step.DEF_RELURE && ((trapFirst && mainSetupAtRisk(s)) || unclassifiedDrop))
			{
				// Reuse real candidates; preserve call and emergency overrides from Planner.
				List<Candidate> options = defender.candidates(Planner.gate(s), settings).stream()
					.filter(c -> !DefenderPlanner.RELURE_TASK.equals(c.getTask())).collect(Collectors.toList());
				Instruction other = Chooser.choose(s, options);
				if (other != null)
				{
					result = other.toBuilder().advice(result.getAdvice()).build();
				}
			}
			if (ordinaryKills && (result.getStep() == Step.DEF_HOLD || result.getStep() == Step.DEF_STAND)
				&& s.getTrapCharges() > 0 && goodKillBait(s) > 0)
			{
				boolean blocking = ArenaTiles.trapFood(s.getWave()).equals(s.getPlayerTile());
				result = result.toBuilder().step(blocking ? Step.DEF_STEP : Step.DEF_HOLD)
					.headline(blocking ? "Step off the food: let the working trap kill" : "Wait beside the working trap")
					.detail("Experimental ordinary-kill policy: no minimum stack size or release timer")
					.target(Target.tile(ArenaTiles.trapStep(s.getWave()), "Beside trap"))
					.task("review.def.ordinary").build();
			}
			return result;
		};
	}

	public static boolean mainSetupAtRisk(Snapshot s)
	{
		return (s.getWaveTick() >= 0 && s.getWaveTick() < WaveInfo.TICKS_PER_CALL)
			|| s.getTrapCharges() <= 0 || goodKillBait(s) == 0;
	}

	public static int goodKillBait(Snapshot s)
	{
		return s.getGroundItems().stream()
			.filter(g -> BaIds.BAIT.contains(g.getItemId()) && g.isGood()
				&& ArenaTiles.trapFood(s.getWave()).equals(g.getTile()))
			.mapToInt(g -> g.getQuantity()).sum();
	}
}
