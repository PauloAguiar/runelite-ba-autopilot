package com.galesong.baautopilot.plan;

import lombok.Value;
import lombok.With;

/**
 * One thing a role could do this tick. Planners list every candidate that applies; the
 * {@link Chooser} picks one. A task's done condition is its planner no longer listing it.
 */
@Value
class Candidate
{
	static final int NO_DEADLINE = Integer.MAX_VALUE;

	Tier tier;
	/** Stable id across ticks ("def.tools", "def.relure"), for keeping a committed task. */
	String task;
	Instruction instruction;
	/** Ticks left before it stops being worth doing (or does harm), {@link #NO_DEADLINE} when none. */
	@With
	int deadline;
	/** Once chosen it is kept until done or a higher tier candidate shows up. */
	@With
	boolean sticky;

	static Candidate of(Tier tier, String task, Instruction instruction)
	{
		return new Candidate(tier, task, instruction, NO_DEADLINE, false);
	}

	/** A multi-tick task the chooser keeps until it is done (a trip, the pattern, a relure). */
	static Candidate task(Tier tier, String task, Instruction instruction)
	{
		return new Candidate(tier, task, instruction, NO_DEADLINE, true);
	}
}
