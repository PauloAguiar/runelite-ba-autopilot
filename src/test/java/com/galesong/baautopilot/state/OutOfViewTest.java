package com.galesong.baautopilot.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.Test;

/** Recorded 2026-10-16: penance walking out of view (11-17 tiles away) was counted as killed. */
public class OutOfViewTest
{
	private static final RoundLogic.Hud HEALER_HUD = new RoundLogic.Hud()
	{
		@Override
		public boolean visible(int component)
		{
			return component == WidgetUtil.packComponentId(Role.HEALER.getHudGroup(), 0);
		}

		@Override
		public String text(int component)
		{
			return null;
		}
	};

	private int tick = 100;

	private RoundLogic arena()
	{
		RoundLogic r = new RoundLogic();
		step(r);
		r.chat(true, "---- Wave: 3 ----", false, tick);
		step(r);
		return r;
	}

	private void step(RoundLogic r)
	{
		r.tick(++tick, true, HEALER_HUD, Collections.emptyMap(), 0, 0, 0);
	}

	private static Snapshot snap(RoundLogic r)
	{
		return r.snapshot().build();
	}

	@Test
	public void aHealerWalkingOutOfViewIsNotDead()
	{
		RoundLogic r = arena();
		r.npcSpawned(7, PenanceType.HEALER, tick);
		r.penance(7, PenanceType.HEALER, new WorldPoint(1, 2, 0), -1, -1, false);
		r.npcDespawned(7, PenanceType.HEALER, 14);
		step(r);
		assertEquals(0, snap(r).getHealersKilled());
		assertEquals(1, snap(r).getUnseenPenance().size());
		assertTrue(snap(r).getUnseenPenance().get(0).isUnseen());
		assertEquals(new WorldPoint(1, 2, 0), snap(r).getUnseenPenance().get(0).getTile());

		// Back in view: still alive, nothing counted
		r.npcSpawned(7, PenanceType.HEALER, tick);
		r.penance(7, PenanceType.HEALER, new WorldPoint(3, 2, 0), -1, -1, false);
		assertEquals(0, snap(r).getHealersKilled());
		assertTrue(snap(r).getUnseenPenance().isEmpty());
	}

	@Test
	public void runnerTimeoutRemainsUncertainUntilItReturns()
	{
		RoundLogic r = arena();
		r.npcSpawned(7, PenanceType.RUNNER, tick);
		r.penance(7, PenanceType.RUNNER, com.galesong.baautopilot.model.ArenaTiles.trapFood(3), -1, -1, false);
		r.npcDespawned(7, PenanceType.RUNNER, 15);
		for (int i = 0; i < RoundLogic.UNSEEN_TICKS; i++)
		{
			step(r);
		}
		assertEquals(0, snap(r).getRunnersKilled());
		assertEquals(1, snap(r).getUncertainRunners());
		assertTrue(snap(r).getUnseenPenance().isEmpty());

		r.npcSpawned(7, PenanceType.RUNNER, tick);
		assertEquals(0, snap(r).getRunnersKilled());
		assertEquals(0, snap(r).getUncertainRunners());
	}

	@Test public void realDeathOrTheClearMessageSettlesATimedOutRunnerOnce()
	{
		for (boolean clear : new boolean[]{false, true})
		{
			RoundLogic r = arena();
			r.npcSpawned(7, PenanceType.RUNNER, tick);
			r.penance(7, PenanceType.RUNNER, com.galesong.baautopilot.model.ArenaTiles.trapFood(3), -1, -1, false);
			r.npcDespawned(7, PenanceType.RUNNER, 15);
			for (int i = 0; i < RoundLogic.UNSEEN_TICKS; i++) step(r);
			assertEquals(1, snap(r).getUncertainRunners());
			if (clear) r.chat(true, "All of the Penance Runners have been killed!", false, tick);
			else { r.npcDied(7, PenanceType.RUNNER); r.npcDied(7, PenanceType.RUNNER); }
			assertEquals(0, snap(r).getUncertainRunners());
			assertEquals(clear ? com.galesong.baautopilot.model.WaveInfo.runnersTotal(3) : 1, snap(r).getRunnersKilled());
		}
	}

	@Test
	public void aRunnerLeavingThroughTheSouthExitIsNotAKill()
	{
		RoundLogic r = arena();
		r.npcSpawned(7, PenanceType.RUNNER, tick);
		r.penance(7, PenanceType.RUNNER, com.galesong.baautopilot.model.ArenaTiles.safety(3).dy(-8), -1, -1, false);
		r.npcDespawned(7, PenanceType.RUNNER, 4);
		for (int i = 0; i < RoundLogic.UNSEEN_TICKS; i++)
		{
			step(r);
		}
		assertEquals(0, snap(r).getRunnersKilled());
		assertTrue(snap(r).getUnseenPenance().isEmpty());
	}

	@Test
	public void onlyTheTrapByTheTrapFoodCounts()
	{
		// The unused west trap stays full; the east one by the trap food is broken
		java.util.List<ObjectView> objects = java.util.Arrays.asList(
			new ObjectView(com.galesong.baautopilot.model.BaIds.TRAP_FULL, com.galesong.baautopilot.model.ArenaTiles.trapFood(3).dx(-30)),
			new ObjectView(com.galesong.baautopilot.model.BaIds.TRAP_BROKEN, com.galesong.baautopilot.model.ArenaTiles.trapFood(3).dy(-1)));
		assertEquals(0, RoundLogic.trapCharges(objects, 3));
	}

	@Test
	public void theGamesAllKilledMessageSettlesTheCount()
	{
		RoundLogic r = arena();
		r.npcSpawned(7, PenanceType.RUNNER, tick);
		r.penance(7, PenanceType.RUNNER, com.galesong.baautopilot.model.ArenaTiles.trapFood(3), -1, -1, false);
		r.npcDespawned(7, PenanceType.RUNNER, 15);
		r.chat(true, "All of the Penance @mes_hl_blu@Runners</col> have been killed!", false, tick);
		Snapshot s = snap(r);
		assertEquals(com.galesong.baautopilot.model.WaveInfo.runnersTotal(3), s.getRunnersKilled());
		assertTrue(s.alive(PenanceType.RUNNER).isEmpty());
		assertTrue(s.getUnseenPenance().isEmpty());
	}

	@Test
	public void aDespawnNearbyIsADeath()
	{
		RoundLogic r = arena();
		r.npcSpawned(7, PenanceType.FIGHTER, tick);
		r.penance(7, PenanceType.FIGHTER, new WorldPoint(1, 2, 0), -1, -1, false);
		r.npcDespawned(7, PenanceType.FIGHTER, 3);
		assertEquals(1, snap(r).getFighterRangerKills());
		assertTrue(snap(r).getUnseenPenance().isEmpty());
	}
}
