package com.bautopilot.baautopilot.plan;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Instruction
{
	Step step;
	String headline;
	@Builder.Default
	String detail = "";
	@Builder.Default
	Target target = Target.NONE;
	/** Safe pickups from the selected ground pile, after capacity and timing limits. */
	@Builder.Default
	int pickupCount = 1;
	/** Planned bad Defender bait stays purple across movement, labels and opening calls. */
	boolean badBait;
	/** The target ground item is under another item on its pile: right-click the pile and choose Take. */
	boolean rightClick;
	/** Inventory items to outline. */
	@Builder.Default
	Set<Integer> items = ImmutableSet.of();
	@Builder.Default
	Urgency urgency = Urgency.NORMAL;
	@Builder.Default
	List<TileMark> marks = ImmutableList.of();
	/** A call to make on the way (horn click), shown under the headline; null when none. */
	com.bautopilot.baautopilot.model.BaCall alsoCall;
	/** The task this step belongs to (kept across ticks until done), null when none. */
	String task;
	/** Why it won: the tier of the candidate it came from, null for steps outside the chooser. */
	Tier tier;
	/** Every candidate the chooser weighed this tick, best first (priorities debugger). */
	@Builder.Default
	List<PriorityLine> priorities = ImmutableList.of();
	/** Why this step is the one shown: the chooser's reason, or the layer that overrode it. */
	String decidedBy;
	/** Weighted roles compare these totals directly; null retains the tier-based chooser. */
	Double score;
	@Builder.Default
	java.util.Map<String, Double> scoreTerms = com.google.common.collect.ImmutableMap.of();
	/**
	 * On a call: the step it leads (the target belongs to it), so the floor marks say what that
	 * target is for ("PICK UP", "FIX") and not "CALL" - you call from wherever you are.
	 */
	Step then;
	/**
	 * A warning shown under the step without replacing it: low HP says retreat when you can, but a
	 * step about to finish (dipping an egg) shouldn't be pulled away (reported 2026-10-17).
	 */
	String advice;
	/** Independent unfinished role obligations, carried into the next snapshot. */
	@Builder.Default
	TaskMemory tasks = TaskMemory.empty();

	public static Instruction of(Step step, String headline, String detail)
	{
		return Instruction.builder().step(step).headline(headline).detail(detail).build();
	}
}
