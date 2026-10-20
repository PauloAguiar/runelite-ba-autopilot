package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenderTargetStabilityTest
{
	private static final int REGION = ArenaTiles.REGION_WAVES_1_9;
	private static final Settings SETTINGS = Settings.builder().build();
	private final Planner planner = new Planner();

	private static WorldPoint tile(int x, int y) { return ArenaTiles.p(REGION, x, y); }
	private static GroundItemView item(int id, WorldPoint tile, int count, boolean good)
	{
		return new GroundItemView(id, tile, count, good, 1);
	}

	private static Snapshot base()
	{
		return Snapshot.builder().tick(100).inArena(true).role(Role.DEFENDER).wave(3).waveTick(10)
			.callPeriod(0).ticksToCallChange(40).listenCall(BaCall.BAIT_WORMS).listenFresh(true).shoutDone(true)
			.hp(99).maxHp(99).freeSlots(4).hasHammer(true).logsTaken(2).trapCharges(2).playerTile(tile(42, 29))
			.inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 6,
				BaCall.BAIT_CRACKERS.getItemId(), 6, BaIds.LOGS, 2))
			.objects(List.of(new ObjectView(BaIds.TRAP_FULL, ArenaTiles.trapFood(3).dy(-1)))).build();
	}

	private static Snapshot next(Snapshot s, Instruction previous, WorldPoint tile)
	{
		return s.toBuilder().tick(s.getTick() + 1).waveTick(s.getWaveTick() + 1).playerTile(tile)
			.lastTask(previous.getTask()).lastTargetTile(previous.getTarget().getTile()).tasks(previous.getTasks()).build();
	}

	private Instruction plan(Snapshot s) { return planner.plan(s, SETTINGS); }

	private static List<GroundItemView> setup()
	{
		return new ArrayList<>(List.of(item(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.trapFood(3), 4, true),
			item(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true),
			item(BaCall.BAIT_TOFU.getItemId(), ArenaTiles.safety(3), 1, false)));
	}

	@Test
	public void passingSafetyDoesNotDivertTheTripToTrapFood()
	{
		Snapshot s = base();
		Instruction trap = plan(s);
		assertEquals(ArenaTiles.trapFood(3), trap.getTarget().getTile());
		for (WorldPoint position : List.of(tile(43, 31), tile(43, 33), tile(45, 35)))
		{
			s = next(s, trap, position);
			trap = plan(s);
			assertEquals(ArenaTiles.trapFood(3), trap.getTarget().getTile());
		}
		// Actual bait placement, not arrival alone, completes this spot.
		Instruction advance = plan(next(s, trap, s.getPlayerTile()).toBuilder()
			.goodBaitLanded(Map.of(ArenaTiles.trapFood(3), 4)).build());
		assertNotEquals(ArenaTiles.trapFood(3), advance.getTarget().getTile());
	}

	@Test
	public void aChosenSafetyDropPersistsUntilDoneButYieldsToTheGoodBaitDeadline()
	{
		Snapshot s = base().toBuilder().playerTile(ArenaTiles.safety(3)).build();
		Instruction safety = plan(s);
		assertEquals(ArenaTiles.safety(3), safety.getTarget().getTile());
		Snapshot farther = next(s, safety, tile(43, 33));
		assertEquals(ArenaTiles.safety(3), plan(farther).getTarget().getTile());
		assertEquals(ArenaTiles.trapFood(3), plan(farther.toBuilder().ticksToCallChange(5).build()).getTarget().getTile());
		assertEquals(ArenaTiles.trapFood(3), plan(farther.toBuilder()
			.badBaitLanded(Map.of(ArenaTiles.safety(3), 1)).build()).getTarget().getTile());
	}

	@Test
	public void missingFirstCallAndAnOwedCallKeepTheOpeningDestination()
	{
		Snapshot waiting = base().toBuilder().listenCall(null).listenFresh(false).build();
		Instruction wait = plan(waiting);
		assertEquals(Step.DEF_WAIT_CALL, wait.getStep());
		Snapshot heard = next(waiting, wait, tile(43, 31)).toBuilder().listenCall(BaCall.BAIT_WORMS).listenFresh(true).build();
		Instruction trap = plan(heard);
		assertEquals(ArenaTiles.trapFood(3), trap.getTarget().getTile());
		Instruction call = plan(next(heard, trap, tile(43, 31)).toBuilder().shoutDone(false).shoutCall(BaCall.POISON_WORMS).build());
		assertEquals(Step.CALL, call.getStep());
		Instruction resume = plan(next(heard, call, tile(43, 31)));
		assertEquals(ArenaTiles.trapFood(3), resume.getTarget().getTile());
	}

	@Test
	public void toolsKeepTheirSelectedItemUntilPickedUpOrNoLongerVisible()
	{
		WorldPoint hammer = tile(32, 42), log = tile(29, 46);
		List<GroundItemView> ground = setup();
		ground.add(item(BaIds.HAMMER, hammer, 1, false));
		ground.add(item(BaIds.LOGS, log, 1, false));
		Snapshot s = base().toBuilder().waveTick(70).callPeriod(1).hasHammer(false).logsTaken(0)
			.inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 6, BaCall.BAIT_CRACKERS.getItemId(), 6))
			.playerTile(hammer).groundItems(ground).build();
		Instruction first = plan(s);
		assertEquals(Step.DEF_HAMMER, first.getStep());
		Snapshot nearerLog = next(s, first, log);
		assertEquals(first.getTarget(), plan(nearerLog).getTarget());
		assertEquals(Step.DEF_LOGS, plan(nearerLog.toBuilder().hasHammer(true).build()).getStep());
		ground.removeIf(g -> g.getItemId() == BaIds.HAMMER);
		assertEquals(Step.DEF_LOGS, plan(nearerLog.toBuilder().groundItems(ground).build()).getStep());
	}

	@Test
	public void theSameLogStaysSelectedWhenAnotherLogBecomesCloser()
	{
		WorldPoint firstLog = tile(29, 46), otherLog = tile(28, 47);
		List<GroundItemView> ground = setup();
		ground.add(item(BaIds.LOGS, firstLog, 1, false));
		ground.add(item(BaIds.LOGS, otherLog, 1, false));
		Snapshot s = base().toBuilder().wave(9).waveTick(70).callPeriod(1).logsTaken(0)
			.inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 6, BaCall.BAIT_CRACKERS.getItemId(), 6))
			.playerTile(firstLog).groundItems(ground).build();
		Instruction first = plan(s);
		assertEquals(firstLog, first.getTarget().getTile());
		Snapshot moved = next(s, first, otherLog);
		assertEquals(firstLog, plan(moved).getTarget().getTile());
		ground.removeIf(g -> g.getTile().equals(firstLog));
		assertEquals(otherLog, plan(moved.toBuilder().groundItems(ground).build()).getTarget().getTile());
	}

	@Test
	public void aPatternToolsTripIsNotInterruptedByWalkingNearBadBait()
	{
		WorldPoint log = tile(29, 46);
		List<GroundItemView> ground = setup();
		ground.add(item(BaIds.LOGS, log, 1, false));
		ground.add(item(BaIds.HAMMER, tile(32, 42), 1, false));
		Snapshot s = base().toBuilder().hasHammer(false).logsTaken(0).playerTile(ArenaTiles.mainstack(3))
			.inventory(Map.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 6, BaCall.BAIT_CRACKERS.getItemId(), 6))
			.groundItems(ground).build();
		Instruction first = plan(s);
		assertEquals("def.pattern", first.getTask());
		assertEquals(Step.DEF_LOGS, first.getStep());
		assertEquals(first.getTarget(), plan(next(s, first, tile(31, 44))).getTarget());
	}

	@Test
	public void waitingForOpeningLogsDoesNotTurnIntoAnUnfinishedHammerDetour()
	{
		Snapshot s = base().toBuilder().wave(9).waveTick(70).callPeriod(1).hasHammer(false)
			.playerTile(ArenaTiles.logSpawn(9)).groundItems(setup()).build();
		Instruction wait = plan(s);
		assertEquals(Step.DEF_LOGS, wait.getStep());
		assertEquals(Target.Kind.TILE, wait.getTarget().getKind());
		List<GroundItemView> ground = setup();
		ground.add(item(BaIds.HAMMER, tile(32, 42), 1, false));
		Snapshot hammerVisible = next(s, wait, tile(32, 42)).toBuilder().groundItems(ground).build();
		assertEquals(wait.getTarget(), plan(hammerVisible).getTarget());
		ground.add(item(BaIds.LOGS, ArenaTiles.logSpawn(9), 1, false));
		Instruction take = plan(hammerVisible.toBuilder().groundItems(ground).build());
		assertEquals(Target.Kind.GROUND_ITEM, take.getTarget().getKind());
		assertEquals(BaIds.LOGS, take.getTarget().getId());
	}

	@Test
	public void aBrokenTrapPreemptsAnUnfinishedPatternSpot()
	{
		Snapshot s = base();
		Instruction first = plan(s);
		Instruction repair = plan(next(s, first, tile(43, 31)).toBuilder().trapCharges(0)
			.objects(List.of(new ObjectView(BaIds.TRAP_BROKEN, ArenaTiles.trapFood(3).dy(-1)))).build());
		assertEquals(Step.DEF_REPAIR_POSITION, repair.getStep());
		assertEquals(ArenaTiles.trapFood(3), repair.getTarget().getTile());
	}
}
