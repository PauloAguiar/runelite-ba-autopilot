package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** Queen care has no egg-holding exemption: health, supplies and chain work compete on value. */
final class QueenHealerPlanner
{
	static final double SWITCH_MARGIN = 12;
	private QueenHealerPlanner() { }
	static boolean applies(Snapshot s)
	{
		return s.getRole() == Role.HEALER && s.getWave() == 10 && (s.isQueenAlive()
			|| s.has(BaIds.EGG_YELLOW) || s.has(BaIds.EGG_POISONED) || CollectorPlanner.nothingLeftToShoot(s));
	}

	static boolean preparing(Snapshot s)
	{
		return s.getWave() == 10 && !s.isQueenAlive() && !s.has(BaIds.EGG_YELLOW)
			&& !s.has(BaIds.EGG_POISONED) && CollectorPlanner.nothingLeftToShoot(s);
	}

	static Instruction plan(Snapshot s, Instruction chain)
	{
		// Keep a valid crater destination while walking; passing it near another crater is not completion.
		Target previous = s.getLastActionTarget();
		if (chain.getStep() == Step.QUEEN_DUNK && s.getLastStep() == Step.QUEEN_DUNK
			&& "queen.egg".equals(s.getLastTask()) && previous != null && previous.getKind() == Target.Kind.OBJECT
			&& s.getObjects().stream().anyMatch(o -> o.getId() == previous.getId() && o.getTile().equals(previous.getTile())))
			chain = chain.toBuilder().target(previous).build();
		List<Candidate> out = new ArrayList<>();
		boolean waitingForEggs = chain.getStep() == Step.QUEEN_WAIT;
		int charges = BaIds.vialCharges(s::has);
		ObjectView spring = s.nearestObject(BaIds.SPRING);
		if (spring != null && s.getMaxHp() > 0 && s.getHp() > 0 && s.getHp() < s.getMaxHp())
		{
			Instruction drink = Instruction.builder().step(Step.RETREAT)
				.headline("Drink from the spring (" + s.getHp() + " HP)")
				.detail(preparing(s) ? "Drink from the spring to top up your HP before the Queen"
					: "Top up your own HP, then return to the team and eggs")
				.target(Target.object(spring.getId(), spring.getTile())).urgency(urgency(s.getHp(), s.getMaxHp())).build();
			Map<String, Double> terms = health(s.getHp(), s.getMaxHp(), s.getSelfDamagePerTick(), waitingForEggs);
			terms.put("only you can heal yourself", 4.0 * weakness(s.getHp()));
			terms.put("near spring opportunity", 8.0 / (s.walk(spring.getTile()) + 1));
			out.add(score(s, chain, "queen.self", drink, terms));
		}
		for (TeammateView who : s.getTeammates())
		{
			if (who.getMaxHp() <= 0 || who.getHp() <= 0 || who.getHp() >= who.getMaxHp()) continue;
			Instruction care = care(s, who, charges);
			Map<String, Double> terms = health(who.getHp(), who.getMaxHp(),
				s.getTeammateDamagePerTick().getOrDefault(who.getName(), 0.0), waitingForEggs);
			// A dry/missing vial is the first step of caring for this person, not a reason to dunk eggs.
			terms.put("refill interaction", charges > 0 ? 0 : -3.0);
			out.add(score(s, chain, "queen.heal." + who.getName(), care, terms));
		}
		if (charges < 4)
		{
			Instruction refill = supply(s, charges, null, Urgency.NORMAL);
			Map<String, Double> terms = new LinkedHashMap<>();
			terms.put("vial readiness", 8.0 * (4 - Math.max(0, charges)));
			out.add(score(s, chain, "queen.vial", refill, terms));
		}
		Map<String, Double> egg = new LinkedHashMap<>();
		boolean waiting = chain.getStep() == Step.QUEEN_WAIT;
		egg.put("egg progress", waiting ? 0.0 : chain.getStep() == Step.QUEEN_PASS ? 42.0 : 24.0);
		// With the ordinary wave over, even a small top-up is better than waiting injured.
		if (waiting && preparing(s)) egg.put("unused Queen preparation time", -24.0);
		out.add(score(s, chain, waiting ? "queen.wait" : "queen.egg", chain, egg));
		return Chooser.choose(s, out);
	}

	private static Map<String, Double> health(int hp, int max, double damagePerTick, boolean waiting)
	{
		Map<String, Double> terms = new LinkedHashMap<>();
		// Absolute HP drives danger. Missing HP only breaks otherwise similar top-up choices.
		terms.put("low HP buffer", .5 * Math.pow(Math.max(0, 50 - hp), 2));
		terms.put("missing HP", 8.0 * Math.pow(1 - hp / (double) max, 2));
		double rate = Double.isFinite(damagePerTick) ? Math.max(0, damagePerTick) : 0;
		terms.put("recent damage pressure", 8.0 * weakness(hp) * rate / (rate + 1));
		terms.put("spare healing time", waiting ? 6.0 : 0);
		return terms;
	}

