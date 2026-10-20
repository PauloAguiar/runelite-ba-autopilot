package com.bautopilot.baautopilot.tutorial;

import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * The current tutorial page's autopilot box: the planner's instruction for the page's situation.
 * The plugin's own box shows it in place of the live one while the tutorial runs, so it sits
 * where the box always is and moving it moves the real one.
 */
@Singleton
public class TutorialPilots
{
	/** Default planner settings, so a page shows what its text describes whatever the config. */
	private static final Settings SETTINGS = Settings.builder().build();
	private static final long TICK_MS = 600;

	/** An instruction and the moment it is for; {@code highlight} when the page talks about the box. */
	@Value
	public static class View
	{
		Instruction instruction;
		Snapshot snapshot;
		boolean highlight;
	}

	private final TutorialSteps steps;
	private final Planner planner = new Planner();
	/** Each page's instruction, planned once. Client thread. */
	private final Map<TutorialSteps.Pilot, Instruction> planned = new IdentityHashMap<>();

	@Inject
	TutorialPilots(TutorialSteps steps)
	{
		this.steps = steps;
	}

	/**
	 * The current page's box, as if its step had just begun; only the call timer moves, running
	 * down from the situation's value. Null when the page has no box.
	 */
	public View current()
	{
		TutorialSteps.Page page = steps.current();
		TutorialSteps.Pilot pilot = page.getPilot();
		if (pilot == null)
		{
			return null;
		}
		Instruction ins = planned.computeIfAbsent(pilot, p -> planner.plan(p.getSituation(), SETTINGS));
		Snapshot s = pilot.getSituation();
		int ticks = s.getTicksToCallChange();
		if (ticks > 0)
		{
			long elapsed = steps.elapsed() / TICK_MS;
			s = s.toBuilder().ticksToCallChange(ticks - (int) (elapsed % (ticks + 1))).build();
		}
		return new View(ins, s, page.isHighlightBox());
	}
}
