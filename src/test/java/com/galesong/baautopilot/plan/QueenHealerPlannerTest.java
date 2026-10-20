package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueenHealerPlannerTest
{
	private static final WorldPoint SPRING = new WorldPoint(1877, 5409, 0);
	private static final WorldPoint CRATER = new WorldPoint(1897, 5425, 0);
	private final Planner planner = new Planner();

	private Snapshot base()
	{
		return Snapshot.builder().tick(1000).wave(10).waveTick(300).inArena(true).role(Role.HEALER)
			.queenAlive(true).shoutDone(true).hp(77).maxHp(77).playerTile(SPRING.dx(1))
			.inventory(Map.of(BaIds.VIAL_FULL, 1)).teammates(List.of(new TeammateView("Attacker", 99, 99)))
			.playerTiles(Map.of("Attacker", SPRING.dx(2)))
			.objects(List.of(new ObjectView(BaIds.SPRING, SPRING), new ObjectView(BaIds.POISON_CRATERS[0], CRATER),
				new ObjectView(BaIds.HEALER_MACHINE, SPRING.dx(15))))
			.team(Map.of("Attacker", Role.ATTACKER)).build();
	}

	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }

	private Snapshot after(Snapshot s, Instruction previous)
	{
		return s.toBuilder().tick(s.getTick() + 1).lastTask(previous.getTask()).lastStep(previous.getStep())
			.lastActionTarget(previous.getTarget()).lastTargetTile(previous.getTarget().getTile()).tasks(previous.getTasks()).build();
	}

	@Test
	public void healthyFillerTopUpsNeverReceiveAContinuationBonus()
	{
		Snapshot filler = base().toBuilder().teammates(List.of(new TeammateView("Attacker", 95, 99))).build();
		Instruction heal = plan(filler);
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		Instruction again = plan(after(filler, heal));
		assertEquals(Step.HEAL_TEAMMATE, again.getStep());
		assertEquals(0, again.getScoreTerms().get("continue current action"), .001);
		assertEquals(Step.QUEEN_DUNK, plan(after(filler, heal).toBuilder()
			.inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1)).build()).getStep());
	}

	@Test
	public void routineVialReadinessYieldsToAnEggInsteadOfHoldingItsTrip()
	{
		Snapshot filler = base().toBuilder()
			.inventory(Map.of(net.runelite.api.gameval.ItemID.BARBASSAULT_VIAL_04_3Q, 1)).build();
		Instruction refill = plan(filler);
		assertEquals(Step.HEAL_FILL_VIAL, refill.getStep());
		assertEquals(0, plan(after(filler, refill)).getScoreTerms().get("continue current action"), .001);
		assertEquals(Step.QUEEN_DUNK, plan(after(filler, refill).toBuilder()
			.inventory(Map.of(net.runelite.api.gameval.ItemID.BARBASSAULT_VIAL_04_3Q, 1, BaIds.EGG_YELLOW, 1)).build()).getStep());
	}

	@Test
	public void absoluteHpMattersMoreThanItsFractionAndSafeHpDoesNotBlockDunking()
	{
		for (int max : new int[]{77, 99})
		{
			Snapshot s = base().toBuilder().hp(47).maxHp(max).playerTile(CRATER.dx(2))
				.inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1)).build();
			assertEquals(Step.QUEEN_DUNK, plan(s).getStep());
			assertEquals(Step.RETREAT, plan(s.toBuilder().hp(29).build()).getStep());
		}
	}

	@Test
	public void aSmallHpDropDoesNotReverseAnEggTripButDangerDoes()
	{
		Snapshot s = base().toBuilder().hp(47).inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1)).build();
		Instruction dunk = plan(s);
		assertEquals(Step.QUEEN_DUNK, dunk.getStep());
		Snapshot lower = s.toBuilder().hp(45).build();
		assertEquals("Without action history the close-to-spring top-up wins", Step.RETREAT, plan(lower).getStep());
		Instruction held = plan(after(lower, dunk));
		assertEquals(Step.QUEEN_DUNK, held.getStep());
		assertTrue(held.getScoreTerms().get("continue current action") > 0);
		assertEquals(Step.RETREAT, plan(after(lower.toBuilder().hp(20).build(), dunk)).getStep());
	}

	@Test
	public void healingTargetsAlsoNeedAMeaningfulAdvantageToSwitch()
	{
		Snapshot s = base().toBuilder().teammates(List.of(new TeammateView("A", 42, 99), new TeammateView("B", 42, 99)))
			.playerTiles(Map.of("A", SPRING.dx(2), "B", SPRING.dx(2))).build();
		Instruction first = plan(s);
		assertEquals("A", first.getTarget().getLabel());
		Snapshot small = after(s, first).toBuilder()
			.playerTiles(Map.of("A", SPRING.dx(4), "B", SPRING.dx(2)))
			.teammates(List.of(new TeammateView("A", 42, 99), new TeammateView("B", 41, 99))).build();
		assertEquals("A", plan(small).getTarget().getLabel());
		assertEquals("B", plan(small.toBuilder()
			.teammates(List.of(new TeammateView("A", 42, 99), new TeammateView("B", 20, 99))).build()).getTarget().getLabel());
	}

	@Test
	public void completedStepsAndUnavailableTargetsLoseTheirContinuationBonus()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1)).build();
		Instruction dunk = plan(s);
		Snapshot poisoned = after(s, dunk).toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_POISONED, 1)).build();
		Instruction pass = plan(poisoned);
		assertEquals(Step.QUEEN_PASS, pass.getStep());
		assertEquals(0, pass.getScoreTerms().get("continue current action"), .001);
		assertEquals(Step.QUEEN_WAIT, plan(after(base(), pass)).getStep());
		ObjectView nearer = new ObjectView(BaIds.POISON_CRATERS[0], SPRING.dx(2));
		Snapshot moved = after(s, dunk).toBuilder().objects(List.of(new ObjectView(BaIds.SPRING, SPRING),
			new ObjectView(BaIds.POISON_CRATERS[0], CRATER), nearer)).build();
		assertEquals(CRATER, plan(moved).getTarget().getTile());
		Instruction lost = plan(moved.toBuilder().objects(List.of(new ObjectView(BaIds.SPRING, SPRING), nearer)).build());
		assertEquals(nearer.getTile(), lost.getTarget().getTile());
		assertEquals(0, lost.getScoreTerms().get("continue current action"), .001);
	}

	@Test
	public void recentDamageAddsASmallPreferenceForWeakPlayersWithoutDominatingActualHp()
	{
		Snapshot s = base().toBuilder().teammates(List.of(new TeammateView("A", 42, 99), new TeammateView("B", 42, 99)))
			.playerTiles(Map.of("A", SPRING.dx(2), "B", SPRING.dx(2)))
			.teammateDamagePerTick(Map.of("B", 3.0)).build();
		Instruction pressured = plan(s);
		assertEquals("B", pressured.getTarget().getLabel());
		double pressure = pressured.getScoreTerms().get("recent damage pressure");
		assertTrue(pressure > 0 && pressure <= 8);
		assertEquals("A", plan(s.toBuilder().teammates(List.of(new TeammateView("A", 30, 99),
			new TeammateView("B", 48, 99))).teammateDamagePerTick(Map.of("B", 100.0)).build()).getTarget().getLabel());
		Snapshot safe = base().toBuilder().hp(47).playerTile(CRATER.dx(1)).selfDamagePerTick(100)
			.inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1)).build();
		assertEquals(Step.QUEEN_DUNK, plan(safe).getStep());
	}

	private Snapshot cleared(int wave)
	{
		return base().toBuilder().wave(wave).queenAlive(false)
			.fighterRangerKills(WaveInfo.fightersTotal(wave) + WaveInfo.rangersTotal(wave))
			.runnersKilled(WaveInfo.runnersTotal(wave)).healersKilled(WaveInfo.healersTotal(wave)).build();
	}

	@Test
	public void preQueenTopUpsNameThePlayerAndMarkTheVialAndObservedPosition()
	{
		Snapshot s = cleared(10).toBuilder().teammates(List.of(new TeammateView("Attacker", 98, 99)))
			.playerTiles(Map.of("Attacker", SPRING.dx(40))).build();
		for (boolean stack : new boolean[]{false, true})
		{
			Instruction ins = planner.plan(s, Settings.builder().queenStack(stack).build());
			assertEquals(Step.HEAL_TEAMMATE, ins.getStep());
			assertEquals("Attacker", ins.getTarget().getLabel());
			assertEquals(SPRING.dx(40), ins.getTarget().getTile());
			assertTrue(ins.getItems().contains(BaIds.VIAL_FULL));
			assertTrue(ins.getDetail().contains("Before the Queen: use the vial on Attacker"));
			assertEquals(Step.QUEEN_WAIT, planner.plan(s.toBuilder()
				.teammates(List.of(new TeammateView("Attacker", 99, 99))).build(), Settings.builder().queenStack(stack).build()).getStep());
		}
	}

	@Test
	public void preparationRefillsBeforeHealingAndEndsWhenTheQueenArrives()
	{
		Snapshot s = cleared(10).toBuilder().inventory(Map.of(BaIds.VIAL_EMPTY, 1))
			.teammates(List.of(new TeammateView("Attacker", 60, 99))).build();
		Instruction refill = plan(s);
		assertEquals(Step.HEAL_FILL_VIAL, refill.getStep());
		assertEquals(SPRING, refill.getTarget().getTile());
		assertTrue(refill.getDetail().contains("Before the Queen:"));
		Instruction heal = plan(s.toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1)).build());
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		assertFalse(plan(s.toBuilder().queenAlive(true).inventory(Map.of(BaIds.VIAL_FULL, 1)).build())
			.getDetail().contains("Before the Queen:"));
	}

	@Test
	public void otherCompletedWavesDoNotRequestRoutineTopUps()
	{
		for (int wave = 1; wave < 10; wave++)
		{
			Instruction ins = plan(cleared(wave).toBuilder().listenCall(BaCall.POISON_MEAT).listenFresh(true)
				.teammates(List.of(new TeammateView("Attacker", 40, 99))).build());
			assertEquals("wave " + wave, Step.HEAL_SUPPORT, ins.getStep());
			assertEquals("Wave complete", ins.getHeadline());
			assertEquals(Target.NONE, ins.getTarget());
			assertFalse(ins.getDetail().contains("Top up"));
		}
		Instruction partial = plan(base().toBuilder().queenAlive(false).listenCall(BaCall.POISON_MEAT).listenFresh(true)
			.healersKilled(WaveInfo.healersTotal(10)).build());
		assertEquals(Step.HEAL_SUPPORT, partial.getStep());
		assertFalse(partial.getDetail().contains("before the next wave"));
		assertFalse(partial.getDetail().contains("Before the Queen"));
	}

	@Test
	public void passingThePoisonedEggBeatsHealingAtFortyEightHp()
	{
		Snapshot s = base().toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_POISONED, 1))
			.teammates(List.of(new TeammateView("Attacker", 48, 85))).build();
		assertEquals(Step.QUEEN_PASS, plan(s).getStep());
		assertEquals(Step.HEAL_TEAMMATE, plan(s.toBuilder()
			.teammates(List.of(new TeammateView("Attacker", 38, 85))).build()).getStep());
	}

	@Test
	public void holdingEitherEggDoesNotBlockHealingAtFortyFivePercent()
	{
		for (int egg : new int[]{BaIds.EGG_YELLOW, BaIds.EGG_POISONED})
		{
			Instruction ins = plan(base().toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1, egg, 1))
				.teammates(List.of(new TeammateView("Attacker", 38, 85))).build());
			assertEquals(Step.HEAL_TEAMMATE, ins.getStep());
			assertEquals("Attacker", ins.getTarget().getLabel());
			assertEquals(SPRING.dx(2), ins.getTarget().getTile());
			assertTrue(ins.getScoreTerms().containsKey("low HP buffer"));
		}
	}

	@Test
	public void lowAbsoluteHpBeatsEggsEvenForALowerMaxHpPlayerFarAway()
	{
		Instruction ins = plan(base().toBuilder().inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_POISONED, 1))
			.teammates(List.of(new TeammateView("Attacker", 30, 50)))
			.playerTiles(Map.of("Attacker", SPRING.dx(50))).build());
		assertEquals(Step.HEAL_TEAMMATE, ins.getStep());
	}

	@Test
	public void ownLowHealthBeatsRoutineTeamCareAndHeldEggs()
	{
		for (int vial : new int[]{BaIds.VIAL_FULL, BaIds.VIAL_EMPTY})
		{
			Instruction ins = plan(base().toBuilder().hp(29).inventory(Map.of(vial, 1, BaIds.EGG_YELLOW, 1))
				.teammates(List.of(new TeammateView("Attacker", 63, 99))).build());
			assertEquals(Step.RETREAT, ins.getStep());
			assertEquals(SPRING, ins.getTarget().getTile());
		}
	}

	@Test
	public void waitingTimeTopsUpTheTeamAndSelfBeforeTheyBecomeCritical()
	{
		assertEquals(Step.HEAL_TEAMMATE, plan(base().toBuilder()
			.teammates(List.of(new TeammateView("Attacker", 85, 99))).build()).getStep());
		assertEquals(Step.RETREAT, plan(base().toBuilder().hp(64).build()).getStep());
		assertEquals(Step.HEAL_FILL_VIAL, plan(base().toBuilder()
			.inventory(Map.of(net.runelite.api.gameval.ItemID.BARBASSAULT_VIAL_04_HALF, 1)).build()).getStep());
	}

	@Test
	public void dryOrMissingVialBecomesAnImmediateCareTaskWhenSomeoneIsInDanger()
	{
		for (int vial : new int[]{BaIds.VIAL_EMPTY, -1})
		{
			Snapshot s = base().toBuilder().teammates(List.of(new TeammateView("Attacker", 15, 99)))
				.inventory(vial < 0 ? Map.of(BaIds.EGG_YELLOW, 1) : Map.of(BaIds.EGG_YELLOW, 1, vial, 1)).build();
			Instruction ins = plan(s);
			assertEquals(Step.HEAL_FILL_VIAL, ins.getStep());
			assertTrue(ins.getDetail().contains("Attacker needs it"));
			assertEquals(Urgency.HIGH, ins.getUrgency());
			assertEquals(vial < 0 ? BaIds.HEALER_MACHINE : BaIds.SPRING, ins.getTarget().getId());
		}
	}

	@Test
	public void safeEggWorkResumesAfterHealingAndDoesNotDemandAFullVial()
	{
		Snapshot holding = base().toBuilder().playerTile(CRATER.dx(1))
			.inventory(Map.of(net.runelite.api.gameval.ItemID.BARBASSAULT_VIAL_04_HALF, 1, BaIds.EGG_YELLOW, 1)).build();
		assertEquals(Step.QUEEN_DUNK, plan(holding).getStep());
		Instruction heal = plan(holding.toBuilder().teammates(List.of(new TeammateView("Attacker", 20, 99))).build());
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		assertEquals(Step.QUEEN_DUNK, plan(holding.toBuilder().lastTask(heal.getTask()).tasks(heal.getTasks()).build()).getStep());
		assertEquals(Step.QUEEN_PASS, plan(base().toBuilder()
			.inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_POISONED, 1)).build()).getStep());
	}

	@Test
	public void shortWalkToFinishAnEggCanBeatAMinorTopUp()
	{
		assertEquals(Step.QUEEN_DUNK, plan(base().toBuilder().playerTile(CRATER.dx(1))
			.inventory(Map.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1))
			.teammates(List.of(new TeammateView("Attacker", 85, 99))).build()).getStep());
	}

	@Test
	public void preparationStartsAfterConfirmedPenanceClearBeforeTheQueenSpawns()
	{
		Snapshot s = base().toBuilder().queenAlive(false).hp(60)
			.fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
			.runnersKilled(WaveInfo.runnersTotal(10)).healersKilled(WaveInfo.healersTotal(10)).build();
		assertEquals(Step.RETREAT, plan(s).getStep());
	}
}
