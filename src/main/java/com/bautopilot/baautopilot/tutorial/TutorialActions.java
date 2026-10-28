package com.bautopilot.baautopilot.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpotanimID;

/**
 * Plays a page's little scene on a loop: arrows flying at a fighter or ranger, the runner
 * eating on the trap as it springs, poison hitting a penance healer. Client thread, every client
 * tick; times are milliseconds into the page.
 */
@Singleton
public class TutorialActions
{
	/** A hitsplat drawn over a prop until a time. */
	@Value
	public static class Splat
	{
		String key;
		int sprite;
		String text;
		long until;
	}

	private static final long SHOOT_EVERY = 2400;
	/** The bow draws before the arrow leaves. */
	private static final long ARROW_LEAVES = 300;
	/** Heights an arrow leaves the bow and reaches its target at, in model units (a tile is 128). */
	private static final int ARROW_FROM = 172;
	private static final int ARROW_TO = 144;
	/** Heights an egg leaves the launcher and hits at. */
	private static final int EGG_FROM = 200;
	private static final int EGG_TO = 240;
	/** Client cycles (20 ms) an arrow flies. */
	private static final int ARROW_CYCLES = 40;
	/** The defender's loop: bait down, walk off, runner in, trap, walk back. */
	private static final long TRAP_EVERY = 9400;
	private static final long BAIT_DOWN = 500;
	private static final long DEFENDER_LEAVES = 900;
	private static final long DEFENDER_AWAY = 2600;
	private static final long RUNNER_COMES = 2800;
	private static final long RUNNER_ARRIVES = 4600;
	private static final long TRAP_SPRINGS = 5900;
	private static final long RUNNER_GONE = 7200;
	/** The repair after every second kill: walk over, fix, walk back. */
	private static final long REPAIR_EVERY = 5200;
	private static final long REPAIR_ARRIVES = 900;
	private static final long REPAIR_DONE = 3600;
	/** On the repair page, the fixed trap shows this long before it breaks again. */
	private static final long REPAIR_REST = 1500;
	private static final long DEFENDER_RETURNS = 7400;
	private static final long DEFENDER_BACK = 9000;
	/** A walk's last step lands this long after it ends, then the walker stands. */
	private static final long WALK_SETTLE = 150;
	/** The healer's loop between the two penance healers. */
	private static final long FEED_ROUND = 6400;
	private static final long FEED_FIRST_HITS = 700;
	private static final long WALK_TO_SECOND = 1400;
	private static final long AT_SECOND = 2800;
	private static final long FEED_SECOND_HITS = 3500;
	private static final long WALK_TO_FIRST = 4400;
	private static final long AT_FIRST = 5800;
	private static final long FEED_EVERY = 3000;
	private static final long POISON_HITS = 700;
	private static final long SPLAT_MS = 1200;
	/** The game's green poison hitsplat. */
	static final int POISON_SPLAT = 1360;
	private static final long CANNON_EVERY = 3600;
	/** The collector loads, then the cannon fires. */
	private static final long CANNON_FIRES = 1200;
	/** Client cycles (20 ms) an egg flies. */
	private static final int EGG_CYCLES = 50;
	private static final long HEAL_EVERY = 2600;
	private static final long COLLECT_EVERY = 3200;
	private static final long PICKUP_STARTS = 800;
	/** The egg leaves the floor part way into the pickup. */
	private static final long PICKUP_TAKES = 500;
	/** The egg drop: two shots, the second kills; the egg lies there until the ranger is back. */
	private static final long DROP_EVERY = 7800;
	private static final long DROP_SECOND_SHOT = 1800;
	private static final long DROP_RANGER_GONE = 4300;
	private static final long SPRING_EVERY = 5400;
	private static final long SPRING_HEALS = 1500;
	private static final long SPRING_DRINKS = 3200;
	private static final long INVITE_EVERY = 2500;
	private static final long USE_EVERY = 2400;
	private static final long USE_STAGGER = 300;
	/** The Queen's loop: a strike, then a ranged attack. */
	private static final long RAGE_EVERY = 5000;
	private static final long RAGE_FIRST = 1200;
	private static final long RAGE_SECOND = 3400;
	/** Each call stays up this long before the next player calls. */
	private static final long HORN_EACH = 2600;
	/** One hand-off round the egg loop: held, carried across, then a short rest. */
	private static final long PASS_LEG = 1800;
	private static final long PASS_HOLD = 500;
	private static final long PASS_REST = 200;
	/** The passed egg travels this high (a player is about 200 tall), arcing up a little more. */
	private static final int EGG_OVERHEAD = 230;
	private static final int EGG_ARC = 60;
	/** A step page plays its one step this long, with time for its tools. */
	private static final long CHAIN_PAGE_STEP = 2800;
	private static final long SPIKES_TAKEN = 1000;
	private static final long SPIKES_USED = 2200;
	/** The Queen's egg at each step of C-H-A-D-C; the last is the finished egg back with the Collector. */
	private static final List<String> CHAIN_EGGS = Arrays.asList(
		"chain-yellow", "chain-poisoned", "chain-spiked", "chain-omega", "chain-loaded");
	/** Who works the egg at each step: Collector, Healer, Attacker, Defender, Collector. */
	private static final List<String> CHAIN_PLAYERS = Arrays.asList(
		"chain-collector", "chain-healer", "chain-attacker", "chain-defender", "chain-collector");
	/** Each player's turn with the Queen's egg. */
	private static final long CHAIN_STEP = 1500;
	private static final long HEAL_LANDS = 600;
	/** Not a game sprite: the overlay draws a heal splat itself. */
	public static final int HEAL_SPLAT = -1;
	/** The game's red damage hitsplat. */
	private static final int DAMAGE_SPLAT = 1359;
	/** Use-item-on reach, for feeding. */
	private static final int FEED = AnimationID.HUMAN_PICKUPTABLE;

