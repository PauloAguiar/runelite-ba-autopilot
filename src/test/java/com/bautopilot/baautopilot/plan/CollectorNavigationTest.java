package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.RoundLogic;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorNavigationTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 20, 44);
	private final Planner planner = new Planner();
	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(3).waveTick(100)
			.collectorOpeningComplete(true).listenCall(BaCall.RED_EGG).listenFresh(true).shoutDone(true)
			.ticksToCallChange(40).freeSlots(10).hp(77).maxHp(77).playerTile(ME).build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }
	private static GroundItemView egg(WorldPoint tile, int quantity, int age)
	{
		return new GroundItemView(BaIds.EGG_RED, tile, quantity, false, 1000 - age);
	}
	private static Snapshot remembered(Snapshot s, WorldPoint tile)
	{
		return s.toBuilder().lastTask("col.collect").lastStep(Step.COL_COLLECT)
			.lastActionTarget(Target.groundItem(BaIds.EGG_RED, tile)).lastTargetTile(tile).build();
	}

	@Test public void nearbyTargetHoldsUnderTheMouseButADistantTargetCanYield()
	{
		WorldPoint old = ME.dx(2), near = ME.dx(1);
		Snapshot s = base().toBuilder().groundItems(List.of(egg(old, 1, 10), egg(near, 1, 180))).build();
		assertEquals(old, plan(remembered(s, old)).getTarget().getTile());
		assertEquals(near, plan(remembered(s.toBuilder().groundItems(List.of(egg(near, 1, 180))).build(), old)).getTarget().getTile());
		WorldPoint far = ME.dx(14);
		assertEquals(near, plan(remembered(s.toBuilder().groundItems(List.of(egg(far, 1, 180), egg(near, 1, 1))).build(), far)).getTarget().getTile());
	}

	@Test public void ageCannotPullUsPastAFreshNearbyEgg()
	{
		Snapshot s = base().toBuilder().groundItems(List.of(egg(ME.dx(20), 1, 180), egg(ME.dx(1), 1, 1))).build();
		assertEquals(ME.dx(1), plan(s).getTarget().getTile());
	}

	@Test public void blueTargetAlsoYieldsWhenANewEggIsMuchNearer()
	{
		Snapshot s = base().toBuilder().listenCall(BaCall.BLUE_EGG).groundItems(List.of(
			new GroundItemView(BaIds.EGG_BLUE, ME.dx(8), 1, false, 990),
			new GroundItemView(BaIds.EGG_BLUE, ME.dx(2), 1, false, 990)))
			.lastTask("col.collect").lastStep(Step.COL_COLLECT).lastActionTarget(Target.groundItem(BaIds.EGG_BLUE, ME.dx(8))).build();
		assertEquals(ME.dx(2), plan(s).getTarget().getTile());
	}

	@Test public void callDeadlineIncludesRunningReactionAndEachPickup()
	{
		GroundItemView pile = egg(ME.dx(3), 8, 1);
		Snapshot s = base().toBuilder().ticksToCallChange(7).groundItems(List.of(pile)).build();
		assertEquals(2, CollectorPlanner.pickable(s, pile));
		assertTrue(plan(s).getHeadline(), plan(s).getHeadline().endsWith("×2"));
		Snapshot late = remembered(s.toBuilder().ticksToCallChange(5).build(), pile.getTile());
		assertNotEquals(Step.COL_COLLECT, plan(late).getStep());
		assertEquals(0, CollectorPlanner.pickable(late, pile));
	}

	@Test public void pickupQuantityIsLimitedByBothSpaceAndDespawn()
	{
		GroundItemView pile = egg(ME, 5, 194);
		Snapshot s = base().toBuilder().freeSlots(2).groundItems(List.of(pile)).build();
		assertEquals(2, CollectorPlanner.pickable(s, pile));
		assertTrue(plan(s).getHeadline().endsWith("×2"));
		assertEquals(0, CollectorPlanner.pickable(s.toBuilder().freeSlots(0).build(), pile));
		assertEquals(0, CollectorPlanner.pickable(s, egg(ME, 5, 197)));
	}

	@Test public void changedColourOrStaleCallReleasesThePreviousPile()
	{
		Snapshot s = remembered(base().toBuilder().groundItems(List.of(egg(ME, 1, 1))).build(), ME);
		assertNotEquals(Step.COL_COLLECT, plan(s.toBuilder().listenCall(BaCall.GREEN_EGG).build()).getStep());
		assertNotEquals(Step.COL_COLLECT, plan(s.toBuilder().listenFresh(false).build()).getStep());
	}

	@Test public void sharedAmmunitionUsesNearHopperEvenWhenOnlyFarCannonCanRescue()
	{
		WorldPoint near = ME.dy(-9), far = ME.dx(20).dy(-9);
		Snapshot s = base().toBuilder().listenCall(BaCall.BLUE_EGG).inventory(Map.of(BaIds.EGG_RED, 20))
			.objects(List.of(new ObjectView(BaIds.HOPPERS[0], near), new ObjectView(BaIds.HOPPERS[1], far),
				new ObjectView(BaIds.LAUNCHERS[0], near.dx(-1)), new ObjectView(BaIds.LAUNCHERS[1], far.dx(-1))))
			.penance(List.of(PenanceView.builder().npcIndex(2).type(PenanceType.RUNNER)
				.tile(far.dy(-14)).build())).build();
		assertEquals(Step.COL_LOAD, plan(s).getStep());
		assertEquals(near, plan(s).getTarget().getTile());
	}

	@Test public void distantRescueShotDoesNotDragCollectorAcrossArena()
	{
		WorldPoint cannon = ME.dx(20).dy(-10);
		Snapshot s = base().toBuilder().listenCall(BaCall.BLUE_EGG).eggsRed(5)
			.objects(List.of(new ObjectView(BaIds.LAUNCHERS[0], cannon)))
			.penance(List.of(PenanceView.builder().npcIndex(2).type(PenanceType.RUNNER).tile(cannon.dy(-14)).build())).build();
		assertNotEquals(Step.COL_FIRE, plan(s).getStep());
		assertEquals(Step.COL_FIRE, plan(s.toBuilder().playerTile(cannon.dy(1)).build()).getStep());
	}

	@Test public void openingMovesBeforeTheEggCallThenStopsReturningToAFixedSpot()
	{
		Snapshot s = base().toBuilder().collectorOpeningComplete(false).waveTick(2).listenCall(null).listenFresh(false)
			.playerTile(ArenaTiles.p(R, 33, 16)).build();
		assertEquals(Step.COL_POSITION, plan(s).getStep());
		assertEquals(ArenaTiles.collectorWestApproach(3), plan(s).getTarget().getTile());
		assertEquals(ArenaTiles.collectorBehindHill(3), plan(s.toBuilder().playerTile(ArenaTiles.collectorWestApproach(3)).build()).getTarget().getTile());
		Snapshot returned = s.toBuilder().collectorOpeningComplete(true).build();
		assertEquals(Target.Kind.NONE, plan(returned).getTarget().getKind());
		assertEquals(Target.Kind.NONE, plan(s.toBuilder().waveTick(60).build()).getTarget().getKind());
	}

	@Test public void reachingTheHillIsRememberedUntilTheNextWave()
	{
		RoundLogic logic = new RoundLogic();
		logic.tick(100, true, new RoundLogic.Hud() {
			public boolean visible(int id) { return id >>> 16 == Role.COLLECTOR.getHudGroup(); }
			public String text(int id) { return null; }
		}, Map.of(), 0, 0, 0);
		logic.observePosition(ArenaTiles.collectorBehindHill(3));
		logic.observePosition(ArenaTiles.p(R, 20, 25));
		assertTrue(logic.snapshot().build().isCollectorOpeningComplete());
		logic.chat(true, "---- Wave: 4 ----", false, 101);
		assertFalse(logic.snapshot().build().isCollectorOpeningComplete());
	}

	@Test public void fullInventoryDiscardsGreenForNearbyRedsAndPrefersBlueWhenAvailable()
	{
		Snapshot s = base().toBuilder().freeSlots(0).bagGreen(4).inventory(Map.of(BaIds.EGG_GREEN, 20))
			.groundItems(List.of(egg(ME.dx(1), 3, 1))).build();
		Instruction discard = plan(s);
		assertEquals(Step.COL_DISCARD, discard.getStep());
		assertEquals(Set.of(BaIds.EGG_GREEN), discard.getItems());
		assertEquals(Set.of(BaIds.EGG_BLUE), plan(s.toBuilder().inventory(Map.of(BaIds.EGG_GREEN, 20, BaIds.EGG_BLUE, 1)).build()).getItems());
		Snapshot room = s.toBuilder().freeSlots(1).inventory(Map.of(BaIds.EGG_GREEN, 19)).build();
		assertEquals(Step.COL_COLLECT, plan(room).getStep());
	}

	@Test public void greenDiscardNeedsInventoryEggsAndTimeToCollectRed()
	{
		Snapshot s = base().toBuilder().freeSlots(0).inventory(Map.of(BaIds.EGG_GREEN, 20))
			.groundItems(List.of(egg(ME.dx(1), 1, 1))).build();
		for (Snapshot noDiscard : List.of(s.toBuilder().freeSlots(3).build(), s.toBuilder().ticksToCallChange(4).build(),
			s.toBuilder().groundItems(List.of()).build(), s.toBuilder().inventory(Map.of()).bagGreen(4).build()))
			assertFalse(plan(noDiscard).getPriorities().stream().anyMatch(p -> "col.room.green".equals(p.getTask())));
	}

	@Test public void displaySeparatesInventoryAndBagColours()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.EGG_RED, 3, BaIds.EGG_GREEN, 8))
			.bagBlue(4).groundItems(List.of(egg(ME, 2, 1))).build();
		String detail = plan(s).getDetail();
		assertTrue(detail, detail.contains("Inventory: 3 red / 8 green / 0 blue; 10 free slots"));
		assertTrue(detail, detail.contains("Bag: 0 red / 0 green / 4 blue"));
	}
}
