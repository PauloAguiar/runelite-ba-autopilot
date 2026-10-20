package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.*;
import com.galesong.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorProactiveTest
{
	private static final WorldPoint ME = ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, 20, 44);
	private final Planner planner = new Planner();
	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).wave(3).waveTick(100).inArena(true).role(Role.COLLECTOR)
			.collectorOpeningComplete(true).playerTile(ME).hp(77).maxHp(77).shoutDone(true)
			.listenCall(BaCall.RED_EGG).listenFresh(true).ticksToCallChange(40).freeSlots(15)
			.objects(List.of(new ObjectView(BaIds.HOPPERS[0], ME.dy(-8))))
			.groundItems(List.of(egg(BaIds.EGG_RED, ME.dx(1)))).build();
	}
	private static GroundItemView egg(int item, WorldPoint tile) { return new GroundItemView(item, tile, 1, false, 990); }
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }
	private static Snapshot after(Snapshot s, Instruction action, int tick, WorldPoint tile)
	{
		return s.toBuilder().tick(tick).playerTile(tile).tasks(action.getTasks()).lastTask(action.getTask())
			.lastStep(action.getStep()).lastActionTarget(action.getTarget()).build();
	}

	@Test public void largeRedReservesSupplyTheCannonWithoutVisibleThreatsOrShooting()
	{
		for (Snapshot s : List.of(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 12)).build(),
			base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 4)).bagRed(8).build()))
		{
			Instruction load = plan(s);
			assertEquals(Step.COL_LOAD, load.getStep());
			assertEquals("col.load", load.getTask());
			assertEquals(Tier.MAINTENANCE, load.getTier());
			assertTrue(load.getScoreTerms().get("proactive red supply") > 0);
			assertFalse(load.getDetail().contains("escaping"));
		}
		Snapshot green = base().toBuilder().listenCall(BaCall.GREEN_EGG).inventory(Map.of(BaIds.EGG_RED, 15))
			.eggsRed(2).groundItems(List.of(egg(BaIds.EGG_GREEN, ME.dx(1)))).build();
		assertEquals(Step.COL_LOAD, plan(green).getStep());
	}

	@Test public void scarcityFullHoppersAndCompletedTargetsStillMatter()
	{
		assertEquals(Step.COL_COLLECT, plan(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 2)).build()).getStep());
		assertEquals(Step.COL_COLLECT, plan(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 20)).eggsRed(5).build()).getStep());
		Snapshot cleared = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 12))
			.cleared(Set.of(PenanceType.RUNNER, PenanceType.HEALER)).build();
		assertTrue(plan(cleared).getPriorities().stream().filter(p -> "col.load".equals(p.getTask()))
			.allMatch(p -> p.getScoreTerms().get("proactive red supply") == 0));
	}

	@Test public void ordinaryEggsStayLocalEvenWhenTheyAreTheOnlyDrops()
	{
		Snapshot far = base().toBuilder().groundItems(List.of(egg(BaIds.EGG_RED, ME.dx(18)))).build();
		assertNotEquals(Step.COL_COLLECT, plan(far).getStep());
		assertNotEquals(Step.COL_DISCARD, plan(far.toBuilder().freeSlots(0).inventory(Map.of(BaIds.EGG_GREEN, 20)).build()).getStep());
		Snapshot detour = base().toBuilder().groundItems(List.of(egg(BaIds.EGG_RED, ME.dx(10)))).build();
		assertTrue(plan(detour).getPriorities().stream().filter(p -> "col.collect".equals(p.getTask()))
			.allMatch(p -> p.getScoreTerms().get("distant egg detour") < 0));
		assertNotEquals(Step.COL_COLLECT, plan(base().toBuilder().walkCost(tile -> 20).build()).getStep());
	}

	@Test public void centreCheckKeepsItsMarkerAndDoesNotRepeatAfterArriving()
	{
		Snapshot idle = base().toBuilder().listenCall(BaCall.BLUE_EGG).groundItems(List.of()).eggsRed(5).build();
		Instruction check = plan(idle);
		assertEquals("col.scan", check.getTask());
		assertTrue(idle.walk(check.getTarget().getTile()) <= 6);
		Instruction moving = plan(after(idle, check, 1001, ME.dx(1)));
		assertEquals(check.getTarget(), moving.getTarget());
		Instruction arrived = plan(after(idle, moving, 1002, check.getTarget().getTile()));
		assertNotEquals("col.scan", arrived.getTask());
		assertNotEquals("col.scan", plan(after(idle, arrived, 1003, ME)).getTask());
		assertEquals("col.scan", plan(after(idle, arrived, 1053, ME)).getTask());
		Instruction ignored = plan(after(idle, check, 1017, ME));
		assertNotEquals("col.scan", ignored.getTask());
	}

	@Test public void centreChecksYieldToLocalWorkAndRequireAmmoAndRemainingTargets()
	{
		Snapshot idle = base().toBuilder().listenCall(BaCall.BLUE_EGG).groundItems(List.of()).eggsRed(5).build();
		for (Snapshot noCheck : List.of(idle.toBuilder().eggsRed(0).build(), idle.toBuilder().waveTick(2).build(),
			idle.toBuilder().collectorOpeningComplete(false).build(), idle.toBuilder().playerTile(ME.dx(7).dy(-6)).build(),
			idle.toBuilder().cleared(Set.of(PenanceType.RUNNER, PenanceType.HEALER)).build(), idle.toBuilder().walkCost(tile -> 20).build()))
			assertNotEquals("col.scan", plan(noCheck).getTask());
		assertEquals(Step.COL_COLLECT, plan(idle.toBuilder().listenCall(BaCall.GREEN_EGG)
			.groundItems(List.of(egg(BaIds.EGG_GREEN, ME.dx(1)))).build()).getStep());
	}
}
