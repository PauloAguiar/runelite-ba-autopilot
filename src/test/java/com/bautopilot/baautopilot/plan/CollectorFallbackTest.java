package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorFallbackTest
{
	private static final WorldPoint ME = ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, 20, 44);
	private final Planner planner = new Planner();
	private static Snapshot base(BaCall call)
	{
		return Snapshot.builder().tick(1000).wave(4).waveTick(100).inArena(true).role(Role.COLLECTOR)
			.collectorOpeningComplete(true).playerTile(ME).hp(77).maxHp(77).shoutDone(true)
			.listenCall(call).listenFresh(true).ticksToCallChange(40).freeSlots(10)
			.groundItems(List.of(new GroundItemView(call.getItemId(), ME.dx(10), 1, false, 990))).build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }

	@Test public void waitingForFutureRedsCannotBeatAReachableRedAlreadyOnTheGround()
	{
		Instruction decision = plan(base(BaCall.RED_EGG).toBuilder().inventory(Map.of(BaIds.EGG_RED, 4)).build());
		assertEquals(Step.COL_COLLECT, decision.getStep());
		assertTrue(decision.getPriorities().stream().filter(p -> "col.follow".equals(p.getTask()))
			.allMatch(p -> p.getScore() < decision.getScore() && p.getScoreTerms().get("visible egg before waiting") < 0));
	}

	@Test public void negativePickupScoresStillBeatWaitingWhenNoOtherWorkIsWorthMore()
	{
		for (BaCall call : List.of(BaCall.BLUE_EGG, BaCall.GREEN_EGG))
		{
			Snapshot s = base(call);
			WorldPoint pile = s.getGroundItems().get(0).getTile();
			s = s.toBuilder().penance(List.of(PenanceView.builder().npcIndex(1).type(PenanceType.HEALER).tile(pile).build())).build();
			Instruction decision = plan(s);
			assertEquals(call.name(), Step.COL_COLLECT, decision.getStep());
			assertTrue(decision.getScore() < 0);
			assertEquals(pile, decision.getTarget().getTile());
		}
	}

	@Test public void nearbyPilesStillBeatDistantOnesAndScoresRemainExplainable()
	{
		for (BaCall call : List.of(BaCall.RED_EGG, BaCall.GREEN_EGG, BaCall.BLUE_EGG))
		{
			Snapshot s = base(call).toBuilder().groundItems(List.of(
				new GroundItemView(call.getItemId(), ME.dx(10), 1, false, 990),
				new GroundItemView(call.getItemId(), ME.dx(2), 1, false, 990))).build();
			Instruction decision = plan(s);
			assertEquals(Step.COL_COLLECT, decision.getStep());
			assertEquals(ME.dx(2), decision.getTarget().getTile());
			for (PriorityLine line : decision.getPriorities())
			{
				assertTrue(Double.isFinite(line.getScore()));
				assertEquals(line.getScore(), line.getScoreTerms().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
			}
		}
	}

	@Test public void wrongColourStaleCallLatePickupsDistanceAndFullInventoryStillBlockCollection()
	{
		Snapshot s = base(BaCall.RED_EGG);
		for (Snapshot blocked : List.of(s.toBuilder().listenCall(BaCall.GREEN_EGG).build(), s.toBuilder().listenFresh(false).build(),
			s.toBuilder().ticksToCallChange(5).build(), s.toBuilder().freeSlots(0).build(),
			s.toBuilder().groundItems(List.of(new GroundItemView(BaIds.EGG_RED, ME.dx(13), 1, false, 990))).build()))
			assertNotEquals(Step.COL_COLLECT, plan(blocked).getStep());
		assertEquals(Step.COL_FOLLOW, plan(s.toBuilder().groundItems(List.of()).build()).getStep());
	}

	@Test public void waveTenBlueOnlyCountsAsUsefulWhenItEnablesAnEmptyCannon()
	{
		Snapshot s = base(BaCall.BLUE_EGG).toBuilder().wave(10).groundItems(List.of(
			new GroundItemView(BaIds.EGG_BLUE, ME.dx(1), 5, false, 990))).build();
		Instruction first = plan(s);
		assertEquals(Step.COL_COLLECT, first.getStep());
		assertEquals(1, first.getPickupCount());
		for (Snapshot supplied : List.of(s.toBuilder().eggsRed(1).build(), s.toBuilder().eggsBlue(1).build(),
			s.toBuilder().inventory(Map.of(BaIds.EGG_BLUE, 1)).build(), s.toBuilder().bagRed(1).build(), s.toBuilder().bagBlue(1).build()))
			assertNotEquals(Step.COL_COLLECT, plan(supplied).getStep());
	}
}