	private final Client client;
	private final TutorialScene scene;

	private TutorialSteps.Page runningPage;
	/** The loop count of the last event fired, per event, so each fires once a loop. */
	private long lastLoop = -1;
	private int lastEvent = -1;
	private final Set<String> hidden = new HashSet<>();
	private String speaker;
	/** Props walking now (their walk animation is looping). */
	private final Set<String> walking = new HashSet<>();
	private final List<Splat> splats = new ArrayList<>();

	@Inject
	TutorialActions(Client client, TutorialScene scene)
	{
		this.client = client;
		this.scene = scene;
	}

	/** Props the scene has taken out for now (a dead runner). */
	public Set<String> hidden()
	{
		return Collections.unmodifiableSet(hidden);
	}

	public List<Splat> splats()
	{
		long now = System.currentTimeMillis();
		splats.removeIf(s -> s.getUntil() < now);
		return Collections.unmodifiableList(splats);
	}

	public void update(TutorialSteps.Page page, long elapsed)
	{
		// A new page starts its scene fresh, even when it plays the same action
		if (page != runningPage)
		{
			stop();
			runningPage = page;
		}
		TutorialSteps.Action action = page.getAction();
		switch (action)
		{
			case SHOOT_FIGHTER:
				// Field arrows: the Accurate call on the page
				shoot("atk-fighter", AnimationID.BARBASSAULT_PENANCE_FIGHTER_DEFEND, SpotanimID.BARBASSAULT_FIELD_ARROW_TRAVEL, elapsed);
				break;
			case SHOOT_RANGER:
				// Bullet arrows: the Controlled call on the page
				shoot("atk-ranger", AnimationID.BARBASSAULT_PENANCE_RANGER_DEFEND, SpotanimID.BARBASSAULT_BULLET_ARROW_TRAVEL, elapsed);
				break;
			case CANNON_QUEEN:
				cannon("cannon-collector", "cannon-gunner", "turret", "hopper", "queen",
					SpotanimID.BARBASSAULT_EGG_PROJ_ATOMIC, AnimationID.BARBASSAULT_PENANCE_QUEEN_DEFEND, elapsed);
				break;
			case CANNON_PENANCE:
				cannon("can-collector", "can-gunner", "can-turret", "can-hopper", "can-runner",
					SpotanimID.BARBASSAULT_EGG_PROJ_02, AnimationID.BARBASSAULT_PENANCE_RUNNER_DEFEND, elapsed);
				break;
			case COLLECT_EGGS:
				collect(elapsed);
				break;
			case EGG_DROP:
				drop(elapsed);
				break;
			case SPRING:
				spring(elapsed);
				break;
			case EGG_CHAIN:
				chain(elapsed, page.getChainStep());
				break;
			case INVITE:
				invite(elapsed);
				break;
			case EGG_PASS:
				pass(elapsed);
				break;
			case HORNS:
				horns(page, elapsed);
				break;
			case QUEEN_RAGE:
				rage(elapsed);
				break;
			case USE:
				use(page, elapsed);
				break;
			case TRAP_REPAIR:
				repairLoop(elapsed);
				break;
			case TRAP_RUNNER:
				trap(elapsed);
				break;
			case POISON_HEALER:
				poison(elapsed);
				break;
			case HEAL_RUNNER:
				heal(elapsed);
				break;
			default:
				break;
		}
	}

