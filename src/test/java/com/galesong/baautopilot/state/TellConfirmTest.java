package com.galesong.baautopilot.state;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.model.Role;
import java.util.Collections;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.Test;

/**
 * Recorded 2026-10-17: Tell-worms then Walk here the same tick - the walk cancelled the horn, no
 * call was made, but the click alone had counted it as done.
 */
public class TellConfirmTest
{
	private static final RoundLogic.Hud DEFENDER_HUD = new RoundLogic.Hud()
	{
		@Override
		public boolean visible(int component)
		{
			return component == WidgetUtil.packComponentId(Role.DEFENDER.getHudGroup(), 0);
		}

		@Override
		public String text(int component)
		{
			return component == Role.DEFENDER.getShoutComponent() ? "Pois. Worms" : null;
		}
	};

	private int tick = 100;

	private RoundLogic arena()
	{
		RoundLogic r = new RoundLogic();
		step(r);
		r.chat(true, "---- Wave: 3 ----", false, tick);
		for (int i = 0; i < 6; i++)
		{
			step(r);
		}
		return r;
	}

	private void step(RoundLogic r)
	{
		r.tick(++tick, true, DEFENDER_HUD, Collections.emptyMap(), 0, 0, 0);
	}

	private Snapshot snap(RoundLogic r)
	{
		return r.snapshot().build();
	}

	@Test
	public void aCallCountsOnceItIsSaid()
	{
		RoundLogic r = arena();
		r.tellClicked("Tell-worms", DEFENDER_HUD);
		// Pending: not asked for twice while the game catches up
		assertTrue(snap(r).isShoutDone());
		step(r);
		r.overhead("Me", "Healer: Poison worms!", true, tick);
		for (int i = 0; i < 10; i++)
		{
			step(r);
		}
		assertTrue(snap(r).isShoutDone());
		assertFalse(snap(r).isShoutLost());
	}

	@Test
	public void aCancelledClickIsOwedAgain()
	{
		RoundLogic r = arena();
		r.tellClicked("Tell-worms", DEFENDER_HUD);
		for (int i = 0; i <= RoundLogic.TELL_CONFIRM_TICKS + 1; i++)
		{
			step(r);
		}
		assertFalse(snap(r).isShoutDone());
		assertTrue(snap(r).isShoutLost());
	}
}
