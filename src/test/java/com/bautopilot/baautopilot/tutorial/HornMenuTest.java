package com.bautopilot.baautopilot.tutorial;

import static org.junit.Assert.assertEquals;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.Role;
import java.util.Arrays;
import org.junit.Test;

/** The tutorial's horn menus list the Tell- options in the game's order (each horn's inventory ops in the cache). */
public class HornMenuTest
{
	@Test
	public void hornOptionsAreInTheGamesOrder()
	{
		assertEquals(Arrays.asList("Tell-red", "Tell-green", "Tell-blue"),
			Tutorials.hornMenu(Role.ATTACKER, BaCall.RED_EGG).getOptions());
		assertEquals(Arrays.asList("Tell-controlled", "Tell-accurate", "Tell-aggressive", "Tell-defensive"),
			Tutorials.hornMenu(Role.COLLECTOR, BaCall.CONTROLLED).getOptions());
		assertEquals(Arrays.asList("Tell-tofu", "Tell-worms", "Tell-meat"),
			Tutorials.hornMenu(Role.DEFENDER, BaCall.POISON_TOFU).getOptions());
		assertEquals(Arrays.asList("Tell-tofu", "Tell-crackers", "Tell-worms"),
			Tutorials.hornMenu(Role.HEALER, BaCall.BAIT_TOFU).getOptions());
	}

	@Test
	public void theOwedCallIsPickedWhereItStands()
	{
		assertEquals(2, Tutorials.hornMenu(Role.ATTACKER, BaCall.BLUE_EGG).getPick());
	}
}
