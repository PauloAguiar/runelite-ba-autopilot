package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID.BarbassaultTurret;
import static org.junit.Assert.*;

public class AttackerOpeningQueenTest
{
	private final Planner planner = new Planner();
	private static Snapshot base(int wave)
	{
		int region = wave == 10 ? ArenaTiles.REGION_WAVE_10 : ArenaTiles.REGION_WAVES_1_9;
		return Snapshot.builder().inArena(true).tick(1000).wave(wave).waveTick(5).role(Role.ATTACKER)
			.hp(77).maxHp(77).shoutDone(true).freeSlots(26).playerTile(ArenaTiles.p(region, 28, 16))
			.listenFresh(true).listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId())
			.objects(List.of(new ObjectView(BaIds.ATTACKER_MACHINE, ArenaTiles.p(region, 29, 16)),
				new ObjectView(BaIds.MUSHROOMS[0], ArenaTiles.p(region, 28, 17)),
				new ObjectView(BaIds.LAUNCHERS[0], ArenaTiles.p(region, 22, 33))))
			.build();
	}
	private Instruction plan(Snapshot s) { return planner.plan(s, Settings.builder().build()); }

	@Test public void openingUsesTheWesternApproachBeforeEngagingOnBothArenas()
	{
		for (int wave : new int[]{1, 10})
		{
			Snapshot s = base(wave).toBuilder().listenCall(null).build();
			assertEquals(Step.ATT_POSITION, plan(s).getStep());
			assertEquals(ArenaTiles.collectorWestApproach(wave), plan(s).getTarget().getTile());
			Snapshot west = s.toBuilder().playerTile(ArenaTiles.collectorWestApproach(wave)).build();
			assertEquals(ArenaTiles.collectorBehindHill(wave), plan(west).getTarget().getTile());
			assertNotEquals(Step.ATT_POSITION, plan(s.toBuilder().attackerOpeningComplete(true).build()).getStep());
			assertNotEquals(Step.ATT_POSITION, plan(s.toBuilder().waveTick(60).build()).getStep());
		}
	}

	@Test public void waveTenGetsArrowsFirstAndDoesNotGetSpikesBetweenRegularSpawns()
	{
		Snapshot start = base(10).toBuilder().ammoId(-1).build();
		assertEquals(Step.ATT_GET_ARROWS, plan(start).getStep());
		Snapshot between = base(10).toBuilder().waveTick(100).attackerOpeningComplete(true).build();
		assertEquals(Step.ATT_SUPPORT, plan(between).getStep());
		assertTrue(plan(between).getPriorities().stream().noneMatch(p -> "att.spikes".equals(p.getTask())));
		PenanceView target = PenanceView.builder().type(PenanceType.FIGHTER).npcIndex(9)
			.tile(between.getPlayerTile().dy(2)).build();
		assertEquals(Step.ATT_ATTACK, plan(between.toBuilder().penance(List.of(target)).build()).getStep());
		Snapshot done = between.toBuilder().fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10)).build();
		assertEquals(Step.ATT_SPIKES, plan(done).getStep());
	}

	@Test public void completedOpeningIsRememberedAcrossLaterMovementAndResetForTheNextWave()
	{
		RoundLogic logic = new RoundLogic();
		RoundLogic.Hud hud = new RoundLogic.Hud() {
			public boolean visible(int id) { return id >>> 16 == Role.ATTACKER.getHudGroup(); }
			public String text(int id) { return null; }
		};
		logic.tick(100, true, hud, Map.of(), 0, 0, 0);
		logic.observePosition(ArenaTiles.collectorBehindHill(1));
		logic.observePosition(base(1).getPlayerTile());
		assertTrue(logic.snapshot().build().isAttackerOpeningComplete());
		logic.chat(true, "---- Wave: 2 ----", false, 101);
		assertFalse(logic.snapshot().build().isAttackerOpeningComplete());
	}

	@Test public void finishedEggsCueTheCannonBeforeLoadingAndRemainThroughTheVolley()
	{
		Snapshot waiting = base(10).toBuilder().queenAlive(true).queenSpikedPassed(5)
			.inventory(Map.of(BaIds.SPIKES, 3)).eggsRed(1).build();
		assertEquals(Step.QUEEN_WAIT, plan(waiting).getStep());
		Snapshot handoff = waiting.toBuilder().queenOmegaHandoffTick(999).build();
		assertEquals(Step.QUEEN_FIRE, plan(handoff).getStep());
		assertTrue(plan(handoff).getHeadline().contains("Get ready"));
		assertEquals(BarbassaultTurret.OMEGA_QUEEN, TurretChoice.pick(handoff));
		assertEquals(Step.QUEEN_FIRE, plan(waiting.toBuilder().eggsOmega(1).build()).getStep());
		assertEquals(Step.QUEEN_FIRE, plan(waiting.toBuilder().queenOmegaFired(1).build()).getStep());
		assertNotEquals(Step.QUEEN_FIRE, plan(handoff.toBuilder().queenDead(true).build()).getStep());
		assertEquals(Step.QUEEN_PASS, plan(handoff.toBuilder().inventory(Map.of(BaIds.EGG_SPIKED, 1)).build()).getStep());
		assertEquals(Step.QUEEN_SPIKE, plan(handoff.toBuilder().inventory(Map.of(BaIds.EGG_POISONED, 1, BaIds.SPIKES, 1)).build()).getStep());
	}
}
