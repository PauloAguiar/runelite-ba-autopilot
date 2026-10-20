package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.Comparator;

/**
 * Wave 10 Penance Queen egg chain, C-H-A-D-C: the collector picks yellow eggs from under the queen,
 * the healer dunks them in a poison crater (use the egg on it), the attacker adds spikes, the
 * defender dunks them in lava (left-click the crater), and the collector loads the omega eggs.
 * Eight omega hits kill her; the reserve counter tracks eggs spent, not simultaneous player hits.
 */
class QueenPlanner
{
	boolean applies(Snapshot s)
	{
		return s.getWave() == 10 && (s.isQueenAlive() || s.isQueenDead()
			|| s.has(BaIds.EGG_YELLOW) || s.has(BaIds.EGG_POISONED) || s.has(BaIds.EGG_SPIKED) || s.has(BaIds.EGG_OMEGA));
	}

	Instruction plan(Snapshot s, Settings c)
	{
		Role role = s.getRole();
		boolean stack = c.isQueenStack();
		if (s.isQueenDead()) return Instruction.builder().step(Step.QUEEN_WAIT).headline("Queen defeated")
			.detail("Keep any unused reserve eggs; wait for the wave to finish").urgency(Urgency.INFO).build();
		if (role == Role.COLLECTOR)
		{
			if (s.has(BaIds.EGG_OMEGA))
			{
				return load(s, stack || c.isQueenSingleLoads());
			}
			Instruction preload = CollectorPlanner.preload(s);
			if (preload != null) return preload;
			return collectorEggs(s, c);
		}
		if (role == Role.HEALER)
		{
			if (s.has(BaIds.EGG_YELLOW))
			{
				return dunk(s, Step.QUEEN_DUNK, BaIds.POISON_CRATERS, BaIds.EGG_YELLOW, "Use the yellow egg on a poison crater",
					"Use the item on the crater; don't left-click it");
			}
			if (s.has(BaIds.EGG_POISONED))
			{
				return pass(s, BaIds.EGG_POISONED, "poisoned egg", Role.ATTACKER);
			}
			if (QueenHealerPlanner.preparing(s)) return waiting(s, role);
		}
		if (role == Role.ATTACKER)
		{
			if (s.has(BaIds.EGG_POISONED))
			{
				int poisoned = s.count(BaIds.EGG_POISONED);
				int spikes = s.count(BaIds.SPIKES);
				if (spikes == 0)
				{
					ObjectView shroom = s.nearestObject(BaIds.MUSHROOMS);
					return Instruction.builder()
						.step(Step.ATT_SPIKES)
						.headline("Get spikes (need " + poisoned + ")")
						.detail("One per poisoned egg, from a petrified mushroom")
						.target(shroom == null ? Target.NONE : Target.object(shroom.getId(), shroom.getTile()))
						.build();
				}
				return Instruction.builder()
					.step(Step.QUEEN_SPIKE)
					.headline(poisoned == 1 ? "Use spikes on the poisoned egg" : "Spike your " + poisoned + " poisoned eggs")
					.detail(spikes < poisoned
						? spikes + " spikes: use them, then get " + (poisoned - spikes) + " more"
						: "Then pass them all to the defender")
					.items(Planner.items(BaIds.SPIKES, BaIds.EGG_POISONED))
					.build();
			}
			if (s.has(BaIds.EGG_SPIKED))
			{
				return pass(s, BaIds.EGG_SPIKED, "spiked egg", Role.DEFENDER);
			}
		}
		if (role == Role.DEFENDER)
		{
			if (s.has(BaIds.EGG_SPIKED))
			{
				return dunk(s, Step.QUEEN_LAVA, BaIds.LAVA_CRATERS, BaIds.EGG_SPIKED, "Left-click a lava crater with the spiked egg",
					"Turns it into an omega egg");
			}
			if (s.has(BaIds.EGG_OMEGA))
			{
				return pass(s, BaIds.EGG_OMEGA, "omega egg", Role.COLLECTOR);
			}
		}

		// Your eggs are done. Stacking: everyone at the cannon spamming Fire while the collector loads
		// one omega at a time. Not stacking: the collector fires, the rest wait by the healer spring
		// for their next egg (easier for the healer to keep them up)
		ObjectView launcher = s.nearestObject(BaIds.LAUNCHERS);
		if (launcher != null && (stack || role == Role.COLLECTOR && s.getEggsOmega() > 0
			|| role == Role.ATTACKER && cannonReady(s)))
		{
			boolean loaded = s.getEggsOmega() > 0;
			return Instruction.builder()
				.step(Step.QUEEN_FIRE)
				.headline(loaded ? "Spam the Omega button at the Queen" : role == Role.ATTACKER ? "Get ready at the cannon: spam Omega" : "Stack at the cannon: spam Fire")
				.detail(loaded ? s.getEggsOmega() + " loaded. Every click on a loaded egg is a hit; eight kill her"
					: (CollectorPlanner.loadedAmmo(s) == 0 ? "Wait for a loaded egg to enable Shoot, then " : "Open Shoot, then ")
						+ "keep clicking Omega → Queen as the Collector loads one at a time"
						+ (s.getQueenOmegaHandoffTick() >= 0 ? ". Defender-to-Collector hand-off observed" : ""))
				.target(Target.object(launcher.getId(), launcher.getTile()))
				.urgency(loaded ? Urgency.HIGH : Urgency.NORMAL)
				.build();
		}
		return waiting(s, role);
	}

