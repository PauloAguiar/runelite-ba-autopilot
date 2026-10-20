package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static com.galesong.baautopilot.state.PenanceDiagnostics.PoisonResult.*;
import static org.junit.Assert.*;

public class PenanceDiagnosticsTest
{
	private static WorldPoint tile(int x, int y) { return ArenaTiles.p(ArenaTiles.REGION_WAVES_1_9, x, y); }
	private static GroundItemView food(int x, int y, boolean good, int tick)
	{
		return new GroundItemView(BaCall.BAIT_TOFU.getItemId(), tile(x, y), 1, good, tick);
	}
	private static Snapshot base() { return Snapshot.builder().inArena(true).tick(100).wave(8).role(Role.DEFENDER).build(); }
	private static PenanceView healer() { return PenanceView.builder().npcIndex(5).type(PenanceType.HEALER).tile(tile(50, 50)).lastFoodTick(100).damageTaken(4).build(); }

	@Test
	public void zonesUseFixedEightTileBoundariesAndWikiPriority()
	{
		WorldPoint runner = tile(35, 35);
		int[][] order = {{43,43},{43,35},{43,27},{35,43},{35,35},{35,27},{27,43},{27,35},{27,27}};
		for (int i = 0; i < order.length; i++) assertEquals(i, PenanceDiagnostics.zoneRank(runner, tile(order[i][0], order[i][1])));
		assertEquals(-1, PenanceDiagnostics.zoneRank(runner, tile(51, 35)));
		assertEquals(4, PenanceDiagnostics.zoneRank(tile(32, 32), tile(39, 39)));
		assertEquals(1, PenanceDiagnostics.zoneRank(tile(39, 32), tile(40, 32)));
	}

	@Test
	public void aNearbyTriggerEnablesZoneSearchAndNewestFoodWinsWithinTheBestZone()
	{
		PenanceView runner = PenanceView.builder().type(PenanceType.RUNNER).tile(tile(39, 39)).build();
		GroundItemView farNe = food(47, 47, false, 1), newerNe = food(46, 46, true, 2), trigger = food(39, 38, true, 99);
		Snapshot s = base().toBuilder().groundItems(List.of(farNe, newerNe)).build();
		assertNull(PenanceDiagnostics.candidateBait(s, runner));
		s = s.toBuilder().groundItems(List.of(trigger, farNe, newerNe)).build();
		assertEquals(newerNe, PenanceDiagnostics.candidateBait(s, runner));
		assertNull(PenanceDiagnostics.candidateBait(s.toBuilder().role(Role.HEALER).build(), runner));
	}

	@Test
	public void observedEatingAndMotionAreDistinctFromInferredHunting()
	{
		PenanceView runner = PenanceView.builder().npcIndex(1).type(PenanceType.RUNNER).tile(tile(39, 39)).previousTile(tile(39, 40)).build();
		assertEquals("R WALK? S", PenanceDiagnostics.runner(base(), runner).getTitle());
		Snapshot baited = base().toBuilder().groundItems(List.of(food(39, 38, true, 99))).build();
		assertEquals("R HUNT? S", PenanceDiagnostics.runner(baited, runner).getTitle());
		runner = runner.toBuilder().animation(AnimationID.BARBASSAULT_PENANCE_RUNNER_EATING).overhead("Chomp, chomp.").build();
		assertEquals("R EAT GOOD", PenanceDiagnostics.runner(baited, runner).getTitle());
		assertEquals("R EAT BAD", PenanceDiagnostics.runner(baited, runner.toBuilder().overhead("Blughhhh.").build()).getTitle());
		assertEquals("R EAT", PenanceDiagnostics.runner(baited, runner.toBuilder().overhead(null).build()).getTitle());
	}

	@Test
	public void waveHealthIncludesTheWaveTenReset()
	{
		int[] hp = {27,32,37,43,49,55,60,67,76,60};
		for (int wave = 1; wave <= 10; wave++) assertEquals(hp[wave - 1], PenanceDiagnostics.healer(base().toBuilder().wave(wave).build(), healer()).getMaxHp());
	}