	/** Back to rest: everyone idle and visible, nothing pending. */
	public void stop()
	{
		for (TutorialScene.Prop p : scene.getProps())
		{
			scene.idle(p);
			scene.home(p);
		}
		hidden.clear();
		walking.clear();
		splats.clear();
		lastLoop = -1;
		lastEvent = -1;
		runningPage = null;
	}

	private void shoot(String target, int defend, int projectile, long elapsed)
	{
		TutorialScene.Prop attacker = scene.prop("atk-attacker");
		TutorialScene.Prop penance = scene.prop(target);
		if (attacker == null || penance == null)
		{
			return;
		}
		face(attacker, penance);
		face(scene.prop("atk-collector"), attacker);
		long at = elapsed % SHOOT_EVERY;
		long loop = elapsed / SHOOT_EVERY;
		if (due(loop, 0, at, 0))
		{
			scene.play(attacker, AnimationID.HUMAN_BOW);
		}
		if (due(loop, 1, at, ARROW_LEAVES))
		{
			fly(projectile, attacker, penance, ARROW_FROM, ARROW_TO, 15, 11);
		}
		if (due(loop, 2, at, ARROW_LEAVES + ARROW_CYCLES * 20L))
		{
			scene.play(penance, defend);
		}
	}

	/**
	 * The Defender drops the bait beside the trap and walks round to its far side; the runner
	 * comes for the bait, eats, and the trap kills it; the Defender walks back and it begins again.
	 */
	private void trap(long elapsed)
	{
		TutorialScene.Prop runner = scene.prop("def-runner");
		TutorialScene.Prop trap = scene.prop("def-trap");
		TutorialScene.Prop defender = scene.prop("def-defender");
		TutorialScene.Prop bait = scene.prop("def-bait");
		LocalPoint far = scene.tile("def-far");
		LocalPoint fix = scene.tile("def-fix");
		if (runner == null || trap == null || defender == null || bait == null || far == null || fix == null
			|| defender.getHome() == null || runner.getHome() == null)
		{
			return;
		}
		face(scene.prop("def-healer"), defender);
		// Two kills, then the broken trap gets repaired
		long round = TRAP_EVERY * 2 + REPAIR_EVERY;
		long at = elapsed % round;
		long loop = elapsed / round;
		if (at < TRAP_EVERY * 2)
		{
			int kill = (int) (at / TRAP_EVERY);
			kill(runner, trap, defender, bait, far, at % TRAP_EVERY, loop, kill * KILL_EVENTS, kill == 1);
		}
		else
		{
			repair(defender, trap, fix, at - TRAP_EVERY * 2, loop, 2 * KILL_EVENTS);
		}
	}

	/** Events one kill fires (their numbers start at {@code first}). */
	private static final int KILL_EVENTS = 5;

	/**
	 * One kill: the Defender drops the bait beside the trap and walks across it to the far side;
	 * the runner comes for the bait, eats, and the trap kills it; the Defender walks back. The
	 * second kill leaves the trap broken.
	 */
	private void kill(TutorialScene.Prop runner, TutorialScene.Prop trap, TutorialScene.Prop defender,
		TutorialScene.Prop bait, LocalPoint far, long at, long loop, int first, boolean breaks)
	{
		LocalPoint post = defender.getHome();
		if (due(loop, first, at, 0))
		{
			hidden.add("def-bait");
			hidden.remove("def-runner");
			hidden.remove("def-trap");
			hidden.add("def-trap-broken");
			scene.move(runner, runner.getHome());
			scene.idle(runner);
			scene.play(defender, AnimationID.HUMAN_PICKUPFLOOR);
		}
		if (due(loop, first + 1, at, BAIT_DOWN))
		{
			hidden.remove("def-bait");
		}
		walk(defender, java.util.Arrays.asList(post, far), at, DEFENDER_LEAVES, DEFENDER_AWAY, AnimationID.HUMAN_WALK_F);
		if (at >= DEFENDER_AWAY && at < DEFENDER_RETURNS)
		{
			face(defender, trap);
		}
		walk(runner, java.util.Arrays.asList(runner.getHome(), bait.getHome()), at, RUNNER_COMES, RUNNER_ARRIVES,
			AnimationID.BARBASSAULT_PENANCE_RUNNER_WALK);
		if (due(loop, first + 2, at, RUNNER_ARRIVES))
		{
			face(runner, bait);
			scene.play(runner, AnimationID.BARBASSAULT_PENANCE_RUNNER_EATING);
		}
		if (due(loop, first + 3, at, TRAP_SPRINGS))
		{
			hidden.add("def-bait");
			scene.play(trap, AnimationID.BARBASSAULT_TRAP_ATTACK);
			scene.play(runner, AnimationID.BARBASSAULT_PENANCE_RUNNER_DEATH);
		}
		if (due(loop, first + 4, at, RUNNER_GONE))
		{
			hidden.add("def-runner");
			if (breaks)
			{
				hidden.add("def-trap");
				hidden.remove("def-trap-broken");
			}
		}
		walk(defender, java.util.Arrays.asList(far, post), at, DEFENDER_RETURNS, DEFENDER_BACK, AnimationID.HUMAN_WALK_F);
	}

