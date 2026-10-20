package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Planner;
import com.galesong.baautopilot.plan.Settings;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.plan.TileMark;
import java.awt.Color;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.RoundLogic;
import com.galesong.baautopilot.dev.RecordedRound;
import com.galesong.baautopilot.dev.RoundReplay;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneOverlayTest
{
	@Test public void completedBadFoodMarksKeepTheirPurpleMeaning()
	{
		for (boolean done : new boolean[]{false, true})
		{
			Color color = SceneOverlay.markColour(new TileMark(ArenaTiles.trapFood(3), "Trail", done, false));
			assertEquals(Palette.BAD_BAIT.getRed(), color.getRed());
			assertEquals(Palette.BAD_BAIT.getGreen(), color.getGreen());
			assertEquals(Palette.BAD_BAIT.getBlue(), color.getBlue());
		}
	}

	@Test public void badFoodInstructionAndCarriedOpeningCallSharePurpleActionColour() throws Exception
	{
		RoundReplay.Frame trail = RoundReplay.run(RecordedRound.read(Path.of(
			"src/test/resources/recordings/2026-10-18_18-02-54-wave2.jsonl"))).stream().filter(f -> f.snapshot.getTick() == 3581).findFirst().get();
		assertEquals(Step.DEF_DROP, trail.instruction.getStep());
		assertTrue(trail.instruction.isBadBait());
		assertEquals(Palette.BAD_BAIT, Palette.action(trail.instruction, Color.GREEN));
		Instruction call = new Planner().plan(trail.snapshot.toBuilder().shoutCall(BaCall.POISON_WORMS)
			.shoutDone(false).callPeriod(0).build(), Settings.builder().build());
		assertEquals(Step.CALL, call.getStep());
		assertEquals(Step.DEF_DROP, call.getThen());
		assertEquals(Palette.BAD_BAIT, Palette.action(call, Color.GREEN));
	}

	@Test
	public void latestCapturedStackReachesTheWorldPickupBadge() throws Exception
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(Path.of(
			"src/test/resources/recordings/2026-10-18_15-32-31-wave1.jsonl")));
		for (int tick : new int[]{799, 800, 803, 804})
		{
			RoundReplay.Frame frame = frames.stream().filter(f -> f.snapshot.getTick() == tick).findFirst().get();
			assertEquals("Pick up Green eggs ×2", frame.instruction.getHeadline());
			assertEquals(Step.COL_COLLECT, frame.instruction.getStep());
			assertEquals(2, frame.instruction.getPickupCount());
			assertEquals("tick " + tick, "PICK UP ×2", SceneOverlay.targetTag(frame.instruction));
		}
	}

	@Test
	public void pickupBadgeCountsOnlySelectedColourAndUpdatesForCapacityTimeAndFirstCall()
	{
		net.runelite.api.coords.WorldPoint tile = ArenaTiles.attackerWait(3);
		Snapshot s = Snapshot.builder().tick(1000).wave(3).waveTick(10).inArena(true).role(Role.COLLECTOR)
			.playerTile(tile).hp(99).maxHp(99).freeSlots(10).listenCall(BaCall.GREEN_EGG).listenFresh(true)
			.shoutDone(true).ticksToCallChange(40).groundItems(RoundLogic.mergeGround(List.of(
				new GroundItemView(BaIds.EGG_GREEN, tile, 1, false, 990),
				new GroundItemView(BaIds.EGG_GREEN, tile, 2, false, 990),
				new GroundItemView(BaIds.EGG_RED, tile, 5, false, 990)))).build();
		Planner planner = new Planner();
		Settings settings = Settings.builder().build();
		assertEquals("PICK UP ×3", SceneOverlay.targetTag(planner.plan(s, settings)));
		assertEquals("PICK UP", SceneOverlay.targetTag(planner.plan(s.toBuilder().freeSlots(1).build(), settings)));
		assertEquals("PICK UP ×2", SceneOverlay.targetTag(planner.plan(s.toBuilder().ticksToCallChange(5).build(), settings)));
		Instruction call = planner.plan(s.toBuilder().shoutCall(BaCall.ACCURATE).shoutDone(false).shoutSettled(true).build(), settings);
		assertEquals(Step.CALL, call.getStep());
		assertEquals(Step.COL_COLLECT, call.getThen());
		assertEquals("PICK UP ×3", SceneOverlay.targetTag(call));
	}

	@Test
	public void queenPickupBadgeUsesRemainingBatchCount()
	{
		net.runelite.api.coords.WorldPoint tile = ArenaTiles.queenCollectorWatch();
		Snapshot s = Snapshot.builder().tick(1000).wave(10).waveTick(200).inArena(true).role(Role.COLLECTOR)
			.queenAlive(true).playerTile(tile).hp(99).maxHp(99).freeSlots(10).shoutDone(true)
			.groundItems(List.of(new GroundItemView(BaIds.EGG_YELLOW, tile, 3, false, 990))).build();
		Planner planner = new Planner();
		Settings settings = Settings.builder().build();
		assertEquals("PICK UP ×3", SceneOverlay.targetTag(planner.plan(s, settings)));
		Instruction last = planner.plan(s.toBuilder().inventory(Map.of(BaIds.EGG_YELLOW, 4)).build(), settings);
		assertEquals(1, last.getPickupCount());
		assertEquals("PICK UP", SceneOverlay.targetTag(last));
	}

	private static PenanceView enemy(int id, PenanceType type)
	{
		return PenanceView.builder().npcIndex(id).type(type).tile(ArenaTiles.attackerWait(8)).build();
	}

	private static Snapshot base()
	{
		return Snapshot.builder().inArena(true).role(Role.ATTACKER).wave(8).interactingNpc(5).lastTargetNpc(6)
			.penance(List.of(enemy(5, PenanceType.FIGHTER), enemy(6, PenanceType.RANGER))).build();
	}

	@Test
	public void missingOrStaleCallsWarnOverTheCurrentEnemyDuringOtherInstructions()
	{
		for (Step step : List.of(Step.ATT_WAIT_CALL, Step.CALL, Step.ATT_GET_ARROWS, Step.RETREAT))
		{
			Instruction ins = Instruction.of(step, "", "");
			for (Snapshot s : List.of(base(), base().toBuilder().listenCall(BaCall.ACCURATE).listenFresh(false).build()))
			{
				List<Target> warnings = SceneOverlay.attackWarnings(s, ins, false);
				assertEquals(step.toString(), 1, warnings.size());
				assertEquals(5, warnings.get(0).getId());
			}
		}
	}

	@Test
	public void freshCallClearsTheWarningAndAnEarlyHudFlipSuppressesOldAttackBadges()
	{
		Snapshot s = base().toBuilder().listenCall(BaCall.ACCURATE).listenFresh(true).build();
		Instruction ins = Instruction.builder().step(Step.ATT_ATTACK)
			.target(Target.npc(6, s.getPenance().get(1).getTile())).build();
		assertTrue(SceneOverlay.attackWarnings(s, ins, false).isEmpty());
		List<Target> warning = SceneOverlay.attackWarnings(s, ins, true);
		assertEquals(2, warning.size());
		assertEquals(5, warning.get(0).getId());
		assertTrue("The old ATTACK target must get the warning instead", warning.contains(ins.getTarget()));
	}

	@Test
	public void fallbackUsesTheLastLiveTargetButNeverDeadUnseenOrOtherRoleNpcs()
	{
		Instruction wait = Instruction.of(Step.ATT_WAIT_CALL, "", "");
		Snapshot s = base().toBuilder().interactingNpc(-1).build();
		assertEquals(6, SceneOverlay.attackWarnings(s, wait, false).get(0).getId());
		for (PenanceView p : List.of(enemy(6, PenanceType.RANGER).toBuilder().dying(true).build(),
			enemy(6, PenanceType.RANGER).toBuilder().unseen(true).build(), enemy(6, PenanceType.HEALER)))
		{
			assertTrue(SceneOverlay.attackWarnings(s.toBuilder().penance(List.of(p)).build(), wait, false).isEmpty());
		}
		assertTrue(SceneOverlay.attackWarnings(s.toBuilder().role(Role.HEALER).build(), wait, false).isEmpty());
		assertTrue(SceneOverlay.attackWarnings(s.toBuilder().inArena(false).build(), wait, false).isEmpty());
		assertTrue(SceneOverlay.attackWarnings(s.toBuilder().queenAlive(true).build(), wait, false).isEmpty());
	}
}
