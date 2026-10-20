package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.PenanceType;
import org.junit.Test;
import static org.junit.Assert.*;

public class HealerPoisonEvidenceTest
{
	@Test
	public void observedPoisonDoesNotInventFoodsAndCanRevealAPartnerRefresh()
	{
		HealerLedger ledger = new HealerLedger();
		ledger.spawned(42);
		ledger.endTick();
		ledger.poisonHit(42, 3, 100);
		HealerLedger.Entry e = ledger.get(42);
		assertEquals(0, e.getFoods());
		assertEquals(-1, e.getLastFoodTick());
		PenanceView h = PenanceView.builder().type(PenanceType.HEALER)
			.lastPoisonDamage(e.getLastPoisonDamage()).lastPoisonTick(e.getLastPoisonTick()).build();
		assertTrue(h.poisoned());
		assertEquals(3, h.nextPoisonHit(100));
		assertEquals(2, h.nextPoisonHit(125));
		// A newer hit is authoritative even when our last own food was long ago.
		h = h.toBuilder().lastFoodTick(0).lastPoisonDamage(4).lastPoisonTick(105).build();
		assertEquals(4, h.nextPoisonHit(105));
	}

	@Test
	public void observedWeakPoisonOverridesARecentFoodEstimateAndConfirmedFoodRestoresFour()
	{
		PenanceView h = PenanceView.builder().lastFoodTick(90).lastPoisonTick(100).lastPoisonDamage(2).build();
		assertEquals(2, h.nextPoisonHit(100));
		assertEquals(4, h.toBuilder().lastFoodTick(100).build().nextPoisonHit(100));
		h = h.toBuilder().lastFoodTick(101).build();
		assertEquals(4, h.nextPoisonHit(101));
	}

	@Test
	public void zeroAndOutOfRangeDamageDoNotBecomePoisonEvidence()
	{
		HealerLedger ledger = new HealerLedger();
		ledger.spawned(42);
		ledger.poisonHit(42, 0, 100);
		ledger.poisonHit(42, 8, 100);
		assertEquals(-1, ledger.get(42).getLastPoisonTick());
	}
}