	private static double weakness(int hp) { return Math.min(1, Math.max(0, (55 - hp) / 25.0)); }

	private static Urgency urgency(int hp, int max) { return hp <= 26 || hp / (double) max < .35 ? Urgency.HIGH : Urgency.NORMAL; }

	private static Instruction care(Snapshot s, TeammateView who, int charges)
	{
		Urgency urgency = urgency(who.getHp(), who.getMaxHp());
		if (charges <= 0) return supply(s, charges, who, urgency);
		WorldPoint tile = s.getPlayerTiles().get(who.getName());
		return Instruction.builder().step(Step.HEAL_TEAMMATE)
			.headline("Heal " + who.getName() + " (" + Math.round(who.fraction() * 100) + "%)")
			.detail((preparing(s) ? "Before the Queen: use the vial on " + who.getName() + ". " : "")
				+ who.getHp() + "/" + who.getMaxHp() + " HP. Vial " + charges + "/4")
			.target(new Target(Target.Kind.PLAYER, -1, tile, who.getName()))
			.items(BaIds.VIALS_WITH_WATER).urgency(urgency).build();
	}

	private static Instruction supply(Snapshot s, int charges, TeammateView who, Urgency urgency)
	{
		String need = (preparing(s) ? "Before the Queen: " : "")
			+ (who == null ? "Keep the vial ready for team healing" : who.getName() + " needs it: " + who.getHp() + "/" + who.getMaxHp() + " HP");
		if (charges < 0)
		{
			ObjectView machine = s.nearestObject(BaIds.HEALER_MACHINE);
			return Instruction.builder().step(Step.HEAL_FILL_VIAL).headline("Take a vial from the healer machine")
				.detail(need + ". Take-vial, then fill it at the spring").leftClick("Take-vial")
				.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile())).urgency(urgency).build();
		}
		ObjectView spring = s.nearestObject(BaIds.SPRING);
		return Instruction.builder().step(Step.HEAL_FILL_VIAL)
			.headline(who == null ? "Top up the vial (" + charges + "/4)" : "Refill the vial: " + who.getName() + " needs it")
			.detail(need + ". Use the vial on the spring")
			.target(spring == null ? Target.NONE : Target.object(spring.getId(), spring.getTile()))
			.items(Planner.items(BaIds.vialItem(s::has))).urgency(urgency).build();
	}

	private static Candidate score(Snapshot s, Instruction chain, String task, Instruction ins, Map<String, Double> terms)
	{
		boolean waiting = chain.getStep() == Step.QUEEN_WAIT;
		WorldPoint tile = ins.getTarget().getTile();
		boolean sameWaitingTrip = waiting && tile != null && tile.equals(chain.getTarget().getTile());
		// Travel breaks ordinary top-up ties; it must not make a distant endangered player disposable.
		terms.put("extra travel", (waiting && task.equals("queen.wait")) || sameWaitingTrip ? 0 : -Math.min(16, .8 * s.walk(tile)));
		terms.put("interaction", task.startsWith("queen.egg") || task.equals("queen.wait") ? 0 : -4.0);
		// Idle top-ups/readiness are filler. Only egg work or actual low-HP care earns commitment.
		double margin = task.equals("queen.egg") ? SWITCH_MARGIN
			: Math.min(SWITCH_MARGIN, terms.getOrDefault("low HP buffer", 0.0) / 2);
		terms.put("continue current action", sameAction(s, task, ins) && ins.getStep().getKind() != ActionKind.WAIT ? margin : 0);
		double total = terms.values().stream().mapToDouble(Double::doubleValue).sum();
		return Candidate.of(ins.getUrgency() == Urgency.HIGH && !task.equals("queen.egg") ? Tier.SAFETY : Tier.MAINTENANCE,
			task, ins.toBuilder().score(total).scoreTerms(ImmutableMap.copyOf(terms)).build());
	}

	private static boolean sameAction(Snapshot s, String task, Instruction ins)
	{
		if (!task.equals(s.getLastTask()) || ins.getStep() != s.getLastStep()) return false;
		Target a = ins.getTarget(), b = s.getLastActionTarget();
		if (b == null || a.getKind() != b.getKind() || a.getId() != b.getId() || !Objects.equals(a.getLabel(), b.getLabel())) return false;
		// A teammate can move while we are healing/passing; their name still identifies the same action.
		return a.getKind() == Target.Kind.PLAYER || Objects.equals(a.getTile(), b.getTile());
	}
}
