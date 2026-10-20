package com.galesong.baautopilot.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.RoundLogic;
import com.galesong.baautopilot.state.Snapshot;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Eggs a left-click takes come first; one under another item (right-click, Take) only wins when the
 * left-clickable one is much further away (reported 2026-10-19).
 */
public class CollectorLeftClickTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 21, 40);
	private final Planner planner = new Planner();
	private final Settings settings = Settings.builder().build();

	private static Snapshot with(GroundItemView... eggs)
	{
		return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(4).waveTick(120)
			.callPeriod(2).ticksToCallChange(30).listenCall(BaCall.RED_EGG).listenFresh(true).shoutDone(true)
			.collectorOpeningComplete(true).playerTile(ME).hp(99).maxHp(99).freeSlots(20)
			.groundItems(List.of(eggs)).build();
	}

	private static GroundItemView red(int dx, boolean underneath)
	{
		return new GroundItemView(BaIds.EGG_RED, ME.dx(dx), 1, false, 990, underneath);
	}

	@Test
	public void aLeftClickableEggBeatsASlightlyNearerOneUnderAnotherItem()
	{
		Instruction ins = planner.plan(with(red(1, true), red(-3, false)), settings);
		assertEquals(Step.COL_COLLECT, ins.getStep());
		assertEquals(ME.dx(-3), ins.getTarget().getTile());
		assertFalse(ins.isRightClick());
	}

	@Test
	public void aMuchNearerEggUnderAnotherItemWinsAndSaysToRightClick()
	{
		Instruction ins = planner.plan(with(red(1, true), red(-10, false)), settings);
		assertEquals(ME.dx(1), ins.getTarget().getTile());
		assertTrue(ins.isRightClick());
		assertTrue(ins.getHeadline().contains("right-click"));
	}

	@Test
	public void mergingAPileKeepsItLeftClickableWhenAnyPartIsOnTop()
	{
		List<GroundItemView> merged = RoundLogic.mergeGround(List.of(red(1, true), red(1, false)));
		assertEquals(1, merged.size());
		assertEquals(2, merged.get(0).getQuantity());
		assertFalse(merged.get(0).isUnderneath());
	}
}
