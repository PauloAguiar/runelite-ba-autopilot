package com.galesong.baautopilot.state;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerHealthHistoryTest
{
	@Test
	public void averagesObservedLossAndCountsHealedAndQuietTicksAsZeroDamage()
	{
		PlayerHealthHistory h = new PlayerHealthHistory();
		h.observeSelf(1, 100, 100);
		h.observeSelf(2, 90, 100);
		h.observeSelf(3, 80, 100);
		h.observeSelf(4, 100, 100);
		h.observeSelf(5, 94, 100);
		assertEquals(6.5, h.selfRate(5), .001);
		h.observeSelf(5, 50, 100); // A second snapshot in the same tick is not another interval.
		assertEquals(6.5, h.selfRate(5), .001);
		assertEquals(0, h.selfRate(25), .001);
	}

	@Test
	public void gapsAndMaximumHpChangesDoNotInventDamage()
	{
		PlayerHealthHistory h = new PlayerHealthHistory();
		h.observeSelf(1, 90, 100);
		h.observeSelf(3, 40, 100);
		h.observeSelf(4, 25, 50);
		assertEquals(0, h.selfRate(4), .001);
		h.observeSelf(5, 20, 50);
		assertEquals(5, h.selfRate(5), .001);
	}

	@Test
	public void eachTeammateHasIndependentHistoryAndNewWavesStartEmpty()
	{
		PlayerHealthHistory h = new PlayerHealthHistory();
		h.observeTeam(1, List.of(new TeammateView("A", 80, 99), new TeammateView("B", 70, 99)));
		h.observeTeam(2, List.of(new TeammateView("A", 70, 99), new TeammateView("B", 70, 99)));
		h.observeSelf(1, 77, 77);
		h.observeSelf(2, 60, 77);
		assertEquals(10, h.teamRates(2).get("A"), .001);
		assertEquals(0, h.teamRates(2).get("B"), .001);
		assertEquals(17, h.selfRate(2), .001);
		h.reset();
		assertEquals(0, h.selfRate(3), .001);
		assertTrue(h.teamRates(3).isEmpty());
	}
}
