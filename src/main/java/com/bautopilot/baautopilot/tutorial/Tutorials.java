package com.bautopilot.baautopilot.tutorial;

import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.Color;
import net.runelite.api.gameval.ItemID;

/** Colours and helpers the tutorials share. */
final class Tutorials
{
	static final Color FIGHTER = new Color(240, 90, 80);
	static final Color RANGER = new Color(90, 200, 120);
	static final Color RUNNER = new Color(240, 200, 70);
	static final Color HEALER = new Color(110, 170, 255);
	static final Color QUEEN = new Color(200, 120, 255);

	/** BA Autopilot's box, as the plugin draws it in this situation; the page is about {@code expect}. */
	static TutorialSteps.Pilot pilot(Step expect, Snapshot.SnapshotBuilder situation)
	{
		return new TutorialSteps.Pilot(expect, situation.build());
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

	private Tutorials()
	{
	}
}