	@Test
	public void quantisedHpBoundsContainEveryPossibleWaveHealthValue()
	{
		for (int wave = 1; wave <= 10; wave++)
		{
			int max = WaveInfo.healerHp(wave);
			for (int hp = 1; hp <= max; hp++) for (int scale : new int[]{1, 30, 100})
			{
				int ratio = 1 + (scale - 1) * hp / max;
				PenanceView p = healer().toBuilder().healthRatio(ratio).healthScale(scale).build();
				PenanceDiagnostics.HealerForecast f = PenanceDiagnostics.healer(base().toBuilder().wave(wave).build(), p);
				assertTrue("wave " + wave + " HP " + hp + " scale " + scale, f.getHpMin() <= hp && hp <= f.getHpMax());
			}
		}
	}

	@Test
	public void aSingleFreshFoodKillsWaveOneButLeavesWaveSixShort()
	{
		PenanceDiagnostics.HealerForecast first = PenanceDiagnostics.healer(base().toBuilder().wave(1).build(), healer());
		assertEquals(KILLS, first.getResult());
		assertEquals(23, first.getHpMin());
		assertEquals(50, first.getDamageMin());
		assertEquals(26, first.getEarliestDeathTicks());
		assertEquals(30, first.getLatestDeathTicks());
		PenanceDiagnostics.HealerForecast sixth = PenanceDiagnostics.healer(base().toBuilder().wave(6).build(), healer());
		assertEquals(SURVIVES, sixth.getResult());
		assertEquals(1, sixth.getHpMin() - sixth.getDamageMax());
		assertEquals(-1, sixth.getEarliestDeathTicks());
	}

	@Test
	public void elapsedTimeAndObservedDamageReduceThePoisonOnlyDeathCountdown()
	{
		PenanceView p = healer().toBuilder().damageTaken(12).lastPoisonDamage(4).lastPoisonTick(107).build();
		PenanceDiagnostics.HealerForecast f = PenanceDiagnostics.healer(base().toBuilder().wave(1).tick(110).build(), p);
		assertEquals(KILLS, f.getResult());
		assertEquals(15, f.getHpMax());
		assertEquals(16, f.getEarliestDeathTicks());
		assertEquals(20, f.getLatestDeathTicks());
	}

	@Test
	public void unknownPositionWithinAPoisonStageProducesBoundsAndAnUncertainVerdict()
	{
		PenanceView p = healer().toBuilder().lastFoodTick(-1).lastPoisonDamage(2).lastPoisonTick(100)
			.healthRatio(5).healthScale(30).build();
		PenanceDiagnostics.HealerForecast f = PenanceDiagnostics.healer(base(), p);
		assertEquals(5, f.getDamageMin());
		assertEquals(13, f.getDamageMax());
		assertEquals(UNCERTAIN, f.getResult());
		p = p.toBuilder().healthRatio(10).build();
		assertEquals(SURVIVES, PenanceDiagnostics.healer(base(), p).getResult());
	}

	@Test
	public void anUnanchoredOneDamageHitCannotClaimAFoodPoisonDeathTime()
	{
		PenanceView p = healer().toBuilder().lastFoodTick(-1).lastPoisonDamage(1).lastPoisonTick(100)
			.healthRatio(1).healthScale(30).build();
		assertEquals(UNKNOWN, PenanceDiagnostics.healer(base(), p).getResult());
		assertEquals(-1, PenanceDiagnostics.healer(base(), p).getLatestDeathTicks());
	}

	@Test
	public void missingHpAndDyingNpcsDoNotGetInventedDeathTimes()
	{
		PenanceView p = healer().toBuilder().damageTaken(0).build();
		assertEquals(UNKNOWN, PenanceDiagnostics.healer(base(), p).getResult());
		assertEquals(-1, PenanceDiagnostics.healer(base(), p).getLatestDeathTicks());
		p = p.toBuilder().healthRatio(30).healthScale(30).lastFoodTick(-1).build();
		assertEquals(NO_POISON, PenanceDiagnostics.healer(base(), p).getResult());
		assertEquals(DYING, PenanceDiagnostics.healer(base(), p.toBuilder().dying(true).build()).getResult());
		assertEquals(0, PenanceDiagnostics.healer(base().toBuilder().wave(0).build(), p).getMaxHp());
	}
}
