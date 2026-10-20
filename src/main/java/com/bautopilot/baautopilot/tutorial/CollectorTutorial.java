package com.bautopilot.baautopilot.tutorial;

import static com.bautopilot.baautopilot.tutorial.Situations.arena;
import static com.bautopilot.baautopilot.tutorial.Situations.item;
import static com.bautopilot.baautopilot.tutorial.Situations.opening;
import static com.bautopilot.baautopilot.tutorial.Situations.penance;
import static com.bautopilot.baautopilot.tutorial.Tutorials.pilot;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.overlay.SceneOverlay;
import com.bautopilot.baautopilot.plan.ActionKind;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Action;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Mark;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Page;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Style;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.TileMark;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * The Collector, and what BA Autopilot shows them: the attack-style call, the west route, called
 * eggs only, the bag, loading, blue eggs, the launcher, waiting, and the Queen's eggs. The box
 * under each page is the plugin's own, for the moment the page describes.
 */
final class CollectorTutorial
{
	private static final Role ROLE = Role.COLLECTOR;
	private static final Color BLUE = new Color(80, 140, 255);
	private static final List<String> EGGS_ROOM = Arrays.asList(
		"col-collector", "col-attacker", "col-egg-red", "col-egg-green", "col-egg-blue");

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("The Collector")
			.body("When Penance die, they drop eggs. As the Collector, you pick up the eggs of the colour the Attacker "
				+ "calls and load them into the egg launchers, so the team can fire them. You also tell the Attacker "
				+ "which style to use.\n\n"
				+ "BA Autopilot shows you one step at a time: which eggs to pick up, when to load them, and when to fire. "
				+ "It sets the right call on your horn.")
			.props(EGGS_ROOM).prop("col-ranger").prop("col-drop-egg").mark(Tutorials.role("col-collector", ROLE))
			.mark(new Mark("col-ranger", "Penance Ranger", Tutorials.RANGER, Style.OUTLINE))
			.action(Action.EGG_DROP)
			.camera("col-spot").build(),
		Page.builder().title("The BA Autopilot overlay")
			.body(Tutorials.BOX_PAGE)
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.pilot(pilot(Step.COL_COLLECT, arena(ROLE, 4, 21, 40).listenCall(BaCall.RED_EGG)
				.groundItems(ImmutableList.of(item(BaIds.EGG_RED, 4, 22, 42, 3))))).highlightBox(true)
			.camera("col-spot").build(),
		Tutorials.call(ROLE, "col-collector", "col-attacker", "Attacker: Accurate/Field/Water!",
				"Accurate/Field/Water", "Red eggs", BaCall.ACCURATE, arena(ROLE, 4, 21, 40).listenCall(BaCall.RED_EGG))
			.title("Your call")
			.body("Every 30 seconds, the Attacker needs a new call from you: which style to use. When it's time, your "
				+ "game view turns red. That's your signal.\n\n"
				+ "*Stop whatever you're doing and click your horn.* BA Autopilot has already set the right call on it, "
				+ "so one click is enough. The red goes away once you've called.")
			.camera("col-spot").build(),
		Page.builder().title("Opening: the west route")
			.body("At the start of each wave, walk around the west cannon hill to reach the Attackers. Don't wait for a "
				+ "call to start walking.\n\n"
				+ "The route is drawn on the floor for you: follow it, and stop on the marked tile at its end.")
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.tile(new TileMark("col-west-route", "West route", ActionKind.MOVE.getColor()))
			.pilot(pilot(Step.COL_POSITION, opening(ROLE, 4, 18, 29)))
			.camera("col-spot").build(),
		Page.builder().title("Picking up eggs")
			.body("Only pick up eggs of the colour the Attacker called. A wrong egg explodes and hurts you.\n\n"
				+ "Every egg of the called colour gets a dot. A green dot means a left-click on it picks it up. A yellow "
				+ "dot means another item is on top of it: right-click that spot and choose Take on the called egg.\n\n"
				+ "BA Autopilot marks the tile of the next egg to pick up. It prefers eggs you can left-click, unless "
				+ "they are much further away.")
			.props(EGGS_ROOM).prop("col-egg-red-under").mark(Tutorials.role("col-collector", ROLE))
			// As the plugin marks them: the egg to take (tile + badge), a dot on every called egg (green: a
			// left-click takes it, yellow: it's under the green egg); wrong eggs unmarked
			.mark(new Mark("col-egg-red", "PICK UP", ActionKind.GET.getColor(), Style.TARGET))
			.mark(new Mark("col-egg-red", null, SceneOverlay.eggDotColour(false), Style.DOT))
			.mark(new Mark("col-egg-red-under", null, SceneOverlay.eggDotColour(true), Style.DOT))
			.says("col-attacker", "Collector: Red eggs!")
			.pilot(pilot(Step.COL_COLLECT, arena(ROLE, 4, 21, 40).listenCall(BaCall.RED_EGG)
				.groundItems(ImmutableList.of(item(BaIds.EGG_RED, 4, 22, 42, 1), item(BaIds.EGG_GREEN, 4, 20, 41, 1),
					item(BaIds.EGG_RED, 4, 20, 41, 1).withUnderneath(true)))))
			.action(Action.COLLECT_EGGS).camera("col-spot").build(),
		Page.builder().title("Your egg bag")
			.body("Your first eggs go into the collection bag in your inventory, so they don't take up any inventory "
				+ "slots. You don't need to do anything for this: it happens when you pick them up.\n\n"
				+ "The overlay counts the eggs in your bag and in your inventory together, on the line called Carrying.")
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.pilot(pilot(Step.COL_FOLLOW, arena(ROLE, 4, 21, 40).listenCall(BaCall.RED_EGG)
				.inventory(ImmutableMap.of(ItemID.BARBASSAULT_COLL_BAG_01, 1, BaIds.EGG_RED, 3)).freeSlots(21).bagRed(2)))
			.camera("col-spot").build(),
		Page.builder().title("Loading the hopper")
			.body("To load, click the hopper next to the egg launcher. One click loads every egg you carry into it, "
				+ "from your bag and your inventory.\n\n"
				+ "*Load as soon as the overlay asks you to:* your team can only fire the eggs that are in the hopper.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-collector").prop("can-gunner").prop("can-runner")
			.mark(Tutorials.role("can-collector", ROLE))
			.mark(new Mark("can-hopper", "LOAD", ActionKind.USE.getColor(), Style.OUTLINE))
			.pilot(pilot(Step.COL_LOAD, arena(ROLE, 4, 23, 33).listenCall(BaCall.BLUE_EGG)
				.inventory(ImmutableMap.of(BaIds.EGG_RED, 4, BaIds.EGG_GREEN, 2)).freeSlots(14)))
			.action(Action.CANNON_PENANCE).camera("can-spot").build(),
		Page.builder().title("Blue eggs")
			.body("Blue eggs stun the Penance, which doesn't really help your team, so there's no real need for them. "
				+ "Pick them up when blue is called: that still counts.\n\n"
				+ "Loading blue eggs into the hopper is fine, and it's a quick way to free up inventory space. You can "
				+ "also destroy them: when you need the space, the overlay asks you to. Right-click a blue egg in your "
				+ "inventory and choose Destroy, or shift-click it.")
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.mark(new Mark("col-egg-blue", "Blue egg", BLUE, Style.OUTLINE))
			.pilot(pilot(Step.COL_BLUE, arena(ROLE, 4, 21, 40).listenCall(BaCall.GREEN_EGG)
				.inventory(ImmutableMap.of(BaIds.EGG_BLUE, 3)).freeSlots(2)))
			.menu(Tutorials.menu(BaIds.EGG_BLUE, "Destroy", "Use", "Destroy", "Examine"))
			.camera("col-spot").build(),
		Page.builder().title("Firing the launcher")
			.body("If a Runner is escaping, the overlay might guide you to the egg launcher, to help bring it down.\n\n"
				+ "Click the launcher to open its window. BA Autopilot outlines the best button to click, and crosses "
				+ "out the stun buttons you should never use.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-collector").prop("can-gunner").prop("can-runner")
			.mark(new Mark("can-launcher", "FIRE", ActionKind.FIGHT.getColor(), Style.OUTLINE))
			.mark(new Mark("can-runner", "Penance Runner", Tutorials.RUNNER, Style.OUTLINE))
			.pilot(pilot(Step.COL_FIRE, arena(ROLE, 4, 41, 33).listenCall(BaCall.BLUE_EGG).eggsRed(4)
				.penance(ImmutableList.of(penance(1, PenanceType.RUNNER, 4, 42, 26).toBuilder().previousTile(Situations.at(4, 42, 27)).build()))))
			.action(Action.CANNON_PENANCE).camera("can-spot").build(),
		Page.builder().title("Waiting for eggs")
			.body("When there's nothing to pick up, the overlay asks you to wait on a marked tile near the Attackers. Stand "
				+ "on that tile.\n\n"
				+ "Eggs drop where Fighters and Rangers die, so new eggs will appear close to you.")
			.props(EGGS_ROOM).prop("col-ranger").prop("col-drop-egg").mark(Tutorials.role("col-collector", ROLE))
			.mark(new Mark("col-ranger", "Penance Ranger", Tutorials.RANGER, Style.OUTLINE))
			.action(Action.EGG_DROP)
			.tile(new TileMark("col-wait", "Wait here", ActionKind.WAIT.getColor()))
			.pilot(pilot(Step.COL_FOLLOW, arena(ROLE, 4, 21, 40).listenCall(BaCall.GREEN_EGG)))
			.camera("col-spot").build(),
		Page.builder().title("Wave 10: before the Queen")
			.body("In wave 10, eggs don't give points. They're still useful, though: load a few to help bring down the "
				+ "Runners and Penance Healers.\n\n"
				+ "Make sure at least one egg stays loaded in the cannon for the Queen. This is called *preloading*: with "
				+ "an egg loaded, your teammates can aim at the Queen as soon as she appears. BA Autopilot tells you when "
				+ "to do it.")
			.prop("cannon").prop("turret").prop("hopper").prop("cannon-collector")
			.mark(Tutorials.role("cannon-collector", ROLE))
			.mark(new Mark("hopper", "LOAD", ActionKind.USE.getColor(), Style.OUTLINE))
			.pilot(pilot(Step.COL_LOAD, arena(ROLE, 10, 23, 33).listenCall(BaCall.RED_EGG)
				.inventory(ImmutableMap.of(BaIds.EGG_RED, 2)).freeSlots(22)))
			.camera("cannon-spot").build(),
		Page.builder().title("Wave 10: make room")
			.body("Once every Penance is dead and an egg is preloaded, the other eggs you carry are no use any more.\n\n"
				+ "You'll need the space for the Queen's yellow eggs, so the overlay asks you to destroy them. "
				+ "Right-click an egg in your inventory and choose Destroy, or shift-click it.")
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.pilot(pilot(Step.COL_DISCARD, arena(ROLE, 10, 21, 40).listenCall(BaCall.GREEN_EGG).eggsRed(1)
				.fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
				.runnersKilled(WaveInfo.runnersTotal(10)).healersKilled(WaveInfo.healersTotal(10))
				.inventory(ImmutableMap.of(BaIds.EGG_RED, 3, BaIds.EGG_GREEN, 2, BaIds.EGG_BLUE, 2)).freeSlots(15)))
			.menu(Tutorials.menu(BaIds.EGG_RED, "Destroy", "Use", "Destroy", "Examine"))
			.camera("col-spot").build(),
		Page.builder().title("Wave 10: yellow eggs")
			.body("Only omega eggs can hurt the Queen, and every omega egg starts as a yellow egg. When the Queen "
				+ "appears, yellow eggs drop around her.\n\n"
				+ "The overlay asks you to pick up 5 of them first. These 5 are your first batch: the next pages show "
				+ "what happens to them.")
			.prop("queen").prop("q-collector").prop("q-yellow-1").prop("q-yellow-2").prop("q-yellow-3")
			.mark(Tutorials.role("q-collector", ROLE))
			.mark(new Mark("q-yellow-1", "PICK UP", ActionKind.GET.getColor(), Style.TARGET))
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.tile(new TileMark("q-watch", "Watch for yellow eggs", ActionKind.WAIT.getColor()))
			.pilot(pilot(Step.QUEEN_COLLECT, arena(ROLE, 10, 28, 25).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 2))
				.groundItems(ImmutableList.of(item(BaIds.EGG_YELLOW, 10, 26, 27, 1)))))
			.action(Action.QUEEN_RAGE).camera("queen-room").build(),
		Page.builder().title("Wave 10: the egg chain")
			.body("A yellow egg goes round the team before it can hurt the Queen. You pass it to the Healer, who poisons "
				+ "it. The Attacker adds spikes, and the Defender heats it into an omega egg.\n\n"
				+ "The omega egg then comes back to you, ready to load into the cannon. Players call this order CHAD: "
				+ "Collector, Healer, Attacker, Defender.")
			.prop("chain-collector").prop("chain-healer").prop("chain-attacker").prop("chain-defender")
			.props(BasicsTutorial.CHAIN_EGGS.subList(0, 4))
			.marks(BasicsTutorial.CHAIN_MARKS)
			.arrow(new TutorialSteps.Arrow("chain-collector", "chain-healer", BasicsTutorial.PASS))
			.arrow(new TutorialSteps.Arrow("chain-healer", "chain-attacker", BasicsTutorial.PASS))
			.arrow(new TutorialSteps.Arrow("chain-attacker", "chain-defender", BasicsTutorial.PASS))
			.arrow(new TutorialSteps.Arrow("chain-defender", "chain-collector", BasicsTutorial.PASS))
			.action(Action.EGG_PASS).camera("chain-spot").build(),
		Page.builder().title("Wave 10: passing the batch")
			.body("Once you have 5 yellow eggs, the overlay asks you to pass them to the Healer. The Healer is marked for "
				+ "you.\n\n"
				+ "To pass an egg, click a yellow egg in your inventory, then click the Healer. Do this once for each "
				+ "egg.")
			.prop("chain-collector").prop("chain-healer").prop("chain-yellow")
			.mark(Tutorials.role("chain-collector", ROLE))
			.mark(Tutorials.role("chain-healer", Role.HEALER))
			.arrow(new TutorialSteps.Arrow("chain-collector", "chain-healer", BasicsTutorial.PASS))
			.pilot(pilot(Step.QUEEN_PASS, arena(ROLE, 10, 28, 25).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 5)).team(ImmutableMap.of("Healer", Role.HEALER))))
			.menu(Tutorials.menu(BaIds.EGG_YELLOW, "Use", "Use", "Examine"))
			.camera("chain-spot").build(),
		Page.builder().title("Wave 10: collecting the reserve")
			.body("After you pass the first 5 yellow eggs, the overlay asks you to pick up 3 more. These 3 eggs are the "
				+ "*reserve*.\n\n"
				+ "The reserve is there in case the first 5 omega eggs aren't enough to kill the Queen. If your team "
				+ "stacks properly at the cannon (explained a few pages on), each egg can hit the Queen several times, so "
				+ "fewer eggs are needed and the reserve might not be used at all.\n\n"
				+ "Pick them up the same way as before: click each marked yellow egg on the ground.\n\n"
				+ "You can change both numbers. To open the panel, click the BA Autopilot button in RuneLite's sidebar. "
				+ "On the Collector card, set Queen yellow batch (the first batch) and Queen reserve eggs (the reserve).")
			.prop("queen").prop("q-collector").prop("q-yellow-1").prop("q-yellow-2")
			.mark(Tutorials.role("q-collector", ROLE))
			.mark(new Mark("q-yellow-1", "PICK UP", ActionKind.GET.getColor(), Style.TARGET))
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_COLLECT, arena(ROLE, 10, 28, 25).queenAlive(true).queenYellowPassed(5)
				.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 1)).team(ImmutableMap.of("Healer", Role.HEALER))
				.groundItems(ImmutableList.of(item(BaIds.EGG_YELLOW, 10, 26, 27, 1)))))
			.action(Action.QUEEN_RAGE).camera("queen-room").build(),
		Page.builder().title("Wave 10: holding the reserve")
			.body("Keep the 3 reserve eggs in your inventory. Don't pass them to the Healer yet: the first 5 omega eggs "
				+ "may be enough to kill the Queen on their own, especially if your team stacks properly at the cannon.\n\n"
				+ "If the Queen is still alive after the first 5 omega eggs have been fired, the overlay asks you to pass "
				+ "the reserve to the Healer. Pass them the same way as the first batch: click a yellow egg, then click "
				+ "the Healer.")
			.prop("queen").prop("q-collector").mark(Tutorials.role("q-collector", ROLE))
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_WAIT, arena(ROLE, 10, 28, 25).queenAlive(true).queenYellowPassed(5)
				.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 3)).team(ImmutableMap.of("Healer", Role.HEALER))))
			.action(Action.QUEEN_RAGE).camera("queen-room").build(),
		Page.builder().title("Wave 10: loading omegas")
			.body("The finished omega eggs come back to you from the Defender. You load them into the hopper one at a "
				+ "time.\n\n"
				+ "To load one egg, click an omega egg in your inventory, then click the hopper. *Don't just click the "
				+ "hopper:* that loads all of them at once.")
			.prop("queen").prop("cannon").prop("turret").prop("hopper").prop("cannon-collector").prop("cannon-gunner")
			.mark(Tutorials.role("cannon-collector", ROLE))
			.mark(new Mark("hopper", "LOAD 1", ActionKind.USE.getColor(), Style.OUTLINE))
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_LOAD_ONE, arena(ROLE, 10, 22, 32).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 3))))
			.menu(Tutorials.menu(BaIds.EGG_OMEGA, "Use", "Use", "Examine"))
			.action(Action.CANNON_QUEEN).camera("cannon-spot").build(),
		Page.builder().title("Wave 10: spamming omegas")
			.body("Many teams stack at the cannon: they wait there and keep clicking the Omega button, *spamming* it, "
				+ "so each omega egg is fired as soon as you load it.\n\n"
				+ "Why is spamming useful? When several players fire the same loaded egg, that one egg can hit the Queen "
				+ "several times. That reduces the total number of omega eggs your team needs.\n\n"
				+ "If your team plays this way, turn on the setting that tells BA Autopilot. To open the panel, click "
				+ "the BA Autopilot button in RuneLite's sidebar. Open the General & display section, and turn on Team "
				+ "stacks at Queen cannon.")
			.prop("queen").prop("cannon").prop("turret").prop("hopper").prop("cannon-collector")
			.prop("cannon-gunner").prop("cannon-stack-1").prop("cannon-stack-2")
			.mark(Tutorials.role("cannon-collector", ROLE))
			.mark(new Mark("cannon-gunner", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.mark(new Mark("cannon-stack-1", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.mark(new Mark("cannon-stack-2", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.pilot(pilot(Step.QUEEN_LOAD_ONE, arena(ROLE, 10, 22, 32).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 2))))
			.action(Action.CANNON_QUEEN).camera("cannon-spot").build(),
		Page.builder().title("Collector: done")
			.body("That's the Collector. Click Next to continue with the Healer tutorial.")
			.props(EGGS_ROOM).mark(Tutorials.role("col-collector", ROLE))
			.camera("col-spot").build()));

	private CollectorTutorial()
	{
	}
}
