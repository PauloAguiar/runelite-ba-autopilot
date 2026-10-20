package com.galesong.baautopilot.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.state.Snapshot;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class ChooserTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 40, 35);

	private static Candidate at(Tier tier, String task, int dx)
	{
		return Candidate.task(tier, task, Instruction.builder().step(Step.DEF_HOLD).headline(task)
			.target(Target.tile(ME.dx(dx), task)).build());
	}

	private static Snapshot me(String lastTask)
	{
		return Snapshot.builder().playerTile(ME).lastTask(lastTask).build();
	}

	@Test
	public void theDebuggerSeesEveryCandidateAndWhyOneWon()
	{
		Candidate near = at(Tier.MAINTENANCE, "near", 1);
		Candidate far = at(Tier.MAINTENANCE, "far", 20);
		Candidate top = at(Tier.TIME_CRITICAL, "top", 30);
		Instruction ins = Chooser.choose(me(null), Arrays.asList(near, far, top));
		assertEquals(3, ins.getPriorities().size());
		assertEquals("top", ins.getPriorities().get(0).getTask());
		assertEquals(true, ins.getPriorities().get(0).isChosen());
		assertEquals("Only TIME_CRITICAL candidate", ins.getDecidedBy());
		// Same tier: the shorter walk, and the reason says so
		assertEquals("Shortest walk in MAINTENANCE", Chooser.choose(me(null), Arrays.asList(near, far)).getDecidedBy());
		// A committed task is kept, and the reason says so
		assertEquals("Committed task far kept (no higher tier)", Chooser.choose(me("far"), Arrays.asList(near, far)).getDecidedBy());
	}

	@Test
	public void highestTierWins()
	{
		Candidate near = at(Tier.MAINTENANCE, "near", 1);
		Candidate far = at(Tier.TIME_CRITICAL, "far", 20);
		assertEquals("far", Chooser.pick(me(null), Arrays.asList(near, far)).getTask());
		assertNull(Chooser.pick(me(null), Collections.emptyList()));
	}

	@Test
	public void withinATierEarliestDeadlineThenShortestWalk()
	{
		Candidate near = at(Tier.TIME_CRITICAL, "near", 1);
		Candidate far = at(Tier.TIME_CRITICAL, "far", 20);
		assertEquals("near", Chooser.pick(me(null), Arrays.asList(far, near)).getTask());
		assertEquals("far", Chooser.pick(me(null), Arrays.asList(near, far.withDeadline(10))).getTask());
		assertEquals("near", Chooser.pick(me(null), Arrays.asList(far.withDeadline(10), near.withDeadline(5))).getTask());
	}

	@Test
	public void aCommittedTaskIsKeptUntilAHigherTierShowsUp()
	{
		Candidate trip = at(Tier.MAINTENANCE, "trip", 20);
		Candidate nearer = at(Tier.MAINTENANCE, "nearer", 1);
		// Fresh: the nearer one
		assertEquals("nearer", Chooser.pick(me(null), Arrays.asList(trip, nearer)).getTask());
		// Under way: the trip, even with something nearer or due sooner of the same tier
		assertEquals("trip", Chooser.pick(me("trip"), Arrays.asList(trip, nearer.withDeadline(0))).getTask());
		// A strictly higher tier interrupts it
		Candidate urgent = at(Tier.TIME_CRITICAL, "urgent", 30);
		assertEquals("urgent", Chooser.pick(me("trip"), Arrays.asList(trip, nearer, urgent)).getTask());
		// Done (no longer listed): back to ranking
		assertEquals("nearer", Chooser.pick(me("trip"), Collections.singletonList(nearer)).getTask());
		// Only sticky tasks are kept
		Candidate hold = trip.withSticky(false);
		assertEquals("nearer", Chooser.pick(me("trip"), Arrays.asList(hold, nearer)).getTask());
	}

	@Test
	public void theChosenInstructionCarriesItsTaskAndTier()
	{
		Instruction ins = Chooser.choose(me(null), Collections.singletonList(at(Tier.IDLE, "wait", 0)));
		assertEquals("wait", ins.getTask());
		assertEquals(Tier.IDLE, ins.getTier());
	}

	@Test
	public void weightedValueBeatsTierDeadlineAndStickyCommitment()
	{
		Candidate eggs = Candidate.of(Tier.MAINTENANCE, "eggs", Instruction.of(Step.COL_COLLECT, "Eggs", "")
			.toBuilder().score(80.0).scoreTerms(java.util.Map.of("reserve", 80.0)).build());
		Candidate cannon = Candidate.task(Tier.TEAM_BLOCKING, "trip", Instruction.of(Step.COL_LOAD, "Load", "")
			.toBuilder().score(35.0).scoreTerms(java.util.Map.of("support", 35.0)).build()).withDeadline(0);
		Instruction picked = Chooser.choose(me("trip"), Arrays.asList(cannon, eggs));
		assertEquals("eggs", picked.getTask());
		assertEquals(80, picked.getScore(), .001);
		assertEquals("eggs", picked.getPriorities().get(0).getTask());
		assertEquals(80, picked.getPriorities().get(0).getScoreTerms().get("reserve"), .001);
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingScoresCannotSilentlyFallBackToTierPrecedence()
	{
		Candidate scored = Candidate.of(Tier.MAINTENANCE, "eggs", Instruction.of(Step.COL_COLLECT, "Eggs", "")
			.toBuilder().score(80.0).build());
		Chooser.choose(me(null), Arrays.asList(scored, at(Tier.TEAM_BLOCKING, "trip", 1)));
	}

	@Test
	public void walkingCostComesFromTheCollisionMapWhenKnown()
	{
		// A wall in the way: the straight-line nearer one is the longer walk
		Snapshot s = me(null).toBuilder().walkCost(tile -> tile.equals(ME.dx(1)) ? 15 : -1).build();
		Candidate behindWall = at(Tier.MAINTENANCE, "behindWall", 1);
		Candidate open = at(Tier.MAINTENANCE, "open", 5);
		assertEquals("open", Chooser.pick(s, Arrays.asList(behindWall, open)).getTask());
		assertEquals("behindWall", Chooser.pick(me(null), Arrays.asList(behindWall, open)).getTask());
	}
}
