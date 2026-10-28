package com.bautopilot.baautopilot.tutorial;

import static com.bautopilot.baautopilot.tutorial.Situations.TICK;
import static com.bautopilot.baautopilot.tutorial.Situations.arena;
import static com.bautopilot.baautopilot.tutorial.Situations.at;
import static com.bautopilot.baautopilot.tutorial.Situations.item;
import static com.bautopilot.baautopilot.tutorial.Situations.opening;
import static com.bautopilot.baautopilot.tutorial.Situations.penance;
import static com.bautopilot.baautopilot.tutorial.Tutorials.pilot;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.state.TeammateView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * The situations behind the tutorial pages' autopilot box: the real planner plans each one, so
 * the box shows what the plugin would. Pages are in tutorial-pages.json; this is the one part
 * that stays code. Keyed by tutorial and page index (from 0).
 */
final class TutorialSituations
{
	static TutorialSteps.Pilot forPage(Tutorial tutorial, int page)
	{
		switch (tutorial.name() + page)
		{
			case "COLLECTOR1":
				return pilot(Step.COL_COLLECT, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.RED_EGG) .groundItems(ImmutableList.of(item(BaIds.EGG_RED, 4, 22, 42, 3))));
			case "COLLECTOR2":
				return pilot(Step.CALL, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.RED_EGG).shoutCall(BaCall.ACCURATE).shoutDone(false));
			case "COLLECTOR3":
				return pilot(Step.COL_POSITION, opening(Role.COLLECTOR, 4, 18, 29));
			case "COLLECTOR4":
				return pilot(Step.COL_COLLECT, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.RED_EGG) .groundItems(ImmutableList.of(item(BaIds.EGG_RED, 4, 22, 42, 1), item(BaIds.EGG_GREEN, 4, 20, 41, 1), item(BaIds.EGG_RED, 4, 20, 41, 1).withUnderneath(true))));
			case "COLLECTOR5":
				return pilot(Step.COL_FOLLOW, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.RED_EGG) .inventory(ImmutableMap.of(ItemID.BARBASSAULT_COLL_BAG_01, 1, BaIds.EGG_RED, 3)).freeSlots(21).bagRed(2));
			case "COLLECTOR6":
				return pilot(Step.COL_LOAD, arena(Role.COLLECTOR, 4, 23, 33).listenCall(BaCall.BLUE_EGG) .inventory(ImmutableMap.of(BaIds.EGG_RED, 4, BaIds.EGG_GREEN, 2)).freeSlots(14));
			case "COLLECTOR7":
				return pilot(Step.COL_BLUE, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.GREEN_EGG) .inventory(ImmutableMap.of(BaIds.EGG_BLUE, 3)).freeSlots(2));
			case "COLLECTOR8":
				return pilot(Step.COL_FIRE, arena(Role.COLLECTOR, 4, 41, 33).listenCall(BaCall.BLUE_EGG).eggsRed(4) .penance(ImmutableList.of(penance(1, PenanceType.RUNNER, 4, 42, 26).toBuilder().previousTile(Situations.at(4, 42, 27)).build())));
			case "COLLECTOR9":
				return pilot(Step.COL_FOLLOW, arena(Role.COLLECTOR, 4, 21, 40).listenCall(BaCall.GREEN_EGG));
			case "COLLECTOR10":
				return pilot(Step.COL_LOAD, arena(Role.COLLECTOR, 10, 23, 33).listenCall(BaCall.RED_EGG) .inventory(ImmutableMap.of(BaIds.EGG_RED, 2)).freeSlots(22));
			case "COLLECTOR11":
				return pilot(Step.COL_DISCARD, arena(Role.COLLECTOR, 10, 21, 40).listenCall(BaCall.GREEN_EGG).eggsRed(1) .fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10)) .runnersKilled(WaveInfo.runnersTotal(10)).healersKilled(WaveInfo.healersTotal(10)) .inventory(ImmutableMap.of(BaIds.EGG_RED, 3, BaIds.EGG_GREEN, 2, BaIds.EGG_BLUE, 2)).freeSlots(15));
			case "COLLECTOR12":
				return pilot(Step.QUEEN_COLLECT, arena(Role.COLLECTOR, 10, 28, 25).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 2)) .groundItems(ImmutableList.of(item(BaIds.EGG_YELLOW, 10, 26, 27, 1))));
			case "COLLECTOR14":
				return pilot(Step.QUEEN_PASS, arena(Role.COLLECTOR, 10, 28, 25).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 5)).team(ImmutableMap.of("Healer", Role.HEALER)));
			case "COLLECTOR15":
				return pilot(Step.QUEEN_COLLECT, arena(Role.COLLECTOR, 10, 28, 25).queenAlive(true).queenYellowPassed(5) .inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 1)).team(ImmutableMap.of("Healer", Role.HEALER)) .groundItems(ImmutableList.of(item(BaIds.EGG_YELLOW, 10, 26, 27, 1))));
			case "COLLECTOR16":
				return pilot(Step.QUEEN_WAIT, arena(Role.COLLECTOR, 10, 28, 25).queenAlive(true).queenYellowPassed(5) .inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 3)).team(ImmutableMap.of("Healer", Role.HEALER)));
			case "COLLECTOR17":
				return pilot(Step.QUEEN_LOAD_ONE, arena(Role.COLLECTOR, 10, 22, 32).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 3)));
			case "COLLECTOR18":
				return pilot(Step.QUEEN_LOAD_ONE, arena(Role.COLLECTOR, 10, 22, 32).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 2)));
			case "HEALER1":
				return pilot(Step.HEAL_WAIT_CALL, stocked(4).listenCall(null).listenFresh(false) .penance(ImmutableList.of(unfed(1))));
			case "HEALER2":
				return pilot(Step.CALL, stocked(4).penance(ImmutableList.of(fed(1, 4))).shoutCall(BaCall.BAIT_TOFU).shoutDone(false));
			case "HEALER3":
				return pilot(Step.HEAL_TAKE_FOOD, opening(Role.HEALER, 4, 35, 16).inventory(ImmutableMap.of(BaIds.VIAL_FULL, 1)));
			case "HEALER4":
				return pilot(Step.HEAL_WAIT_CALL, opening(Role.HEALER, 4, 35, 16).inventory(foods()));
			case "HEALER5":
				return pilot(Step.HEAL_FEED, stocked(4).penance(ImmutableList.of(fed(1, 4), unfed(2))));
			case "HEALER6":
				return pilot(Step.HEAL_FEED, stocked(4).penance(ImmutableList.of(fed(1, 40), fed(2, 4))));
			case "HEALER7":
				return pilot(Step.HEAL_SUPPORT, lastHealers().penance(ImmutableList.of(fed(1, 4), fed(2, 6))) .teammates(team(62, 80)));
			case "HEALER8":
				return pilot(Step.HEAL_TAKE_FOOD, stocked(9).penance(ImmutableList.of(fed(1, 4), fed(2, 6))) .inventory(ImmutableMap.of(BaCall.POISON_WORMS.getItemId(), 1, BaIds.VIAL_FULL, 1)));
			case "HEALER9":
				return pilot(Step.HEAL_TEAMMATE, lastHealers().penance(ImmutableList.of(fed(1, 4), fed(2, 6))) .teammates(team(42, 90)));
			case "HEALER10":
				return pilot(Step.RETREAT, stocked(4).hp(14).penance(ImmutableList.of(fed(1, 4))));
			case "HEALER11":
				return pilot(Step.QUEEN_DUNK, arena(Role.HEALER, 10, 19, 18).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 2, BaIds.VIAL_FULL, 1)));
			case "HEALER12":
				return pilot(Step.HEAL_FILL_VIAL, arena(Role.HEALER, 10, 27, 15).queenAlive(true) .inventory(ImmutableMap.of(ItemID.BARBASSAULT_VIAL_04_HALF, 1)) .teammates(team(85, 90)));
			case "ATTACKER1":
				return pilot(Step.ATT_WAIT_CALL, armed(4).listenCall(null).listenFresh(false) .penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))));
			case "ATTACKER2":
				return pilot(Step.CALL, armed(4) .penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))).shoutCall(BaCall.RED_EGG).shoutDone(false));
			case "ATTACKER3":
				return pilot(Step.ATT_GET_ARROWS, opening(Role.ATTACKER, 4, 33, 16));
			case "ATTACKER4":
				return pilot(Step.ATT_POSITION, opening(Role.ATTACKER, 4, 30, 18).inventory(allArrows()));
			case "ATTACKER5":
				return pilot(Step.ATT_EQUIP, armed(4).ammoId(BaCall.CONTROLLED.getItemId()) .penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))));
			case "ATTACKER6":
				return pilot(Step.ATT_ATTACK, armed(4) .penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46), penance(2, PenanceType.RANGER, 4, 18, 45))));
			case "ATTACKER7":
				return pilot(Step.ATT_HOLD, armed(4).ticksToCallChange(2) .penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))));
			case "ATTACKER8":
				return pilot(Step.ATT_SUPPORT, armed(4).cleared(ImmutableSet.of(PenanceType.FIGHTER, PenanceType.RANGER)) .fighterRangerKills(WaveInfo.fightersTotal(4) + WaveInfo.rangersTotal(4)) .penance(ImmutableList.of(penance(3, PenanceType.RUNNER, 4, 36, 44))));
			case "ATTACKER9":
				return pilot(Step.RETREAT, armed(4).hp(24).maxHp(99));
			case "ATTACKER10":
				return pilot(Step.QUEEN_SPIKE, arena(Role.ATTACKER, 10, 27, 17).queenAlive(true) .inventory(ImmutableMap.of(BaIds.SPIKES, 6, BaIds.EGG_POISONED, 1)));
			case "ATTACKER11":
				return pilot(Step.QUEEN_WAIT, arena(Role.ATTACKER, 10, 27, 16).queenAlive(true) .inventory(ImmutableMap.of(BaIds.SPIKES, 8)));
			case "DEFENDER2":
				return pilot(Step.DEF_WAIT_CALL, ready().listenCall(null).listenFresh(false));
			case "DEFENDER3":
				return pilot(Step.CALL, ready().shoutCall(BaCall.POISON_WORMS).shoutDone(false));
			case "DEFENDER4":
				return pilot(Step.DEF_TAKE_BAIT, opening(Role.DEFENDER, 4, 34, 16));
			case "DEFENDER7":
				return pilot(Step.DEF_WATCH, beside().trapCharges(2).runnersKilled(WaveInfo.runnersTotal(4) - 1) .penance(ImmutableList.of(penance(1, PenanceType.RUNNER, 4, 35, 45))));
			case "DEFENDER8":
				return pilot(Step.DEF_DROP, opening(Role.DEFENDER, 4, 45, 35).inventory(baits()).listenCall(BaCall.BAIT_CRACKERS) .listenFresh(true).waveTick(12));
			case "DEFENDER9":
				return pilot(Step.DEF_HAMMER, patterned().playerTile(at(4, 31, 44)).groundItems(tools()));
			case "DEFENDER10":
				return pilot(Step.DEF_SERVICE, equipped().trapCharges(2) .penance(ImmutableList.of(penance(1, PenanceType.RUNNER, 4, 44, 37))));
			case "DEFENDER11":
				return pilot(Step.DEF_REPAIR, equipped().trapCharges(0).objects(brokenTrap()));
			case "DEFENDER12":
				return pilot(Step.DEF_RELURE, beside().trapCharges(2).runnersKilled(WaveInfo.runnersTotal(4) - 1) .penance(ImmutableList.of(penance(1, PenanceType.RUNNER, 4, 45, 27).toBuilder() .previousTile(at(4, 45, 28)).build())));
			case "DEFENDER13":
				return pilot(Step.DEF_DROP, beside().trapCharges(2).groundItems(trapAndTrail()));
			case "DEFENDER14":
				return pilot(Step.DEF_SUPPORT, beside().cleared(ImmutableSet.of(PenanceType.RUNNER)) .runnersKilled(WaveInfo.runnersTotal(4)));
			case "DEFENDER15":
				return pilot(Step.QUEEN_LAVA, arena(Role.DEFENDER, 10, 17, 20).queenAlive(true) .inventory(ImmutableMap.of(BaIds.EGG_SPIKED, 1)));
			case "DEFENDER16":
				return pilot(Step.QUEEN_WAIT, arena(Role.DEFENDER, 10, 27, 16).queenAlive(true));
			default:
				return null;
		}
	}

	static ImmutableMap<Integer, Integer> foods()
	{
		return ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 4, BaCall.POISON_WORMS.getItemId(), 4,
			BaCall.POISON_MEAT.getItemId(), 4, BaIds.VIAL_FULL, 1);
	}

	static Snapshot.SnapshotBuilder stocked(int wave)
	{
		return arena(Role.HEALER, wave, 42, 42).listenCall(BaCall.POISON_WORMS).inventory(foods());
	}

	static Snapshot.SnapshotBuilder lastHealers()
	{
		return stocked(4).healersKilled(WaveInfo.healersTotal(4) - 2)
			.killsByType(ImmutableMap.of(PenanceType.HEALER, WaveInfo.healersTotal(4) - 2));
	}

	static PenanceView unfed(int order)
	{
		return penance(order, PenanceType.HEALER, 4, 40 + 2 * order, 44);
	}

	static PenanceView fed(int order, int ticksAgo)
	{
		return unfed(order).toBuilder().foods(1).foodsThisPeriod(1).lastFoodTick(TICK - ticksAgo).build();
	}

	static ImmutableList<TeammateView> team(int first, int second)
	{
		return ImmutableList.of(new TeammateView("Attacker", first * 99 / 100, 99),
			new TeammateView("Collector", second * 99 / 100, 99));
	}

	static ImmutableMap<Integer, Integer> allArrows()
	{
		ImmutableMap.Builder<Integer, Integer> b = ImmutableMap.builder();
		BaIds.ARROWS.forEach(id -> b.put(id, 50));
		return b.build();
	}

	static Snapshot.SnapshotBuilder armed(int wave)
	{
		return arena(Role.ATTACKER, wave, 21, 43).listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId()).inventory(allArrows());
	}

	static ImmutableMap<Integer, Integer> baits()
	{
		return ImmutableMap.of(BaCall.BAIT_TOFU.getItemId(), 9, BaCall.BAIT_CRACKERS.getItemId(), 9, BaCall.BAIT_WORMS.getItemId(), 9);
	}

	static Snapshot.SnapshotBuilder ready()
	{
		return arena(Role.DEFENDER, 4, 45, 35).listenCall(BaCall.BAIT_CRACKERS).inventory(baits());
	}

	static Snapshot.SnapshotBuilder patterned()
	{
		Map<WorldPoint, Integer> good = new java.util.HashMap<>();
		Map<WorldPoint, Integer> bad = new java.util.HashMap<>();
		for (ArenaTiles.FoodSpot f : ArenaTiles.defenderPattern(4))
		{
			(f.isGood() ? good : bad).put(f.getTile(), f.getCount());
		}
		return ready().goodBaitLanded(ImmutableMap.copyOf(good)).badBaitLanded(ImmutableMap.copyOf(bad))
			.groundItems(trapBait());
	}

	static Snapshot.SnapshotBuilder equipped()
	{
		Map<Integer, Integer> inv = new java.util.HashMap<>(baits());
		inv.put(BaIds.LOGS, 1);
		inv.put(BaIds.HAMMER, 1);
		return patterned().inventory(ImmutableMap.copyOf(inv)).hasHammer(true).logsTaken(2);
	}

	static Snapshot.SnapshotBuilder beside()
	{
		return equipped().playerTile(ArenaTiles.defenderService(4));
	}

	static ImmutableList<GroundItemView> trapBait()
	{
		WorldPoint food = ArenaTiles.trapFood(4);
		return ImmutableList.of(new GroundItemView(BaCall.BAIT_CRACKERS.getItemId(), food, 4, true, Situations.TICK - 30));
	}

	static ImmutableList<GroundItemView> trapAndTrail()
	{
		List<GroundItemView> out = new ArrayList<>(trapBait());
		ArenaTiles.defenderPattern(4).stream().filter(f -> f.getName().equals("Trail")).findFirst()
			.ifPresent(f -> out.add(new GroundItemView(BaCall.BAIT_TOFU.getItemId(), f.getTile(), 1, false, Situations.TICK - 30)));
		return ImmutableList.copyOf(out);
	}

	static ImmutableList<GroundItemView> tools()
	{
		List<GroundItemView> out = new ArrayList<>(trapBait());
		out.add(item(BaIds.LOGS, 4, 29, 46, 1));
		out.add(item(BaIds.LOGS, 4, 28, 47, 1));
		out.add(item(BaIds.HAMMER, 4, 32, 42, 1));
		return ImmutableList.copyOf(out);
	}

	static ImmutableList<ObjectView> brokenTrap()
	{
		List<ObjectView> out = new ArrayList<>();
		for (ObjectView o : Situations.objects(4))
		{
			out.add(o.getId() == BaIds.TRAP_FULL && o.getTile().equals(at(4, 45, 34)) ? new ObjectView(BaIds.TRAP_BROKEN, o.getTile()) : o);
		}
		return ImmutableList.copyOf(out);
	}

	private TutorialSituations()
	{
	}
}