	/** Just the repair, on repeat: the trap shows broken, the Defender fixes it, a moment fixed. */
	private void repairLoop(long elapsed)
	{
		TutorialScene.Prop trap = scene.prop("def-trap");
		TutorialScene.Prop defender = scene.prop("def-defender");
		LocalPoint fix = scene.tile("def-fix");
		if (trap == null || defender == null || fix == null || defender.getHome() == null)
		{
			return;
		}
		long round = REPAIR_EVERY + REPAIR_REST;
		long at = elapsed % round;
		long loop = elapsed / round;
		if (due(loop, 0, at, 0))
		{
			hidden.add("def-trap");
			hidden.remove("def-trap-broken");
		}
		if (at < REPAIR_EVERY)
		{
			repair(defender, trap, fix, at, loop, 1);
		}
	}

	/** The Defender walks to the broken trap, repairs it, and goes back to the bait spot. */
	private void repair(TutorialScene.Prop defender, TutorialScene.Prop trap, LocalPoint fix, long at, long loop, int first)
	{
		LocalPoint post = defender.getHome();
		walk(defender, java.util.Arrays.asList(post, fix), at, 0, REPAIR_ARRIVES, AnimationID.HUMAN_WALK_F);
		if (due(loop, first, at, REPAIR_ARRIVES + WALK_SETTLE))
		{
			face(defender, trap);
			scene.loop(defender, AnimationID.BARBASSAULT_TRAP_DOOR_BUILD);
		}
		if (due(loop, first + 1, at, REPAIR_DONE))
		{
			scene.idle(defender);
			hidden.remove("def-trap");
			hidden.add("def-trap-broken");
		}
		walk(defender, java.util.Arrays.asList(fix, post), at, REPAIR_DONE + 200, REPAIR_EVERY - 300, AnimationID.HUMAN_WALK_F);
	}

	/**
	 * Moves a prop along a path between two times into the loop, walking (looping the walk
	 * animation, facing where it goes); idle again once there.
	 */
	private void walk(TutorialScene.Prop p, List<LocalPoint> path, long at, long from, long to, int animation)
	{
		if (at < from || at > to + WALK_SETTLE)
		{
			return;
		}
		double f = Math.min(1, (at - from) / (double) (to - from));
		LocalPoint here = TutorialPath.along(path, f);
		LocalPoint ahead = TutorialPath.along(path, Math.min(1, f + 0.05));
		if (f < 1)
		{
			if (walking.add(p.getKey()))
			{
				scene.loop(p, animation);
			}
			if (!ahead.equals(here))
			{
				faceToward(p, here, ahead);
			}
		}
		else if (walking.remove(p.getKey()))
		{
			scene.idle(p);
		}
		scene.move(p, here);
	}

	/** Turns a prop to look from one point towards another. */
	private static void faceToward(TutorialScene.Prop who, LocalPoint a, LocalPoint b)
	{
		double angle = Math.atan2(-(b.getX() - a.getX()), -(b.getY() - a.getY()));
		who.getObject().setOrientation((int) Math.round(angle * 1024 / Math.PI) & 2047);
	}

	private void heal(long elapsed)
	{
		TutorialScene.Prop healer = scene.prop("heal-pen");
		TutorialScene.Prop runner = scene.prop("heal-runner");
		if (healer == null || runner == null)
		{
			return;
		}
		face(healer, runner);
		long at = elapsed % HEAL_EVERY;
		long loop = elapsed / HEAL_EVERY;
		if (due(loop, 0, at, 0))
		{
			scene.play(healer, AnimationID.BARBASSAULT_PENANCE_HEALER_HEAL);
		}
		if (due(loop, 1, at, HEAL_LANDS))
		{
			splats.add(new Splat("heal-runner", HEAL_SPLAT, "+6", System.currentTimeMillis() + SPLAT_MS));
		}
	}

