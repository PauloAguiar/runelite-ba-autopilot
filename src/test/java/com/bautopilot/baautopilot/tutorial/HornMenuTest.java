package com.bautopilot.baautopilot.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The tutorial's horn menus list the Tell- options in the game's order (each horn's inventory ops
 * in the cache: items 10516, 10560, 10538, 10526; checked 2026-10-27), with the owed call picked.
 */
public class HornMenuTest
{
	@Test
	public void hornOptionsAreInTheGamesOrder()
	{
		assertEquals(Arrays.asList("Tell-red", "Tell-green", "Tell-blue"), tells(Role.ATTACKER));
		assertEquals(Arrays.asList("Tell-controlled", "Tell-accurate", "Tell-aggressive", "Tell-defensive"),
			tells(Role.COLLECTOR));
		assertEquals(Arrays.asList("Tell-tofu", "Tell-worms", "Tell-meat"), tells(Role.DEFENDER));
		assertEquals(Arrays.asList("Tell-tofu", "Tell-crackers", "Tell-worms"), tells(Role.HEALER));
	}

	/** Every page's open horn menu: the role's options, the page's owed call picked. */
	@Test
	public void pagesOpenTheirHornOnTheOwedCall()
	{
		int checked = 0;
		for (Tutorial t : Tutorial.values())
		{
			for (TutorialSteps.Page page : TutorialTestPages.of(t))
			{
				TutorialSteps.Menu menu = page.getMenu();
				if (menu == null || page.getPilot() == null
					|| menu.getItemId() != Tutorials.horn(page.getPilot().getSituation().getRole()))
				{
					continue;
				}
				Role role = page.getPilot().getSituation().getRole();
				BaCall shout = page.getPilot().getSituation().getShoutCall();
				String where = t.getLabel() + " / " + page.getTitle();
				assertEquals(where, tells(role), menu.getOptions());
				assertEquals(where, tells(role).indexOf(shout.getHornOption()), menu.getPick());
				checked++;
			}
		}
		assertTrue(checked > 0);
	}

	/** BaCall's order is the horns' own option order. */
	private static List<String> tells(Role role)
	{
		List<String> options = new ArrayList<>();
		for (BaCall c : BaCall.values())
		{
			if (c.getReceiver() == role.partner())
			{
				options.add(c.getHornOption());
			}
		}
		return options;
	}
}
