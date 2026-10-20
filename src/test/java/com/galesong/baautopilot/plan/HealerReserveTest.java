package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class HealerReserveTest
{
	private static final WorldPoint ME = ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, 50, 50);
	private static final Settings SETTINGS = Settings.builder().build();
	private final Planner planner = new Planner();

	private static PenanceView healer(int order, int hpBar)
	{
		return PenanceView.builder().npcIndex(order).order(order).type(PenanceType.HEALER).tile(ME.dy(1))
			.healthRatio(hpBar).healthScale(30).foods(1).lastFoodTick(995).build();
	}

	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).inArena(true).wave(5).waveTick(40).ticksToCallChange(10)
			.role(Role.HEALER).hp(77).maxHp(77).playerTile(ME).listenCall(BaCall.POISON_MEAT)
			.listenFresh(true).shoutDone(true).inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 9))
			.penance(List.of(healer(1, 10), healer(2, 23), healer(3, 25), healer(4, 28))).build();
	}

	@Test
	public void finishTheLowestHpHealerRatherThanWaitAtFourPoison()
	{
		Instruction ins = planner.plan(base(), SETTINGS);
		assertEquals(Step.HEAL_FEED, ins.getStep());
		assertEquals("heal.reserve", ins.getTask());
		assertEquals(1, ins.getTarget().getId());
		assertTrue(ins.getHeadline().startsWith("Finish healer #1"));
		assertTrue(ins.getItems().contains(BaCall.POISON_MEAT.getItemId()));
		assertTrue(ins.getDetail().contains("1 reserve"));
	}

	@Test
	public void firstTagsAndWeakPoisonPreemptTheFinishTarget()
	{
		Instruction first = planner.plan(base(), SETTINGS);
		for (int fed : new int[]{-1, 960})
		{
			Snapshot next = base().toBuilder().tasks(first.getTasks()).lastTask(first.getTask()).lastTargetNpc(1)
				.penance(List.of(healer(1, 10), healer(2, 30).toBuilder().lastFoodTick(fed).build())).build();
			Instruction chosen = planner.plan(next, SETTINGS);
			assertEquals(2, chosen.getTarget().getId());
			assertEquals(fed < 0 ? "heal.cover" : "heal.feed", chosen.getTask());
		}
	}

	@Test
	public void savesFoodForFutureTagsAndCurrentHealersPoisonWillNotFinish()
	{
		Snapshot early = base().toBuilder().wave(8).penance(List.of(healer(1, 28), healer(2, 28))).build();
		// Five still to spawn, and these two need another feed later: keep seven.
		assertEquals(7, HealerPlanner.foodToKeep(early, early.alive(PenanceType.HEALER)));
		assertEquals("heal.reserve", planner.plan(early.toBuilder().inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 8)).build(), SETTINGS).getTask());
		assertNotEquals("heal.reserve", planner.plan(early.toBuilder().inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 7)).build(), SETTINGS).getTask());
	}

	@Test
	public void stopsOnceTheRequiredKillOpensTheLastReserveSlot()
	{
		Snapshot next = base().toBuilder().healersKilled(1).penance(base().getPenance().subList(1, 4)).build();
		assertEquals(0, HealerPlanner.reservesWaiting(next));
		assertNotEquals(Step.HEAL_FEED, planner.plan(next, SETTINGS).getStep());
		// The reserve itself is always tagged when it appears.
		Instruction spawn = planner.plan(next.toBuilder().penance(List.of(healer(2, 20), healer(5, 30).toBuilder().lastFoodTick(-1).build())).build(), SETTINGS);
		assertEquals("heal.cover", spawn.getTask());
		assertEquals(5, spawn.getTarget().getId());
	}

	@Test
	public void multipleReservesNeedMultipleKillsAndDyingIsNotCountedTwice()
	{
		Snapshot s = base().toBuilder().wave(7).healersKilled(1).penance(List.of(
			healer(1, 0).toBuilder().dying(true).build(), healer(2, 10), healer(3, 20), healer(4, 25))).build();
		assertEquals(2, HealerPlanner.reservesWaiting(s));
		assertEquals("heal.reserve", planner.plan(s, SETTINGS).getTask());
		assertEquals(2, planner.plan(s, SETTINGS).getTarget().getId());
	}

	@Test
	public void observedReserveSpawnsAlsoProveTheirSlotsOpened()
	{
		Snapshot s = base().toBuilder().penance(List.of(healer(5, 20))).build();
		assertEquals(0, HealerPlanner.reservesWaiting(s));
		assertNotEquals("heal.reserve", planner.plan(s, SETTINGS).getTask());
		// Two reserves are already observed on wave 7, and another slot will open next hit.
		Snapshot missedKill = base().toBuilder().wave(7).healersKilled(1)
			.penance(List.of(healer(3, 20), healer(6, 2))).build();
		assertEquals(0, HealerPlanner.reservesWaiting(missedKill));
	}

	@Test
	public void noSpareFeedingForWavesWithoutReservesOrAnImminentPoisonKill()
	{
		for (int wave : new int[]{1, 2})
			assertNotEquals("heal.reserve", planner.plan(base().toBuilder().wave(wave).build(), SETTINGS).getTask());
		Snapshot dyingSoon = base().toBuilder().penance(List.of(healer(1, 2), healer(2, 25))).build();
		assertEquals(0, HealerPlanner.reservesWaiting(dyingSoon));
		assertNotEquals("heal.reserve", planner.plan(dyingSoon, SETTINGS).getTask());
	}

	@Test
	public void extraFeedingDoesNotChaseUnseenOrDistantHealersOrCrossTheCallChange()
	{
		PenanceView target = healer(1, 10);
		assertNotEquals("heal.reserve", planner.plan(base().toBuilder().penance(List.of())
			.unseenPenance(List.of(target.toBuilder().unseen(true).build())).build(), SETTINGS).getTask());
		assertNotEquals("heal.reserve", planner.plan(base().toBuilder().penance(List.of(target.toBuilder().tile(ME.dy(9)).build())).build(), SETTINGS).getTask());
		assertNotEquals("heal.reserve", planner.plan(base().toBuilder().ticksToCallChange(3).build(), SETTINGS).getTask());
		assertNotEquals("heal.reserve", planner.plan(base().toBuilder().listenFresh(false).build(), SETTINGS).getTask());
	}

	@Test
	public void smallHealthChangesKeepTheFinishTargetButALargeDifferenceReleasesIt()
	{
		Instruction first = planner.plan(base().toBuilder().penance(List.of(healer(1, 10), healer(2, 11))).build(), SETTINGS);
		Snapshot next = base().toBuilder().lastTask(first.getTask()).lastTargetNpc(1).tasks(first.getTasks())
			.penance(List.of(healer(1, 10), healer(2, 9))).build();
		assertEquals(1, planner.plan(next, SETTINGS).getTarget().getId());
		assertEquals(2, planner.plan(next.toBuilder().penance(List.of(healer(1, 20), healer(2, 7))).build(), SETTINGS).getTarget().getId());
	}

	@Test
	public void callsAndEmergencyHealingStillInterruptExtraFood()
	{
		assertEquals(Step.CALL, planner.plan(base().toBuilder().shoutCall(BaCall.BAIT_CRACKERS).shoutDone(false).build(), SETTINGS).getStep());
		Snapshot urgent = base().toBuilder().inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 9, BaIds.VIAL_FULL, 1))
			.teammates(List.of(new TeammateView("Hurt", 10, 99))).build();
		assertEquals(Step.HEAL_TEAMMATE, planner.plan(urgent, SETTINGS).getStep());
	}

	@Test
	public void finishFocusSurvivesTheCallInterruptionAndClearsWhenItsSlotOpens()
	{
		Instruction started = planner.plan(base(), SETTINGS);
		Snapshot call = base().toBuilder().tasks(started.getTasks()).lastTask(started.getTask()).lastTargetNpc(1)
			.listenFresh(false).build();
		Instruction interrupted = planner.plan(call, SETTINGS);
		assertEquals(TaskMemory.Status.BLOCKED, interrupted.getTasks().getEntries().get("heal.reserve").getStatus());
		Snapshot resumed = base().toBuilder().tasks(interrupted.getTasks()).lastTask("call").lastTargetNpc(-1)
			.penance(List.of(healer(1, 10), healer(2, 9))).build();
		assertEquals(1, planner.plan(resumed, SETTINGS).getTarget().getId());
		Instruction complete = planner.plan(resumed.toBuilder().healersKilled(1).build(), SETTINGS);
		assertFalse(complete.getTasks().getEntries().containsKey("heal.reserve"));
	}
}
