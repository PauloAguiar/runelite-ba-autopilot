package com.galesong.baautopilot.state;

import static org.junit.Assert.assertEquals;

import com.galesong.baautopilot.model.PenanceType;
import org.junit.Test;

public class DamageLogTest
{
	@Test
	public void totalsAndRollingDamagePerSecond()
	{
		DamageLog d = new DamageLog();
		d.hit(1, 100, 6, true, false);
		d.hit(1, 102, 3, false, false);
		d.hit(1, 104, 2, false, true);
		d.hit(2, 104, 4, true, false);
		assertEquals(11, d.total(1));
		assertEquals(6, d.mine(1));
		assertEquals(2, d.poison(1));
		// 11 damage over the 6 s window
		assertEquals(11 / 6.0, d.dps(1, 105), 1e-9);
		assertEquals(10 / 6.0, d.myDps(105), 1e-9);
		assertEquals(15 / 6.0, d.teamDps(105), 1e-9);
		// The first two hits leave the window; totals stay
		assertEquals(2 / 6.0, d.dps(1, 112), 1e-9);
		assertEquals(11, d.total(1));
		assertEquals(10, d.myTotal());
	}

	@Test
	public void hpLeftAndTimeToKillFromDamageAndTheHealthBar()
	{
		// 12 damage took the bar to 60%: max ~30, 18 left; at 3/s, 6 s
		PenanceView p = PenanceView.builder().npcIndex(1).type(PenanceType.FIGHTER)
			.healthRatio(18).healthScale(30).damageTaken(12).dps(3).build();
		assertEquals(18, p.estimatedHpLeft());
		assertEquals(6.0, p.secondsToKill(), 1e-9);
		// Unknown before any damage or bar
		assertEquals(-1, PenanceView.builder().type(PenanceType.FIGHTER).build().estimatedHpLeft());
	}
}
