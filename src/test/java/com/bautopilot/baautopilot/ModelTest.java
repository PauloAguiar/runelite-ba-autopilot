package com.bautopilot.baautopilot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.HealCode;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.state.CallClock;
import com.bautopilot.baautopilot.state.HealerLedger;
import org.junit.Test;

public class ModelTest
{
	@Test
	public void parsesHudCalls()
	{
		assertEquals(BaCall.CONTROLLED, BaCall.parse(Role.ATTACKER, "Controlled/Bullet/Wind"));
		assertEquals(BaCall.DEFENSIVE, BaCall.parse(Role.ATTACKER, "Defensive/"));
		assertEquals(BaCall.RED_EGG, BaCall.parse(Role.COLLECTOR, "Red egg"));
		assertEquals(BaCall.GREEN_EGG, BaCall.parse(Role.COLLECTOR, "<col=ffffff>Green egg</col>"));
		assertEquals(BaCall.POISON_WORMS, BaCall.parse(Role.HEALER, "Pois. Worms"));
		assertEquals(BaCall.POISON_MEAT, BaCall.parse(Role.HEALER, "Poison meat"));
		assertEquals(BaCall.BAIT_CRACKERS, BaCall.parse(Role.DEFENDER, "Crackers"));
		assertEquals(BaCall.BAIT_TOFU, BaCall.parse(Role.DEFENDER, "Tofu"));
		assertEquals(BaCall.POISON_TOFU, BaCall.parse(Role.HEALER, "Pois. Tofu"));
		assertNull(BaCall.parse(Role.ATTACKER, "- - -"));
		assertNull(BaCall.parse(Role.COLLECTOR, ""));
		assertNull(BaCall.parse(Role.DEFENDER, "Meat"));
	}

	@Test
	public void partnersPairUp()
	{
		assertEquals(Role.COLLECTOR, Role.ATTACKER.partner());
		assertEquals(Role.ATTACKER, Role.COLLECTOR.partner());
		assertEquals(Role.HEALER, Role.DEFENDER.partner());
		assertEquals(Role.DEFENDER, Role.HEALER.partner());
	}

	@Test
	public void parsesHealCodes()
	{
		HealCode w4 = HealCode.regular(4);
		assertEquals(2, w4.getPeriods().size());
		assertEquals(2, w4.feed(0, 1).getCount());
		assertEquals(5, w4.feed(0, 2).getCount());
		assertEquals(1, w4.feed(0, 3).getCount());
		assertEquals(24, w4.feed(0, 3).getAfterSeconds());
		assertEquals(7, w4.feed(1, 4).getCount());
		assertNull(w4.feed(0, 4));
		assertNull(w4.feed(2, 1));

		HealCode w6 = HealCode.regular(6);
		assertEquals(2, w6.feed(0, 1).getCount());
		assertEquals(18, w6.feed(0, 1).getAfterSeconds());
		assertEquals(HealCode.SPAM, w6.feed(1, 6).getCount());
		assertEquals(1, w6.getNotes().size());

		HealCode w9 = HealCode.regular(9);
		assertEquals(3, w9.getPeriods().size());
		assertEquals(54, w9.feed(1, 1).getBeforeSeconds());
		assertEquals(75, w9.feed(2, 7).getBeforeSeconds());

		for (int wave = 1; wave <= 10; wave++)
		{
			assertFalse("wave " + wave, HealCode.regular(wave).getPeriods().isEmpty());
		}
	}

	@Test
	public void poisonMath()
	{
		assertEquals(54, WaveInfo.fullPoisonDamage());
		assertEquals(4, WaveInfo.poisonHit(1));
		assertEquals(4, WaveInfo.poisonHit(5));
		assertEquals(3, WaveInfo.poisonHit(6));
		assertEquals(1, WaveInfo.poisonHit(20));
		assertEquals(0, WaveInfo.poisonHit(21));
	}

	@Test
	public void attackerKillOrder()
	{
		assertEquals(PenanceType.FIGHTER, WaveInfo.attackerNext(1, 0));
		assertEquals(PenanceType.RANGER, WaveInfo.attackerNext(1, 1));
		assertEquals(PenanceType.FIGHTER, WaveInfo.attackerNext(2, 1));
		assertEquals(PenanceType.RANGER, WaveInfo.attackerNext(2, 2));
		assertEquals(PenanceType.FIGHTER, WaveInfo.attackerNext(2, 3));
		assertEquals(PenanceType.RANGER, WaveInfo.attackerNext(3, 0));
		assertEquals(PenanceType.FIGHTER, WaveInfo.attackerNext(7, 0));
	}

	@Test
	public void defenderPatternTotals()
	{
		assertEquals(8, ArenaTiles.patternGood(1));
		assertEquals(3, ArenaTiles.patternBad(1));
		assertEquals(8, ArenaTiles.patternGood(10));
		assertEquals(2, ArenaTiles.patternBad(10));
		assertEquals(ArenaTiles.REGION_WAVE_10, ArenaTiles.trapFood(10).getRegionID());
		assertEquals(ArenaTiles.REGION_WAVES_1_9, ArenaTiles.trapFood(3).getRegionID());
	}

	@Test
	public void callClockRunsAndResyncs()
	{
		CallClock clock = new CallClock();
		assertEquals(-1, clock.ticksToChange(0));
		clock.start(102 - CallClock.START_OFFSET);
		assertEquals(0, clock.ticksInto(101));
		assertEquals(50, clock.ticksToChange(102));
		assertFalse(clock.tick(151));
		assertTrue(clock.tick(152));
		assertEquals(1, clock.period());

		// the HUD flipped three ticks early: start the new period there
		assertTrue(clock.callFlipped(199));
		assertEquals(2, clock.period());
		assertEquals(50, clock.ticksToChange(199));

		// a flip just after a boundary only re-anchors
		clock.tick(249);
		assertEquals(3, clock.period());
		assertFalse(clock.callFlipped(251));
		assertEquals(3, clock.period());
		assertEquals(50, clock.ticksToChange(251));
	}

