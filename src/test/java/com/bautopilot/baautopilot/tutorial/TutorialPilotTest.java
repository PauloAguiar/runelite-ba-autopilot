package com.bautopilot.baautopilot.tutorial;

import static org.junit.Assert.assertTrue;

import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Every tutorial page with an autopilot box: the plugin's planner, given the page's situation,
 * still gives the step the page is about. A planner change that moves a page's step fails here,
 * so the page's text gets looked at again.
 */
public class TutorialPilotTest
{
	@Test
	public void everyPageShowsItsStep()
	{
		Planner planner = new Planner();
		Settings settings = Settings.builder().build();
		List<String> wrong = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (Tutorial tutorial : Tutorial.values())
		{
			for (TutorialSteps.Page page : TutorialTestPages.of(tutorial))
			{
				TutorialSteps.Pilot pilot = page.getPilot();
				if (pilot == null)
				{
					continue;
				}
				Instruction ins = planner.plan(pilot.getSituation(), settings);
				String line = tutorial.getLabel() + " / " + page.getTitle() + ": " + ins.getStep()
					+ " | " + ins.getHeadline() + " | " + String.valueOf(ins.getDetail()).replace("\n", " // ")
					+ (ins.getAdvice() == null ? "" : " | advice: " + ins.getAdvice());
				report.append(line).append('\n');
				if (ins.getStep() != pilot.getExpect())
				{
					wrong.add(line + "   (page is about " + pilot.getExpect() + "; " + ins.getDecidedBy() + ")"
						+ (ins.getPriorities() == null ? "" : ins.getPriorities().stream()
							.map(p -> "\n      " + p.getTier() + " " + p.getTask() + " " + p.getScore() + " " + p.getHeadline() + " " + p.getScoreTerms())
							.collect(java.util.stream.Collectors.joining())));
				}
			}
		}
		// What every page's box says, for reading the pages against: build/tutorial-pilots.txt
		try
		{
			java.nio.file.Files.write(java.nio.file.Paths.get("build", "tutorial-pilots.txt"),
				report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		catch (java.io.IOException e)
		{
			// The report is a convenience; the check below is the test
		}
		assertTrue("Pages whose box shows another step:\n" + String.join("\n", wrong), wrong.isEmpty());
	}
}