	/** The collector picks up the called red egg; it turns up again for the next loop. */
	private void collect(long elapsed)
	{
		TutorialScene.Prop collector = scene.prop("col-collector");
		TutorialScene.Prop egg = scene.prop("col-egg-red");
		if (collector == null || egg == null)
		{
			return;
		}
		face(collector, egg);
		face(scene.prop("col-attacker"), collector);
		long at = elapsed % COLLECT_EVERY;
		long loop = elapsed / COLLECT_EVERY;
		if (due(loop, 0, at, 0))
		{
			hidden.remove("col-egg-red");
		}
		if (due(loop, 1, at, PICKUP_STARTS))
		{
			scene.play(collector, AnimationID.HUMAN_PICKUPFLOOR);
		}
		if (due(loop, 2, at, PICKUP_STARTS + PICKUP_TAKES))
		{
			hidden.add("col-egg-red");
		}
	}

	/**
	 * The Attacker shoots the ranger twice; it dies and leaves an egg on its tile, which waits there
	 * for the Collector. The ranger comes back for the next loop.
	 */
	private void drop(long elapsed)
	{
		TutorialScene.Prop attacker = scene.prop("col-attacker");
		TutorialScene.Prop ranger = scene.prop("col-ranger");
		if (attacker == null || ranger == null)
		{
			return;
		}
		face(attacker, ranger);
		face(scene.prop("col-collector"), ranger);
		long at = elapsed % DROP_EVERY;
		long loop = elapsed / DROP_EVERY;
		long hits = ARROW_LEAVES + ARROW_CYCLES * 20L;
		if (due(loop, 0, at, 0))
		{
			hidden.remove("col-ranger");
			hidden.add("col-drop-egg");
			scene.idle(ranger);
			scene.play(attacker, AnimationID.HUMAN_BOW);
		}
		if (due(loop, 1, at, ARROW_LEAVES))
		{
			fly(SpotanimID.BARBASSAULT_BULLET_ARROW_TRAVEL, attacker, ranger, ARROW_FROM, ARROW_TO, 15, 11);
		}
		if (due(loop, 2, at, hits))
		{
			scene.play(ranger, AnimationID.BARBASSAULT_PENANCE_RANGER_DEFEND);
		}
		if (due(loop, 3, at, DROP_SECOND_SHOT))
		{
			scene.play(attacker, AnimationID.HUMAN_BOW);
		}
		if (due(loop, 4, at, DROP_SECOND_SHOT + ARROW_LEAVES))
		{
			fly(SpotanimID.BARBASSAULT_BULLET_ARROW_TRAVEL, attacker, ranger, ARROW_FROM, ARROW_TO, 15, 11);
		}
		if (due(loop, 5, at, DROP_SECOND_SHOT + hits))
		{
			scene.play(ranger, AnimationID.BARBASSAULT_PENANCE_RANGER_DEATH);
		}
		if (due(loop, 6, at, DROP_RANGER_GONE))
		{
			hidden.add("col-ranger");
			hidden.remove("col-drop-egg");
		}
	}

	/** The healer fills the vial at the spring, heals the teammate, then drinks from the spring. */
	private void spring(long elapsed)
	{
		TutorialScene.Prop healer = scene.prop("spr-healer");
		TutorialScene.Prop spring = scene.prop("spring");
		TutorialScene.Prop mate = scene.prop("spr-attacker");
		if (healer == null || spring == null || mate == null)
		{
			return;
		}
		long at = elapsed % SPRING_EVERY;
		long loop = elapsed / SPRING_EVERY;
		if (due(loop, 0, at, 0))
		{
			face(healer, spring);
			scene.play(healer, AnimationID.BARBASSAULT_VIAL_SCOOP);
		}
		if (due(loop, 1, at, SPRING_HEALS))
		{
			face(healer, mate);
			scene.play(healer, FEED);
			splats.add(new Splat("spr-attacker", HEAL_SPLAT, "+10", System.currentTimeMillis() + SPLAT_MS));
		}
		if (due(loop, 2, at, SPRING_DRINKS))
		{
			face(healer, spring);
			scene.play(healer, AnimationID.BARBASSAULT_DRINK_FROM_SPRING);
			splats.add(new Splat("spr-healer", HEAL_SPLAT, "+5", System.currentTimeMillis() + SPLAT_MS + SPLAT_MS / 2));
		}
		face(mate, healer);
	}

