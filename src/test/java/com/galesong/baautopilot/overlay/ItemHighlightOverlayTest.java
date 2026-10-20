package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Planner;
import com.galesong.baautopilot.plan.Settings;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static com.galesong.baautopilot.overlay.ItemHighlightOverlay.Highlight.*;
import static org.junit.Assert.*;

public class ItemHighlightOverlayTest
{
	private static Snapshot base(Role role, BaCall call)
	{
		return Snapshot.builder().inArena(true).role(role).wave(8).tick(1000).waveTick(100)
			.playerTile(ArenaTiles.healerWait(8)).hp(99).maxHp(99).shoutDone(true)
			.listenCall(call).listenFresh(true).build();
	}

	private static Instruction action(Step step, int... items)
	{
		Set<Integer> ids = new java.util.HashSet<>();
		for (int item : items) ids.add(item);
		return Instruction.builder().step(step).items(ids).build();
	}

	@Test
	public void emergencyHealingKeepsTheFoodAlongsideTheVial()
	{
		Snapshot s = base(Role.HEALER, BaCall.POISON_WORMS).toBuilder()
			.inventory(Map.of(BaCall.POISON_WORMS.getItemId(), 8, BaIds.VIAL_FULL, 1))
			.teammates(List.of(new TeammateView("Hurt", 10, 99))).build();
		Instruction heal = new Planner().plan(s, Settings.builder().build());
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		assertFalse(heal.getItems().contains(BaCall.POISON_WORMS.getItemId()));
		assertEquals(CORRECT, ItemHighlightOverlay.highlight(BaCall.POISON_WORMS.getItemId(), heal, s, false));
		assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.VIAL_FULL, heal, s, false));
		assertEquals(NONE, ItemHighlightOverlay.highlight(BaCall.POISON_TOFU.getItemId(), heal, s, false));
		for (Step step : List.of(Step.HEAL_FILL_VIAL, Step.HEAL_TAKE_FOOD, Step.HEAL_SUPPORT, Step.RETREAT))
		{
			assertEquals(step.toString(), CORRECT,
				ItemHighlightOverlay.highlight(BaCall.POISON_WORMS.getItemId(), action(step), s, false));
		}
	}

	@Test
	public void everyDefenderCallMarksBothBadFoodsAcrossDropsAndOtherJobs()
	{
		for (BaCall call : List.of(BaCall.BAIT_WORMS, BaCall.BAIT_TOFU, BaCall.BAIT_CRACKERS))
		{
			Snapshot s = base(Role.DEFENDER, call);
			int example = BaIds.BAIT.stream().filter(id -> id != call.getItemId()).findFirst().get();
			for (Instruction ins : List.of(action(Step.DEF_DROP, example), action(Step.DEF_RELURE, example),
				action(Step.DEF_REPAIR, BaIds.LOGS), action(Step.DEF_HAMMER), action(Step.DEF_HOLD)))
			{
				for (int bait : BaIds.BAIT)
				{
					assertEquals(call + " " + ins.getStep() + " bait " + bait,
						bait == call.getItemId() ? GOOD_BAIT : BAD_BAIT,
						ItemHighlightOverlay.highlight(bait, ins, s, false));
				}
			}
			assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.LOGS, action(Step.DEF_REPAIR, BaIds.LOGS), s, false));
		}
	}

	@Test
	public void callChangesImmediatelyReclassifyAllBaitWithoutKeepingTheOldExample()
	{
		Snapshot s = base(Role.DEFENDER, BaCall.BAIT_WORMS);
		Instruction ins = action(Step.DEF_DROP, BaCall.BAIT_CRACKERS.getItemId());
		assertEquals(GOOD_BAIT, ItemHighlightOverlay.highlight(BaCall.BAIT_WORMS.getItemId(), ins, s, false));
		s = s.toBuilder().listenCall(BaCall.BAIT_CRACKERS).build();
		assertEquals(BAD_BAIT, ItemHighlightOverlay.highlight(BaCall.BAIT_WORMS.getItemId(), ins, s, false));
		assertEquals(GOOD_BAIT, ItemHighlightOverlay.highlight(BaCall.BAIT_CRACKERS.getItemId(), ins, s, false));
	}

	@Test
	public void staleMissingAndChangingCallsCannotAdvertiseOldFoodOrArrows()
	{
		for (BaCall call : List.of(BaCall.POISON_WORMS, BaCall.BAIT_TOFU, BaCall.CONTROLLED))
		{
			Snapshot fresh = base(call.getReceiver(), call);
			Instruction ins = action(Step.HEAL_TEAMMATE, BaIds.VIAL_FULL, call.getItemId());
			for (Snapshot s : List.of(fresh.toBuilder().listenFresh(false).build(), fresh.toBuilder().listenCall(null).build()))
			{
				assertEquals(NONE, ItemHighlightOverlay.highlight(call.getItemId(), ins, s, false));
				assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.VIAL_FULL, ins, s, false));
			}
			assertEquals(NONE, ItemHighlightOverlay.highlight(call.getItemId(), ins, fresh, true));
		}
	}

	@Test
	public void callsAndCollectorWorkKeepTheirOwnItemsAlongsideTheIncomingCall()
	{
		Snapshot healer = base(Role.HEALER, BaCall.POISON_MEAT);
		int horn = BaIds.HORNS.iterator().next();
		Instruction call = action(Step.CALL, horn);
		assertEquals(CALL, ItemHighlightOverlay.highlight(horn, call, healer, false));
		assertEquals(CORRECT, ItemHighlightOverlay.highlight(BaCall.POISON_MEAT.getItemId(), call, healer, false));
		Snapshot collector = base(Role.COLLECTOR, BaCall.GREEN_EGG);
		Instruction load = action(Step.COL_LOAD, BaIds.EGG_RED);
		assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.EGG_RED, load, collector, false));
		assertEquals(CORRECT, ItemHighlightOverlay.highlight(BaIds.EGG_GREEN, load, collector, false));
		assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.EGG_BLUE, action(Step.COL_BLUE, BaIds.EGG_BLUE),
			collector.toBuilder().listenFresh(false).build(), false));
		Snapshot attacker = base(Role.ATTACKER, BaCall.ACCURATE);
		assertEquals(CORRECT, ItemHighlightOverlay.highlight(BaCall.ACCURATE.getItemId(), action(Step.ATT_FIRE), attacker, false));
	}

	@Test
	public void leavingTheArenaOrEnteringQueenWorkClearsCallContext()
	{
		Snapshot s = base(Role.HEALER, BaCall.POISON_WORMS);
		Instruction queen = action(Step.QUEEN_DUNK, BaIds.EGG_YELLOW);
		assertEquals(NONE, ItemHighlightOverlay.highlight(BaCall.POISON_WORMS.getItemId(), queen, s.toBuilder().queenAlive(true).build(), false));
		assertEquals(ACTION, ItemHighlightOverlay.highlight(BaIds.EGG_YELLOW, queen, s.toBuilder().queenAlive(true).build(), false));
		assertEquals(NONE, ItemHighlightOverlay.highlight(BaCall.POISON_WORMS.getItemId(), queen, s.toBuilder().inArena(false).build(), false));
	}
}
