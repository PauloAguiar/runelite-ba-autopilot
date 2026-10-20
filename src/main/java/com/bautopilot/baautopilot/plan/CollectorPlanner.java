package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Collector: only the called colour (a wrong egg costs 5 HP and an egg), preferably from where the
 * attackers fight (eggs near runners and healers lure them or block the defender), keep the
 * hoppers stocked with red/green so anyone can fire at runners and healers. Gather reds, load a
 * batch during blue calls, and load near capacity during green calls. Blue eggs are spare work.
 *
 * <p>Feasible actions compete on weighted value. Opening red scarcity, diminishing reserve value,
 * travel, inventory pressure, batch size and cannon threats trade off continuously. Shooting is
 * costly Collector time; teammates should normally use the ammunition. Calls, survival and Queen
 * eligibility still belong to the outer planner.
 */
class CollectorPlanner
{
	/** Each colour holds up to five loads in the shared hopper (recorded: "can't load any more" at 5). */
	static final int HOPPER_CAP = 5;
	/** Ground eggs vanish this many ticks after they drop (recorded: untouched eggs went at 179-199). */
	static final int EGG_LIFETIME_TICKS = 200;
	/** Running speed, and slack, for deciding whether an egg can still be reached before it vanishes. */
	static final int RUN_TILES_PER_TICK = 2;
	static final int EXPIRY_MARGIN = 3;
	/** Ordinary pickups stay local; yellow Queen eggs have their own collection policy. */
	static final int MAX_EGG_WALK = 12;
	/** Expiry metadata for the debugger; the score determines precedence. */
	static final int EXPIRY_URGENT_TICKS = 10;
	static final int LOW_RED_AMMO = 2;
	/** Confirmed ammunition consumption within 4.8 seconds is active firing. */
	static final int ACTIVE_SHOOTING_TICKS = 8;
	/** Collector points stop at this many eggs a wave (OSRS Wiki: x capped at 60). */
	static final int POINTS_CAP = 60;
	/** Crowded eggs receive a value penalty, rather than an absolute exclusion. */
	static final int AVOID_RADIUS = 2;

