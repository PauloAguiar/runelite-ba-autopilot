package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorWaveTenTest
{
	private static final WorldPoint ME = ArenaTiles.queenCollectorWatch();
	private final Planner planner = new Planner();
	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).inArena(true).wave(10).waveTick(100).role(Role.COLLECTOR)
			.playerTile(ME).hp(77).maxHp(77).shoutDone(true).listenCall(BaCall.RED_EGG).listenFresh(true)
			.freeSlots(15).ticksToCallChange(40).objects(List.of(new ObjectView(BaIds.HOPPERS[0], ME.dx(3))))
			.groundItems(List.of(new GroundItemView(BaIds.EGG_RED, ME.dx(1), 3, false, 990))).build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }
	private static Snapshot cleared(Snapshot s)
	{
		return s.toBuilder().fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
			.runnersKilled(WaveInfo.runnersTotal(10)).healersKilled(WaveInfo.healersTotal(10)).build();
	}

	@Test public void emptyCannonGetsAnyHeldColourIncludingBlueBeforeQueenWaiting()
	{
		for (int egg : new int[]{BaIds.EGG_RED, BaIds.EGG_GREEN, BaIds.EGG_BLUE})
		{
			Instruction preload = plan(cleared(base().toBuilder().inventory(Map.of(egg, 1)).build()));
			assertEquals(Step.COL_LOAD, preload.getStep());
			assertTrue(preload.getHeadline(), preload.getHeadline().contains("Preload"));
			assertTrue(preload.getItems().contains(egg));
		}
		assertEquals(Step.COL_LOAD, plan(cleared(base().toBuilder().bagBlue(1).build())).getStep());
		for (Snapshot loaded : List.of(base().toBuilder().eggsRed(1).build(), base().toBuilder().eggsGreen(1).build(), base().toBuilder().eggsBlue(1).build()))
			assertEquals(Step.COL_QUEEN_READY, plan(cleared(loaded)).getStep());
	}

	@Test public void earlyRedsAreUsefulButStockedCannonDoesNotCausePointHoarding()
	{
		assertEquals(Step.COL_COLLECT, plan(base()).getStep());
		assertEquals(Step.COL_COLLECT, plan(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 1)).build()).getStep());
		assertEquals(Step.COL_LOAD, plan(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 3)).build()).getStep());
		assertEquals(5, plan(base().toBuilder().groundItems(List.of(new GroundItemView(BaIds.EGG_RED, ME.dx(1), 20, false, 990))).build()).getPickupCount());
		assertEquals(2, plan(base().toBuilder().listenCall(BaCall.GREEN_EGG).groundItems(List.of(
			new GroundItemView(BaIds.EGG_GREEN, ME.dx(1), 20, false, 990))).build()).getPickupCount());
		Snapshot stocked = base().toBuilder().eggsRed(5).inventory(Map.of(BaIds.EGG_RED, 5)).build();
		assertNotEquals(Step.COL_COLLECT, plan(stocked).getStep());
		assertNotEquals(Step.COL_COLLECT, plan(stocked.toBuilder().lastTask("col.collect").lastStep(Step.COL_COLLECT)
			.lastActionTarget(Target.groundItem(BaIds.EGG_RED, ME.dx(1))).build()).getStep());
	}

	@Test public void postClearOnlyOneSafelyCalledEggIsCollectedToEnablePreloading()
	{
		Instruction collect = plan(cleared(base()));
		assertEquals(Step.COL_COLLECT, collect.getStep());
		assertEquals(1, collect.getPickupCount());
		assertNotEquals(Step.COL_COLLECT, plan(cleared(base().toBuilder().listenFresh(false).build())).getStep());
		assertNotEquals(Step.COL_COLLECT, plan(cleared(base().toBuilder().ticksToCallChange(1).build())).getStep());
		Snapshot blue = cleared(base().toBuilder().listenCall(BaCall.BLUE_EGG).groundItems(List.of(
			new GroundItemView(BaIds.EGG_BLUE, ME.dx(1), 4, false, 990))).build());
		assertEquals(Step.COL_COLLECT, plan(blue).getStep());
		assertNotEquals(Step.COL_COLLECT, plan(blue.toBuilder().eggsBlue(1).build()).getStep());
	}
}