	@Test
	public void teamListNamesAndRoleIcons()
	{
		java.util.Map<Integer, String> texts = new java.util.HashMap<>();
		java.util.Map<Integer, Integer> models = new java.util.HashMap<>();
		int name = net.runelite.api.gameval.InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME;
		int icon = net.runelite.api.gameval.InterfaceID.BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_ICON;
		texts.put(name, "Leader: Zezima");
		texts.put(name + 1, "Player 1: Lynx Titan");
		texts.put(name + 2, "-----");
		models.put(icon, 20563);
		models.put(icon + 1, 20566);
		com.bautopilot.baautopilot.state.TeamRoster roster = new com.bautopilot.baautopilot.state.TeamRoster();
		roster.read(new com.bautopilot.baautopilot.state.RoundLogic.Hud()
		{
			public boolean visible(int c)
			{
				return texts.containsKey(c);
			}

			public String text(int c)
			{
				return texts.get(c);
			}

			public int model(int c)
			{
				return models.getOrDefault(c, -1);
			}
		});
		assertEquals(Role.COLLECTOR, roster.roles().get("Zezima"));
		assertEquals(Role.DEFENDER, roster.roles().get("Lynx Titan"));
		assertEquals(2, roster.roles().size());
	}

	@Test
	public void inventoryStepsAreTheOnesThatStartWithAnItemClick()
	{
		java.util.Set<com.bautopilot.baautopilot.plan.Step> inv = java.util.EnumSet.noneOf(com.bautopilot.baautopilot.plan.Step.class);
		for (com.bautopilot.baautopilot.plan.Step st : com.bautopilot.baautopilot.plan.Step.values())
		{
			if (st.isInventory())
			{
				inv.add(st);
			}
		}
		assertTrue(inv.contains(com.bautopilot.baautopilot.plan.Step.CALL));
		assertTrue(inv.contains(com.bautopilot.baautopilot.plan.Step.HEAL_FEED));
		assertTrue(inv.contains(com.bautopilot.baautopilot.plan.Step.QUEEN_DUNK));
		assertFalse(inv.contains(com.bautopilot.baautopilot.plan.Step.QUEEN_LAVA));
		assertFalse(inv.contains(com.bautopilot.baautopilot.plan.Step.COL_LOAD));
		assertFalse(inv.contains(com.bautopilot.baautopilot.plan.Step.COL_COLLECT));
		assertFalse(inv.contains(com.bautopilot.baautopilot.plan.Step.ATT_ATTACK));
	}

	@Test
	public void queenPhaseOutlastsHerLeavingTheScene()
	{
		com.bautopilot.baautopilot.state.RoundLogic logic = new com.bautopilot.baautopilot.state.RoundLogic();
		assertFalse(logic.queenPhase(false));
		logic.chat(true, "---- Wave: 10 ----", false, 1);
		assertTrue(logic.queenPhase(true));
		assertTrue(logic.queenPhase(false));
		logic.chat(true, "---- Wave: 1 ----", false, 900);
		assertFalse(logic.queenPhase(false));
	}

	@Test
	public void ledgerOrdersBySpawnThenIndex()
	{
		HealerLedger ledger = new HealerLedger();
		ledger.spawned(40);
		ledger.spawned(12);
		ledger.endTick();
		ledger.spawned(5);
		ledger.endTick();
		assertEquals(1, ledger.get(12).getOrder());
		assertEquals(2, ledger.get(40).getOrder());
		assertEquals(3, ledger.get(5).getOrder());

		ledger.fed(40, 10, 0);
		ledger.fed(40, 12, 0);
		ledger.fed(40, 60, 1);
		assertEquals(3, ledger.get(40).getFoods());
		assertEquals(2, ledger.get(40).foodsIn(0));
		assertEquals(1, ledger.get(40).foodsIn(1));
		assertEquals(60, ledger.get(40).getLastFoodTick());
	}

	@Test
	public void waveTableInitialPlusReserves()
	{
		// OSRS Wiki wave table: wave 8 fighters 7+0, rangers 5+3; wave 9 runners 5+4; wave 1 healers 2+0
		assertEquals(7, WaveInfo.initial(com.bautopilot.baautopilot.model.PenanceType.FIGHTER, 8));
		assertEquals(5, WaveInfo.initial(com.bautopilot.baautopilot.model.PenanceType.RANGER, 8));
		assertEquals(8, WaveInfo.total(com.bautopilot.baautopilot.model.PenanceType.RANGER, 8));
		assertEquals(5, WaveInfo.initial(com.bautopilot.baautopilot.model.PenanceType.RUNNER, 9));
		assertEquals(9, WaveInfo.total(com.bautopilot.baautopilot.model.PenanceType.RUNNER, 9));
		for (int w = 1; w <= 10; w++)
		{
			for (com.bautopilot.baautopilot.model.PenanceType type : new com.bautopilot.baautopilot.model.PenanceType[]{
				com.bautopilot.baautopilot.model.PenanceType.FIGHTER, com.bautopilot.baautopilot.model.PenanceType.RANGER,
				com.bautopilot.baautopilot.model.PenanceType.RUNNER, com.bautopilot.baautopilot.model.PenanceType.HEALER})
			{
				assertTrue(WaveInfo.initial(type, w) <= WaveInfo.total(type, w));
			}
		}
	}
}
