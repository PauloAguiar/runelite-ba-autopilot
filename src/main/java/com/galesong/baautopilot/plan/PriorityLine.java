package com.galesong.baautopilot.plan;

import lombok.Value;

/** One candidate the chooser weighed this tick, for the priorities debugger. */
@Value
public class PriorityLine
{
	Tier tier;
	/** The task id ("def.tools"), or null. */
	String task;
	String headline;
	/** Ticks left, or {@link Candidate#NO_DEADLINE}. */
	int deadline;
	/** Tiles to walk to its target (0 for inventory clicks). */
	int walk;
	boolean sticky;
	boolean chosen;
	Double score;
	java.util.Map<String, Double> scoreTerms;
}
