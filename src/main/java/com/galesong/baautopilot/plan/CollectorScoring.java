package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Comparable action values, not probabilities or predicted wave scores. Constants are tuning weights. */
final class CollectorScoring
{
	private CollectorScoring() { }

	static int reds(Snapshot s) { return s.count(BaIds.EGG_RED) + s.getBagRed(); }
	static int greens(Snapshot s) { return s.count(BaIds.EGG_GREEN) + s.getBagGreen(); }

	static double redValue(Snapshot s)
	{
		if (s.getWave() == 10) return 2 + 90 / Math.pow(1 + (reds(s) + s.getEggsRed()) / 2.0, 2);
		double opening = 1 + .8 * Math.exp(-.7 * Math.max(0, s.getCallPeriod()));
		return 12 + 230 * opening / Math.pow(1 + reds(s) / 3.5, 2);
	}

	static double eggValue(Snapshot s)
	{
		if (s.getWave() == 10) return s.getListenCall() == BaCall.RED_EGG ? redValue(s)
			: s.getListenCall() == BaCall.GREEN_EGG ? 1 + 18 / Math.pow(1 + (greens(s) + s.getEggsGreen()) / 2.0, 2) : 0;
		return s.getListenCall() == BaCall.RED_EGG ? redValue(s)
			: s.getListenCall() == BaCall.GREEN_EGG ? 16 + 24 / (1 + greens(s) / 4.0) : 4;
	}

	/** Continuous severity south of the trap; only visible, live runners reachable by this cannon. */
	static double escapeRisk(Snapshot s, WorldPoint cannon)
	{
		double risk = 0;
		for (PenanceView p : s.getPenance())
		{
			if (p.getType() != PenanceType.RUNNER || p.isDying() || p.isUnseen() || p.getTile() == null
				|| cannon == null || ArenaTiles.distance(cannon, p.getTile()) > TurretChoice.RANGE) continue;
			double south = Math.max(0, ArenaTiles.trapFood(s.getWave()).getY() - p.getTile().getY() - 1);
			double movingSouth = p.getPreviousTile() != null && p.getPreviousTile().getY() > p.getTile().getY() ? 1.15 : 1;
			risk = Math.max(risk, Math.min(1, (1 - Math.exp(-south / 6)) * movingSouth));
		}
		return risk;
	}

	static double activity(Snapshot s)
	{
		int age = s.getTick() - s.getLastLauncherShotTick();
		return s.getLastLauncherShotTick() < 0 || age < 0 ? 0 : Math.exp(-age / 4.0);
	}

	/**
	 * An egg under another item needs a right-click and Take: worth about this many tiles of walking,
	 * so a left-clickable egg comes first unless it is much further away (reported 2026-10-19).
	 */
	static final double RIGHT_CLICK_TILES = 4;

	static Candidate collect(Snapshot s, Instruction ins, int slack, boolean risky)
	{
		return collect(s, ins, slack, risky, false);
	}

