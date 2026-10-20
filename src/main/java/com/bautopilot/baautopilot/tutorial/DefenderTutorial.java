package com.bautopilot.baautopilot.tutorial;

import static com.bautopilot.baautopilot.tutorial.Situations.arena;
import static com.bautopilot.baautopilot.tutorial.Situations.at;
import static com.bautopilot.baautopilot.tutorial.Situations.item;
import static com.bautopilot.baautopilot.tutorial.Situations.opening;
import static com.bautopilot.baautopilot.tutorial.Situations.penance;
import static com.bautopilot.baautopilot.tutorial.Tutorials.pilot;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.ActionKind;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Action;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Mark;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Page;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.Style;
import com.bautopilot.baautopilot.tutorial.TutorialSteps.TileMark;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * The Defender, and what BA Autopilot shows them: the poison call, Stock-Up, the bait pattern,
 * logs and hammer, good and bad bait, runner outlines, stepping off the food, repairs, escaping
 * runners, restocking, and the Queen's lava crater. The plugin's own box shows each page's step.
 */
final class DefenderTutorial
{
	private static final Role ROLE = Role.DEFENDER;
	/** Pattern tiles: gold while good bait is still due, purple for bad bait (the plugin's colours). */
	private static final Color GOOD = new Color(255, 196, 0);
	private static final Color BAD = new Color(190, 120, 255);
	/** Runner outlines by how trapped they are. */
	private static final Color ESCAPING = new Color(240, 70, 60);
	private static final Color LURED = new Color(150, 230, 110);
	private static final List<String> TRAP_ROOM = Arrays.asList(
		"def-runner", "def-trap", "def-trap-broken", "def-bait", "def-defender", "def-healer", "def-logs", "def-hammer");
	private static final List<String> QUIET_ROOM = Arrays.asList(
		"def-trap", "def-bait", "def-defender", "def-healer", "def-logs", "def-hammer");
	private static final int WAVE = 4;

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("Before you start")
			.body("The Defender is probably the hardest role to get started with. Try to get a good idea of the minigame "
				+ "first, for example by playing the other roles.\n\n"
				+ "The Defender also benefits a lot from acting quickly at the start of each wave: the faster you drop the "
				+ "bait pattern, the better the Runners are led to the trap.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.camera("def-spot").build(),
		Page.builder().title("The Defender")
			.body("As the Defender, you lure the Penance Runners onto the trap with the bait the Healer calls, and you "
				+ "repair the trap when it breaks. You also tell the Healer which poisoned food to use.\n\n"
				+ "BA Autopilot shows you one step at a time: where to drop bait, when to repair the trap, and when a "
				+ "Runner is escaping. It sets the right call on your horn.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.camera("def-spot").build(),
		Page.builder().title("The BA Autopilot overlay")
			.body(Tutorials.BOX_PAGE)
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.pilot(pilot(Step.DEF_WAIT_CALL, ready().listenCall(null).listenFresh(false))).highlightBox(true)
			.camera("def-spot").build(),
		Tutorials.call(ROLE, "def-defender", "def-healer", "Healer: Poison worms!",
				"Pois. Worms", "Drop Crackers", BaCall.POISON_WORMS, ready())
			.title("Your call")
			.body("Every 30 seconds, the Healer needs a new call from you: which poisoned food to use. When it's time, "
				+ "your game view turns red. That's your signal.\n\n"
				+ "*Stop whatever you're doing and click your horn.* BA Autopilot has already set the right call on it, "
				+ "so one click is enough. The red goes away once you've called.")
			.camera("def-spot").build(),
		Page.builder().title("Opening: Stock-Up")
			.body("Start at your dispenser. Click it once: its left-click is Stock-Up, which gives you all three kinds "
				+ "of bait in one go.")
			.props(BasicsTutorial.MACHINES).prop("dsp-defender")
			.mark(new Mark("machine-defender", "Stock-Up", ActionKind.GET.getColor(), Style.OUTLINE))
			.mark(Tutorials.role("dsp-defender", ROLE))
			.use(new TutorialSteps.Arrow("dsp-defender", "machine-defender", null))
			.pilot(pilot(Step.DEF_TAKE_BAIT, opening(ROLE, WAVE, 34, 16)))
			.action(Action.USE).camera("dsp-spot").build(),
		Page.builder().title("Good bait and its range")
			.body("Runners only notice bait that is close to them: within 4 tiles, or 5 tiles from Defender level 2. On "
				+ "the ground, BA Autopilot outlines the tiles around each bait where a Runner notices it.\n\n"
				+ "Good bait is the bait the Healer called. A Runner that notices good bait walks to it and eats it. If "
				+ "the bait is on the trap's food tile, the trap kills the Runner while it eats.\n\n"
				+ "A bait counts as good or bad by the call at the moment it hits the ground, so wait for the Healer's "
				+ "call before you drop it.")
			.props(TRAP_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-bait", "Good bait", GOOD, Style.OUTLINE))
			.mark(new Mark("def-runner", "Penance Runner", Tutorials.RUNNER, Style.OUTLINE))
			.action(Action.TRAP_RUNNER).camera("def-spot").build(),
		Page.builder().title("Bad bait")
			.body("Bad bait is any bait the Healer didn't call. A Runner that notices bad bait walks to it, eats it, and "
				+ "then runs several tiles north.\n\n"
				+ "That makes bad bait useful too: drop it in a Runner's way to send it north, back towards the trap. The "
				+ "bait pattern uses a few bad baits for this.")
			.props(QUIET_ROOM).prop("def-runner-esc").mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-runner-esc", "Penance Runner", Tutorials.RUNNER, Style.OUTLINE))
			.tile(new TileMark("pat-safety", "Bad bait", BAD))
			.camera("def-spot").build(),
		Page.builder().title("Good and bad bait")
			.body("Your inventory marks each bait as GOOD or BAD for the current call. Good bait is the one the Healer "
				+ "called.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-bait", "4 good", GOOD, Style.OUTLINE))
			.tile(new TileMark("pat-safety", "1 bad", BAD))
			.pilot(pilot(Step.DEF_WATCH, beside().trapCharges(2).runnersKilled(WaveInfo.runnersTotal(WAVE) - 1)
				.penance(ImmutableList.of(penance(1, PenanceType.RUNNER, WAVE, 35, 45)))))
			.camera("def-spot").build(),
		Page.builder().title("The bait pattern")
			.body("In the first call, drop bait on the marked tiles. Each tile shows how much good or bad bait it "
				+ "needs.\n\n"
				+ "To drop bait, stand on the tile, then click the bait in your inventory: its left-click is Drop. Good "
				+ "bait pulls Runners to the trap, and bad bait sends them north. *Drop it early.*")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.tile(new TileMark("pat-trap", "Trap 0/4 good", GOOD))
			.tile(new TileMark("pat-trail", "Trail 1 bad", BAD))
			.tile(new TileMark("pat-main", "Mainstack 0/4 good", GOOD))
			.tile(new TileMark("pat-west", "West relure 1 bad", BAD))
			.tile(new TileMark("pat-safety", "Safety 1 bad", BAD))
			.pilot(pilot(Step.DEF_DROP, opening(ROLE, WAVE, 45, 35).inventory(baits()).listenCall(BaCall.BAIT_CRACKERS)
				.listenFresh(true).waveTick(12)))
			.camera("def-spot").build(),
		Page.builder().title("Logs and hammer")
			.body("Then pick up the hammer and the logs in one trip: click each of them on the ground. You need them to "
				+ "repair the trap.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-logs", "LOGS", ActionKind.GET.getColor(), Style.OUTLINE))
			.mark(new Mark("def-hammer", "Hammer", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.DEF_HAMMER, patterned().playerTile(at(WAVE, 31, 44)).groundItems(tools())))
			.camera("def-spot").build(),
		Page.builder().title("Runner outlines")
			.body("Runners are outlined in green when they're lured, and in red when they're escaping.\n\n"
				+ "Don't stand on the trap's food tile: Runners can't eat it while you're on it. The overlay marks the tile "
				+ "to step onto.")
			.props(TRAP_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-runner", "Lured", LURED, Style.OUTLINE))
			.mark(new Mark("def-trap", "Trap", Tutorials.PROP, Style.OUTLINE))
			.says("def-healer", "Defender: Drop Crackers!")
			.pilot(pilot(Step.DEF_SERVICE, equipped().trapCharges(2)
				.penance(ImmutableList.of(penance(1, PenanceType.RUNNER, WAVE, 44, 37)))))
			.tile(new TileMark("def-clear", "Stand clear", ActionKind.MOVE.getColor()))
			.action(Action.TRAP_RUNNER).camera("def-spot").build(),
		Page.builder().title("Repairing the trap")
			.body("After two kills, the trap breaks. You need logs and the hammer in your inventory to fix it.\n\n"
				+ "Stand on the food tile and click the trap. With logs and the hammer, its left-click is Fix.")
			.props(QUIET_ROOM).prop("def-trap-broken").mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-trap-broken", "REPAIR", ActionKind.USE.getColor(), Style.OUTLINE))
			.pilot(pilot(Step.DEF_REPAIR, equipped().trapCharges(0).objects(brokenTrap())))
			.action(Action.TRAP_REPAIR).camera("def-spot").build(),
		Page.builder().title("Escaping runners")
			.body("A Runner that gets past the trap is escaping, and an escape costs your team points.\n\n"
				+ "*Get ahead of it:* walk to the marked tile. Then click a bad bait in your inventory to drop it: the "
				+ "Runner eats it and runs north, back towards the trap.")
			.props(QUIET_ROOM).prop("def-runner-esc").mark(Tutorials.role("def-defender", ROLE))
			.mark(new Mark("def-runner-esc", "Escaping", ESCAPING, Style.OUTLINE))
			.tile(new TileMark("def-ahead", "Get here", ActionKind.MOVE.getColor()))
			.pilot(pilot(Step.DEF_RELURE, beside().trapCharges(2).runnersKilled(WaveInfo.runnersTotal(WAVE) - 1)
				.penance(ImmutableList.of(penance(1, PenanceType.RUNNER, WAVE, 45, 27).toBuilder()
					.previousTile(at(WAVE, 45, 28)).build()))))
			.camera("def-spot").build(),
		Page.builder().title("Restocking")
			.body("Runners eat the bait. When a tile runs out, the overlay sends you to drop more there, the same way as "
				+ "before.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.tile(new TileMark("pat-main", "Mainstack", GOOD))
			.pilot(pilot(Step.DEF_DROP, beside().trapCharges(2).groundItems(trapAndTrail())))
			.camera("def-spot").build(),
		Page.builder().title("All Runners dead")
			.body("Once every Runner is dead, help at the egg launcher. Click it when the overlay asks, then click the "
				+ "button BA Autopilot outlines.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-collector").prop("can-gunner").prop("can-runner")
			.mark(new Mark("can-gunner", "Defender", com.bautopilot.baautopilot.overlay.Palette.role(ROLE), Style.CIRCLE))
			.mark(new Mark("can-launcher", "FIRE", ActionKind.FIGHT.getColor(), Style.OUTLINE))
			.pilot(pilot(Step.DEF_SUPPORT, beside().cleared(ImmutableSet.of(PenanceType.RUNNER))
				.runnersKilled(WaveInfo.runnersTotal(WAVE))))
			.action(Action.CANNON_PENANCE).camera("can-spot").build(),
		BasicsTutorial.chainStepBuilder(3, "In wave 10, the Attacker passes you spiked eggs. With a spiked egg in your "
				+ "inventory, click a lava crater: the egg becomes an omega egg.\n\n"
				+ "Then pass the omega egg to the Collector: click it in your inventory, then click the Collector.", "Omega egg")
			.title("Wave 10: lava crater").prop("chain-lava")
			.mark(new Mark("chain-lava", "Lava crater", new Color(255, 120, 40), Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_LAVA, arena(ROLE, 10, 17, 20).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_SPIKED, 1))))
			.menu(Tutorials.menu(BaIds.EGG_SPIKED, "Use", "Use", "Examine"))
			.camera("chain-lava-spot").build(),
		Page.builder().title("Wave 10: waiting")
			.body("Leave the Queen alone: weapons can't hurt her. Wait by the Healer spring for the next spiked egg.")
			.prop("spring").prop("spr-healer").prop("spr-attacker")
			.mark(new Mark("spr-attacker", "Defender", com.bautopilot.baautopilot.overlay.Palette.role(ROLE), Style.CIRCLE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_WAIT, arena(ROLE, 10, 27, 16).queenAlive(true)))
			.camera("spr-spot").build(),
		Page.builder().title("Defender: done")
			.body("That's the Defender, and all four roles. You're ready for a run: BA Autopilot will guide you through "
				+ "each wave.")
			.props(QUIET_ROOM).mark(Tutorials.role("def-defender", ROLE))
			.camera("def-spot").build()));

	/** All three baits, as Stock-Up gives them. */
	private static ImmutableMap<Integer, Integer> baits()
	{
		return ImmutableMap.of(BaCall.BAIT_TOFU.getItemId(), 9, BaCall.BAIT_CRACKERS.getItemId(), 9, BaCall.BAIT_WORMS.getItemId(), 9);
	}

	/** On the trap's food tile, stocked up, after a Crackers call. */
	private static Snapshot.SnapshotBuilder ready()
	{
		return arena(ROLE, WAVE, 45, 35).listenCall(BaCall.BAIT_CRACKERS).inventory(baits());
	}

	/** The whole pattern dropped. */
	private static Snapshot.SnapshotBuilder patterned()
	{
		Map<WorldPoint, Integer> good = new java.util.HashMap<>();
		Map<WorldPoint, Integer> bad = new java.util.HashMap<>();
		for (ArenaTiles.FoodSpot f : ArenaTiles.defenderPattern(WAVE))
		{
			(f.isGood() ? good : bad).put(f.getTile(), f.getCount());
		}
		return ready().goodBaitLanded(ImmutableMap.copyOf(good)).badBaitLanded(ImmutableMap.copyOf(bad))
			.groundItems(trapBait());
	}

	/** Pattern down, logs and hammer in the inventory, on the food tile. */
	private static Snapshot.SnapshotBuilder equipped()
	{
		Map<Integer, Integer> inv = new java.util.HashMap<>(baits());
		inv.put(BaIds.LOGS, 1);
		inv.put(BaIds.HAMMER, 1);
		return patterned().inventory(ImmutableMap.copyOf(inv)).hasHammer(true).logsTaken(2);
	}

	/** Equipped, a tile north of the trap food, where the plugin has you stand clear. */
	private static Snapshot.SnapshotBuilder beside()
	{
		return equipped().playerTile(ArenaTiles.defenderService(WAVE));
	}

	/** The 4 good bait on the trap's food tile. */
	private static ImmutableList<GroundItemView> trapBait()
	{
		WorldPoint food = ArenaTiles.trapFood(WAVE);
		return ImmutableList.of(new GroundItemView(BaCall.BAIT_CRACKERS.getItemId(), food, 4, true, Situations.TICK - 30));
	}

	/** The trap's good bait and the trail's bad bait still down; the mainstack eaten. */
	private static ImmutableList<GroundItemView> trapAndTrail()
	{
		List<GroundItemView> out = new ArrayList<>(trapBait());
		ArenaTiles.defenderPattern(WAVE).stream().filter(f -> f.getName().equals("Trail")).findFirst()
			.ifPresent(f -> out.add(new GroundItemView(BaCall.BAIT_TOFU.getItemId(), f.getTile(), 1, false, Situations.TICK - 30)));
		return ImmutableList.copyOf(out);
	}

	/** The logs and hammer where they lie at a wave's start. */
	private static ImmutableList<GroundItemView> tools()
	{
		List<GroundItemView> out = new ArrayList<>(trapBait());
		out.add(item(BaIds.LOGS, WAVE, 29, 46, 1));
		out.add(item(BaIds.LOGS, WAVE, 28, 47, 1));
		out.add(item(BaIds.HAMMER, WAVE, 32, 42, 1));
		return ImmutableList.copyOf(out);
	}

	/** The arena with the east trap broken. */
	private static ImmutableList<ObjectView> brokenTrap()
	{
		List<ObjectView> out = new ArrayList<>();
		for (ObjectView o : Situations.objects(WAVE))
		{
			out.add(o.getId() == BaIds.TRAP_FULL && o.getTile().equals(at(WAVE, 45, 34)) ? new ObjectView(BaIds.TRAP_BROKEN, o.getTile()) : o);
		}
		return ImmutableList.copyOf(out);
	}

	private DefenderTutorial()
	{
	}
}
