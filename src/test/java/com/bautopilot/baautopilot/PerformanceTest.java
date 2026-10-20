package com.bautopilot.baautopilot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.Performance;
import com.bautopilot.baautopilot.state.WaveStats;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class PerformanceTest
{
	@Test
	public void measuresCallReactionsAndPartnerDelays()
	{
		Performance p = new Performance();
		// Call 1 starts at tick 100: owed from 100, called at 104; partner's call heard at 103
		for (int t = 100; t < 150; t++)
		{
			p.tick(t, 3, Role.COLLECTOR, 0, 100, t >= 103, t < 104, t >= 104);
		}
		// Call 2: owed from 150, never called; partner never calls
		for (int t = 150; t < 200; t++)
		{
			p.tick(t, 3, Role.COLLECTOR, 1, 150, false, true, false);
		}
		// Call 3 begins, which closes call 2
		p.tick(200, 3, Role.COLLECTOR, 2, 200, false, true, false);

		WaveStats w = p.current();
		assertEquals(1, w.getCallReactions().size());
		assertEquals(4, (int) w.getCallReactions().get(0));
		assertEquals(1, w.getCallsMissed());
		assertEquals(3, (int) w.getPartnerDelays().get(0));
		assertEquals(1, w.getPartnerMissed());
		assertEquals(2.4, WaveStats.meanSeconds(w.getCallReactions()), 1e-9);
	}

	@Test
	public void aCallCoveredByATeammateIsNeitherTimedNorMissed()
	{
		Performance p = new Performance();
		for (int t = 100; t < 150; t++)
		{
			p.tick(t, 3, Role.ATTACKER, 0, 100, true, true, false, t >= 103);
		}
		p.tick(150, 3, Role.ATTACKER, 1, 150, true, true, false, false);
		WaveStats w = p.current();
		assertEquals(0, w.getCallReactions().size());
		assertEquals(0, w.getCallsMissed());
	}

	@Test
	public void countsCollectorWorkAndAttachesTeamStatsToTheFinishedWave()
	{
		Performance p = new Performance();
		p.tick(10, 5, Role.COLLECTOR, 0, 10, true, false, false);
		p.chat("You put the egg in the bag.");
		p.chat("You put the egg in the bag.");
		p.inventory(ImmutableMap.of(), ImmutableMap.of(BaIds.EGG_RED, 3));
		p.chat("The egg exploded.");
		p.chat("You load the hopper.");
		p.chat("You healed 10 hitpoints.");
		p.chat("You healed 1 hitpoint.");
		p.menu("Shoot", "Egg launcher");
		p.death(PenanceType.RUNNER, 40);
		p.finish(300);

		assertNull(p.current());
		WaveStats w = p.history().get(0);
		assertEquals(5, w.getEggsPicked());
		assertEquals(1, w.getEggsWrong());
		assertEquals(1, w.getLoads());
		assertEquals(1, w.getShots());
		assertEquals(1, w.getRunnerKills());
		assertEquals(290, w.durationTicks(0));
		assertEquals(11, w.getHpHealed());

		p.teamStats(2, 1, 40, 120, 0);
		assertTrue(p.history().get(0).hasTeamStats());
		assertEquals(1, (int) p.history().get(0).getTeamRunnersPassed());
		assertEquals(2, (int) p.history().get(0).getTeamFailedAttacks());
	}
}