	/**
	 * C-H-A-D-C: one egg at a time, worked by each player in turn and passed along to the next -
	 * yellow from the Collector, poisoned by the Healer, spiked by the Attacker, heated by the
	 * Defender, and back to the Collector to load.
	 */
	private void chain(long elapsed, Integer only)
	{
		List<String> eggs = CHAIN_EGGS;
		List<String> players = CHAIN_PLAYERS;
		int steps = eggs.size();
		// One step on repeat (slower, with its tools), or the whole chain
		long at = only != null ? elapsed % CHAIN_PAGE_STEP : elapsed % (CHAIN_STEP * steps);
		long loop = only != null ? elapsed / CHAIN_PAGE_STEP : elapsed / (CHAIN_STEP * steps);
		int step = only != null ? only : (int) (at / CHAIN_STEP);
		long starts = only != null ? 0 : step * CHAIN_STEP;
		// Only the egg of the current step is in the room
		for (int i = 0; i < steps; i++)
		{
			if (i == step)
			{
				hidden.remove(eggs.get(i));
			}
			else
			{
				hidden.add(eggs.get(i));
			}
		}
		TutorialScene.Prop player = scene.prop(players.get(step));
		TutorialScene.Prop egg = scene.prop(eggs.get(step));
		if (player == null || egg == null)
		{
			return;
		}
		// The Healer and Defender use the egg on their crater, when the page shows it
		TutorialScene.Prop crater = step == 1 ? scene.prop("chain-crater") : step == 3 ? scene.prop("chain-lava") : null;
		boolean dunk = crater != null && crater.getObject().isActive();
		TutorialScene.Prop mushroom = step == 2 ? scene.prop("chain-mushroom") : null;
		if (mushroom != null && mushroom.getObject().isActive())
		{
			spikes(player, mushroom, egg, at, loop);
			return;
		}
		face(player, dunk ? crater : egg);
		if (due(loop, step, at, starts))
		{
			// A reach towards the crater or the egg (the game's egg-poisoning animation isn't a player's)
			scene.play(player, step == 0 ? AnimationID.HUMAN_PICKUPFLOOR : FEED);
		}
	}

	/** The Attacker takes spikes from the mushroom, then uses them on the egg. */
	private void spikes(TutorialScene.Prop attacker, TutorialScene.Prop mushroom, TutorialScene.Prop egg, long at, long loop)
	{
		if (due(loop, 0, at, 0))
		{
			hidden.add("chain-spikes");
			face(attacker, mushroom);
			scene.play(attacker, FEED);
		}
		if (due(loop, 1, at, SPIKES_TAKEN))
		{
			hidden.remove("chain-spikes");
			face(attacker, egg);
			scene.play(attacker, FEED);
		}
		if (due(loop, 2, at, SPIKES_USED))
		{
			hidden.add("chain-spikes");
		}
	}

	/**
	 * C-H-A-D-C round the loop: each player in turn works the egg, then it slides to the next
	 * player, as it is after their step - yellow to the Healer, poisoned to the Attacker, spiked to
	 * the Defender, omega back to the Collector.
	 */
	private void pass(long elapsed)
	{
		List<String> players = CHAIN_PLAYERS;
		List<String> eggs = CHAIN_EGGS.subList(0, 4);
		int legs = eggs.size();
		long at = elapsed % (PASS_LEG * legs);
		long loop = elapsed / (PASS_LEG * legs);
		int leg = (int) (at / PASS_LEG);
		long into = at % PASS_LEG;
		TutorialScene.Prop from = scene.prop(players.get(leg));
		TutorialScene.Prop to = scene.prop(players.get(leg + 1));
		TutorialScene.Prop egg = scene.prop(eggs.get(leg));
		if (from == null || to == null || egg == null)
		{
			return;
		}
		for (int i = 0; i < legs; i++)
		{
			if (i == leg)
			{
				hidden.remove(eggs.get(i));
			}
			else
			{
				hidden.add(eggs.get(i));
			}
		}
		face(from, to);
		face(to, from);
		if (due(loop, leg, at, leg * PASS_LEG))
		{
			scene.play(from, FEED);
		}
		// Held, then handed across
		double f = into < PASS_HOLD ? 0 : Math.min(1, (into - PASS_HOLD) / (double) (PASS_LEG - PASS_HOLD - PASS_REST));
		LocalPoint a = from.getObject().getLocation();
		LocalPoint b = to.getObject().getLocation();
		if (a != null && b != null)
		{
			// Over the heads: held up high, a little arc on the way across
			int lift = EGG_OVERHEAD + (int) Math.round(EGG_ARC * Math.sin(Math.PI * f));
			scene.move(egg, TutorialPath.along(java.util.Arrays.asList(a, b), f * f * (3 - 2 * f)), lift);
		}
	}

