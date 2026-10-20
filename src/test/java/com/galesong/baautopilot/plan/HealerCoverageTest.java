package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.HealCodeSet;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HealerCoverageTest
{
	private static final WorldPoint ME = ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, 50, 50);
	private final Planner planner = new Planner();

	private static PenanceView healer(int id, int order, int fedTick)
	{
		return PenanceView.builder().npcIndex(id).order(order).type(PenanceType.HEALER).tile(ME.dy(2))
			.lastFoodTick(fedTick).foods(fedTick < 0 ? 0 : 1).foodsThisPeriod(0).build();
	}

	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).wave(8).waveTick(110).callPeriod(2).inArena(true).role(Role.HEALER)
			.playerTile(ME).hp(99).maxHp(99).freeSlots(20).listenFresh(true).shoutDone(true)
			.listenCall(BaCall.POISON_MEAT).inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 20)).build();
	}

	@Test
	public void healthyUnpoisonedHealerPreemptsAWoundedCommittedCoverageTarget()
	{
		Snapshot wounded = base().toBuilder().penance(List.of(healer(1, 1, -1).toBuilder().healthRatio(3).healthScale(30).build())).build();
		Instruction first = planner.plan(wounded, Settings.builder().build());
		Snapshot newcomer = wounded.toBuilder().tick(1001).tasks(first.getTasks()).lastTargetNpc(1).lastTask("heal.cover")
			.penance(List.of(wounded.getPenance().get(0), healer(2, 2, -1))).build();
		assertEquals(2, planner.plan(newcomer, Settings.builder().build()).getTarget().getId());
	}

	@Test
	public void weakestPoisonPreemptsPreviousTargetVisibilityAndEveryGuideSetting()
	{
		for (HealCodeSet set : HealCodeSet.values())
		{
			for (int strength = 0; strength < 3; strength++)
			{
				PenanceView weak = healer(2, 2, strength == 0 ? 890 : strength == 1 ? 920 : 945).toBuilder().unseen(true).build();
				Snapshot s = base().toBuilder().penance(List.of(healer(1, 1, 970)))
					.unseenPenance(List.of(weak)).lastTargetNpc(1).lastTask("heal.feed").build();
				assertEquals(strength, weak.nextPoisonHit(s.getTick()));
				assertEquals(set + " poison " + strength, 2, planner.plan(s, Settings.builder().healCodeSet(set).build()).getTarget().getId());
			}
		}
	}

	@Test
	public void equalUrgencyKeepsItsTargetButActualPoisonEvidenceReleasesIt()
	{
		PenanceView a = healer(1, 1, 960);
		PenanceView b = healer(2, 2, 960);
		Snapshot s = base().toBuilder().penance(List.of(a, b)).lastTargetNpc(2).build();
		assertEquals(2, planner.plan(s, Settings.builder().build()).getTarget().getId());
		s = s.toBuilder().penance(List.of(a, b.toBuilder().lastPoisonDamage(4).lastPoisonTick(1000).build())).build();
		assertEquals(1, planner.plan(s, Settings.builder().build()).getTarget().getId());
	}

	@Test
	public void codeCountsAndSpamEntriesNeverForceFullPoisonFeedsWithoutBlockedReserves()
	{
		for (HealCodeSet set : HealCodeSet.values())
		{
			for (int wave = 1; wave <= 10; wave++)
			{
				for (int period = 0; period < 3; period++)
				{
					Snapshot s = base().toBuilder().wave(wave).callPeriod(period).waveTick(period * 50 + 40)
						.healersKilled(WaveInfo.healersTotal(wave) - WaveInfo.healersInitial(wave))
						.penance(List.of(healer(1, 1, 990), healer(2, 2, 990), healer(3, 3, 990))).lastTargetNpc(3).build();
					assertEquals(4, s.getPenance().get(2).nextPoisonHit(s.getTick()));
					Instruction ins = planner.plan(s, Settings.builder().healCodeSet(set).build());
					assertNotEquals(set + " wave " + wave + " period " + period, Step.HEAL_FEED, ins.getStep());
				}
			}
		}
	}

	@Test
	public void unseenUntaggedHealerPreemptsEvenADueCodeRefresh()
	{
		for (HealCodeSet set : HealCodeSet.values())
		{
			Snapshot s = base().toBuilder().penance(List.of(healer(1, 1, 960)))
				.unseenPenance(List.of(healer(9, 7, -1).toBuilder().unseen(true).build())).lastTargetNpc(1).lastTask("heal.feed")
				.team(Map.of("Partner", Role.HEALER, "Me", Role.HEALER)).selfName("Me").build();
			Instruction initial = planner.plan(s, Settings.builder().healCodeSet(set).build());
			assertEquals("heal.cover", initial.getTask());
			assertEquals(9, initial.getTarget().getId());
			assertTrue(initial.getHeadline().contains("out of view"));
		}
	}

	@Test
	public void oneConfirmedRefreshStopsAndANewSpawnTakesOver()
	{
		Snapshot s = base().toBuilder().healersKilled(2).penance(List.of(healer(3, 3, 960))).build();
		Settings settings = Settings.builder().build();
		Instruction refresh = planner.plan(s, settings);
		assertEquals(Step.HEAL_FEED, refresh.getStep());
		assertEquals(3, refresh.getTarget().getId());
		Snapshot confirmed = s.toBuilder().tick(1001).lastTargetNpc(3).lastTask(refresh.getTask()).tasks(refresh.getTasks())
			.penance(List.of(healer(3, 3, 1001).toBuilder().foods(2).foodsThisPeriod(1).build())).build();
		assertNotEquals(Step.HEAL_FEED, planner.plan(confirmed, settings).getStep());
		Instruction next = planner.plan(confirmed.toBuilder().penance(List.of(healer(3, 3, 1001), healer(9, 7, -1))).build(), settings);
		assertEquals("heal.cover", next.getTask());
		assertEquals(9, next.getTarget().getId());
	}

	@Test
	public void enoughFoodForUpcomingFirstTagsDoesNotTriggerATopupTrip()
	{
		Snapshot s = base().toBuilder().wave(6).inventory(Map.of(BaCall.POISON_MEAT.getItemId(), 6))
			.objects(List.of(new ObjectView(com.galesong.baautopilot.model.BaIds.HEALER_MACHINE, ME.dy(-25))))
			.penance(List.of(healer(1, 1, 995), healer(2, 2, 995))).build();
		Instruction ins = planner.plan(s, Settings.builder().build());
		assertEquals(Step.HEAL_SUPPORT, ins.getStep());
	}
}