	static boolean cannonReady(Snapshot s)
	{
		return s.getQueenOmegaHandoffTick() >= 0 || s.getEggsOmega() > 0 || s.getQueenOmegaFired() > 0;
	}

	/** Let the final projectile resolve before releasing unused yellow reserves. */
	static final int VOLLEY_SETTLE_TICKS = 3;

	static boolean reserveReady(Snapshot s, Settings c)
	{
		return s.isQueenAlive() && !s.isQueenDead() && s.getQueenOmegaFired() >= Math.max(1, Math.min(8, c.getQueenEggBatch()))
			&& s.getLastQueenOmegaShotTick() >= 0 && s.getTick() - s.getLastQueenOmegaShotTick() >= VOLLEY_SETTLE_TICKS;
	}

	private static Instruction collectorEggs(Snapshot s, Settings c)
	{
		int batch = Math.max(1, Math.min(8, c.getQueenEggBatch()));
		int buffer = Math.max(0, Math.min(8, c.getQueenEggBuffer()));
		int held = s.count(BaIds.EGG_YELLOW);
		int initialLeft = Math.max(0, batch - s.getQueenYellowPassed());
		int reserveLeft = Math.max(0, batch + buffer - Math.max(batch, s.getQueenYellowPassed()));
		boolean initial = initialLeft > 0;
		int wanted = initial ? initialLeft : reserveLeft;
		if (held > 0 && ((initial && (held >= wanted || s.getFreeSlots() == 0 || s.getTasks().lastActive("queen.col.pass") >= 0))
			|| (!initial && wanted > 0 && reserveReady(s, c))))
		{
			int count = Math.min(held, wanted);
			Instruction handoff = pass(s, BaIds.EGG_YELLOW, "yellow egg", Role.HEALER, count);
			return handoff.toBuilder().task("queen.col.pass").detail(handoff.getDetail()
				+ (initial ? ". Initial batch: " + initialLeft + " left to pass. Keep any extras as reserve"
					: ". The initial " + batch + " omegas were fired and the Queen survived; use the reserve now")).build();
		}
		GroundItemView yellow = s.getGroundItems().stream().filter(g -> g.getItemId() == BaIds.EGG_YELLOW && g.getQuantity() > 0)
			.min(Comparator.comparingInt(g -> s.walk(g.getTile()) - (s.getLastStep() == Step.QUEEN_COLLECT
				&& s.getLastActionTarget() != null && g.getTile().equals(s.getLastActionTarget().getTile()) && s.walk(g.getTile()) <= 2 ? 3 : 0)))
			.orElse(null);
		if (held < wanted && yellow != null && s.getFreeSlots() > 0)
		{
			int take = Math.min(yellow.getQuantity(), Math.min(wanted - held, s.getFreeSlots()));
			return Instruction.builder().step(Step.QUEEN_COLLECT)
				.headline((initial ? "Collect yellow eggs (" : "Collect reserve yellow eggs (") + held + "/" + wanted + ")"
					+ (take > 1 ? " · pick up ×" + take : ""))
				.detail(initial ? "Finish the initial " + batch + "-egg batch, then gather " + buffer + " spare yellows"
					: "Hold these yellows. Pass reserves only after the initial " + batch + " omegas are fired and the Queen survives")
				.target(Target.groundItem(yellow.getItemId(), yellow.getTile())).pickupCount(take).build();
		}
		if (s.getEggsOmega() > 0)
		{
			ObjectView launcher = s.nearestObject(BaIds.LAUNCHERS);
			return Instruction.builder().step(Step.QUEEN_FIRE).headline("Fire the loaded omega at the Queen")
				.detail("Keep the yellow reserve until the initial volley is finished")
				.target(launcher == null ? Target.NONE : Target.object(launcher.getId(), launcher.getTile())).build();
		}
		boolean gathering = held < wanted;
		if (wanted == 0) return Instruction.builder().step(Step.QUEEN_WAIT).headline("Wait for the processed omegas")
			.detail("All configured yellow eggs have been passed. Keep the cannon ready for the remaining omegas").urgency(Urgency.INFO).build();
		return Instruction.builder().step(Step.QUEEN_WAIT)
			.headline(initial ? "Watch for yellow eggs (" + held + "/" + wanted + ")"
				: gathering ? "Watch for reserve eggs (" + held + "/" + wanted + ")" : "Hold the Queen reserve")
			.detail(initial ? "Wait at the marked spot for yellow drops. Gather " + initialLeft + " before passing"
				: "Initial omegas fired: " + s.getQueenOmegaFired() + "/" + batch + ". Keep " + held
					+ " yellow eggs until the initial volley lands; then use them only if the Queen survives")
			.target(!gathering || s.walk(ArenaTiles.queenCollectorWatch()) <= 2 ? Target.NONE
				: Target.tile(ArenaTiles.queenCollectorWatch(), "Watch for yellow eggs")).urgency(Urgency.INFO).build();
	}

