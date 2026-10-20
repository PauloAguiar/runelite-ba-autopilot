package com.galesong.baautopilot.tutorial;

import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.tutorial.TutorialSteps.Mark;
import com.galesong.baautopilot.tutorial.TutorialSteps.Page;
import com.galesong.baautopilot.tutorial.TutorialSteps.Style;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The introduction: what Barbarian Assault is - the team, the waves, the Penance and the Queen. */
final class IntroductionTutorial
{
	static final List<String> TEAM = Arrays.asList("attacker-1", "attacker-2", "defender", "collector", "healer");

	/** The five team members, each on a ring in its role's colour. */
	static final List<Mark> TEAM_MARKS = Arrays.asList(
		Tutorials.role("attacker-1", Role.ATTACKER),
		Tutorials.role("attacker-2", Role.ATTACKER),
		Tutorials.role("defender", Role.DEFENDER),
		Tutorials.role("collector", Role.COLLECTOR),
		Tutorials.role("healer", Role.HEALER));

	/** The camera's slow glide from where it was (room 9's Penance) to the Queen. */
	private static final long QUEEN_PUSH_IN_MS = 3500;

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("Welcome")
			.body("Welcome to the BA Autopilot tutorial. It starts with a brief introduction to Barbarian Assault, "
				+ "then explains how the plugin works. Use Next and Back to move through it.\n\n"
				+ "BA Autopilot is still being tested: there may be bugs, and some mechanics may not work reliably yet. "
				+ "Feel free to join our Discord to give feedback and suggestions. The link is at the top left of the "
				+ "BA Autopilot panel (click the BA Autopilot button in RuneLite's sidebar to open it).\n\n"
				+ "BA Autopilot guides your actions, one step at a time. It never clicks or does anything automatically: "
				+ "you are always the one who plays.")
			.build(),
		Page.builder().title("Your team")
			.body("Barbarian Assault is played by a team of five. Each player has a role: Attacker, Defender, Collector or Healer.")
			.props(TEAM).marks(TEAM_MARKS).camera("team").build(),
		Page.builder().title("Team setups")
			.body("One role is doubled. Teams usually run two Attackers (as shown) or two Healers. "
				+ "A single Healer can struggle, but so can a single weak Attacker: the right setup depends on the players.")
			.props(TEAM).marks(TEAM_MARKS).camera("team").build(),
		Page.builder().title("Changing roles")
			.body("Roles can change between waves. A team might start with two Attackers and switch to two Healers for the last few waves.")
			.props(TEAM).marks(TEAM_MARKS).camera("team").build(),
		waves(),
		Page.builder().title("The Penance")
			.body("Every wave, four kinds of Penance come at the team: Fighters, Rangers, Runners and Healers.")
			.prop("pen-fighter").prop("pen-ranger").prop("pen-runner").prop("pen-healer")
			.mark(new Mark("pen-fighter", "Penance Fighter", Tutorials.FIGHTER, Style.OUTLINE))
			.mark(new Mark("pen-ranger", "Penance Ranger", Tutorials.RANGER, Style.OUTLINE))
			.mark(new Mark("pen-runner", "Penance Runner", Tutorials.RUNNER, Style.OUTLINE))
			.mark(new Mark("pen-healer", "Penance Healer", Tutorials.HEALER, Style.OUTLINE))
			.camera("wave-9").build(),
		Page.builder().title("The Queen")
			.body("Wave 10 starts like the others, but once all its Penance are dead, the Penance Queen appears. "
				+ "*Defeat her to finish the run.*")
			.prop("queen")
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.stop("queen-room").tourMs(QUEEN_PUSH_IN_MS)
			.action(TutorialSteps.Action.QUEEN_RAGE).build()));

	/** The ten-wave page: the camera glides through lobby rooms 1 to 9, one per wave. */
	private static Page waves()
	{
		List<String> stops = new ArrayList<>();
		for (int wave = 1; wave <= 9; wave++)
		{
			stops.add("wave-" + wave);
		}
		return Page.builder().title("Ten waves")
			.body("Barbarian Assault is played in ten waves. Waves 1 to 9 play the same way, each one harder than the last.")
			.prop("pen-fighter").prop("pen-ranger").prop("pen-runner").prop("pen-healer")
			.tour(stops).autoNext(true).build();
	}

	private IntroductionTutorial()
	{
	}
}
