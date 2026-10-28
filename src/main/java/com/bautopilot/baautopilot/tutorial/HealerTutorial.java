package com.bautopilot.baautopilot.tutorial;

import static com.bautopilot.baautopilot.tutorial.Situations.TICK;
import static com.bautopilot.baautopilot.tutorial.Situations.arena;
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
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.state.TeammateView;
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
 * The Healer, and what BA Autopilot shows them: the bait call, Stock-Up, the healer spawn, poison
 * outlines and the healer table, feeding and top-ups, healing teammates, the spring, and the
 * Queen's eggs. The plugin's own box shows each page's step.
 */
final class HealerTutorial
{
	private static final Role ROLE = Role.HEALER;
	private static final List<String> HEAL_ROOM = Arrays.asList("heal-pen", "heal-pen-2", "heal-healer", "heal-defender");
	private static final List<String> SPRING_ROOM = Arrays.asList("spring", "spr-healer", "spr-attacker", "spr-vial");

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("The Healer")
			.body("As the Healer, you poison the Penance Healers with the food the Defender calls, and you heal your "
				+ "teammates with the vial. You also tell the Defender which bait the Runners want.\n\n"
				+ "BA Autopilot shows you one step at a time: which food to use and on which Penance Healer, when to "
				+ "restock, and who to heal. It colours the right call green in your horn's menu.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.camera("heal-spot").build(),
		Page.builder().title("The BA Autopilot overlay")
			.body(Tutorials.BOX_PAGE)
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.pilot(pilot(Step.HEAL_WAIT_CALL, stocked(4).listenCall(null).listenFresh(false)
				.penance(ImmutableList.of(unfed(1))))).highlightBox(true)
			.camera("heal-spot").build(),
		Tutorials.call(ROLE, "heal-healer", "heal-defender", "Defender: Drop Tofu!",
				"Drop Tofu", "Poison worms", BaCall.BAIT_TOFU, stocked(4).penance(ImmutableList.of(fed(1, 4))))
			.title("Your call")
			.body("Every 30 seconds, the Defender needs a new call from you: which bait the Runners want. When it's "
				+ "time, your game view turns red. That's your signal.\n\n"
				+ "*Stop whatever you're doing.* Right-click your horn and pick the Tell- option that BA Autopilot "
				+ "colours green. The red goes away once you've called.")
			.camera("heal-spot").build(),
		Page.builder().title("Opening: Stock-Up")
			.body("Start every wave at your dispenser. Click it once: its left-click is Stock-Up, which gives you all "
				+ "three kinds of poisoned food in one go.")
			.props(BasicsTutorial.MACHINES).prop("dsp-healer")
			.mark(new Mark("machine-healer", "Stock-Up", ActionKind.GET.getColor(), Style.OUTLINE))
			.mark(Tutorials.role("dsp-healer", ROLE))
			.use(new TutorialSteps.Arrow("dsp-healer", "machine-healer", null))
			.pilot(pilot(Step.HEAL_TAKE_FOOD, opening(ROLE, 4, 35, 16).inventory(ImmutableMap.of(BaIds.VIAL_FULL, 1))))
			.action(Action.USE).camera("dsp-spot").build(),
		Page.builder().title("Opening: the healer spawn")
			.body("Then walk to where the Penance Healers come out, and wait there for the Defender's call.\n\n"
				+ "The call tells you which food the Penance Healers want. Don't feed them before it comes.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.tile(new TileMark("heal-spawn", "Healers", ActionKind.MOVE.getColor()))
			.pilot(pilot(Step.HEAL_WAIT_CALL, opening(ROLE, 4, 35, 16).inventory(foods())))
			.camera("heal-spot").build(),
		Page.builder().title("Poison outlines")
			.body("Each Penance Healer is outlined by its poison: green while it's fresh, then light green, yellow and "
				+ "orange as it fades, and red when it has none, because it was never fed or the poison wore off. Feed "
				+ "the red ones first.\n\n"
				+ "BA Autopilot marks the one to feed next: it gets a thicker outline and a FEED badge above its head, "
				+ "with the food to use. The overlay names it too, so you can simply follow it.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			// As the plugin draws them: each healer's poison outline, and the step's FEED target on top
			.mark(poison("heal-pen", fed(1, 4)))
			.mark(poison("heal-pen-2", unfed(2)))
			.mark(feedTarget("heal-pen-2"))
			.pilot(pilot(Step.HEAL_FEED, stocked(4).penance(ImmutableList.of(fed(1, 4), unfed(2)))))
			.camera("heal-spot").build(),
		Page.builder().title("Feeding")
			.body("To feed a Penance Healer, click the called food in your inventory (it's outlined), then click the "
				+ "Penance Healer.\n\n"
				+ "The overlay may ask you to feed the same Penance Healer again: that brings it down quicker.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.mark(poison("heal-pen", fed(1, 40)))
			.mark(poison("heal-pen-2", fed(2, 4)))
			.mark(feedTarget("heal-pen"))
			.says("heal-defender", "Healer: Poison worms!")
			.pilot(pilot(Step.HEAL_FEED, stocked(4).penance(ImmutableList.of(fed(1, 40), fed(2, 4)))))
			.menu(Tutorials.menu(BaCall.POISON_WORMS.getItemId(), "Use", "Use", "Destroy", "Examine"))
			.action(Action.POISON_HEALER).camera("heal-spot").build(),
		Page.builder().title("The healer table")
			.body("At the bottom of the overlay, you can see each Penance Healer's food and poison, how full your vial is, and your "
				+ "two most hurt teammates.\n\n"
				+ "You don't need to act on this: the overlay already tells you what to do next.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.pilot(pilot(Step.HEAL_SUPPORT, lastHealers().penance(ImmutableList.of(fed(1, 4), fed(2, 6)))
				.teammates(team(62, 80)))).highlightBox(true)
			.camera("heal-spot").build(),
		Page.builder().title("Keeping food")
			.body("You need food for the Penance Healers that haven't come out yet.\n\n"
				+ "When you're running low, the overlay sends you back to your dispenser for more. Right-click the "
				+ "dispenser and pick the Take- option for the food it names. Its left-click, Stock-Up, takes all three.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.pilot(pilot(Step.HEAL_TAKE_FOOD, stocked(9).penance(ImmutableList.of(fed(1, 4), fed(2, 6)))
				.inventory(ImmutableMap.of(BaCall.POISON_WORMS.getItemId(), 1, BaIds.VIAL_FULL, 1))))
			.camera("heal-spot").build(),
		Page.builder().title("Healing teammates")
			.body("To fill your vial, click the vial in your inventory, then click the Healer spring.\n\n"
				+ "To heal a teammate, click the vial, then click the teammate that the overlay names.")
			.props(SPRING_ROOM).mark(Tutorials.role("spr-healer", ROLE))
			.mark(new Mark("spr-attacker", "Teammate 42%", new Color(240, 160, 60), Style.CIRCLE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.HEAL_TEAMMATE, lastHealers().penance(ImmutableList.of(fed(1, 4), fed(2, 6)))
				.teammates(team(42, 90))))
			.menu(Tutorials.menu(BaIds.VIAL_FULL, "Use", "Use", "Destroy", "Examine"))
			.action(Action.SPRING).camera("spr-spot").build(),
		Page.builder().title("Staying alive")
			.body("Only you, the Healer, can drink from the spring. When your own health is low, the overlay sends you "
				+ "there first.\n\n"
				+ "Click the spring to drink from it, then go back to what the overlay tells you.")
			.props(SPRING_ROOM).mark(Tutorials.role("spr-healer", ROLE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.RETREAT, stocked(4).hp(14).penance(ImmutableList.of(fed(1, 4)))))
			.action(Action.SPRING).camera("spr-spot").build(),
		BasicsTutorial.chainStepBuilder(1, "In wave 10, the Collector passes you yellow eggs. To poison one, click the "
				+ "yellow egg in your inventory, then click a poison crater. *Don't click the crater on its own.*\n\n"
				+ "Then pass the poisoned egg to the Attacker: click it in your inventory, then click the Attacker.", "Poisoned egg")
			.title("Wave 10: poisoning eggs").prop("chain-crater")
			.mark(new Mark("chain-crater", "Poison crater", new Color(120, 220, 90), Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_DUNK, arena(ROLE, 10, 19, 18).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 2, BaIds.VIAL_FULL, 1))))
			.menu(Tutorials.menu(BaIds.EGG_YELLOW, "Use", "Use", "Destroy", "Examine"))
			.camera("chain-crater-spot").build(),
		Page.builder().title("Wave 10: by the spring")
			.body("Between eggs, stay by the Healer spring. Hurt teammates can come to you there, and you can refill "
				+ "your vial right away.")
			.props(SPRING_ROOM).mark(Tutorials.role("spr-healer", ROLE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.HEAL_FILL_VIAL, arena(ROLE, 10, 27, 15).queenAlive(true)
				.inventory(ImmutableMap.of(ItemID.BARBASSAULT_VIAL_04_HALF, 1))
				.teammates(team(85, 90))))
			.action(Action.SPRING).camera("spr-spot").build(),
		Page.builder().title("Healer: done")
			.body("That's the Healer. Click Attacker > to continue with the Attacker tutorial.")
			.props(HEAL_ROOM).mark(Tutorials.role("heal-healer", ROLE))
			.camera("heal-spot").build()));

	/** All three poisoned foods, as Stock-Up gives them, and a full vial. */
	private static ImmutableMap<Integer, Integer> foods()
	{
		return ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 4, BaCall.POISON_WORMS.getItemId(), 4,
			BaCall.POISON_MEAT.getItemId(), 4, BaIds.VIAL_FULL, 1);
	}

	/** By the healer cave, stocked up, after a Poisoned worms call. */
	private static Snapshot.SnapshotBuilder stocked(int wave)
	{
		return arena(ROLE, wave, 42, 42).listenCall(BaCall.POISON_WORMS).inventory(foods());
	}

	/** The last two Penance Healers are out: none left to spawn. */
	private static Snapshot.SnapshotBuilder lastHealers()
	{
		return stocked(4).healersKilled(WaveInfo.healersTotal(4) - 2)
			.killsByType(ImmutableMap.of(PenanceType.HEALER, WaveInfo.healersTotal(4) - 2));
	}

	/** The plugin's poison outline for this Penance Healer (the same one the page's situation holds). */
	private static Mark poison(String key, PenanceView h)
	{
		return new Mark(key, null, SceneOverlay.poisonColour(h.nextPoisonHit(TICK)), Style.THIN);
	}

	/** The plugin's mark on the Penance Healer the FEED step targets. */
	private static Mark feedTarget(String key)
	{
		return new Mark(key, Step.HEAL_FEED.getTag(), Step.HEAL_FEED.getKind().getColor(), Style.NPC_TARGET);
	}

	/** A Penance Healer by its cave, never fed. */
	private static PenanceView unfed(int order)
	{
		return penance(order, PenanceType.HEALER, 4, 40 + 2 * order, 44);
	}

	/** One fed this many ticks ago. */
	private static PenanceView fed(int order, int ticksAgo)
	{
		return unfed(order).toBuilder().foods(1).foodsThisPeriod(1).lastFoodTick(TICK - ticksAgo).build();
	}

	/** Two teammates at these HP percentages (of 99). */
	private static ImmutableList<TeammateView> team(int first, int second)
	{
		return ImmutableList.of(new TeammateView("Attacker", first * 99 / 100, 99),
			new TeammateView("Collector", second * 99 / 100, 99));
	}

	private HealerTutorial()
	{
	}
}