	/** Ticks with the queen out and no omega loaded before the chain counts as stuck. */
	static final int STUCK_TICKS = 50;
	/** One spike per omega egg; eight omega hits kill the Queen. */
	static final int SPIKES_WANTED = 8;

	/**
	 * Waiting for your link in the chain: say which egg comes from whom, what you will do with it,
	 * and where to stand (by the healer spring, out of the Queen's reach: recorded team advice).
	 */
	private static Instruction waiting(Snapshot s, Role role)
	{
		// Recorded: the attacker started the Queen with 2-3 spikes and ran back once per egg
		if (role == Role.ATTACKER && s.count(BaIds.SPIKES) < SPIKES_WANTED && s.getQueenSpikedPassed() == 0 && !cannonReady(s))
		{
			ObjectView shroom = s.nearestObject(BaIds.MUSHROOMS);
			return Instruction.builder()
				.step(Step.ATT_SPIKES)
				.headline("Get spikes while you wait (" + s.count(BaIds.SPIKES) + "/" + SPIKES_WANTED + ")")
				.detail("One per poisoned egg. Ignore the Queen and her spawns")
				.target(shroom == null ? Target.NONE : Target.object(shroom.getId(), shroom.getTile()))
				.build();
		}
		String headline;
		String detail;
		switch (role)
		{
			case ATTACKER:
				headline = "Wait for the healer's poisoned egg";
				detail = "Spikes: " + s.count(BaIds.SPIKES) + ". Use spikes on the POISONED egg, not a yellow one";
				break;
			case DEFENDER:
				headline = "Wait for the attacker's spiked egg";
				detail = "Then left-click a lava crater with it";
				break;
			case HEALER:
				headline = QueenHealerPlanner.preparing(s) ? "Wait for the Queen by the healer spring" : "Wait for the collector's yellow egg";
				detail = QueenHealerPlanner.preparing(s) ? "The ordinary Penance are finished. The collector will pass you yellow eggs"
					: "Then use it on a poison crater and pass it to the attacker";
				break;
			default:
				headline = "Wait for yellow eggs under the Queen";
				detail = "Pick them up and pass them to the healer";
				break;
		}
		detail += "\nDon't attack the Queen or her spawns. Stand by the healer spring";
		int queenSince = s.getPenance().stream().filter(p -> p.getType() == PenanceType.QUEEN)
			.mapToInt(p -> s.getTick() - p.getSpawnTick()).max().orElse(0);
		if (queenSince >= STUCK_TICKS && s.getEggsOmega() == 0)
		{
			detail += "\nNo omega yet. Chain: collector yellow > healer poison crater > attacker spikes"
				+ " > defender lava > collector loads";
		}
		ObjectView spring = s.nearestObject(BaIds.SPRING);
		return Instruction.builder()
			.step(Step.QUEEN_WAIT)
			.headline(headline)
			.detail(detail)
			.target(spring == null ? Target.NONE : Target.object(spring.getId(), spring.getTile()))
			.urgency(Urgency.INFO)
			.build();
	}

