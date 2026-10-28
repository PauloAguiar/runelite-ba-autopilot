package com.bautopilot.baautopilot.tutorial;

import static com.bautopilot.baautopilot.tutorial.Situations.arena;
import static com.bautopilot.baautopilot.tutorial.Situations.opening;
import static com.bautopilot.baautopilot.tutorial.Situations.penance;
import static com.bautopilot.baautopilot.tutorial.Tutorials.pilot;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.ActionKind;
import com.bautopilot.baautopilot.plan.Step;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Attacker, and what BA Autopilot shows them: the egg call, arrows, the west route and wait
 * tile, the called arrows, attacking in kill order, holding as the call flips, the launcher, the
 * hill when hurt, and the Queen's spikes. The plugin's own box shows each page's step.
 */
final class AttackerTutorial
{
	private static final Role ROLE = Role.ATTACKER;
	private static final Color DONT = new Color(160, 160, 160);
	private static final List<String> FIGHT_ROOM = Arrays.asList("atk-fighter", "atk-ranger", "atk-attacker", "atk-collector");
	private static final List<String> ARROWS = Arrays.asList("atk-bullet", "atk-field", "atk-blunt", "atk-barbed");

	static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
		Page.builder().title("The Attacker")
			.body("As the Attacker, you kill the Penance Fighters and Rangers, using the style the Collector calls. You "
				+ "also tell the Collector which eggs to pick up.\n\n"
				+ "BA Autopilot shows you one step at a time: which arrows to wield, who to attack, and when to stop. "
				+ "It colours the right call green in your horn's menu.")
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.camera("atk-spot").build(),
		Page.builder().title("The BA Autopilot overlay")
			.body(Tutorials.BOX_PAGE)
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.pilot(pilot(Step.ATT_WAIT_CALL, armed(4).listenCall(null).listenFresh(false)
				.penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))))).highlightBox(true)
			.camera("atk-spot").build(),
		Tutorials.call(ROLE, "atk-attacker", "atk-collector", "Collector: Red eggs!",
				"Red eggs", "Accurate/Field/Water", BaCall.RED_EGG, armed(4)
					.penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46))))
			.title("Your call")
			.body("Every 30 seconds, the Collector needs a new call from you: which eggs to pick up. When it's time, "
				+ "your game view turns red. That's your signal.\n\n"
				+ "*Stop whatever you're doing.* Right-click your horn and pick the Tell- option that BA Autopilot "
				+ "colours green. The red goes away once you've called.")
			.camera("atk-spot").build(),
		Page.builder().title("Opening: arrows")
			.body("Start at your dispenser and take all four types of arrows. Right-click the dispenser to see each "
				+ "type, and take every one.\n\n"
				+ "With all four in your inventory, you can switch as soon as the Collector calls.")
			.props(BasicsTutorial.MACHINES).prop("dsp-attacker")
			.mark(new Mark("machine-attacker", "TAKE", ActionKind.GET.getColor(), Style.OUTLINE))
			.mark(Tutorials.role("dsp-attacker", ROLE))
			.use(new TutorialSteps.Arrow("dsp-attacker", "machine-attacker", null))
			.pilot(pilot(Step.ATT_GET_ARROWS, opening(ROLE, 4, 33, 16)))
			.action(Action.USE).camera("dsp-spot").build(),
		Page.builder().title("Opening: route and wait tile")
			.body("Walk around the west cannon hill to reach the caves. The route is drawn on the floor for you.\n\n"
				+ "Then stand on the marked tile by the caves and wait for the first Fighter or Ranger.")
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.tile(new TileMark("atk-west-route", "West route", ActionKind.MOVE.getColor()))
			.tile(new TileMark("atk-wait", "Wait here", ActionKind.WAIT.getColor()))
			.pilot(pilot(Step.ATT_POSITION, opening(ROLE, 4, 30, 18).inventory(allArrows())))
			.camera("atk-spot").build(),
		Page.builder().title("The called arrows")
			.body("When the Collector calls, wield the arrows the call names. They're outlined in green in your "
				+ "inventory.\n\n"
				+ "To wield them, click the outlined arrows.")
			.props(FIGHT_ROOM).props(ARROWS).mark(Tutorials.role("atk-attacker", ROLE))
			.mark(new Mark("atk-field", "Field arrows", ActionKind.USE.getColor(), Style.OUTLINE))
			.says("atk-collector", "Attacker: Accurate/Field/Water!")
			.hud(new TutorialSteps.Hud(ROLE, "Red eggs", "Accurate/Field/Water"))
			.pilot(pilot(Step.ATT_EQUIP, armed(4).ammoId(BaCall.CONTROLLED.getItemId())
				.penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46)))))
			.menu(Tutorials.menu(BaCall.ACCURATE.getItemId(), "Wield", "Wield", "Use", "Drop", "Examine"))
			.camera("atk-spot").build(),
		Page.builder().title("Attacking")
			.body("Click the outlined Penance to attack it. The overlay keeps you on the same one until it dies, so you "
				+ "don't have to choose.")
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.mark(new Mark("atk-fighter", "ATTACK", ActionKind.FIGHT.getColor(), Style.OUTLINE))
			.says("atk-collector", "Attacker: Accurate/Field/Water!")
			.pilot(pilot(Step.ATT_ATTACK, armed(4)
				.penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46), penance(2, PenanceType.RANGER, 4, 18, 45)))))
			.action(Action.SHOOT_FIGHTER).camera("atk-spot").build(),
		Page.builder().title("Hold as the call changes")
			.body("Stop attacking just before the call changes: a hit with the wrong style hurts you.\n\n"
				+ "The overlay tells you when to stop. To stop, click an empty tile on the ground.")
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.mark(new Mark("atk-fighter", "DON'T ATTACK", DONT, Style.OUTLINE))
			.pilot(pilot(Step.ATT_HOLD, armed(4).ticksToCallChange(2)
				.penance(ImmutableList.of(penance(1, PenanceType.FIGHTER, 4, 24, 46)))))
			.camera("atk-spot").build(),
		Page.builder().title("Nothing to attack")
			.body("Once the Fighters and Rangers are all dead, help at the egg launcher.\n\n"
				+ "Click the launcher when the overlay asks, then click the button that BA Autopilot outlines in its "
				+ "window.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-collector").prop("can-gunner").prop("can-runner")
			.mark(new Mark("can-gunner", "Attacker", com.bautopilot.baautopilot.overlay.Palette.role(ROLE), Style.CIRCLE))
			.mark(new Mark("can-launcher", "Egg launcher", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.ATT_SUPPORT, armed(4).cleared(ImmutableSet.of(PenanceType.FIGHTER, PenanceType.RANGER))
				.fighterRangerKills(WaveInfo.fightersTotal(4) + WaveInfo.rangersTotal(4))
				.penance(ImmutableList.of(penance(3, PenanceType.RUNNER, 4, 36, 44)))))
			.action(Action.CANNON_PENANCE).camera("can-spot").build(),
		Page.builder().title("Low HP: the hill")
			.body("When your health is low, the overlay sends you onto a launcher hill. Penance can't follow you up there.\n\n"
				+ "Walk up the hill and ask your Healer for the vial. Go back once you're healed.")
			.prop("can-launcher").prop("can-turret").prop("can-hopper").prop("can-gunner")
			.mark(new Mark("can-gunner", "Attacker 24%", new Color(240, 90, 70), Style.CIRCLE))
			.mark(new Mark("can-launcher", "Launcher hill", ActionKind.DANGER.getColor(), Style.OUTLINE))
			.pilot(pilot(Step.RETREAT, armed(4).hp(24).maxHp(99)))
			.camera("can-spot").build(),
		BasicsTutorial.chainStepBuilder(2, "In wave 10, take spikes from a petrified mushroom: click the mushroom. "
				+ "The overlay asks you to do this before the Queen appears.\n\n"
				+ "When the Healer passes you a poisoned egg, click the spikes in your inventory, then click the poisoned "
				+ "egg. Then pass the spiked egg to the Defender: click it, then click the Defender.", "Spiked egg")
			.title("Wave 10: spikes").prop("chain-mushroom").prop("chain-spikes")
			.mark(new Mark("chain-mushroom", "Petrified mushroom", Tutorials.PROP, Style.OUTLINE))
			.mark(new Mark("chain-spikes", "Spikes", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_SPIKE, arena(ROLE, 10, 27, 17).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.SPIKES, 6, BaIds.EGG_POISONED, 1))))
			.menu(Tutorials.menu(BaIds.SPIKES, "Use", "Use", "Drop", "Examine"))
			.camera("chain-mushroom-spot").build(),
		Page.builder().title("Wave 10: waiting")
			.body("Leave the Queen alone: weapons can't hurt her. Wait by the Healer spring for the next poisoned egg.")
			.prop("spring").prop("spr-healer").prop("spr-attacker")
			.mark(Tutorials.role("spr-attacker", ROLE))
			.mark(new Mark("spring", "Healer spring", Tutorials.PROP, Style.OUTLINE))
			.pilot(pilot(Step.QUEEN_WAIT, arena(ROLE, 10, 27, 16).queenAlive(true)
				.inventory(ImmutableMap.of(BaIds.SPIKES, 8))))
			.camera("spr-spot").build(),
		Page.builder().title("Attacker: done")
			.body("That's the Attacker. Click Defender > to continue with the Defender tutorial.")
			.props(FIGHT_ROOM).mark(Tutorials.role("atk-attacker", ROLE))
			.camera("atk-spot").build()));

	/** All four arrow types, as the attacker dispenser gives them. */
	private static ImmutableMap<Integer, Integer> allArrows()
	{
		ImmutableMap.Builder<Integer, Integer> b = ImmutableMap.builder();
		BaIds.ARROWS.forEach(id -> b.put(id, 50));
		return b.build();
	}

	/** By the caves with every arrow type, Field arrows on, after an Accurate call. */
	private static Snapshot.SnapshotBuilder armed(int wave)
	{
		return arena(ROLE, wave, 21, 43).listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId()).inventory(allArrows());
	}

	private AttackerTutorial()
	{
	}
}
