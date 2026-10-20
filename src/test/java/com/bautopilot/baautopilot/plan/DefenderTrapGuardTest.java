package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenderTrapGuardTest
{
	private static final WorldPoint FOOD = ArenaTiles.trapFood(5);
	private final Planner planner = new Planner();
	private static PenanceView runner(int id, WorldPoint tile)
	{
		return PenanceView.builder().npcIndex(id).type(PenanceType.RUNNER).tile(tile).build();
	}
	private static Snapshot base()
	{
		return Snapshot.builder().inArena(true).tick(1000).role(Role.DEFENDER).wave(5).waveTick(100)
			.callPeriod(2).ticksToCallChange(45).playerTile(FOOD).hp(77).maxHp(77).freeSlots(8)
			.shoutDone(true).listenCall(BaCall.BAIT_WORMS).listenFresh(true).trapCharges(2)
			.hasHammer(true).logsTaken(3).inventory(Map.of(BaIds.LOGS, 3, BaCall.BAIT_WORMS.getItemId(), 8,
				BaCall.BAIT_CRACKERS.getItemId(), 8, BaCall.BAIT_TOFU.getItemId(), 8))
			.objects(List.of(new ObjectView(BaIds.TRAP_FULL, FOOD.dy(-1)), new ObjectView(BaIds.DEFENDER_MACHINE, FOOD.dy(-15))))
			.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), FOOD, 6, true, 990),
				new GroundItemView(BaIds.HAMMER, ArenaTiles.mainstack(5), 1, false, 990),
				new GroundItemView(BaIds.LOGS, ArenaTiles.logSpawn(5), 1, false, 990)))
			.penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)))).build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().defenderMultiKill(true).build()); }
	private static Snapshot next(Snapshot s, Instruction previous)
	{
		return s.toBuilder().tick(s.getTick() + 1).waveTick(s.getWaveTick() + 1)
			.ticksToCallChange(s.getTicksToCallChange() <= 1 ? 50 : s.getTicksToCallChange() - 1)
			.lastTask(previous.getTask()).lastStep(previous.getStep()).lastTargetTile(previous.getTarget().getTile())
			.tasks(previous.getTasks()).build();
	}

	@Test public void lateAndUnseenRunnersCannotBlockAReadyBatchForever()
	{
		for (boolean unseen : List.of(false, true))
		{
			PenanceView late = runner(3, FOOD.dy(12)).toBuilder().unseen(unseen).build();
			Snapshot s = unseen ? base().toBuilder().unseenPenance(List.of(late)).build()
				: base().toBuilder().penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), late)).build();
			for (int tick = 0; tick <= DefenderPlanner.MAX_STACK_WAIT; tick++)
			{
				Instruction instruction = plan(s);
				assertEquals("tick " + tick + " unseen " + unseen,
					tick < DefenderPlanner.MAX_STACK_WAIT ? Step.DEF_HOLD : Step.DEF_STEP, instruction.getStep());
				assertEquals(0, s.getRunnersKilled());
				s = next(s, instruction);
			}
			// The release prompt survives the one-tick timer mark until the player moves.
			Instruction release = plan(s);
			assertEquals(Step.DEF_STEP, release.getStep());
			assertEquals(ArenaTiles.trapStep(5), release.getTarget().getTile());
			Snapshot beside = next(s, release).toBuilder().playerTile(ArenaTiles.trapStep(5)).build();
			Instruction waitForEating = plan(beside);
			assertEquals("Stay off the food while the runners eat", waitForEating.getHeadline());
			assertEquals(ArenaTiles.trapStep(5), waitForEating.getTarget().getTile());
			assertEquals(Step.DEF_HOLD, plan(next(beside, waitForEating).toBuilder().trapCharges(1).build()).getStep());
			assertEquals(Step.DEF_REPAIR_POSITION, plan(next(beside, waitForEating).toBuilder().trapCharges(0).build()).getStep());
		}
	}

	@Test public void aSingletonWithUnspawnedReservesEventuallyGetsToEat()
	{
		Snapshot s = base().toBuilder().trapCharges(1).penance(List.of(runner(1, FOOD.dx(1))))
			.inventory(Map.of(BaIds.LOGS, 2, BaCall.BAIT_WORMS.getItemId(), 8,
				BaCall.BAIT_TOFU.getItemId(), 8, BaCall.BAIT_CRACKERS.getItemId(), 8)).build();
		for (int tick = 0; tick < DefenderPlanner.MAX_STACK_WAIT; tick++)
		{
			Instruction hold = plan(s);
			assertEquals(Step.DEF_HOLD, hold.getStep());
			s = next(s, hold);
		}
		Instruction release = plan(s);
		assertEquals(Step.DEF_STEP, release.getStep());
		assertEquals("def.last", release.getTask());
		assertTrue(release.getHeadline().contains("waiting runner"));
		assertNotEquals(Step.DEF_STEP, plan(s.toBuilder().trapCharges(-1).build()).getStep());
	}

	@Test public void aNewArrivalGetsItsOwnGatheringWindow()
	{
		Snapshot s = base().toBuilder().unseenPenance(List.of(runner(9, FOOD.dy(15)).toBuilder().unseen(true).build())).build();
		for (int tick = 0; tick < 8; tick++) s = next(s, plan(s));
		s = s.toBuilder().penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), runner(3, FOOD.dx(-1)))).build();
		for (int tick = 0; tick < DefenderPlanner.MAX_STACK_WAIT; tick++)
		{
			Instruction hold = plan(s);
			assertEquals(Step.DEF_HOLD, hold.getStep());
			s = next(s, hold);
		}
	}

	@Test public void staleUnseenPositionsDoNotBlockOrJoinTheVisibleStack()
	{
		PenanceView unseen = runner(3, FOOD.dy(1)).toBuilder().unseen(true).unseenTicks(30).build();
		Snapshot s = base().toBuilder().unseenPenance(List.of(unseen)).build();
		assertEquals(Step.DEF_STEP, plan(s).getStep());
		assertEquals(3, s.alive(PenanceType.RUNNER).size());
		assertEquals(0, s.getRunnersKilled());
		assertNotEquals("def.step", plan(s.toBuilder().penance(List.of(runner(1, FOOD.dx(1)))).build()).getTask());
	}

	@Test public void allRemainingRunnersGatheredReleaseAtTheTimingMark()
	{
		Snapshot s = base().toBuilder().runnersKilled(WaveInfo.runnersTotal(5) - 2).build();
		Instruction release = plan(s);
		assertEquals(Step.DEF_STEP, release.getStep());
		assertEquals("def.step", release.getTask());
		assertEquals(ArenaTiles.trapStep(5), release.getTarget().getTile());
		assertNotEquals(Step.DEF_STEP, plan(s.toBuilder().trapCharges(1).build()).getStep());
		assertNotEquals(Step.DEF_STEP, plan(s.toBuilder().trapCharges(-1).build()).getStep());
	}

	@Test public void aRunnerMovingAwayDoesNotKeepTheGatheredGroupWaiting()
	{
		PenanceView escaping = runner(3, FOOD.dy(-4)).toBuilder().previousTile(FOOD.dy(-3)).observedTick(1000).build();
		Snapshot s = base().toBuilder().penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), escaping)).build();
		assertEquals(Step.DEF_STEP, plan(s).getStep());
		assertEquals(3, s.alive(PenanceType.RUNNER).size());
		assertEquals(0, s.getRunnersKilled());
		assertEquals(0, s.getUncertainRunners());
	}

	@Test public void aRunnerAlreadyFarSouthIsNotMistakenForAnIncomingRunnerOrADeath()
	{
		Snapshot s = base().toBuilder().penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), runner(3, FOOD.dy(-12)))).build();
		Instruction release = plan(s);
		assertEquals(Step.DEF_STEP, release.getStep());
		assertEquals(ArenaTiles.trapStep(5), release.getTarget().getTile());
		assertEquals(3, s.alive(PenanceType.RUNNER).size());
		assertEquals(0, s.getRunnersKilled());
		assertFalse(s.getCleared().contains(PenanceType.RUNNER));
	}

	@Test public void anEscapingRunnerOnItsOwnGetsRelureGuidanceInsteadOfAStackRelease()
	{
		Snapshot s = base().toBuilder().runnersKilled(WaveInfo.runnersTotal(5) - 1)
			.penance(List.of(runner(3, FOOD.dy(-6)))).build();
		Instruction recover = plan(s);
		assertEquals(Step.DEF_RELURE, recover.getStep());
		assertEquals(1, s.alive(PenanceType.RUNNER).size());
		assertEquals(WaveInfo.runnersTotal(5) - 1, s.getRunnersKilled());
	}

	@Test public void groupReleaseWaitsForApproachingAndUnseenRunners()
	{
		for (PenanceView pending : List.of(runner(3, FOOD.dy(7)), runner(3, FOOD.dy(2)),
			runner(3, FOOD.dy(7)).toBuilder().unseen(true).build()))
		{
			Snapshot s = pending.isUnseen() ? base().toBuilder().unseenPenance(List.of(pending)).build()
				: base().toBuilder().penance(List.of(runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), pending)).build();
			Instruction hold = plan(s);
			assertEquals(Step.DEF_HOLD, hold.getStep());
			assertEquals(FOOD, hold.getTarget().getTile());
			assertTrue(hold.getDetail().contains("approaching"));
		}
		assertEquals(Step.DEF_STEP, plan(base().toBuilder().penance(List.of(
			runner(1, FOOD.dx(1)), runner(2, FOOD.dy(1)), runner(3, FOOD.dx(-1)))).build()).getStep());
	}

	@Test public void timerHoldMarksTheFoodAndWornTrapsRequireRepair()
	{
		Instruction timer = plan(base().toBuilder().ticksToCallChange(48).build());
		assertEquals(Step.DEF_HOLD, timer.getStep());
		assertEquals(FOOD, timer.getTarget().getTile());
		assertEquals(Step.DEF_REPAIR, plan(base().toBuilder().trapCharges(1).build()).getStep());
		assertNotEquals(Step.DEF_STEP, plan(base().toBuilder().trapCharges(-1).build()).getStep());
		Snapshot missing = base().toBuilder().trapCharges(1).hasHammer(false).inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 8)).build();
		Instruction tools = plan(missing);
		assertEquals(Step.DEF_HAMMER, tools.getStep());
		assertTrue(tools.getDetail().contains("1 charge"));
	}

	@Test public void lowReservesAndStickySupplyTripsCannotBreakAnActiveHold()
	{
		Snapshot low = base().toBuilder().inventory(Map.of(BaIds.LOGS, 3, BaCall.BAIT_WORMS.getItemId(), 1,
			BaCall.BAIT_TOFU.getItemId(), 8, BaCall.BAIT_CRACKERS.getItemId(), 8)).lastTask("def.reserve")
			.penance(List.of(runner(1, FOOD.dy(4)))).build();
		assertEquals(Step.DEF_HOLD, plan(low).getStep());
		assertEquals(FOOD, plan(low).getTarget().getTile());
		assertEquals(Step.DEF_TAKE_BAIT, plan(low.toBuilder().playerTile(FOOD.dy(-5)).build()).getStep());
		assertEquals(Step.DEF_HOLD, plan(low.toBuilder().hasHammer(false).lastTask("def.tools").build()).getStep());
	}

	@Test public void holdingStillAllowsMissingKillBaitAndBrokenTrapRecovery()
	{
		Snapshot empty = base().toBuilder().groundItems(List.of()).build();
		assertEquals(Step.DEF_DROP, plan(empty).getStep());
		assertEquals(FOOD, plan(empty).getTarget().getTile());
		assertEquals(Step.DEF_TAKE_BAIT, plan(empty.toBuilder().inventory(Map.of(BaIds.LOGS, 3)).build()).getStep());
		assertEquals(Step.DEF_REPAIR, plan(base().toBuilder().trapCharges(0).objects(List.of(new ObjectView(BaIds.TRAP_BROKEN, FOOD.dy(-1)))).build()).getStep());
	}

	@Test public void finalAdjacentSingletonKeepsItsOneChargeException()
	{
		Snapshot last = base().toBuilder().trapCharges(1).runnersKilled(WaveInfo.runnersTotal(5) - 1)
			.penance(List.of(runner(1, FOOD.dx(1)))).listenFresh(false).build();
		assertEquals("def.last", plan(last).getTask());
		assertEquals(Step.DEF_STEP, plan(last).getStep());
		assertNotEquals(Step.DEF_STEP, plan(last.toBuilder().penance(List.of(runner(1, FOOD.dy(2)))).build()).getStep());
	}

	@Test public void conflictingNearbyTrapStatesNeverGrantTwoChargesByListOrder()
	{
		ObjectView full = new ObjectView(BaIds.TRAP_FULL, FOOD.dy(-1)), worn = new ObjectView(BaIds.TRAP_ONE, FOOD.dy(-1));
		assertEquals(1, RoundLogic.trapCharges(List.of(full, worn), 5));
		assertEquals(1, RoundLogic.trapCharges(List.of(worn, full), 5));
		assertEquals(0, RoundLogic.trapCharges(List.of(full, new ObjectView(BaIds.TRAP_BROKEN, FOOD.dy(-1))), 5));
		assertEquals(2, RoundLogic.trapCharges(List.of(full, new ObjectView(BaIds.TRAP_BROKEN, FOOD.dx(-30))), 5));
	}

	@Test public void opportunisticPreparationUsesOnlySurplusHeldBaitAndNeverInterruptsReadyWork()
	{
		Snapshot waiting = base().toBuilder().penance(List.of(runner(1, FOOD.dy(3)), runner(2, FOOD.dy(5)), runner(3, FOOD.dy(7))))
			.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), FOOD, 2, true, 990))).build();
		assertEquals("def.prepare", plan(waiting).getTask());
		assertEquals(FOOD, plan(waiting).getTarget().getTile());
		assertNotEquals("def.prepare", plan(waiting.toBuilder().listenFresh(false).build()).getTask());
		assertNotEquals("def.prepare", plan(waiting.toBuilder().inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 2)).build()).getTask());
		assertNotEquals("def.prepare", plan(waiting.toBuilder().playerTile(FOOD.dy(-2)).build()).getTask());
		assertNotEquals("def.prepare", plan(base()).getTask());
		assertEquals(Step.DEF_STEP, plan(base()).getStep());
		assertEquals(Step.DEF_REPAIR, plan(base().toBuilder().trapCharges(1).build()).getStep());
	}
}
