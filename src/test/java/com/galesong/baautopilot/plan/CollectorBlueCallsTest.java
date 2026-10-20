package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class CollectorBlueCallsTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 40, 50);
	private static final WorldPoint HOPPER = ArenaTiles.p(R, 40, 30);
	private static final WorldPoint LAUNCHER = ArenaTiles.p(R, 41, 30);
	private static final Settings SETTINGS = Settings.builder().collectorLoadAt(15).build();
	private final Planner planner = new Planner();

	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(3).waveTick(70)
			.callPeriod(1).ticksToCallChange(30).listenCall(BaCall.BLUE_EGG).listenFresh(true).shoutDone(true)
			.playerTile(ME).hp(99).maxHp(99).freeSlots(15)
			.eggsRed(1).eggsGreen(1)
			.objects(List.of(new ObjectView(BaIds.HOPPERS[0], HOPPER), new ObjectView(BaIds.LAUNCHERS[0], LAUNCHER)))
			.penance(List.of(PenanceView.builder().npcIndex(9).type(PenanceType.RUNNER).tile(ArenaTiles.p(R, 44, 40)).build()))
			.groundItems(List.of(new GroundItemView(BaIds.EGG_BLUE, ME.dx(-1), 1, false, 810))).build();
	}

	@Test
	public void blueCallLoadsConfiguredBatchAcrossInventoryAndBagBeforeAnExpiringBlue()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 13, BaIds.EGG_BLUE, 2)).bagRed(1).bagGreen(1)
			.lastTask("col.collect").lastTargetTile(ME.dx(-1)).build();
		Instruction ins = planner.plan(s, SETTINGS);
		assertEquals(Step.COL_LOAD, ins.getStep());
		assertEquals(Tier.TIME_CRITICAL, ins.getTier());
		assertEquals(HOPPER, ins.getTarget().getTile());
		assertTrue(ins.getDetail().contains("Blue call"));
		assertTrue(ins.getDetail().contains("4 red + 1 green fit"));
		assertFalse(ins.getItems().contains(BaIds.EGG_BLUE));
	}

	@Test
	public void loadingComesBeforeBlueDeletionWithAFullInventory()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 3, BaIds.EGG_BLUE, 20)).freeSlots(0).build();
		assertEquals(Step.COL_LOAD, planner.plan(s, SETTINGS).getStep());
	}

	@Test
	public void ordinaryShootingYieldsToCollectionAndSpareCleanup()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_BLUE, 2)).lastTask("col.collect").build();
		Instruction fire = planner.plan(s, SETTINGS);
		assertNotEquals(Step.COL_FIRE, fire.getStep());
		// Cleanup is also spare work when there is no ground egg or no inventory room.
		assertEquals(Step.COL_BLUE, planner.plan(s.toBuilder().groundItems(List.of()).freeSlots(0).build(), SETTINGS).getStep());
	}

	@Test
	public void fullHoppersDoNotCauseAPointlessLoadAndOnlyFittingEggsCount()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 20, BaIds.EGG_GREEN, 1))
			.eggsRed(5).eggsGreen(1).penance(List.of()).build();
		// Only one green fits; the twenty reds cannot justify this long trip.
		assertEquals(Step.COL_COLLECT, planner.plan(s, SETTINGS).getStep());
		Instruction full = planner.plan(base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 20, BaIds.EGG_BLUE, 2))
			.eggsRed(5).eggsGreen(5).build(), SETTINGS);
		assertNotEquals(Step.COL_FIRE, full.getStep());
		assertNotEquals(Step.COL_LOAD, full.getStep());
	}

	@Test
	public void bluesRemainFallbackWorkWhenNothingCanBeLoadedOrShot()
	{
		Snapshot s = base().toBuilder().eggsRed(0).eggsGreen(0).inventory(Map.of(BaIds.EGG_RED, 1)).build();
		Instruction collect = planner.plan(s, SETTINGS);
		assertEquals(Step.COL_COLLECT, collect.getStep());
		assertEquals(BaIds.EGG_BLUE, collect.getTarget().getId());
		assertEquals(Tier.IDLE, collect.getTier());
		Instruction delete = planner.plan(s.toBuilder().inventory(Map.of(BaIds.EGG_BLUE, 28)).freeSlots(0).build(), SETTINGS);
		assertEquals(Step.COL_BLUE, delete.getStep());
		assertEquals(Tier.IDLE, delete.getTier());
		// Loaded eggs alone do not justify firing if no launcher has an in-range target.
		assertEquals(Step.COL_COLLECT, planner.plan(base().toBuilder().penance(List.of()).build(), SETTINGS).getStep());
	}

	@Test
	public void blueCleanupAlsoYieldsToUsefulCollectionOnOtherCalls()
	{
		Snapshot s = base().toBuilder().listenCall(BaCall.RED_EGG).inventory(Map.of(BaIds.EGG_BLUE, 2))
			.penance(List.of()).groundItems(List.of(new GroundItemView(BaIds.EGG_RED, ME.dx(-1), 1, false, 990))).build();
		Instruction ins = planner.plan(s, SETTINGS);
		assertEquals(Step.COL_COLLECT, ins.getStep());
		assertEquals(BaIds.EGG_RED, ins.getTarget().getId());
	}

	@Test
	public void staleCallsDoNotOpenANewBlueOpportunityAndOwedCallsStillLead()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 3)).build();
		assertEquals(Step.COL_WAIT_CALL, planner.plan(s.toBuilder().listenFresh(false).build(), SETTINGS).getStep());
		assertEquals(Step.CALL, planner.plan(s.toBuilder().shoutDone(false).shoutCall(BaCall.ACCURATE).build(), SETTINGS).getStep());
		assertEquals(Step.RETREAT, planner.plan(s.toBuilder().hp(5).build(), SETTINGS).getStep());
	}

	/** Penance all dead, cannon preloaded: spare eggs of every colour go (reported 2026-10-19: not only blue). */
	@Test
	public void queenPreparationClearsSpareEggsOfEveryColour()
	{
		Snapshot s = base().toBuilder().wave(10).penance(List.of()).inventory(Map.of(BaIds.EGG_BLUE, 2, BaIds.EGG_RED, 1))
			.fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
			.runnersKilled(WaveInfo.runnersTotal(10)).healersKilled(WaveInfo.healersTotal(10)).build();
		Instruction ins = planner.plan(s, SETTINGS);
		assertEquals(Step.COL_DISCARD, ins.getStep());
		assertTrue(ins.getItems().contains(BaIds.EGG_RED) && ins.getItems().contains(BaIds.EGG_BLUE));
		assertEquals(Step.COL_QUEEN_READY, planner.plan(s.toBuilder().inventory(Map.of()).build(), SETTINGS).getStep());
	}
}
