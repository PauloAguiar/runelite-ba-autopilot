package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.model.*;
import com.galesong.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenderContinuousTest
{
	private final Settings settings = Settings.builder().safetyBait(false).build();
	private final Planner planner = new Planner();
	private static final WorldPoint FOOD = ArenaTiles.trapFood(5);
	private static Snapshot base()
	{
		return Snapshot.builder().inArena(true).tick(1000).role(Role.DEFENDER).wave(5).waveTick(100)
			.callPeriod(2).ticksToCallChange(45).shoutDone(true).listenCall(BaCall.BAIT_WORMS).listenFresh(true)
			.playerTile(FOOD).hp(77).maxHp(77).freeSlots(8).trapCharges(2).hasHammer(true).logsTaken(4)
			.inventory(Map.of(BaIds.LOGS, 2, BaCall.BAIT_WORMS.getItemId(), 8,
				BaCall.BAIT_TOFU.getItemId(), 8, BaCall.BAIT_CRACKERS.getItemId(), 8))
			.groundItems(List.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), FOOD, 4, true, 990),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(5), 4, true, 990),
				new GroundItemView(BaCall.BAIT_TOFU.getItemId(), ArenaTiles.defenderPattern(5).get(2).getTile(), 1, false, 990)))
			.objects(List.of(new ObjectView(BaIds.TRAP_FULL, FOOD.dy(-1))))
			.penance(List.of(PenanceView.builder().npcIndex(1).type(PenanceType.RUNNER).tile(FOOD.dx(1)).build(),
				PenanceView.builder().npcIndex(2).type(PenanceType.RUNNER).tile(FOOD.dy(1)).build())).build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, settings); }

	@Test public void continuousUpkeepIsTheDefaultAndMultikillsAreExplicitlyOptIn()
	{
		assertFalse(new BaAutopilotConfig() { }.defenderMultiKill());
		assertFalse(Settings.builder().build().isDefenderMultiKill());
		Instruction aside = plan(base());
		assertEquals(Step.DEF_SERVICE, aside.getStep());
		assertEquals(ArenaTiles.defenderService(5), aside.getTarget().getTile());
		Instruction advanced = planner.plan(base(), settings.toBuilder().defenderMultiKill(true).build());
		assertEquals(Step.DEF_STEP, advanced.getStep());
		assertEquals("def.step", advanced.getTask());
	}

	@Test public void brokenTrapWalksToTheRepairSpotThenFixesBeforeAnyBaitOrStickyReserve()
	{
		for (int wave : new int[]{5, 10})
		{
			WorldPoint food = ArenaTiles.trapFood(wave);
			Snapshot broken = base().toBuilder().wave(wave).trapCharges(0).playerTile(food.dx(-3))
				.lastTask("def.trapfood").groundItems(List.of()).objects(List.of(new ObjectView(BaIds.TRAP_BROKEN, food.dy(-1)))).build();
			Instruction walk = plan(broken);
			assertEquals(Step.DEF_REPAIR_POSITION, walk.getStep());
			assertEquals(food, walk.getTarget().getTile());
			assertEquals("def.repair", walk.getTask());
			assertEquals("REPAIR SPOT", walk.getStep().getTag());
			Instruction fix = plan(broken.toBuilder().playerTile(food).lastTask(walk.getTask()).build());
			assertEquals(Step.DEF_REPAIR, fix.getStep());
			assertTrue(fix.getDetail().contains("Stay on the food tile"));
		}
	}

	@Test public void repairFoodAndStepAsideHaveClearCompletionConditions()
	{
		Snapshot onFood = base().toBuilder().trapCharges(0).groundItems(List.of()).build();
		assertEquals(Step.DEF_REPAIR, plan(onFood).getStep());
		Snapshot repaired = onFood.toBuilder().trapCharges(2).lastTask("def.repair").build();
		Instruction drop = plan(repaired);
		assertEquals(Step.DEF_DROP, drop.getStep());
		assertEquals(FOOD, drop.getTarget().getTile());
		Snapshot stocked = repaired.toBuilder().groundItems(base().getGroundItems()).lastTask(drop.getTask()).build();
		assertEquals(Step.DEF_SERVICE, plan(stocked).getStep());
		Instruction watch = plan(stocked.toBuilder().playerTile(ArenaTiles.defenderService(5)).lastTask("def.clear").build());
		assertEquals(Step.DEF_WATCH, watch.getStep());
		assertEquals(Target.NONE, watch.getTarget());
	}

	@Test public void routineUncalledBaitReservesCannotPullThePlayerAwayAfterSteppingAside()
	{
		Snapshot enough = base().toBuilder().playerTile(ArenaTiles.defenderService(5)).lastTask("def.reserve")
			.inventory(Map.of(BaIds.LOGS, 2, BaCall.BAIT_WORMS.getItemId(), 8, BaCall.BAIT_TOFU.getItemId(), 1,
				BaCall.BAIT_CRACKERS.getItemId(), 8)).build();
		assertEquals(Step.DEF_WATCH, plan(enough).getStep());
		assertNotEquals("def.reserve", plan(enough).getTask());
	}

	@Test public void staleCallsNeverDropNewFoodAndOneChargeDoesNotRequestAMultikill()
	{
		Snapshot waiting = base().toBuilder().trapCharges(1).listenFresh(false).groundItems(List.of()).build();
		Instruction result = plan(waiting);
		assertEquals(Step.DEF_WAIT_CALL, result.getStep());
		assertEquals(FOOD, result.getTarget().getTile());
		assertNotEquals(Step.DEF_DROP, result.getStep());
		assertNotEquals(Step.DEF_HOLD, result.getStep());
		assertNotEquals(Step.DEF_STEP, result.getStep());
		assertFalse(result.getDetail().contains("multi-kill"));
	}

	@Test public void repairToolsAreRequestedBeforeTheRepairPosition()
	{
		Snapshot missing = base().toBuilder().trapCharges(0).hasHammer(false)
			.groundItems(List.of(new GroundItemView(BaIds.HAMMER, FOOD.dx(-5), 1, false, 990))).build();
		assertEquals(Step.DEF_HAMMER, plan(missing).getStep());
		assertTrue(plan(missing).getDetail().contains("repair"));
		assertEquals(Step.DEF_HAMMER, plan(missing.toBuilder().groundItems(List.of()).build()).getStep());
	}
}