	/** The healer feeds one penance healer, walks over to the other and feeds it, then walks back. */
	private void poisonTwo(long elapsed, TutorialScene.Prop second)
	{
		TutorialScene.Prop healer = scene.prop("heal-healer");
		TutorialScene.Prop first = scene.prop("heal-pen");
		LocalPoint atFirst = scene.tile("heal-at-1");
		LocalPoint atSecond = scene.tile("heal-at-2");
		if (healer == null || first == null || atFirst == null || atSecond == null)
		{
			return;
		}
		face(scene.prop("heal-defender"), healer);
		long at = elapsed % FEED_ROUND;
		long loop = elapsed / FEED_ROUND;
		if (due(loop, 0, at, 0))
		{
			scene.move(healer, atFirst);
			face(healer, first);
			scene.play(healer, FEED);
		}
		if (due(loop, 1, at, FEED_FIRST_HITS))
		{
			scene.play(first, AnimationID.BARBASSAULT_PENANCE_HEALER_POISION);
			splats.add(new Splat("heal-pen", POISON_SPLAT, "4", System.currentTimeMillis() + SPLAT_MS));
		}
		walk(healer, java.util.Arrays.asList(atFirst, atSecond), at, WALK_TO_SECOND, AT_SECOND, AnimationID.HUMAN_WALK_F);
		if (due(loop, 2, at, AT_SECOND + WALK_SETTLE))
		{
			face(healer, second);
			scene.play(healer, FEED);
		}
		if (due(loop, 3, at, FEED_SECOND_HITS))
		{
			scene.play(second, AnimationID.BARBASSAULT_PENANCE_HEALER_POISION);
			splats.add(new Splat("heal-pen-2", POISON_SPLAT, "4", System.currentTimeMillis() + SPLAT_MS));
		}
		walk(healer, java.util.Arrays.asList(atSecond, atFirst), at, WALK_TO_FIRST, AT_FIRST, AnimationID.HUMAN_WALK_F);
	}

	/** Partners face each other; one horn after another. */
	/**
	 * The callers blow their horns one at a time (in the order of the page's calls); only the
	 * one calling now shows their call and their arrow, so each call reads on its own.
	 */
	private void horns(TutorialSteps.Page page, long elapsed)
	{
		List<String> callers = new ArrayList<>(page.getSpeech().keySet());
		for (TutorialSteps.Arrow a : page.getArrows())
		{
			face(scene.prop(a.getFrom()), scene.prop(a.getTo()));
		}
		if (callers.isEmpty())
		{
			return;
		}
		long at = elapsed % (HORN_EACH * callers.size());
		long loop = elapsed / (HORN_EACH * callers.size());
		int turn = (int) (at / HORN_EACH);
		speaker = callers.get(turn);
		TutorialScene.Prop p = scene.prop(speaker);
		if (p != null && due(loop, turn, at, turn * HORN_EACH))
		{
			scene.play(p, AnimationID.BARBASSAULT_HUMAN_USE_HORN);
		}
	}

	/** Who is calling now on a horn page; null on other pages (everyone's text shows). */
	public String speaker()
	{
		return runningPage != null && runningPage.getAction() == TutorialSteps.Action.HORNS ? speaker : null;
	}

	/** The page's players reach into what they use (a dispenser), every few seconds. */
	private void use(TutorialSteps.Page page, long elapsed)
	{
		long at = elapsed % USE_EVERY;
		long loop = elapsed / USE_EVERY;
		List<TutorialSteps.Arrow> uses = page.getUses();
		for (int i = 0; i < uses.size(); i++)
		{
			TutorialScene.Prop who = scene.prop(uses.get(i).getFrom());
			TutorialScene.Prop what = scene.prop(uses.get(i).getTo());
			face(who, what);
			if (who != null && due(loop, i, at, i * USE_STAGGER))
			{
				scene.play(who, FEED);
			}
		}
	}

	/** The Queen's attacks, one after the other: a strike, then a ranged blast. */
	private void rage(long elapsed)
	{
		TutorialScene.Prop queen = scene.prop("queen");
		if (queen == null)
		{
			return;
		}
		long at = elapsed % RAGE_EVERY;
		long loop = elapsed / RAGE_EVERY;
		if (due(loop, 0, at, RAGE_FIRST))
		{
			scene.play(queen, AnimationID.BARBASSAULT_PENANCE_QUEEN_ATTACK);
		}
		if (due(loop, 1, at, RAGE_SECOND))
		{
			scene.play(queen, AnimationID.BARBASSAULT_PENANCE_QUEEN_RANGEATTACK);
		}
	}

	/** The leader holds the scroll out to you, again and again. */
	private void invite(long elapsed)
	{
		TutorialScene.Prop leader = scene.prop("leader");
		TutorialScene.Prop you = scene.prop("you");
		if (leader == null || you == null)
		{
			return;
		}
		face(leader, you);
		face(you, leader);
		long at = elapsed % INVITE_EVERY;
		long loop = elapsed / INVITE_EVERY;
		if (due(loop, 0, at, 0))
		{
			scene.play(leader, FEED);
		}
	}

