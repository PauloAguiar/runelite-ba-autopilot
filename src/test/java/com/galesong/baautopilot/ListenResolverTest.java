package com.galesong.baautopilot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.ListenResolver;
import org.junit.Test;

public class ListenResolverTest
{
	@Test
	public void hornLinesAreAddressedToTheReceiver()
	{
		check("Healer: Poison worms!", Role.HEALER, BaCall.POISON_WORMS);
		check("Healer: Poison tofu!", Role.HEALER, BaCall.POISON_TOFU);
		check("Healer: Poison meat!", Role.HEALER, BaCall.POISON_MEAT);
		check("Defender: Drop Tofu!", Role.DEFENDER, BaCall.BAIT_TOFU);
		check("Defender: Drop Crackers!", Role.DEFENDER, BaCall.BAIT_CRACKERS);
		check("Defender: Drop Worms!", Role.DEFENDER, BaCall.BAIT_WORMS);
		check("Collector: Red eggs!", Role.COLLECTOR, BaCall.RED_EGG);
		check("Collector: Green eggs!", Role.COLLECTOR, BaCall.GREEN_EGG);
		check("Collector: Blue eggs!", Role.COLLECTOR, BaCall.BLUE_EGG);
		check("Attacker: Accurate/Field/Water!", Role.ATTACKER, BaCall.ACCURATE);
		check("Attacker: Aggressive/Blunt/Earth!", Role.ATTACKER, BaCall.AGGRESSIVE);
		check("Attacker: Controlled/Bullet/Wind!", Role.ATTACKER, BaCall.CONTROLLED);
		check("Attacker: Defensive/Barbed/Fire!", Role.ATTACKER, BaCall.DEFENSIVE);
		assertNull(ListenResolver.addressed("gz on the pet!"));
	}

	private static void check(String line, Role receiver, BaCall call)
	{
		ListenResolver.Addressed a = ListenResolver.addressed(line);
		assertEquals(line, receiver, a.receiver);
		assertEquals(line, call, a.call);
	}

	@Test
	public void ourOwnCallIsNotOurListen()
	{
		ListenResolver r = new ListenResolver();
		// As healer we shout "Defender: Drop Tofu!"; that is the defender's call, not ours
		assertFalse(r.chat(Role.HEALER, "Defender: Drop Tofu!", 10));
		assertNull(r.resolve(0).getCall());
		assertTrue(r.chat(Role.HEALER, "Healer: Poison worms!", 12));
		assertEquals(BaCall.POISON_WORMS, r.resolve(0).getCall());
	}

	@Test
	public void newestSourceWinsAndFreshnessFollowsThePeriod()
	{
		ListenResolver r = new ListenResolver();
		r.hud(Role.HEALER, "Pois. Meat", 5);
		ListenResolver.Result first = r.resolve(2);
		assertEquals(BaCall.POISON_MEAT, first.getCall());
		assertTrue(first.isFresh());
		assertEquals(ListenResolver.Source.HUD, first.getSource());

		// Next period: the HUD still shows meat, nothing new heard yet
		r.hud(Role.HEALER, "Pois. Meat", 53);
		ListenResolver.Result stale = r.resolve(52);
		assertEquals(BaCall.POISON_MEAT, stale.getCall());
		assertFalse(stale.isFresh());

		// The defender calls worms: the chat line arrives before (or without) a HUD change
		r.chat(Role.HEALER, "Healer: Poison worms!", 55);
		ListenResolver.Result heard = r.resolve(52);
		assertEquals(BaCall.POISON_WORMS, heard.getCall());
		assertTrue(heard.isFresh());
		assertEquals(ListenResolver.Source.CHAT, heard.getSource());

		// The same call repeated next period is fresh again through chat even though the HUD can't change
		r.chat(Role.HEALER, "Healer: Poison worms!", 104);
		assertTrue(r.resolve(102).isFresh());

		// A later HUD change takes over
		r.hud(Role.HEALER, "Pois. Tofu", 160);
		assertEquals(BaCall.POISON_TOFU, r.resolve(152).getCall());
	}

	@Test
	public void dashMeansNoCall()
	{
		ListenResolver r = new ListenResolver();
		r.hud(Role.COLLECTOR, "- - -", 3);
		assertNull(r.resolve(0).getCall());
		assertEquals(ListenResolver.Source.NONE, r.resolve(0).getSource());
	}
}