	/** Enumerate feasible alternatives; do not remove one merely because another has priority. */
	List<Candidate> candidates(Snapshot s, Settings c)
	{
		List<Candidate> out = new ArrayList<>();
		BaCall call = s.getListenCall();
		boolean blueCall = call == BaCall.BLUE_EGG;
		int blueInv = s.count(BaIds.EGG_BLUE);
		int red = CollectorScoring.reds(s), green = CollectorScoring.greens(s);
		boolean preparing = s.getWave() == 10 && !s.isQueenAlive() && nothingLeftToShoot(s);
		Instruction preload = preload(s);
		if (preload != null) out.add(CollectorScoring.preload(s, preload));
		if (preparing && loadedAmmo(s) > 0)
		{
			// Nothing left to shoot and the cannon preloaded: ordinary eggs of every colour are dead weight,
			// and the Queen's yellow eggs need the room (reported 2026-10-19: not only blue)
			Instruction spare = destroySpare(s);
			return spare == null ? List.of(CollectorScoring.follow(s, queenReady(s)))
				: List.of(CollectorScoring.blue(s, spare), CollectorScoring.follow(s, queenReady(s)));
		}
		String full = fullHoppers(s, red, green);
		Snapshot afterDiscard = s.toBuilder().freeSlots(Math.max(1, s.getFreeSlots()))
			.ticksToCallChange(s.getTicksToCallChange() < 0 ? -1 : Math.max(0, s.getTicksToCallChange() - 1)).build();
		GroundItemView roomRed = call == BaCall.RED_EGG && s.isListenFresh() && s.getFreeSlots() <= 2
			&& (s.getWave() != 10 || CollectorScoring.redValue(s) > 8)
			? s.getGroundItems().stream().filter(g -> g.getItemId() == BaIds.EGG_RED && pickable(afterDiscard, g) > 0)
				.min(java.util.Comparator.comparingInt(g -> s.walk(g.getTile()))).orElse(null) : null;
		if (call != null && s.isListenFresh() && s.getFreeSlots() > 0)
		{
			for (GroundItemView egg : s.ground(g -> g.getItemId() == call.getItemId() && pickable(s, g) > 0))
			{
				int slack = ticksLeft(s, egg) - travelTicks(s, egg);
				int take = preparing ? 1 : usefulPickups(s, pickable(s, egg));
				if (take == 0) continue;
				out.add(CollectorScoring.collect(s, collect(s, call, egg, take, full), slack, !safe(s, egg), egg.isUnderneath()));
			}
		}
		int redFit = Math.min(red, Math.max(0, HOPPER_CAP - s.getEggsRed()));
		int greenFit = Math.min(green, Math.max(0, HOPPER_CAP - s.getEggsGreen()));
		int fit = redFit + greenFit;
		// Both hoppers replenish the same ammunition. The threat is shared; only travel differs.
		double supplyRisk = s.getObjects().stream().filter(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId()))
			.mapToDouble(o -> CollectorScoring.escapeRisk(s, o.getTile())).max().orElse(0);
		for (ObjectView object : s.getObjects())
		{
			if (fit > 0 && BaIds.contains(BaIds.HOPPERS, object.getId()))
			{
				Instruction load = Instruction.builder().step(Step.COL_LOAD).headline("Load your eggs into the hopper")
					.detail((blueCall ? "Blue call: " : "") + redFit + " red + " + greenFit + " green fit. "
						+ "Both cannons share ammunition; use the nearest hopper.\n" + holdings(s))
					.items(Planner.items(BaIds.EGG_RED, BaIds.EGG_GREEN)).target(Target.object(object.getId(), object.getTile())).build();
				out.add(CollectorScoring.load(s, c, load, fit, redFit, supplyRisk));
			}
			if (BaIds.contains(BaIds.LAUNCHERS, object.getId()) && TurretChoice.pick(s, object.getTile()) != TurretChoice.NONE)
				out.add(CollectorScoring.fire(s, fire(s, object), CollectorScoring.escapeRisk(s, object.getTile())));
		}
		if (blueInv > 0) out.add(roomRed == null ? CollectorScoring.blue(s, destroyBlue(blueInv, blueCall, s.getWave()))
			: CollectorScoring.makeRoom(s, discardForRed(s, BaIds.EGG_BLUE), roomRed, false));
		if (roomRed != null && s.count(BaIds.EGG_GREEN) > 0)
			out.add(CollectorScoring.makeRoom(s, discardForRed(s, BaIds.EGG_GREEN), roomRed, true));
		Instruction fallback = preparing ? queenReady(s) : positioning(s, call);
		double bestPickup = out.stream().filter(candidate -> "col.collect".equals(candidate.getTask()))
			.mapToDouble(candidate -> candidate.getInstruction().getScore()).max().orElse(Double.POSITIVE_INFINITY);
		out.add(CollectorScoring.follow(s, fallback, bestPickup));
		return out;
	}
	static final String LOAD_TASK = "col.load";
	static final String SUPPORT_TASK = "col.support";

	/** Absence from the scene is not evidence that every runner and healer has died. */
	static boolean cannonTargetsRemain(Snapshot s)
	{
		return WaveInfo.valid(s.getWave()) && ((!s.getCleared().contains(PenanceType.RUNNER)
			&& s.getRunnersKilled() < WaveInfo.runnersTotal(s.getWave()))
			|| (!s.getCleared().contains(PenanceType.HEALER) && s.getHealersKilled() < WaveInfo.healersTotal(s.getWave())));
	}

	static int loadedAmmo(Snapshot s)
	{
		return s.getEggsRed() + s.getEggsGreen() + s.getEggsBlue() + s.getEggsOmega();
	}

	/** Wave 10 piles are ammunition, not points. A blue only helps enable an empty cannon. */
	private static int usefulPickups(Snapshot s, int safe)
	{
		if (s.getWave() != 10) return safe;
		int needed = s.getListenCall() == BaCall.RED_EGG ? HOPPER_CAP - CollectorScoring.reds(s) - s.getEggsRed()
			: s.getListenCall() == BaCall.GREEN_EGG ? 2 - CollectorScoring.greens(s) - s.getEggsGreen()
			: loadedAmmo(s) == 0 && CollectorScoring.reds(s) + CollectorScoring.greens(s)
				+ s.count(BaIds.EGG_BLUE) + s.getBagBlue() == 0 ? 1 : 0;
		return Math.min(safe, Math.max(0, needed));
	}

	/** Any ordinary colour enables the aiming interface, including blue during wave 10. */
	static Instruction preload(Snapshot s)
	{
		if (s.getWave() != 10 || s.isQueenDead() || loadedAmmo(s) > 0
			|| CollectorScoring.reds(s) + CollectorScoring.greens(s) + s.count(BaIds.EGG_BLUE) + s.getBagBlue() <= 0) return null;
		ObjectView hopper = s.nearestObject(BaIds.HOPPERS);
		if (hopper == null) return null;
		// One egg of any single colour is enough; highlighting every held colour read as "one of each".
		int egg = CollectorScoring.reds(s) > 0 ? BaIds.EGG_RED : CollectorScoring.greens(s) > 0 ? BaIds.EGG_GREEN : BaIds.EGG_BLUE;
		return Instruction.builder().step(Step.COL_LOAD).headline("Preload the cannon with an egg")
			.detail("One egg is enough, any colour: teammates can then open the aiming interface and pre-click Omega. Reds also help clear Penance.\n" + holdings(s))
			.items(Planner.items(egg)).target(Target.object(hopper.getId(), hopper.getTile())).build();
	}

	private static Instruction queenReady(Snapshot s)
	{
		boolean loaded = loadedAmmo(s) > 0;
		return Instruction.builder().step(Step.COL_QUEEN_READY).headline(loaded ? "Get ready for the Queen" : "Keep one egg ready for cannon preloading")
			.detail(loaded ? "Cannon preloaded. Stop ordinary egg collection and wait for yellow drops"
				: "The cannon needs an egg of any colour. Pick up only a safely called egg, then load it before the Queen")
			.target(s.walk(ArenaTiles.queenCollectorWatch()) <= 2 ? Target.NONE : Target.tile(ArenaTiles.queenCollectorWatch(), "Watch for yellow eggs"))
			.urgency(Urgency.INFO).build();
	}

	/** Supply must address the escaping threat or the low-red shortage; green-only loads cannot refill reds. */
	static boolean supportReady(Snapshot s)
	{
		if (!WaveInfo.valid(s.getWave())) return false;
		int reds = s.count(BaIds.EGG_RED) + s.getBagRed();
		boolean refillRed = reds > 0 && s.getEggsRed() <= LOW_RED_AMMO;
		if (refillRed && activelyFiring(s)) return true;
		if (escapingLauncher(s, true) == null) return false;
		return refillRed || s.getEggsRed() + s.getEggsGreen() == 0;
	}

	static boolean activelyFiring(Snapshot s)
	{
		int age = s.getTick() - s.getLastLauncherShotTick();
		return s.getLastLauncherShotTick() >= 0 && age >= 0 && age <= ACTIVE_SHOOTING_TICKS;
	}

	private static ObjectView escapingLauncher(Snapshot s, boolean includeHeld)
	{
		if (!WaveInfo.valid(s.getWave())) return null;
		List<PenanceView> escaping = s.getPenance().stream().filter(p -> p.getType() == PenanceType.RUNNER
			&& !p.isDying() && !p.isUnseen() && RunnerState.of(s, p) == RunnerState.ESCAPING).collect(Collectors.toList());
		if (escaping.isEmpty()) return null;
		Snapshot targets = s.toBuilder().penance(escaping).unseenPenance(List.of()).build();
		if (!includeHeld) return TurretChoice.bestLauncher(targets);
		Snapshot supplied = targets.toBuilder()
			.eggsRed(s.getEggsRed() + Math.min(s.count(BaIds.EGG_RED) + s.getBagRed(), Math.max(0, HOPPER_CAP - s.getEggsRed())))
			.eggsGreen(s.getEggsGreen() + Math.min(s.count(BaIds.EGG_GREEN) + s.getBagGreen(), Math.max(0, HOPPER_CAP - s.getEggsGreen())))
			.build();
		return TurretChoice.bestLauncher(supplied);
	}

	private static Instruction collect(Snapshot s, BaCall call, GroundItemView egg, int pickable, String full)
	{
		boolean risky = !safe(s, egg);
		// Wave 10 points are fixed, so no counter there
		int counted = s.getWaveStats() == null || s.getWave() == 10 ? -1
			: Math.max(0, s.getWaveStats().getEggsPicked() - s.getWaveStats().getEggsWrong());
		String points = counted < 0 ? ""
			: counted >= POINTS_CAP ? POINTS_CAP + "/" + POINTS_CAP + " for points: more eggs only feed the launcher"
			: counted + "/" + POINTS_CAP + " for points";
		// Most useful first: the box shows only the first line at a glance, the rest is for diagnostics
		List<String> lines = new ArrayList<>();
		if (risky) lines.add("Careful: a runner or healer is next to it");
		if (egg.getQuantity() > 1) lines.add(egg.getQuantity() + " on this tile; take " + pickable + " while the call is safe");
		if (!full.isEmpty()) lines.add(full + (s.getWave() == 10 ? " Collect only for useful ammunition" : " Keep collecting: they still count"));
		if (!points.isEmpty()) lines.add(points);
		lines.add(holdings(s));
		if (s.getTicksToCallChange() >= 0) lines.add(s.getTicksToCallChange() + " ticks to call change; ~" + travelTicks(s, egg) + " ticks travel");
		// Under another item, a left-click would take that one: say how to take this egg
		return Instruction.builder()
			.step(Step.COL_COLLECT)
			.headline("Pick up " + call.getItemName() + (pickable > 1 ? " ×" + pickable : "")
				+ (egg.isUnderneath() ? ": right-click, Take" : ""))
			.detail(String.join("\n", lines))
			.target(Target.groundItem(egg.getItemId(), egg.getTile()))
			.pickupCount(pickable)
			.rightClick(egg.isUnderneath())
			.build();
	}

	private static Instruction positioning(Snapshot s, BaCall call)
	{
		if (!s.isCollectorOpeningComplete() && s.getPlayerTile() != null && s.getWaveTick() >= 0 && s.getWaveTick() < WaveInfo.TICKS_PER_CALL
			&& (s.getPlayerTile().getY() & 63) < 37)
		{
			boolean west = (s.getPlayerTile().getX() & 63) <= 19
				&& (s.getPlayerTile().getY() & 63) >= (s.getWave() == 10 ? 30 : 28);
			return Instruction.builder().step(Step.COL_POSITION)
				.headline(west ? "Move north behind the west cannon hill" : "Go west around the cannon hill")
				.detail("Use the western approach to the attackers. You can move before their egg call")
				.target(Target.tile(west ? ArenaTiles.collectorBehindHill(s.getWave()) : ArenaTiles.collectorWestApproach(s.getWave()), "West route"))
				.urgency(Urgency.INFO).build();
		}
		if (call != null) return follow(s, call);
		return Instruction.builder().step(Step.COL_WAIT_CALL).headline("Wait for the attacker's call")
			.detail("Stay where the eggs are dropping; only pick up after a fresh colour call.\n" + holdings(s))
			.urgency(Urgency.INFO).build();
	}

	private static Instruction follow(Snapshot s, BaCall call)
	{
		String full = fullHoppers(s, s.count(BaIds.EGG_RED) + s.getBagRed(), s.count(BaIds.EGG_GREEN) + s.getBagGreen());
		return Instruction.builder()
			.step(Step.COL_FOLLOW)
			.headline("Wait near the attackers for " + call.getItemName())
			.detail((s.getWave() == 10 ? "Keep a small red supply for remaining Penance and one loaded egg for Queen preloading. Ordinary eggs do not score on wave 10."
				: call == BaCall.RED_EGG ? (s.getCallPeriod() < 2
				? "Opening red call: stay for drops and build your red stockpile. Save cannon trips for blue."
				: "Stay for red drops and stockpile reds.") + (full.isEmpty() ? "" : " " + full + " Keep your eggs.")
				: "Eggs drop where fighters and rangers die, by their caves") + "\n" + holdings(s))
			.urgency(Urgency.INFO).build();
	}

	/**
	 * Which shot: runners first (5 HP, and every escape costs the team), then healers; red before
	 * green (red hits for about 3 and splashes, green only poisons). Never blue.
	 */
	static Instruction fire(Snapshot s, ObjectView launcher)
	{
		// Same choice as the aiming window highlight, for targets in this launcher's range
		int button = TurretChoice.pick(s, launcher.getTile());
		String label = TurretChoice.label(button);
		String colour = label.startsWith("Red") ? "red" : "green";
		String at = label.endsWith("Healer") ? "healers" : "runners";
		boolean healersToo = at.equals("runners") && TurretChoice.pick(s.toBuilder().penance(
			s.penance(PenanceType.HEALER)).build(), launcher.getTile()) != TurretChoice.NONE;
		String side = side(s, launcher);
		return Instruction.builder()
			.step(Step.COL_FIRE)
			.headline("Fire the launcher: " + colour + " at " + at)
			.detail((side.isEmpty() ? "" : "The " + side + " one is in range. ") + "Shoot, then " + colour + " at " + at
				+ (healersToo ? " (healers next)" : "") + ". Never blue")
			.target(Target.object(launcher.getId(), launcher.getTile()))
			.build();
	}

	private static String holdings(Snapshot s)
	{
		return "Inventory: " + s.count(BaIds.EGG_RED) + " red / " + s.count(BaIds.EGG_GREEN) + " green / " + s.count(BaIds.EGG_BLUE)
			+ " blue; " + s.getFreeSlots() + " free slots\nBag: " + s.getBagRed() + " red / " + s.getBagGreen() + " green / " + s.getBagBlue() + " blue";
	}

	private static Instruction discardForRed(Snapshot s, int item)
	{
		String colour = item == BaIds.EGG_GREEN ? "green" : "blue";
		return Instruction.builder().step(Step.COL_DISCARD).headline("Destroy a " + colour + " egg to make room for reds")
			.detail("Shift-click an inventory " + colour + " egg, then collect red. Bag eggs do not free inventory slots.\n" + holdings(s))
			.items(Planner.items(item)).build();
	}

	/** "west" / "east" for the two launchers, "" when only one is known. */
	private static String side(Snapshot s, ObjectView launcher)
	{
		long launchers = s.getObjects().stream().filter(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId())).count();
		if (launchers < 2)
		{
			return "";
		}
		boolean westmost = s.getObjects().stream()
			.filter(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId()))
			.allMatch(o -> o.getTile().getX() >= launcher.getTile().getX());
		return westmost ? "west" : "east";
	}

	/** Every ordinary egg in the inventory (bag eggs take no slot), or null when there are none. */
	private static Instruction destroySpare(Snapshot s)
	{
		java.util.List<Integer> held = new ArrayList<>();
		for (int egg : new int[]{BaIds.EGG_RED, BaIds.EGG_GREEN, BaIds.EGG_BLUE})
		{
			if (s.has(egg))
			{
				held.add(egg);
			}
		}
		if (held.isEmpty())
		{
			return null;
		}
		int count = s.count(BaIds.EGG_RED) + s.count(BaIds.EGG_GREEN) + s.count(BaIds.EGG_BLUE);
		return Instruction.builder()
			.step(Step.COL_DISCARD)
			.headline("Destroy your spare egg" + (count == 1 ? "" : "s"))
			.detail("Shift-click, Destroy. Ordinary eggs no longer do anything: make room for the Queen's yellow eggs")
			.items(Planner.items(held.stream().mapToInt(Integer::intValue).toArray()))
			.build();
	}

	private static Instruction destroyBlue(int blue, boolean blueCall, int wave)
	{
		return Instruction.builder()
			.step(Step.COL_BLUE)
			.headline("Destroy your blue egg" + (blue == 1 ? "" : "s"))
			.detail("Shift-click, Destroy. " + (wave == 10 ? "Make inventory room." : blueCall ? "They already counted." : "Costs nothing.")
				+ (wave == 10 ? "\nKeep one loaded egg of any colour for Queen preloading. Spare blues have no point value" : "\nOr load them with the rest: no real use, but it frees space"))
			.items(Planner.items(BaIds.EGG_BLUE))
			.build();
	}

	/** "Red hopper is full (5/5)." for each colour you hold that can't be loaded, or "". */
	private static String fullHoppers(Snapshot s, int red, int green)
	{
		StringBuilder b = new StringBuilder();
		if (red > 0 && s.getEggsRed() >= HOPPER_CAP)
		{
			b.append("Red");
		}
		if (green > 0 && s.getEggsGreen() >= HOPPER_CAP)
		{
			b.append(b.length() > 0 ? " and green" : "Green");
		}
		return b.length() == 0 ? "" : b + " hopper" + (b.indexOf(" and ") > 0 ? "s are" : " is") + " full (5/5).";
	}

	/** Every fighter, ranger, runner and healer of the wave is dead: no egg drops, no launcher target. */
	static boolean nothingLeftToShoot(Snapshot s)
	{
		int wave = s.getWave();
		for (PenanceType type : new PenanceType[]{PenanceType.FIGHTER, PenanceType.RANGER, PenanceType.RUNNER, PenanceType.HEALER})
		{
			if (!s.alive(type).isEmpty())
			{
				return false;
			}
		}
		return s.getFighterRangerKills() >= WaveInfo.fightersTotal(wave) + WaveInfo.rangersTotal(wave)
			&& s.getRunnersKilled() >= WaveInfo.runnersTotal(wave)
			&& s.getHealersKilled() >= WaveInfo.healersTotal(wave);
	}

	/** Ticks until a ground egg vanishes. */
	private static int ticksLeft(Snapshot s, GroundItemView egg)
	{
		return EGG_LIFETIME_TICKS - (s.getTick() - egg.getSpawnTick());
	}

	private static int travelTicks(Snapshot s, GroundItemView egg)
	{
		return (s.walk(egg.getTile()) + RUN_TILES_PER_TICK - 1) / RUN_TILES_PER_TICK;
	}

	/** Capacity, running, reaction/interaction slack and one tick per pickup, before either deadline. */
	static int pickable(Snapshot s, GroundItemView egg)
	{
		if (egg.getTile() == null || egg.getQuantity() <= 0 || s.getFreeSlots() <= 0) return 0;
		if (s.walk(egg.getTile()) > MAX_EGG_WALK) return 0;
		int deadline = ticksLeft(s, egg);
		if (s.getTicksToCallChange() >= 0) deadline = Math.min(deadline, s.getTicksToCallChange());
		return Math.max(0, Math.min(Math.min(egg.getQuantity(), s.getFreeSlots()), deadline - travelTicks(s, egg) - EXPIRY_MARGIN));
	}

	private static boolean safe(Snapshot s, GroundItemView egg)
	{
		for (PenanceView p : s.getPenance())
		{
			if ((p.getType() == PenanceType.RUNNER || p.getType() == PenanceType.HEALER)
				&& ArenaTiles.distance(p.getTile(), egg.getTile()) <= AVOID_RADIUS)
			{
				return false;
			}
		}
		return true;
	}
}
