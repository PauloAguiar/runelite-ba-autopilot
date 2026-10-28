package com.bautopilot.baautopilot.tutorial;

import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.overlay.Palette;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Action;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Mark;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Page;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Style;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The basics: each role and the Penance it deals with, then the eggs, the egg launcher, the
 * Healer spring, and how the team makes and fires the Queen's eggs. Callers stand by with their
 * call overhead; only the player doing the job gets a ring.
 */
final class BasicsTutorial
{
	/** The Queen's egg at each step of C-H-A-D-C; the last is the finished egg back with the Collector. */
	static final List<String> CHAIN_EGGS = Arrays.asList(
		"chain-yellow", "chain-poisoned", "chain-spiked", "chain-omega", "chain-loaded");
	/** Who works the egg at each step: Collector, Healer, Attacker, Defender, Collector. */
	static final List<String> CHAIN_PLAYERS = Arrays.asList(
		"chain-collector", "chain-healer", "chain-attacker", "chain-defender", "chain-collector");

	/** The floor arrows of the egg loop. */
	static final Color PASS = new Color(255, 152, 31);

	/** The leader's ring colour. */
	private static final Color LEADER = new Color(255, 152, 31);

	/** The four players of the egg chain, each on its role's ring. */
	static final List<Mark> CHAIN_MARKS = Arrays.asList(
		Tutorials.role("chain-collector", Role.COLLECTOR),
		Tutorials.role("chain-healer", Role.HEALER),
		Tutorials.role("chain-attacker", Role.ATTACKER),
		Tutorials.role("chain-defender", Role.DEFENDER));

