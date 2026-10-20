package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.HealCodeSet;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskMemoryTest
{
	private static final WorldPoint ME = ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, 40, 35);
	private static final Settings SETTINGS = Settings.builder().build();
	private final Planner planner = new Planner();

	private static PenanceView healer(int id, int order, boolean fed)
	{
		return PenanceView.builder().npcIndex(id).order(order).type(PenanceType.HEALER).tile(ME.dy(10))
			.foods(fed ? 1 : 0).foodsThisPeriod(fed ? 1 : 0).lastFoodTick(fed ? 100 : -1).build();
	}

	private static Snapshot healerSnapshot()
	{
		return Snapshot.builder().tick(100).inArena(true).role(Role.HEALER).wave(10).waveTick(15)
			.playerTile(ME).hp(99).maxHp(99).freeSlots(5).shoutDone(true).listenFresh(true)
			.listenCall(BaCall.POISON_TOFU).inventory(Map.of(BaCall.POISON_TOFU.getItemId(), 20, BaIds.VIAL_FULL, 1))
			.objects(List.of(new ObjectView(BaIds.HEALER_MACHINE, ME.dy(-10))))
			.penance(List.of(healer(7, 1, false), healer(8, 2, false))).build();
	}

	private static Snapshot next(Snapshot s, Instruction previous)
	{
		return s.toBuilder().tick(s.getTick() + 1).waveTick(s.getWaveTick() + 1)
			.tasks(previous.getTasks()).lastTask(previous.getTask()).lastTargetNpc(previous.getTarget().getId()).build();
	}

	@Test
	public void coverageResumesAfterCallsHealingAndRestocking()
	{
		Snapshot s = healerSnapshot();
		Instruction first = planner.plan(s, SETTINGS);
		assertEquals("heal.cover", first.getTask());
		assertEquals(7, first.getTarget().getId());

		s = next(s, first).toBuilder().shoutDone(false).shoutCall(BaCall.BAIT_TOFU).build();
		Instruction call = planner.plan(s, SETTINGS);
		assertEquals(Step.CALL, call.getStep());
		assertEquals(TaskMemory.Status.RUNNING, call.getTasks().getEntries().get("call").getStatus());
		assertEquals(TaskMemory.Status.SUSPENDED, call.getTasks().getEntries().get("heal.cover").getStatus());
		assertEquals(7, call.getTasks().target("heal.cover").getId());

		s = next(s, call).toBuilder().shoutDone(true).teammates(List.of(new TeammateView("Hurt", 5, 99))).build();
		Instruction emergency = planner.plan(s, SETTINGS);
		assertEquals(Step.HEAL_TEAMMATE, emergency.getStep());
		assertEquals(TaskMemory.Status.SUSPENDED, emergency.getTasks().getEntries().get("heal.cover").getStatus());

		// The NPC list can arrive in a different order after the interruption.
		s = next(s, emergency).toBuilder().teammates(List.of()).penance(List.of(healer(8, 2, false), healer(7, 1, false)))
			.inventory(Map.of(BaIds.VIAL_FULL, 1)).build();
		Instruction stock = planner.plan(s, SETTINGS);
		assertEquals(Step.HEAL_TAKE_FOOD, stock.getStep());
		assertEquals(TaskMemory.Status.BLOCKED, stock.getTasks().getEntries().get("heal.cover").getStatus());
		assertEquals("Waiting for correct food", stock.getTasks().getEntries().get("heal.cover").getReason());

		s = next(s, stock).toBuilder().inventory(healerSnapshot().getInventory()).build();
		Instruction resume = planner.plan(s, SETTINGS);
		assertEquals(Step.HEAL_FEED, resume.getStep());
		assertEquals(7, resume.getTarget().getId());
		assertFalse(resume.getTasks().getEntries().containsKey("heal.stock"));
		assertEquals(first.getTasks().getEntries().get("heal.cover").getStartedTick(),
			resume.getTasks().getEntries().get("heal.cover").getStartedTick());
	}

	@Test
	public void confirmationAdvancesCoverageAndStaleCallsBlockIt()
	{
		Snapshot s = healerSnapshot();
		Instruction first = planner.plan(s, SETTINGS);
		// A recommendation alone is not evidence that food landed.
		assertEquals(7, planner.plan(next(s, first), SETTINGS).getTarget().getId());
		Instruction movingNpc = planner.plan(next(s, first).toBuilder()
			.penance(List.of(healer(7, 1, false).toBuilder().tile(ME.dy(12)).build(), healer(8, 2, false))).build(), SETTINGS);
		assertEquals("The NPC walking is not our progress", 100,
			movingNpc.getTasks().getEntries().get("heal.cover").getLastProgressTick());
		s = next(s, first).toBuilder().penance(List.of(healer(7, 1, true), healer(8, 2, false)))
			.listenFresh(false).build();
		Instruction blocked = planner.plan(s, SETTINGS);
		assertNotEquals(Step.HEAL_FEED, blocked.getStep());
		assertEquals(TaskMemory.Status.BLOCKED, blocked.getTasks().getEntries().get("heal.cover").getStatus());
		assertEquals(s.getTick(), blocked.getTasks().getEntries().get("heal.cover").getLastProgressTick());
		Instruction fresh = planner.plan(next(s, blocked).toBuilder().listenFresh(true).build(), SETTINGS);
		assertEquals(8, fresh.getTarget().getId());
		Instruction complete = planner.plan(next(s, fresh).toBuilder().listenFresh(true)
			.penance(List.of(healer(7, 1, true), healer(8, 2, true))).build(), SETTINGS);
		assertFalse(complete.getTasks().getEntries().containsKey("heal.cover"));
	}

	@Test
	public void guideDelaysNeverBlockCoverageOrWeakPoisonRefreshes()
	{
		for (HealCodeSet set : Arrays.asList(HealCodeSet.REGULAR, HealCodeSet.ONE_OS, HealCodeSet.TWO_OS))
		{
			for (int wave : new int[]{6, 7, 9, 10})
			{
				Instruction first = planner.plan(healerSnapshot().toBuilder().wave(wave)
					.penance(List.of(healer(7, 1, false))).build(), SETTINGS.toBuilder().healCodeSet(set).build());
				assertEquals(set + " wave " + wave, Step.HEAL_FEED, first.getStep());
				assertEquals("heal.cover", first.getTask());
			}
		}
		Snapshot tagged = healerSnapshot().toBuilder().penance(List.of(healer(7, 1, true))).build();
		assertNotEquals(Step.HEAL_FEED, planner.plan(tagged.toBuilder().waveTick(40).build(), SETTINGS).getStep());
		tagged = tagged.toBuilder().penance(List.of(healer(7, 1, true).toBuilder().lastFoodTick(70).build())).build();
		Instruction early = planner.plan(tagged, SETTINGS);
		assertEquals(Step.HEAL_FEED, early.getStep());
		assertTrue(early.getDetail().contains("refresh once to 4"));
		Instruction due = planner.plan(tagged.toBuilder().waveTick(35).build(), SETTINGS);
		assertEquals(Step.HEAL_FEED, due.getStep());
		assertTrue(due.getDetail().contains("refresh once to 4"));
	}

	@Test
	public void invalidCoverageTargetIsReplacedAndLateSpawnsAreCovered()
	{
		Snapshot s = healerSnapshot();
		Instruction first = planner.plan(s, SETTINGS);
		Instruction replacement = planner.plan(next(s, first).toBuilder().penance(List.of(healer(8, 2, false))).build(), SETTINGS);
		assertEquals(8, replacement.getTarget().getId());
		Snapshot later = next(s, replacement).toBuilder().callPeriod(2).waveTick(130)
			.penance(List.of(healer(8, 2, true), healer(9, 6, false))).build();
		Instruction reserve = planner.plan(later, SETTINGS);
		assertEquals("heal.cover", reserve.getTask());
		assertEquals(9, reserve.getTarget().getId());
	}

	@Test
	public void interruptedTripResumesWhileCompletionIsObservedInTheBackground()
	{
		Snapshot s = healerSnapshot().toBuilder().role(Role.COLLECTOR).wave(3).penance(List.of())
			.inventory(Map.of(BaIds.EGG_RED, 3)).build();
		Candidate trip = Candidate.task(Tier.MAINTENANCE, "col.load", Instruction.builder().step(Step.COL_LOAD)
			.target(Target.tile(ME.dy(-15), "Hopper")).build());
		Candidate nearer = Candidate.task(Tier.MAINTENANCE, "other", Instruction.builder().step(Step.COL_COLLECT)
			.target(Target.tile(ME.dy(1), "Egg")).build());
		TaskMemory started = TaskMemory.empty().observe(s, SETTINGS).advance(s, SETTINGS, Chooser.choose(s, List.of(trip)));
		s = s.toBuilder().tick(101).tasks(started).inventory(Map.of(BaIds.EGG_RED, 1)).build();
		TaskMemory interrupted = started.observe(s, SETTINGS).advance(s, SETTINGS, Instruction.builder().step(Step.CALL).build());
		assertEquals(TaskMemory.Status.SUSPENDED, interrupted.getEntries().get("col.load").getStatus());
		assertEquals(101, interrupted.getEntries().get("col.load").getLastProgressTick());
		assertEquals("col.load", Chooser.choose(s.toBuilder().tasks(interrupted).lastTask("call").build(), List.of(nearer, trip)).getTask());
		// Empty inventory completes the trip even if the top instruction was still a call.
		TaskMemory complete = interrupted.observe(s.toBuilder().inventory(Map.of()).build(), SETTINGS);
		assertFalse(complete.getEntries().containsKey("col.load"));
		assertEquals("other", Chooser.choose(s.toBuilder().tasks(complete).lastTask("call").build(), List.of(nearer, trip)).getTask());
	}

	@Test
	public void fullHopperBlocksTripAndRoleWaveOrLobbyClearsCommitment()
	{
		Snapshot s = healerSnapshot();
		TaskMemory tasks = planner.plan(s, SETTINGS).getTasks();
		assertTrue(tasks.observe(s.toBuilder().inArena(false).build(), SETTINGS).getEntries().isEmpty());
		assertEquals(-1, tasks.observe(s.toBuilder().wave(9).build(), SETTINGS).lastActive("heal.cover"));
		TaskMemory collector = tasks.observe(s.toBuilder().role(Role.COLLECTOR).inventory(Map.of(BaIds.EGG_RED, 3))
			.eggsRed(5).build(), SETTINGS);
		assertFalse(collector.getEntries().containsKey("heal.cover"));
		assertEquals(TaskMemory.Status.BLOCKED, collector.getEntries().get("col.load").getStatus());
	}
}
