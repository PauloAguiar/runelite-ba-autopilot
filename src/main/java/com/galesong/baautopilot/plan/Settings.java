package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.DefenderGuide;
import com.galesong.baautopilot.model.HealCodeSet;
import lombok.Builder;
import lombok.Value;

/** Config values the planner needs, copied so the planner never touches the config proxy. */
@Value
@Builder(toBuilder = true)
public class Settings
{
	@Builder.Default
	AttackMethod attackMethod = AttackMethod.RANGED;
	/** Retreat and call for a medic below this fraction of max HP (non-healers). */
	@Builder.Default
	double retreatBelow = 0.30;
	/** Healer: heal teammates below this fraction. */
	@Builder.Default
	double healTeammateBelow = 0.50;
	/** Collector: useful eggs held in inventory plus bag for a blue-call batch. */
	@Builder.Default
	int collectorLoadAt = 5;
	/** Collector: gather this many yellows before starting a Queen hand-off. */
	@Builder.Default
	int queenEggBatch = 5;
	/** Collector: yellow reserves held until the initial omega batch is fired and the Queen survives. */
	@Builder.Default
	int queenEggBuffer = 3;
	/** Collector: use one omega at a time from two tiles south of the hopper. */
	@Builder.Default
	boolean queenSingleLoads = true;
	/** Healer: optional code order/timing for needed refreshes (OFF: poison priority). */
	@Builder.Default
	HealCodeSet healCodeSet = HealCodeSet.REGULAR;
	/** Defender: whose bait pattern and log counts to follow. */
	@Builder.Default
	DefenderGuide defenderGuide = DefenderGuide.WIKI;
	/** Defender: keep the safety bait south of the trap. */
	@Builder.Default
	boolean safetyBait = true;
	/** Skip your call when another player of your role already made it this period. */
	@Builder.Default
	boolean skipCoveredCalls = true;
	/** Attackers running the plugin split fighters and rangers between them by NPC index. */
	@Builder.Default
	boolean splitTargets = true;
	/** Defender: drop the 1-4-1-4-1 / 1-4-4-1 pattern in the first call. */
	@Builder.Default
	boolean defenderPattern = true;
	/** Advanced Defender strategy: block food to stack runners and time multi-kill releases. */
	@Builder.Default
	boolean defenderMultiKill = false;
	/**
	 * Queen: everyone stacks at the cannon and spams Fire while the collector loads omegas one by
	 * one; off, other roles prepare eggs and wait by the healer spring. Collector loading has its own option.
	 */
	@Builder.Default
	boolean queenStack = false;
}