	private void poison(long elapsed)
	{
		TutorialScene.Prop second = scene.prop("heal-pen-2");
		if (second != null && second.getObject().isActive())
		{
			poisonTwo(elapsed, second);
			return;
		}
		TutorialScene.Prop healer = scene.prop("heal-healer");
		TutorialScene.Prop penance = scene.prop("heal-pen");
		if (healer == null || penance == null)
		{
			return;
		}
		face(healer, penance);
		face(scene.prop("heal-defender"), healer);
		long at = elapsed % FEED_EVERY;
		long loop = elapsed / FEED_EVERY;
		if (due(loop, 0, at, 0))
		{
			scene.play(healer, FEED);
		}
		if (due(loop, 1, at, POISON_HITS))
		{
			scene.play(penance, AnimationID.BARBASSAULT_PENANCE_HEALER_POISION);
			splats.add(new Splat("heal-pen", POISON_SPLAT, "4", System.currentTimeMillis() + SPLAT_MS));
		}
	}

	/** Event {@code event} of loop {@code loop} is due (time {@code at} into the loop) and hasn't fired yet. */
	private boolean due(long loop, int event, long at, long when)
	{
		if (at < when || loop < lastLoop || (loop == lastLoop && event <= lastEvent))
		{
			return false;
		}
		lastLoop = loop;
		lastEvent = event;
		return true;
	}

	/** The collector loads the hopper, the gunner fires, the egg hits the target. */
	private void cannon(String collectorKey, String gunnerKey, String turretKey, String hopperKey, String targetKey,
		int egg, int defend, long elapsed)
	{
		TutorialScene.Prop collector = scene.prop(collectorKey);
		TutorialScene.Prop gunner = scene.prop(gunnerKey);
		TutorialScene.Prop turret = scene.prop(turretKey);
		TutorialScene.Prop queen = scene.prop(targetKey);
		if (collector == null || gunner == null || turret == null || queen == null)
		{
			return;
		}
		face(collector, scene.prop(hopperKey));
		face(gunner, queen);
		face(turret, queen);
		long at = elapsed % CANNON_EVERY;
		long loop = elapsed / CANNON_EVERY;
		if (due(loop, 0, at, 0))
		{
			scene.play(collector, FEED);
		}
		if (due(loop, 1, at, CANNON_FIRES))
		{
			scene.play(gunner, AnimationID.BARBASSAULT_EGG_CANNON_HUMAN_FIRE);
			scene.play(turret, AnimationID.BARBASSAULT_EGG_CANNON_FIRE);
			fly(egg, turret, queen, EGG_FROM, EGG_TO, 20, 0);
		}
		if (due(loop, 2, at, CANNON_FIRES + EGG_CYCLES * 20L))
		{
			scene.play(queen, defend);
			splats.add(new Splat(targetKey, DAMAGE_SPLAT, "25", System.currentTimeMillis() + SPLAT_MS));
		}
	}

	private void fly(int spotanim, TutorialScene.Prop from, TutorialScene.Prop to, int startHeight, int endHeight,
		int slope, int startPos)
	{
		LocalPoint a = from.getObject().getLocation();
		LocalPoint b = to.getObject().getLocation();
		if (a == null || b == null)
		{
			return;
		}
		int cycle = client.getGameCycle();
		boolean egg = spotanim >= SpotanimID.BARBASSAULT_EGG_PROJ_01 && spotanim <= SpotanimID.BARBASSAULT_EGG_PROJ_ATOMIC;
		int cycles = egg ? EGG_CYCLES : ARROW_CYCLES;
		Projectile p = client.createProjectile(spotanim,
			WorldPoint.fromLocal(client, a), startHeight, null,
			WorldPoint.fromLocal(client, b), endHeight, null,
			cycle, cycle + cycles, slope, startPos);
		client.getProjectiles().addLast(p);
	}

	/** Turns a prop to look at another (0 south, 512 west, 1024 north, 1536 east). */
	private static void face(TutorialScene.Prop who, TutorialScene.Prop at)
	{
		if (who == null || at == null)
		{
			return;
		}
		LocalPoint a = who.getObject().getLocation();
		LocalPoint b = at.getObject().getLocation();
		if (a == null || b == null || a.equals(b))
		{
			return;
		}
		double angle = Math.atan2(-(b.getX() - a.getX()), -(b.getY() - a.getY()));
		who.getObject().setOrientation((int) Math.round(angle * 1024 / Math.PI) & 2047);
	}
}