	static final List<String> MACHINES = Arrays.asList(
		"machine-attacker", "machine-defender", "machine-healer", "machine-collector");
	private static final List<String> DISPENSER_USERS = Arrays.asList(
		"dsp-attacker", "dsp-defender", "dsp-healer", "dsp-collector");

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("Finding a team")
			.body("Before any wave you need a team of five players. Teams form here, in room 1 of the lobby, where you "
				+ "are now.\n\n"
				+ "If you're looking for a team, type the role you want in the chat, for example \"Def\" for Defender, "
				+ "or \"Coll or Atk\" if you'd play Collector or Attacker.\n\n"
				+ "Teams that are missing a player say what they need: \"Need atk\" means they need an Attacker, and "
				+ "\"Atk2go\" means they're ready to go once an Attacker joins. When a team needs your role, reply to "
				+ "them, and their leader sends you an invite.")
			.prop("lf-1").prop("lf-2").prop("lf-3")
			.says("lf-1", "Def here")
			.says("lf-2", "Atk2go")
			.says("lf-3", "Coll or Atk")
			.camera("team").build(),
		Page.builder().title("Joining a team")
			.body("The team's leader sends you an invite. Pick your role in it and confirm to join.")
			.prop("leader").prop("you")
			.mark(new Mark("leader", "Leader", LEADER, Style.CIRCLE))
			.mark(new Mark("you", "You", Tutorials.PROP, Style.CIRCLE))
			.action(Action.INVITE).camera("team").build(),
		Page.builder().title("Leading a team")
			.body("To lead, take a scroll from the table, use it on each player, and confirm the role they picked.")
			.object(new TutorialSteps.ObjectMark(net.runelite.api.gameval.ObjectID.BARBASSAULT_SCROLL_TABLE, "Scroll table", LEADER))
			.camera(TutorialScene.OBJECT_KEY + net.runelite.api.gameval.ObjectID.BARBASSAULT_SCROLL_TABLE).build(),
		Page.builder().title("Starting a wave")
			.body("Once the team is ready, the leader climbs down the ladder to start the wave. "
				+ "The leader does this for the first wave and for every wave after it.")
			.object(new TutorialSteps.ObjectMark(TutorialScene.LADDER, "Ladder", LEADER))
			.camera(TutorialScene.OBJECT_KEY + TutorialScene.LADDER).build(),
		Page.builder().title("If someone leaves")
			.body("If a player leaves the team after wave 1, the run can't carry on. Come back to room 1's door, "
				+ "right-click the entrance and choose to reset the wave: the team can then start again from the beginning.")
			.object(new TutorialSteps.ObjectMark(net.runelite.api.gameval.ObjectID.BARBASSAULT_DOOR_01, "Room 1 entrance", LEADER))
			.camera(TutorialScene.OBJECT_KEY + net.runelite.api.gameval.ObjectID.BARBASSAULT_DOOR_01).build(),
		Page.builder().title("Inside a wave")
			.body("When the leader climbs down the ladder, the whole team is taken into the arena, and the wave "
				+ "begins.\n\n"
				+ "Every player starts the wave with a horn in their inventory. A role panel also appears on your "
				+ "screen: it shows your role, the time left before the next call, the call you have to make, and the "
				+ "call you heard from your partner.")
			.hud(new TutorialSteps.Hud(Role.ATTACKER, "Red eggs", "Accurate/Field/Water"))
			.item(Tutorials.horn(Role.ATTACKER), 1)
			.props(IntroductionTutorial.TEAM).marks(IntroductionTutorial.TEAM_MARKS).camera("team").build(),
		Page.builder().title("The arena: waves 1 to 9")
			.body("Penance come out of the caves along the north wall. The traps are on the west and east sides, and the egg "
				+ "launchers and hoppers stand on two hills. The hills are safer: Penance can't go up them, though Rangers can still shoot you there. "
				+ "The Healer spring and the dispensers are by the south wall.\n\n"
				+ "This is just a first look at the arena: each of these places is explained on the pages that follow.")
			.map("1-9").build(),
		Page.builder().title("The arena: wave 10")
			.body("Wave 10 has a single egg launcher, and adds poison and lava craters and petrified mushrooms for the Queen's eggs. "
				+ "The Queen appears in the middle.")
			.map("10").build(),
		Page.builder().title("The horn")
			.body("Every 30 seconds, each player gets a new call to make, shown in their role's panel. "
				+ "Right-click the horn in your inventory and pick that Tell- option to send it to your partner. "
				+ "\"Collector: Red eggs!\" tells the Collector which eggs to pick up.\n\n"
				+ "BA Autopilot makes this easier: it colours the right Tell- option green in the horn's menu.")
			.hud(new TutorialSteps.Hud(Role.ATTACKER, "Red eggs", "Accurate/Field/Water"))
			.prop("attacker-1").prop("collector")
			.mark(Tutorials.role("attacker-1", Role.ATTACKER))
			.mark(Tutorials.role("collector", Role.COLLECTOR))
			.arrow(new TutorialSteps.Arrow("attacker-1", "collector", PASS))
			.says("attacker-1", "Collector: Red eggs!")
			.item(Tutorials.horn(Role.ATTACKER), 1).menu(Tutorials.hornMenu(Role.ATTACKER, com.bautopilot.baautopilot.model.BaCall.RED_EGG))
			.action(Action.HORNS).camera("team").build(),
		Page.builder().title("Partners")
			.body("Calls go both ways between partners: Attackers and Collectors call each other, "
				+ "and so do Defenders and Healers. Each call tells the partner what to use.")
			.prop("attacker-1").prop("collector").prop("defender").prop("healer")
			.mark(Tutorials.role("attacker-1", Role.ATTACKER))
			.mark(Tutorials.role("collector", Role.COLLECTOR))
			.mark(Tutorials.role("defender", Role.DEFENDER))
			.mark(Tutorials.role("healer", Role.HEALER))
			.arrow(new TutorialSteps.Arrow("attacker-1", "collector", PASS))
			.arrow(new TutorialSteps.Arrow("collector", "attacker-1", PASS))
			.arrow(new TutorialSteps.Arrow("defender", "healer", PASS))
			.arrow(new TutorialSteps.Arrow("healer", "defender", PASS))
			.says("attacker-1", "Collector: Red eggs!")
			.says("collector", "Attacker: Accurate/Field/Water!")
			.says("defender", "Healer: Poison meat!")
			.says("healer", "Defender: Drop Tofu!")
			.action(Action.HORNS).camera("team").build(),
		Page.builder().title("Roles in battle")
			.body("In a wave, Attackers kill the Fighters and Rangers, the Defender traps the Runners, the Healer poisons "
				+ "the Penance Healers and keeps the team alive, and the Collector gathers the eggs. The next pages cover each role in turn.")
			.props(IntroductionTutorial.TEAM).marks(IntroductionTutorial.TEAM_MARKS).camera("team").build(),
		Page.builder().title("Attacker: the call")
			.body("The Collector calls what the Penance are weak to, in three forms at once: a melee style, "
				+ "an arrow and a spell, such as Accurate/Field/Water. Use the one that fits your weapon.")
			.prop("atk-attacker").prop("atk-collector")
			.prop("atk-bullet").prop("atk-field").prop("atk-blunt").prop("atk-barbed")
			.mark(Tutorials.role("atk-attacker", Role.ATTACKER))
			.mark(new Mark("atk-bullet", "Controlled: Bullet", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("atk-field", "Accurate: Field", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("atk-blunt", "Aggressive: Blunt", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("atk-barbed", "Defensive: Barbed", Tutorials.PROP, Style.OUTLINE))
			.says("atk-collector", "Attacker: Accurate/Field/Water!")
			.camera("atk-spot").build(),
		Page.builder().title("Attacker: weapons")
			.body("*Ranged is the strongest style, and the best choice for new players:* switch to the arrows the call names. "
				+ "Melee is used only in very advanced play, and magic is basically not used in BA.")
			.prop("atk-attacker").prop("atk-collector")
			.prop("atk-bullet").prop("atk-field").prop("atk-blunt").prop("atk-barbed")
			.mark(Tutorials.role("atk-attacker", Role.ATTACKER))
			.mark(new Mark("atk-field", "Field arrows", Tutorials.PROP, Style.OUTLINE))
			.says("atk-collector", "Attacker: Accurate/Field/Water!")
			.camera("atk-spot").build(),
		Page.builder().title("Attacker: Fighters")
			.body("Attackers kill Penance Fighters, using the attack style the Collector calls.")
			.prop("atk-fighter").prop("atk-ranger").prop("atk-attacker").prop("atk-collector")
			.mark(new Mark("atk-fighter", "Penance Fighter", Tutorials.FIGHTER, Style.OUTLINE))
			.mark(Tutorials.role("atk-attacker", Role.ATTACKER))
			.says("atk-collector", "Attacker: Accurate/Field/Water!")
			.action(Action.SHOOT_FIGHTER).camera("atk-spot").build(),
		Page.builder().title("Attacker: Rangers")
			.body("Rangers attack from a distance. Attackers kill them the same way, with the called style.")
			.prop("atk-fighter").prop("atk-ranger").prop("atk-attacker").prop("atk-collector")
			.mark(new Mark("atk-ranger", "Penance Ranger", Tutorials.RANGER, Style.OUTLINE))
			.mark(Tutorials.role("atk-attacker", Role.ATTACKER))
			.says("atk-collector", "Attacker: Controlled/Bullet/Wind!")
			.action(Action.SHOOT_RANGER).camera("atk-spot").build(),
		Page.builder().title("Defender: Runners")
			.body("Runners try to escape. The Defender lures them onto the trap with the bait the Healer calls, and the trap kills them. "
				+ "After two kills the trap breaks, and the Defender fixes it with logs and a hammer.")
			.prop("def-runner").prop("def-trap").prop("def-trap-broken").prop("def-bait").prop("def-defender").prop("def-healer")
			.prop("def-logs").prop("def-hammer")
			.mark(new Mark("def-logs", "Logs", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("def-hammer", "Hammer", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("def-trap-broken", "Broken trap", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("def-runner", "Penance Runner", Tutorials.RUNNER, Style.OUTLINE))
			.mark(new Mark("def-bait", "Crackers", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("def-trap", "Trap", Tutorials.PROP, Style.OUTLINE))
			.mark(Tutorials.role("def-defender", Role.DEFENDER))
			.says("def-healer", "Defender: Drop Crackers!")
			.action(Action.TRAP_RUNNER).camera("def-spot").build(),
		Page.builder().title("Healer: Penance Healers")
			.body("Penance Healers can heal the Runners.")
			.prop("heal-pen").prop("heal-runner").prop("heal-healer").prop("heal-defender")
			.mark(new Mark("heal-pen", "Penance Healer", Tutorials.HEALER, Style.OUTLINE))
			.mark(new Mark("heal-runner", "Runner", Tutorials.RUNNER, Style.OUTLINE))
			.action(Action.HEAL_RUNNER).camera("heal-spot").build(),
		Page.builder().title("Healer: poisoning")
			.body("The Healer kills them with poisoned food: the kind the Defender calls. With several about, "
				+ "go between them, feeding each in turn.")
			.prop("heal-pen").prop("heal-pen-2").prop("heal-healer").prop("heal-defender")
			.mark(new Mark("heal-pen-2", "Penance Healer", Tutorials.HEALER, Style.OUTLINE))
			.mark(new Mark("heal-pen", "Penance Healer", Tutorials.HEALER, Style.OUTLINE))
			.mark(Tutorials.role("heal-healer", Role.HEALER))
			.says("heal-defender", "Healer: Poison meat!")
			.action(Action.POISON_HEALER).camera("heal-spot").build(),
		Page.builder().title("Collector: eggs")
			.body("Dead Penance drop eggs. The Collector picks up the colour the Attacker calls.")
			.prop("col-collector").prop("col-attacker").prop("col-egg-red").prop("col-egg-green").prop("col-egg-blue")
			.mark(Tutorials.role("col-collector", Role.COLLECTOR))
			.mark(new Mark("col-egg-red", "Red egg", new Color(230, 60, 60), Style.OUTLINE))
			.says("col-attacker", "Collector: Red eggs!")
			.action(Action.COLLECT_EGGS).camera("col-spot").build(),
		Page.builder().title("The egg launcher")
			.body("After collecting eggs, the Collector loads them into the egg launcher. The Collector, or any teammate, can then fire them to help kill the Penance.\n\n"
				+ "There's no aiming: you pick the kind of Penance, and the launcher picks which one to hit. "
				+ "Whoever fires, aim in this order: red at Runners, red at Healers, green at Runners, then green at Healers.\n\n"
				+ "BA Autopilot outlines that button in the launcher window and crosses out the stun buttons.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-gunner").prop("can-collector").prop("can-runner")
			.mark(new Mark("can-launcher", "Egg launcher", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("can-hopper", "Egg hopper", Tutorials.PROP, Style.OUTLINE))
			.mark(Tutorials.role("can-collector", Role.COLLECTOR))
			.mark(new Mark("can-gunner", "Teammate", Tutorials.PROP, Style.CIRCLE))
			.action(Action.CANNON_PENANCE).camera("can-spot").build(),
		Page.builder().title("Egg types")
			.body("Red eggs deal 3 damage, and 0-1 to Penance within 3 tiles.\n\n"
				+ "Green eggs deal 1 and poison for 5 more.\n\n"
				+ "Blue eggs stun: the Penance can't move, but it can't be attacked either, which only slows the team down. "
				+ "Blue eggs are basically not used.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper")
			.prop("can-egg-red").prop("can-egg-green").prop("can-egg-blue")
			.mark(new Mark("can-egg-red", "Red: damage", new Color(230, 60, 60), Style.OUTLINE))
			.mark(new Mark("can-egg-green", "Green: poison", new Color(70, 200, 90), Style.OUTLINE))
			.mark(new Mark("can-egg-blue", "Blue: stun (avoid)", new Color(80, 140, 255), Style.OUTLINE))
			.camera("can-spot").build(),
		Page.builder().title("The Healer spring")
			.body("The Healer watches the team's health.\n\n"
				+ "To heal teammates, use the vial on the Healer spring to fill it, then use it on them.\n\n"
				+ "The Healer can also drink from the spring to heal itself.")
			.prop("spring").prop("spr-healer").prop("spr-attacker").prop("spr-vial")
			.mark(new Mark("spr-vial", "Vial", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.mark(Tutorials.role("spr-healer", Role.HEALER))
			.mark(new Mark("spr-attacker", "Teammate", Tutorials.PROP, Style.CIRCLE))
			.item(BaIds.VIAL_FULL, 1).menu(Tutorials.menu(BaIds.VIAL_FULL, "Use", "Use", "Destroy", "Examine"))
			.action(Action.SPRING).camera("spr-spot").build(),
		Page.builder().title("Eggs for the Queen: C-H-A-D-C")
			.body("Once the Queen appears, the team passes each of her eggs around in a fixed order: Collector, Healer, "
				+ "Attacker, Defender, and back to the Collector. Players remember it as CHAD C. "
				+ "Each player works on the egg, then hands it to the next.")
			.prop("chain-collector").prop("chain-healer").prop("chain-attacker").prop("chain-defender")
			.props(CHAIN_EGGS.subList(0, 4))
			.marks(CHAIN_MARKS)
			.arrow(new TutorialSteps.Arrow("chain-collector", "chain-healer", PASS))
			.arrow(new TutorialSteps.Arrow("chain-healer", "chain-attacker", PASS))
			.arrow(new TutorialSteps.Arrow("chain-attacker", "chain-defender", PASS))
			.arrow(new TutorialSteps.Arrow("chain-defender", "chain-collector", PASS))
			.action(Action.EGG_PASS).camera("chain-spot").build(),
		chainStep(0, "C: Collector", "Yellow eggs appear at random on the ground around the Queen. The Collector picks them up and passes them to the Healer.", "Yellow egg"),
		chainStepBuilder(1, "The Healer poisons the egg by using it on a poison crater.", "Poisoned egg")
			.title("H: Healer").prop("chain-crater")
			.mark(new Mark("chain-crater", "Poison crater", new Color(120, 220, 90), Style.OUTLINE))
			.camera("chain-crater-spot").build(),
		chainStepBuilder(2, "The Attacker gets spikes from a petrified mushroom and adds them to the egg.", "Spiked egg")
			.title("A: Attacker").prop("chain-mushroom").prop("chain-spikes")
			.mark(new Mark("chain-mushroom", "Petrified mushroom", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("chain-spikes", "Spikes", Tutorials.PROP, Style.OUTLINE))
			.camera("chain-mushroom-spot").build(),
		chainStepBuilder(3, "The Defender heats the egg by using it on a lava crater, making an omega egg.", "Omega egg")
			.title("D: Defender").prop("chain-lava")
			.mark(new Mark("chain-lava", "Lava crater", new Color(255, 120, 40), Style.OUTLINE))
			.camera("chain-lava-spot").build(),
		chainStep(4, "C: Collector", "The Collector takes the omega egg back to load it.", "Omega egg"),
		Page.builder().title("Defeating the Queen")
			.body("Weapons can't hurt the Queen. The Collector loads the omega eggs into the egg hopper, "
				+ "and teammates fire them at her from the egg launcher.")
			.prop("queen").prop("cannon").prop("turret").prop("hopper").prop("cannon-collector").prop("cannon-gunner")
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.mark(new Mark("cannon", "Egg launcher", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("hopper", "Egg hopper", Tutorials.PROP, Style.OUTLINE))
			.mark(Tutorials.role("cannon-collector", Role.COLLECTOR))
			.mark(new Mark("cannon-gunner", "Teammate", Tutorials.PROP, Style.CIRCLE))
			.action(Action.CANNON_QUEEN).camera("cannon-spot").build(),
		Page.builder().title("Spamming omegas")
			.body("A common strategy is to stack: everyone whose eggs are done stands at the cannon.\n\n"
				+ "Clicking the preloaded cannon opens its aiming window, where the Omega button shows 0, because no omega "
				+ "egg is loaded yet. The players keep clicking that button over and over: this is called *spamming* it.\n\n"
				+ "Meanwhile, the Collector loads the omega eggs one at a time: click one omega egg in the inventory, "
				+ "then click the hopper. Clicking the hopper on its own loads all the eggs at once, which is not what "
				+ "you want here.\n\n"
				+ "Why spam? As soon as an egg is loaded, every player clicking the button fires it, so one egg hits "
				+ "the Queen several times. Eight hits finish her, so the team needs far fewer omega eggs.")
			.prop("queen").prop("cannon").prop("turret").prop("hopper").prop("cannon-collector")
			.prop("cannon-gunner").prop("cannon-stack-1").prop("cannon-stack-2")
			.mark(new Mark("queen", "Penance Queen", Tutorials.QUEEN, Style.OUTLINE))
			.mark(Tutorials.role("cannon-collector", Role.COLLECTOR))
			.mark(new Mark("cannon-gunner", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.mark(new Mark("cannon-stack-1", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.mark(new Mark("cannon-stack-2", "Spamming", Tutorials.PROP, Style.CIRCLE))
			.action(Action.CANNON_QUEEN).camera("cannon-spot").build(),
		Page.builder().title("Dispensers")
			.body("Next to the Healer spring stand the dispensers, one for each role. Players stock up at theirs at the start of a wave, and restock during it.")
			.props(MACHINES).props(DISPENSER_USERS)
			.mark(new Mark("machine-attacker", "Attacker", Palette.role(Role.ATTACKER), Style.OUTLINE))
			.mark(new Mark("machine-defender", "Defender", Palette.role(Role.DEFENDER), Style.OUTLINE))
			.mark(new Mark("machine-healer", "Healer", Palette.role(Role.HEALER), Style.OUTLINE))
			.mark(new Mark("machine-collector", "Collector", Palette.role(Role.COLLECTOR), Style.OUTLINE))
			.camera("dsp-spot").build(),
		dispenser("Attacker dispenser", "Attackers take arrows or runes here.",
			"machine-attacker", "dsp-attacker", Role.ATTACKER),
		dispenser("Defender dispenser", "Defenders take bait here: crackers, tofu or worms.\n\n"
				+ "Stock-Up takes all three kinds of bait in one click, so you're ready for any call.",
			"machine-defender", "dsp-defender", Role.DEFENDER),
		dispenser("Healer dispenser", "Healers take vials and poisoned food here: tofu, worms or meat.\n\n"
				+ "Stock-Up takes all three poisoned foods in one click, so you're ready for any call.",
			"machine-healer", "dsp-healer", Role.HEALER),
		dispenser("Collector Converter", "From Collector level 2, the Collector can convert eggs here: green to red, red to green, "
				+ "and blue to green. Some eggs break: 25% convert at level 2, up to 85% at level 5.",
			"machine-collector", "dsp-collector", Role.COLLECTOR),
		Page.builder().title("That's the basics")
			.body("You now know how a Barbarian Assault run works: the team, the calls, the Penance, the eggs and the Queen. "
				+ "The next tutorials cover one role each, in CHAD order: Collector, Healer, Attacker and Defender, and how BA Autopilot "
				+ "guides you through it, step by step.")
			.props(IntroductionTutorial.TEAM).marks(IntroductionTutorial.TEAM_MARKS).camera("team").build()));

	/** One dispenser: the role's player using it on a loop, the others' machines still in the row. */
	private static Page dispenser(String title, String body, String machine, String user, Role role)
	{
		return Page.builder().title(title).body(body)
			.props(MACHINES).prop(user)
			.mark(new Mark(machine, title, Palette.role(role), Style.OUTLINE))
			.mark(Tutorials.role(user, role))
			.use(new TutorialSteps.Arrow(user, machine, null))
			.action(Action.USE).camera("dsp-spot").build();
	}

	/** One C-H-A-D-C step on repeat: that player works the egg as it is at that step. */
	private static Page chainStep(int step, String title, String body, String egg)
	{
		return chainStepBuilder(step, body, egg).title(title).build();
	}

	static Page.PageBuilder chainStepBuilder(int step, String body, String egg)
	{
		return Page.builder().body(body)
			.prop("chain-collector").prop("chain-healer").prop("chain-attacker").prop("chain-defender")
			.prop(CHAIN_EGGS.get(step))
			.marks(CHAIN_MARKS)
			.mark(new Mark(CHAIN_EGGS.get(step), egg, Tutorials.EGG, Style.OUTLINE))
			.action(Action.EGG_CHAIN).chainStep(step).camera("chain-spot");
	}

	private BasicsTutorial()
	{
	}
}
