package com.bautopilot.baautopilot;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.plan.*;
import com.bautopilot.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Recovery scenarios that previously stranded or misdirected a beginner. */
public class ReliabilityTest
{
	private final Planner planner = new Planner();
	private final Settings settings = Settings.builder().defenderMultiKill(true).build();

	private Snapshot.SnapshotBuilder defender(int wave)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		return Snapshot.builder().inArena(true).role(Role.DEFENDER).wave(wave).waveTick(100)
			.tick(200).callPeriod(2).ticksToCallChange(48).listenCall(BaCall.BAIT_WORMS).listenFresh(true)
			.shoutDone(true).hp(99).maxHp(99).freeSlots(12).playerTile(food).trapCharges(2)
			.hasHammer(true).logsTaken(2).inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 8,
				BaCall.BAIT_TOFU.getItemId(), 8, BaCall.BAIT_CRACKERS.getItemId(), 8, BaIds.LOGS, 2))
			.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), food, 4, true, 1)))
			.objects(List.of(new ObjectView(BaIds.TRAP_FULL, food.dy(-1))));
	}

	private static PenanceView runner(int index, WorldPoint tile)
	{
		return PenanceView.builder().npcIndex(index).type(PenanceType.RUNNER).tile(tile).build();
	}

	private Instruction plan(Snapshot s)
	{
		return planner.plan(s, settings);
	}

	@Test
	public void lastRunnerUsesOneChargeAndExistingBaitWithoutWaitingForAnotherCall()
	{
		for (int wave : new int[]{3, 10})
		{
			WorldPoint food = ArenaTiles.trapFood(wave);
			Snapshot s = defender(wave).trapCharges(1).runnersKilled(WaveInfo.runnersTotal(wave) - 1)
				.listenFresh(false).ticksToCallChange(20).lastTask("def.tools")
				.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), food, 1, true, 1)))
				.penance(List.of(runner(7, food.dx(1)))).build();
			Instruction release = plan(s);
			assertEquals(Step.DEF_STEP, release.getStep());
			assertEquals(ArenaTiles.trapStep(wave), release.getTarget().getTile());
			assertTrue(release.getItems().isEmpty());
			// After stepping off, don't send the player straight back onto the food.
			Instruction wait = plan(s.toBuilder().playerTile(ArenaTiles.trapStep(wave)).build());
			assertEquals(Step.DEF_HOLD, wait.getStep());
			assertEquals(ArenaTiles.trapStep(wave), wait.getTarget().getTile());
			assertNotEquals(Step.DEF_STEP, plan(s.toBuilder().trapCharges(0).build()).getStep());
		}
	}

	@Test
	public void finishTheSingleMainRunnerBeforeRecoveringDeferredOutliers()
	{
		WorldPoint food = ArenaTiles.trapFood(3);
		Snapshot s = defender(3).runnersKilled(2)
			.penance(List.of(runner(7, food.dx(1)), runner(8, food.dy(-8)))).lastTask("def.relure").build();
		assertEquals(Step.DEF_STEP, plan(s).getStep());
		assertEquals(Step.DEF_RELURE, plan(s.toBuilder().runnersKilled(3)
			.penance(List.of(runner(8, food.dy(-8)))).build()).getStep());
	}

	@Test
	public void aCommittedChaseYieldsToBrokenTrapAndMissingKillBait()
	{
		WorldPoint food = ArenaTiles.trapFood(3);
		Snapshot s = defender(3).runnersKilled(3).lastTask("def.relure")
			.penance(List.of(runner(7, food.dy(-8)))).build();
		assertEquals(Step.DEF_REPAIR, plan(s.toBuilder().trapCharges(0)
			.objects(List.of(new ObjectView(BaIds.TRAP_BROKEN, food.dy(-1)))).build()).getStep());
		assertEquals(Step.DEF_DROP, plan(s.toBuilder().groundItems(List.of()).build()).getStep());
		assertNotEquals(Step.DEF_RELURE, plan(s.toBuilder().trapCharges(-1).build()).getStep());
		assertNotEquals(Step.DEF_RELURE, plan(s.toBuilder().waveTick(20).build()).getStep());
	}

	@Test
	public void approachingOrUnseenRunnersAndRepairReadinessDeferCleanup()
	{
		WorldPoint food = ArenaTiles.trapFood(3);
		Snapshot s = defender(3).runnersKilled(2).lastTask("def.relure")
			.penance(List.of(runner(7, food.dy(-8)), runner(8, food.dy(8)))).build();
		assertNotEquals(Step.DEF_RELURE, plan(s).getStep());
		assertNotEquals(Step.DEF_RELURE, plan(s.toBuilder().penance(List.of(runner(7, food.dy(-8))))
			.unseenPenance(List.of(runner(8, food.dy(8)).toBuilder().unseen(true).build())).build()).getStep());
		Snapshot tools = defender(9).runnersKilled(5).hasHammer(false).inventory(Map.of(
			BaCall.BAIT_WORMS.getItemId(), 8, BaCall.BAIT_TOFU.getItemId(), 8, BaCall.BAIT_CRACKERS.getItemId(), 8))
			.penance(List.of(runner(7, ArenaTiles.trapFood(9).dy(-8)), runner(8, ArenaTiles.trapFood(9).dy(-9)),
				runner(9, ArenaTiles.trapFood(9).dy(-10)), runner(10, ArenaTiles.trapFood(9).dy(-11))))
			.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.trapFood(9), 4, true, 1),
				new GroundItemView(BaIds.HAMMER, ArenaTiles.mainstack(9), 1, false, 1))).build();
		assertEquals(Step.DEF_HAMMER, plan(tools).getStep());
	}

	@Test
	public void staleCallsStayBlockedAtEveryClockPositionForEveryRole()
	{
		WorldPoint food = ArenaTiles.trapFood(3);
		Snapshot def = defender(3).runnersKilled(3).penance(List.of(runner(7, food.dy(-8))))
			.listenFresh(false).build();
		Snapshot heal = def.toBuilder().role(Role.HEALER).listenCall(BaCall.POISON_WORMS)
			.inventory(Map.of(BaCall.POISON_WORMS.getItemId(), 8))
			.penance(List.of(PenanceView.builder().npcIndex(1).order(1).type(PenanceType.HEALER).tile(food).build())).build();
		Snapshot att = def.toBuilder().role(Role.ATTACKER).listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId())
			.penance(List.of(PenanceView.builder().npcIndex(1).type(PenanceType.FIGHTER).tile(food).build())).build();
		Snapshot col = def.toBuilder().role(Role.COLLECTOR).listenCall(BaCall.RED_EGG)
			.inventory(Map.of()).groundItems(List.of(new GroundItemView(BaIds.EGG_RED, food, 1, false, 190))).build();
		for (int countdown : new int[]{48, 20, 0, -1})
		{
			assertEquals(Step.HEAL_WAIT_CALL, plan(heal.toBuilder().ticksToCallChange(countdown).build()).getStep());
			assertEquals(Step.ATT_WAIT_CALL, plan(att.toBuilder().ticksToCallChange(countdown).build()).getStep());
			assertEquals(Step.COL_WAIT_CALL, plan(col.toBuilder().ticksToCallChange(countdown).build()).getStep());
			Instruction defender = plan(def.toBuilder().ticksToCallChange(countdown).build());
			assertNotEquals(Step.DEF_RELURE, defender.getStep());
			assertTrue(defender.getItems().isEmpty());
		}
		assertTrue(plan(att.toBuilder().interactingNpc(1).build()).getHeadline().startsWith("Stop attacking"));
		assertEquals(Step.HEAL_FEED, plan(heal.toBuilder().listenFresh(true).build()).getStep());
	}

	@Test
	public void missingCallsStillAllowRepairCallingAndEmergencyHealing()
	{
		Snapshot s = defender(3).listenFresh(false).trapCharges(0).build();
		assertEquals(Step.DEF_REPAIR, plan(s).getStep());
		assertEquals(Step.CALL, plan(s.toBuilder().shoutDone(false).shoutCall(BaCall.POISON_WORMS).build()).getStep());
		Snapshot heal = s.toBuilder().role(Role.HEALER).inventory(Map.of(BaIds.VIAL_FULL, 1))
			.teammates(List.of(new TeammateView("Player2", 10, 99))).build();
		assertEquals(Step.HEAL_TEAMMATE, plan(heal).getStep());
	}
}
