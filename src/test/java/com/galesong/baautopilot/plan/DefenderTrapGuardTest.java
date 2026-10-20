package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.*;
import com.galesong.baautopilot.state.*;
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
