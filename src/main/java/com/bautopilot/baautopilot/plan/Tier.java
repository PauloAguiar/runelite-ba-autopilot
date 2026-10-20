package com.bautopilot.baautopilot.plan;

/**
 * How much a candidate step matters, highest first. A committed task is only interrupted by a
 * candidate of a strictly higher tier; within a tier the earliest deadline, then the shortest walk,
 * wins.
 */
public enum Tier
{
	/** Staying alive: any death ends the game for the whole team. */
	SAFETY,
	/** Team work: owed calls, escaping runners, broken traps, or low-red cannon supply during active firing. */
	TEAM_BLOCKING,
	/** Has a deadline inside the current call: the first-call pattern, an egg about to vanish, food for the current call. */
	TIME_CRITICAL,
	/** Keeps the next minutes working: tools, restocks, reserves. */
	MAINTENANCE,
	/** Nothing pressing: stand, hold, follow, wait. */
	IDLE
}
