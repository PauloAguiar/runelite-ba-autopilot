package com.bautopilot.baautopilot.tutorial;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.overlay.Palette;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Mark;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Style;
import java.awt.Color;
import net.runelite.api.gameval.ItemID;

/** Colours and marks the tutorials share. */
final class Tutorials
{
	static final Color FIGHTER = new Color(240, 90, 80);
	static final Color RANGER = new Color(90, 200, 120);
	static final Color RUNNER = new Color(240, 200, 70);
	static final Color HEALER = new Color(110, 170, 255);
	static final Color QUEEN = new Color(200, 120, 255);
	static final Color PROP = new Color(235, 225, 200);
	static final Color EGG = new Color(240, 210, 60);

	/**
	 * Each role's page about the autopilot box (its own page, after the role's summary; the box
	 * flashes there).
	 */
	static final String BOX_PAGE = "This is the BA Autopilot overlay. It shows the one thing to do now. You can move it by "
		+ "holding Alt and dragging it.\n\n"
		+ "The coloured tag shows what kind of step it is. Under it are your call, the call you heard, and the time "
		+ "left before the next call.";

	/** A player in a role: a ring in the role's colour, the role's name over them. */
	static Mark role(String key, Role role)
	{
		return new Mark(key, role.getLabel(), Palette.role(role), Style.CIRCLE);
	}

	/** BA Autopilot's box, as the plugin draws it in this situation; the page is about {@code expect}. */
	static TutorialSteps.Pilot pilot(Step expect, Snapshot.SnapshotBuilder situation)
	{
		return new TutorialSteps.Pilot(expect, situation.build());
	}

	/**
	 * A role's call page: the caller blows the horn, the call shows over their head with an arrow to
	 * the partner, the role panel shows it, the view is tinted red and the autopilot box asks for it.
	 */
	static TutorialSteps.Page.PageBuilder call(Role role, String caller, String partner, String said,
		String panelCall, String panelHeard, BaCall shout, Snapshot.SnapshotBuilder situation)
	{
		return TutorialSteps.Page.builder()
			.prop(caller).prop(partner)
			.mark(role(caller, role))
			.arrow(new TutorialSteps.Arrow(caller, partner, CALL_ARROW))
			.says(caller, said)
			.hud(new TutorialSteps.Hud(role, panelCall, panelHeard))
			.pilot(pilot(Step.CALL, situation.shoutCall(shout).shoutDone(false)))
			.tint(true)
			.action(TutorialSteps.Action.HORNS);
	}

	private static final Color CALL_ARROW = new Color(255, 196, 0);

	/** An item's right-click menu with {@code pick} flashing; the options are the item's own, top first. */
	static TutorialSteps.Menu menu(int itemId, String pick, String... options)
	{
		return new TutorialSteps.Menu(itemId, java.util.Arrays.asList(options), java.util.Arrays.asList(options).indexOf(pick));
	}

	/** The horn every player of a role starts the wave with. */
	static int horn(Role role)
	{
		switch (role)
		{
			case ATTACKER:
				return ItemID.BARBASSAULT_ATT_HORN_01;
			case COLLECTOR:
				return ItemID.BARBASSAULT_HORN_COLLECTOR;
			case DEFENDER:
				return ItemID.BARBASSAULT_DEFENDER_HORN;
			default:
				return ItemID.BARBASSAULT_HEAL_HORN_01;
		}
	}

	/**
	 * The horn's right-click menu: one Tell- option per call the partner can get, in call order, the
	 * owed one flashing (the plugin colours it green; it doesn't move it).
	 */
	static TutorialSteps.Menu hornMenu(Role role, BaCall shout)
	{
		java.util.List<String> options = new java.util.ArrayList<>();
		for (BaCall c : BaCall.values())
		{
			if (c.getReceiver() == role.partner())
			{
				options.add(c.getHornOption());
			}
		}
		return new TutorialSteps.Menu(horn(role), options, options.indexOf(shout.getHornOption()));
	}

	private Tutorials()
	{
	}
}
