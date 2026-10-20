package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueenCollectorTest
{
	private static final WorldPoint HOPPER = ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 22, 34);
	private static final WorldPoint WATCH = ArenaTiles.queenCollectorWatch();
	private final Planner planner = new Planner();
	private static Snapshot base(int yellow)
	{
		return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(10).waveTick(200)
			.queenAlive(true).shoutDone(true).hp(77).maxHp(77).freeSlots(20).playerTile(WATCH)
			.inventory(Map.of(BaIds.EGG_YELLOW, yellow)).team(Map.of("Zed", Role.HEALER))
			.groundItems(List.of(new GroundItemView(BaIds.EGG_YELLOW, WATCH.dx(1), 3, false, 990)))
			.objects(List.of(new ObjectView(BaIds.HOPPERS[0], HOPPER), new ObjectView(BaIds.LAUNCHERS[0], HOPPER.dx(-1))))
			.build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }

	@Test public void fiveEggDefaultFinishesCollectionBeforePassing()
	{
		for (int n = 0; n < 5; n++)
		{
			Instruction ins = plan(base(n));
			assertEquals(Step.QUEEN_COLLECT, ins.getStep());
			assertTrue(ins.getHeadline().contains(n + "/5"));
		}
		assertEquals(Step.QUEEN_PASS, plan(base(5)).getStep());
		assertEquals(Step.QUEEN_PASS, planner.plan(base(3), Settings.builder().queenEggBatch(3).build()).getStep());
		assertEquals(Step.QUEEN_PASS, plan(base(2).toBuilder().freeSlots(0).build()).getStep());
	}

	@Test public void passingKeepsFinishingTheBatchThroughInventoryDropsAndSurvival()
	{
		Instruction pass = plan(base(5));
		Snapshot partial = base(4).toBuilder().tasks(pass.getTasks()).build();
		assertEquals(Step.QUEEN_PASS, plan(partial).getStep());
		Instruction interrupted = plan(partial.toBuilder().hp(5).build());
		assertEquals(Step.RETREAT, interrupted.getStep());
		assertEquals(Step.QUEEN_PASS, plan(base(1).toBuilder().tasks(interrupted.getTasks()).build()).getStep());
		assertEquals(Step.QUEEN_COLLECT, plan(base(0).toBuilder().tasks(pass.getTasks()).build()).getStep());
	}

	@Test public void healerRecipientSurvivesRosterReorderingAndLaterBatches()
	{
		Instruction first = plan(base(5));
		assertEquals("Zed", first.getTarget().getLabel());
		Snapshot two = base(4).toBuilder().team(Map.of("Amy", Role.HEALER, "Zed", Role.HEALER)).tasks(first.getTasks()).build();
		assertEquals("Zed", plan(two).getTarget().getLabel());
		Instruction empty = plan(two.toBuilder().inventory(Map.of()).build());
		assertEquals("Zed", plan(two.toBuilder().inventory(Map.of(BaIds.EGG_YELLOW, 5)).tasks(empty.getTasks()).build()).getTarget().getLabel());
		assertEquals("Amy", plan(two.toBuilder().team(Map.of("Amy", Role.HEALER)).build()).getTarget().getLabel());
	}

	@Test public void aPartialBatchWaitsNorthForDropsInsteadOfPassingOrReturningToThePool()
	{
		Snapshot s = base(2).toBuilder().groundItems(List.of()).playerTile(WATCH.dy(-18)).build();
		Instruction watch = plan(s);
		assertEquals(Step.QUEEN_WAIT, watch.getStep());
		assertEquals(WATCH, watch.getTarget().getTile());
		assertTrue(watch.getHeadline().contains("2/5"));
		assertEquals(Target.NONE, plan(s.toBuilder().playerTile(WATCH).build()).getTarget());
	}

	@Test public void omegaDeliveryInterruptsGatheringAndPositionsForSingleLoadingByDefault()
	{
		Snapshot s = base(2).toBuilder().inventory(Map.of(BaIds.EGG_YELLOW, 2, BaIds.EGG_OMEGA, 3)).build();
		Instruction position = plan(s);
		assertEquals(Step.QUEEN_LOAD_POSITION, position.getStep());
		assertEquals(HOPPER.dy(-2), position.getTarget().getTile());
		assertTrue(position.getItems().contains(BaIds.EGG_OMEGA));
		Snapshot at = s.toBuilder().playerTile(HOPPER.dy(-2)).build();
		assertEquals(Step.QUEEN_LOAD_ONE, plan(at).getStep());
		Instruction hold = plan(at.toBuilder().eggsOmega(1).build());
		assertEquals(Step.QUEEN_WAIT, hold.getStep());
		assertTrue(hold.getItems().contains(BaIds.EGG_OMEGA));
		assertEquals(Step.QUEEN_LOAD_ONE, plan(at.toBuilder().eggsOmega(0).build()).getStep());
	}

	@Test public void bulkLoadingRequiresAnExplicitOptOut()
	{
		Snapshot s = base(0).toBuilder().inventory(Map.of(BaIds.EGG_OMEGA, 3)).build();
		assertEquals(Step.QUEEN_LOAD, planner.plan(s, Settings.builder().queenSingleLoads(false).build()).getStep());
	}

	@Test public void extraYellowsStayReservedUntilTheInitialVolleyIsFiredAndSettled()
	{
		Snapshot reserve = base(3).toBuilder().queenYellowPassed(5).build();
		assertEquals("Hold the Queen reserve", plan(reserve).getHeadline());
		assertEquals(Step.QUEEN_WAIT, plan(reserve).getStep());
		assertEquals(Step.QUEEN_COLLECT, plan(reserve.toBuilder().inventory(Map.of(BaIds.EGG_YELLOW, 1)).build()).getStep());
		assertNotEquals(Step.QUEEN_PASS, plan(reserve.toBuilder().queenOmegaFired(4).lastQueenOmegaShotTick(990).build()).getStep());
		assertNotEquals(Step.QUEEN_PASS, plan(reserve.toBuilder().queenOmegaFired(5).lastQueenOmegaShotTick(999).build()).getStep());
		Snapshot ready = reserve.toBuilder().queenOmegaFired(5).lastQueenOmegaShotTick(997).build();
		assertEquals(Step.QUEEN_PASS, plan(ready).getStep());
		assertTrue(plan(ready).getHeadline().contains("3 yellow eggs"));
		assertEquals("Queen defeated", plan(ready.toBuilder().queenDead(true).build()).getHeadline());
	}

	@Test public void initialHandoffKeepsExtrasAndReserveIsConfigurableAndFinite()
	{
		Instruction pass = plan(base(8));
		assertTrue(pass.getHeadline(), pass.getHeadline().contains("5 yellow eggs"));
		assertEquals(Step.QUEEN_WAIT, plan(base(3).toBuilder().queenYellowPassed(5).tasks(pass.getTasks()).build()).getStep());
		Snapshot empty = base(0).toBuilder().queenYellowPassed(5).build();
		Instruction two = planner.plan(empty, Settings.builder().queenEggBuffer(2).build());
		assertEquals(2, two.getPickupCount());
		assertEquals(Step.QUEEN_WAIT, planner.plan(empty, Settings.builder().queenEggBuffer(0).build()).getStep());
		assertEquals(Step.QUEEN_WAIT, plan(empty.toBuilder().queenYellowPassed(8).queenOmegaFired(5).lastQueenOmegaShotTick(990).build()).getStep());
		assertEquals(Step.QUEEN_LOAD_POSITION, plan(empty.toBuilder().inventory(Map.of(BaIds.EGG_OMEGA, 1)).build()).getStep());
	}
}