	static Candidate collect(Snapshot s, Instruction ins, int slack, boolean risky, boolean rightClick)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		terms.put("next egg / reserve", eggValue(s));
		terms.put("needs right-click", rightClick ? -1.1 * RIGHT_CLICK_TILES : 0);
		// Expiry breaks close ties; it must not send us past a nearby egg for an old distant one.
		terms.put("expiry", s.getListenCall() == BaCall.BLUE_EGG ? 0 : 2 * Math.exp(-Math.max(0, slack) / 8.0));
		terms.put("crowding", risky ? -12.0 : 0);
		double preload = s.getWave() == 10 && CollectorPlanner.loadedAmmo(s) == 0 && reds(s) + greens(s)
			+ s.count(BaIds.EGG_BLUE) + s.getBagBlue() == 0 ? preloadValue(s) : 0;
		double detour = Math.max(0, s.walk(ins.getTarget().getTile()) - 6) / 4.0;
		terms.put("distant egg detour", -(eggValue(s) + preload) * (1 - Math.exp(-detour * detour)));
		if (s.getWave() == 10)
		{
			terms.put("pickup time / no wave-10 points", -8.0);
			terms.put("enable Queen preloading", preload);
		}
		return score(s, "col.collect", s.getListenCall() == BaCall.BLUE_EGG ? Tier.IDLE
			: slack <= CollectorPlanner.EXPIRY_URGENT_TICKS ? Tier.TIME_CRITICAL : Tier.MAINTENANCE, ins, terms);
	}

	static Candidate load(Snapshot s, Settings settings, Instruction ins, int fit, int redFit, double risk)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		terms.put("batch", 4.0 * fit);
		if (s.getWave() == 10) terms.put("useful wave-10 ammunition", 18.0 * redFit / (s.getEggsRed() + 1)
			+ 6.0 * (fit - redFit) / (s.getEggsGreen() + 1));
		terms.put("inventory pressure", 240.0 / Math.pow(s.getFreeSlots() + 1, 1.5));
		double batch = 1 / (1 + Math.exp(-(reds(s) + greens(s) - settings.getCollectorLoadAt()) / 3.0));
		terms.put("blue window", s.getListenCall() == BaCall.BLUE_EGG ? 42 * batch * fit / (fit + 2.0) : 0);
		double shortage = Math.pow(Math.max(0, 5 - s.getEggsRed()) / 5.0, 2);
		double reserve = Math.pow(reds(s) / 8.0, 6);
		terms.put("proactive red supply", CollectorPlanner.cannonTargetsRemain(s)
			? 65 * redFit * shortage * reserve / (reserve + 1) : 0);
		terms.put("escaping runners", 110 * risk * shortage * Math.min(1, redFit / 2.0));
		terms.put("active shooter", 55 * activity(s) * shortage * Math.min(1, redFit / 3.0));
		terms.put("trip / interaction", -18.0);
		terms.put("collection opportunity", s.getListenCall() == BaCall.RED_EGG ? -.4 * redValue(s) : s.getListenCall() == BaCall.GREEN_EGG ? -5.0 : 0);
		boolean support = risk > 0 || (redFit > 0 && s.getEggsRed() <= CollectorPlanner.LOW_RED_AMMO && CollectorPlanner.activelyFiring(s));
		return score(s, support ? CollectorPlanner.SUPPORT_TASK : CollectorPlanner.LOAD_TASK,
			support ? Tier.TEAM_BLOCKING : s.getListenCall() == BaCall.BLUE_EGG || s.getFreeSlots() <= 3
				? Tier.TIME_CRITICAL : Tier.MAINTENANCE, ins, terms);
	}

	static Candidate fire(Snapshot s, Instruction ins, double risk)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		// A rescue may be over before a long trip ends; nearby teammates should take that shot.
		terms.put("escaping runners", 65 * risk * Math.exp(-s.walk(ins.getTarget().getTile()) / 12.0));
		terms.put("self-shooting cost", -28.0);
		terms.put("loaded reds", s.getEggsRed() > 0 ? 3.0 : 0);
		terms.put("leave shooting to others", -15 * activity(s));
		terms.put("collection opportunity", s.getListenCall() == BaCall.RED_EGG ? -.6 * redValue(s)
			: s.getListenCall() == BaCall.GREEN_EGG ? -.4 * eggValue(s) : 0);
		return score(s, risk > 0 ? "col.escape.fire" : "col.fire", risk > 0 ? Tier.TEAM_BLOCKING : Tier.MAINTENANCE, ins, terms);
	}

	static Candidate blue(Snapshot s, Instruction ins)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		terms.put("spare cleanup", .5);
		terms.put("free inventory", (180 + .6 * eggValue(s)) / Math.pow(s.getFreeSlots() + 1, 2));
		return score(s, "col.blue", Tier.IDLE, ins, terms);
	}

	static Candidate preload(Snapshot s, Instruction ins)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		terms.put("enable Queen preloading", preloadValue(s));
		terms.put("load interaction", -8.0);
		return score(s, "col.preload", Tier.MAINTENANCE, ins, terms);
	}

	/** Gather a useful red batch early; guarantee aiming ammo once the regular wave ends. */
	private static double preloadValue(Snapshot s)
	{
		return s.isQueenAlive() || CollectorPlanner.nothingLeftToShoot(s) ? 500 : 16;
	}

	static Candidate makeRoom(Snapshot s, Instruction ins, com.galesong.baautopilot.state.GroundItemView red, boolean green)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		terms.put("inventory pressure", 240.0 / Math.pow(s.getFreeSlots() + 1, 1.5));
		terms.put("room for red eggs", .8 * redValue(s) / (s.getFreeSlots() + 1));
		terms.put("discarded egg value", green ? -(16 + 24 / (1 + greens(s) / 4.0)) : 0);
		terms.put("discard interaction", -6.0);
		terms.put("then reach reds", -1.1 * s.walk(red.getTile()));
		return finish(green ? "col.room.green" : "col.room.blue", Tier.MAINTENANCE, ins, terms);
	}

	static Candidate follow(Snapshot s, Instruction ins)
	{
		return follow(s, ins, Double.POSITIVE_INFINITY);
	}

	static Candidate follow(Snapshot s, Instruction ins, double bestPickupScore)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		double waiting = s.getWave() != 10 && s.getListenCall() == BaCall.RED_EGG ? .3 * redValue(s) : 0;
		terms.put("wait for useful drops", waiting);
		// Waiting for a future drop cannot beat a called, reachable pickup already on the ground.
		// Keep its score visible; useful loading/cleanup still competes with the pickup normally.
		if (ins.getStep() == Step.COL_FOLLOW && Double.isFinite(bestPickupScore))
			terms.put("visible egg before waiting", Math.min(0, bestPickupScore - 1 - waiting));
		// A positioning fallback is not penalised for walking: it remains preferable to pointless work.
		return finish("col.follow", Tier.IDLE, ins, terms);
	}

	private static Candidate score(Snapshot s, String task, Tier tier, Instruction ins, Map<String, Double> terms)
	{
		int walk = s.walk(ins.getTarget().getTile());
		terms.put("travel", -(task.equals("col.collect") && s.getListenCall() != BaCall.BLUE_EGG ? 1.1 : .65) * walk);
		boolean same = task.equals(s.getLastTask()) && s.getLastStep() == ins.getStep() && s.getLastActionTarget() != null
			&& ins.getTarget().getKind() == s.getLastActionTarget().getKind() && ins.getTarget().getId() == s.getLastActionTarget().getId()
			&& ins.getTarget().getTile() != null && ins.getTarget().getTile().equals(s.getLastActionTarget().getTile());
		double continuation = same ? task.equals("col.collect") ? walk <= 2 ? 18.0
			: s.getListenCall() == BaCall.BLUE_EGG ? 2.0 : 4.0 : 2.0 : 0;
		if (s.getWave() == 10 && task.equals("col.collect")) continuation = Math.min(continuation, Math.max(0, eggValue(s) - 8));
		terms.put("continue same target", continuation);
		return finish(task, tier, ins, terms);
	}

	private static Candidate finish(String task, Tier tier, Instruction ins, Map<String, Double> terms)
	{
		double total = terms.values().stream().mapToDouble(Double::doubleValue).sum();
		return Candidate.of(tier, task, ins.toBuilder().score(total).scoreTerms(ImmutableMap.copyOf(terms)).build());
	}
}