	/**
	 * Stacking (the one-egg method, wiki): load omega eggs one at a time while everyone at the cannon
	 * keeps clicking Fire, so each egg is fired by several players. Otherwise load them all with one
	 * click (spam-clicking the hopper can delete eggs) and fire them yourself.
	 */
	private static Instruction load(Snapshot s, boolean stack)
	{
		ObjectView hopper = s.nearestObject(BaIds.HOPPERS);
		int n = s.count(BaIds.EGG_OMEGA);
		Target at = hopper == null ? Target.NONE : Target.object(hopper.getId(), hopper.getTile());
		if (stack)
		{
			if (hopper == null)
				return Instruction.builder().step(Step.QUEEN_WAIT).headline("Find a hopper for one-at-a-time loading")
					.detail("Keep the omegas. Stand two tiles south of a hopper, then use one egg on it")
					.items(Planner.items(BaIds.EGG_OMEGA)).build();
			net.runelite.api.coords.WorldPoint stand = hopper.getTile().dy(-2);
			if (!stand.equals(s.getPlayerTile()))
				return Instruction.builder().step(Step.QUEEN_LOAD_POSITION)
					.headline("Stand two tiles south of the hopper")
					.detail("Then stay on this tile and use ONE omega egg on the hopper. Let the team fire before loading another (" + n + " left)")
					.target(Target.tile(stand, "Load from here")).items(Planner.items(BaIds.EGG_OMEGA)).build();
			if (s.getEggsOmega() > 0)
			{
				return Instruction.builder()
					.step(Step.QUEEN_WAIT)
					.headline("Hold: an omega is in the launcher")
					.detail("Stay two tiles south of the hopper. Load the next one when it's fired (" + n + " left)")
					.target(at)
					.items(Planner.items(BaIds.EGG_OMEGA))
					.urgency(Urgency.INFO)
					.build();
			}
			return Instruction.builder()
				.step(Step.QUEEN_LOAD_ONE)
				.headline("Load ONE omega egg")
				.detail("Stay two tiles south. Use one egg on the hopper (clicking the hopper loads them all); the team fires it, then load the next (" + n + " left)")
				.target(at)
				.items(Planner.items(BaIds.EGG_OMEGA))
				.urgency(Urgency.HIGH)
				.build();
		}
		return Instruction.builder()
			.step(Step.QUEEN_LOAD)
			.headline(n > 1 ? "Load your " + n + " omega eggs" : "Load the omega egg into a hopper")
			.detail("Click the hopper once: spam-clicking can delete eggs")
			.target(at)
			.items(Planner.items(BaIds.EGG_OMEGA))
			.urgency(Urgency.HIGH)
			.build();
	}

	private static Instruction pass(Snapshot s, int item, String name, Role to)
	{
		return pass(s, item, name, to, s.count(item));
	}

	private static Instruction pass(Snapshot s, int item, String name, Role to, int limit)
	{
		// Recorded: a spiked egg used on the wrong teammate ("You only need to pass ... to a Defender")
		Target remembered = s.getRole() == Role.COLLECTOR ? s.getTasks().target("queen.col.recipient") : null;
		String who = remembered != null && s.getTeam().get(remembered.getLabel()) == to ? remembered.getLabel() : s.getTeam().entrySet().stream()
			.filter(e -> e.getValue() == to)
			.map(java.util.Map.Entry::getKey)
			.sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
			.findFirst().orElse(null);
		int n = Math.min(s.count(item), limit);
		String what = n > 1 ? n + " " + name + "s" : "the " + name;
		return Instruction.builder()
			.step(Step.QUEEN_PASS)
			.headline("Pass " + what + " to " + (who != null ? who : "the " + to.getLabel().toLowerCase()))
			.detail((who != null ? "The " + to.getLabel().toLowerCase() + ". " : "") + "Use each egg on them")
			.items(Planner.items(item))
			.target(who != null ? Target.player(who) : Target.NONE)
			.urgency(Urgency.HIGH)
			.build();
	}

	/** Healer uses the egg on the crater (inventory first); defender left-clicks the crater (world). */
	private static Instruction dunk(Snapshot s, Step step, int[] craters, int item, String headline, String detail)
	{
		ObjectView crater = s.nearestObject(craters);
		int n = s.count(item);
		return Instruction.builder()
			.step(step)
			.headline(headline + (n > 1 ? " (" + n + ")" : ""))
			.detail(detail)
			.target(crater == null ? Target.NONE : Target.object(crater.getId(), crater.getTile()))
			.items(Planner.items(item))
			.urgency(Urgency.HIGH)
			.build();
	}
}
