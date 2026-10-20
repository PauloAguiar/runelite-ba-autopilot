package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.HealCode;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceDiagnostics;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.state.TeammateView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Healer: cover every unpoisoned healer, including those last seen out of view, then refresh
 * poison below 4, lowest strength first. With coverage secure, spare food can finish a nearby
 * healer to release reserves. Optional heal codes only break equivalent-target ties.
 */
class HealerPlanner
{
	/**
	 * A teammate in real danger is safety; food for the current call and needed poison are time
	 * critical; topping up a hurt teammate is upkeep; waiting for the next spawn is idle.
	 */
	List<Candidate> candidates(Snapshot s, Settings c)
	{
		List<Candidate> out = new ArrayList<>();
		if (s.getWave() < 10 && CollectorPlanner.nothingLeftToShoot(s))
		{
			out.add(Candidate.of(Tier.IDLE, "heal.complete", Instruction.builder()
				.step(Step.HEAL_SUPPORT).headline("Wave complete")
				.detail("Wait for the next wave: everyone's HP restores automatically")
				.urgency(Urgency.INFO).build()));
			return out;
		}
		BaCall call = s.getListenCall();
		Instruction medic = medic(s, c, true);
		if (medic != null)
		{
			out.add(Candidate.task(Tier.SAFETY, "heal.medic", medic));
		}

		// Stock-Up needs no call: from the first tick, then up the east side to the healer spawn
		// (recorded 2026-10-17: "Wait for the defender's call" for 7 ticks, then a wait, while the
		// player stocked up by hand)
		boolean healersToCome = !s.alive(PenanceType.HEALER).isEmpty()
			|| s.getHealersKilled() < WaveInfo.healersTotal(s.getWave());
		if (healersToCome && BaIds.POISON_FOOD.stream().noneMatch(s::has) && s.getWaveTick() >= 0
			&& s.getWaveTick() < WaveInfo.TICKS_PER_CALL)
		{
			ObjectView machine = s.nearestObject(BaIds.HEALER_MACHINE);
			out.add(Candidate.task(Tier.TIME_CRITICAL, "heal.stock", Instruction.builder()
				.step(Step.HEAL_TAKE_FOOD)
				.headline("Stock-Up at the healer item machine")
				.detail("All three foods in one click; no call needed")
				.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
				.leftClick("Stock-Up")
				.build()));
			return out;
		}
		if (call == null)
		{
			if (s.getWaveTick() >= 0 && s.getWaveTick() < WaveInfo.TICKS_PER_CALL)
			{
				out.add(Candidate.of(Tier.IDLE, "heal.wait", Instruction.builder()
					.step(Step.HEAL_WAIT_CALL)
					.headline("Head to the healer spawn")
					.detail("Feed the first healer as soon as the defender calls")
					.target(Target.tile(ArenaTiles.healerWait(s.getWave()), "Healers"))
					.urgency(Urgency.INFO)
					.build()));
				return out;
			}
			out.add(Candidate.of(Tier.IDLE, "heal.wait", Instruction.builder()
				.step(Step.HEAL_WAIT_CALL)
				.headline("Wait for the defender's call")
				.detail("Stand north-east where the healers spawn; wrong food is a penalty")
				.urgency(Urgency.INFO)
				.build()));
			return out;
		}

		int food = call.getItemId();
		String stale = staleFood(s, food);
		List<PenanceView> alive = s.alive(PenanceType.HEALER);
		boolean healersLeft = !alive.isEmpty() || s.getHealersKilled() < WaveInfo.healersTotal(s.getWave());
		if (!s.has(food) && healersLeft)
		{
			ObjectView machine = s.nearestObject(BaIds.HEALER_MACHINE);
			out.add(Candidate.task(Tier.TIME_CRITICAL, "heal.food", Instruction.builder()
				.step(Step.HEAL_TAKE_FOOD)
				.headline("Take " + call.getItemName())
				.leftClick("Take-" + call.getKeyword())
				.detail("Healer item machine: Take-" + call.getKeyword() + (stale.isEmpty() ? "" : "\n" + stale))
				.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
				.build()));
			return out;
		}

		// Healers that walked out of view stay on the list: they're alive, and dropping them made the
		// target jump to another healer and back (reported 2026-10-16)
		List<PenanceView> healers = s.alive(PenanceType.HEALER).stream()
			.sorted(Comparator.comparingInt(PenanceView::getOrder))
			.collect(Collectors.toList());

		HealCode code = HealCode.of(c.getHealCodeSet(), s.getWave());
		Candidate pick = byPriority(s, healers, call, code);
		if (pick == null) pick = reserveFeed(s, healers, call);
		if (pick != null)
		{
			Instruction ins = pick.getInstruction();
			out.add(stale.isEmpty() ? pick : new Candidate(pick.getTier(), pick.getTask(),
				ins.toBuilder().detail(ins.getDetail() + "\n" + stale).build(), pick.getDeadline(), pick.isSticky()));
		}

		Instruction support = medic(s, c, false);
		if (support != null)
		{
			out.add(Candidate.task(Tier.MAINTENANCE, "heal.medic", support));
		}
		if (pick != null)
		{
			return out;
		}
		boolean more = s.getHealersKilled() + healers.size() < WaveInfo.healersTotal(s.getWave());
		int keep = foodToKeep(s, healers);
		int reserves = reservesWaiting(s);
		// A trip for one or two foods isn't worth leaving the healers for
		if (s.count(food) < keep - TOPUP_SLACK)
		{
			// Nothing to feed now: fill up for the healers still to come instead of running out later
			ObjectView machine = s.nearestObject(BaIds.HEALER_MACHINE);
			out.add(Candidate.task(Tier.MAINTENANCE, "heal.topup", Instruction.builder()
				.step(Step.HEAL_TAKE_FOOD)
				.headline("Top up " + call.getItemName() + " (" + s.count(food) + "/" + keep + ")")
				.detail("Every healer out is poisoned: get food now for the ones still to come")
				.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
				.leftClick("Take-" + call.getKeyword())
				.build()));
		}
		out.add(Candidate.of(Tier.IDLE, "heal.support", Instruction.builder()
			.step(Step.HEAL_SUPPORT)
			.headline(!healers.isEmpty() ? "All healers poisoned: let it work"
				: more ? "Wait where the next healer spawns" : "All penance healers are down")
			.detail(!healers.isEmpty()
				? "Keeping " + keep + " food for coverage. " + (reserves > 0
					? reserves + " reserves still need a kill; use spare food on a nearby healer when available"
					: "No reserve slot needs opening; refresh when poison falls below 4")
				: more ? "On the marked tile by the healer cave" : "Support the team while the remaining Penance are finished")
			.target(more ? Target.tile(ArenaTiles.healerWait(s.getWave()), "Wait here") : Target.NONE)
			.urgency(Urgency.INFO)
			.build()));
		return out;
	}

	private static Candidate byPriority(Snapshot s, List<PenanceView> healers, BaCall call, HealCode code)
	{
		int tick = s.getTick();
		Target coverage = s.getTasks().target("heal.cover");
		// Strength beats visibility, guide timing and commitment. Within equivalent strength,
		// cover visible healthy newcomers before wounded ones, retaining the target within that
		// health band so small health-bar changes do not make guidance flicker.
		PenanceView h = healers.stream().filter(p -> p.nextPoisonHit(tick) < WaveInfo.FOOD_DAMAGE)
			.min(Comparator.comparingInt((PenanceView p) -> p.nextPoisonHit(tick))
				.thenComparing(PenanceView::isUnseen)
				.thenComparingInt(p -> p.hpFraction() > 0.5 ? 0 : 1)
				.thenComparingInt(p -> p.getNpcIndex() == s.getLastTargetNpc()
					|| coverage != null && p.getNpcIndex() == coverage.getId() ? 0 : 1)
				.thenComparingInt(p -> guideDue(p, s, code) ? 0 : 1)
				.thenComparingInt(PenanceView::getOrder))
			.orElse(null);
		if (h == null)
		{
			return null;
		}
		int strength = h.nextPoisonHit(tick);
		String why = strength == 0 ? (h.poisoned() ? "Poison has expired" : "Initial poison")
			+ ": feed before refreshing poisoned healers. Wait for the food to land"
			: "Poison down to " + strength + " a hit: refresh once to 4. Lower poison comes first";
		if (strength > 0 && guideDue(h, s, code)) why += ". Guide " + code.getName();
		Instruction ins = feed(h, call, why);
		return strength == 0 ? Candidate.task(Tier.TIME_CRITICAL, "heal.cover", ins)
			: Candidate.of(Tier.TIME_CRITICAL, "heal.feed", ins);
	}

	static final String RESERVE_TASK = "heal.reserve";
	/** Extra damage is local work, not a reason to chase a healer across the arena. */
	static final int RESERVE_RANGE = 8;

	/**
	 * Each death releases one reserve, even if the staggered initial spawns are not all out yet.
	 * Dying healers are already included in the snapshot's kill count. A healer guaranteed to
	 * die to its next 4-poison hit also supplies a slot without spending more food.
	 * Observed reserve spawn orders also establish progress if a death was missed out of view.
	 */
	static int reservesWaiting(Snapshot s)
	{
		if (s.getCleared().contains(PenanceType.HEALER)) return 0;
		int initial = WaveInfo.healersInitial(s.getWave());
		int total = WaveInfo.healersTotal(s.getWave());
		List<PenanceView> alive = s.alive(PenanceType.HEALER);
		int imminent = (int) alive.stream().filter(p -> !p.isUnseen() && p.nextPoisonHit(s.getTick()) == 4
			&& knownHpUpper(s, p) > 0 && knownHpUpper(s, p) <= WaveInfo.FOOD_DAMAGE).count();
		int observed = alive.stream().mapToInt(PenanceView::getOrder).max().orElse(0);
		int released = Math.max(s.getHealersKilled(), observed - initial) + imminent;
		return Math.max(0, total - initial - released);
	}

	/** Only called after every first tag and weak-poison refresh has been covered. */
	private static Candidate reserveFeed(Snapshot s, List<PenanceView> healers, BaCall call)
	{
		int reserves = reservesWaiting(s);
		int keep = foodToKeep(s, healers);
		if (reserves == 0 || s.count(call.getItemId()) <= keep) return null;
		Target remembered = s.getTasks().target(RESERVE_TASK);
		PenanceView h = healers.stream().filter(p -> !p.isUnseen() && p.getTile() != null
			&& s.getPlayerTile() != null && s.walk(p.getTile()) <= RESERVE_RANGE
			&& (s.getTicksToCallChange() < 0 || feedTravel(s, p) + 2 < s.getTicksToCallChange())
			&& hpUpper(s, p) > WaveInfo.FOOD_DAMAGE)
			.min(Comparator.comparingDouble((PenanceView p) -> hpUpper(s, p) + WaveInfo.FOOD_DAMAGE * feedTravel(s, p)
				// One food's worth of continuity avoids changing targets on small HP-bar differences.
				- (RESERVE_TASK.equals(s.getLastTask()) && p.getNpcIndex() == s.getLastTargetNpc()
					|| remembered != null && p.getNpcIndex() == remembered.getId() ? WaveInfo.FOOD_DAMAGE : 0))
				.thenComparingInt(PenanceView::getOrder)).orElse(null);
		if (h == null) return null;
		Instruction ins = feed(h, call, "Finish this healer to open a spawn slot: " + reserves
			+ " reserve" + (reserves == 1 ? " still needs a kill" : "s still need kills") + ". Extra food deals 4 direct damage even at 4 poison"
			+ ". Keep " + keep + " food for coverage; switch to any new unpoisoned healer first")
			.toBuilder().headline("Finish healer #" + h.getOrder() + " with " + call.getItemName()).build();
		// Re-evaluate every tick: this commitment cannot bypass new tags, refreshes or safety.
		return Candidate.of(Tier.TIME_CRITICAL, RESERVE_TASK, ins);
	}

	private static int feedTravel(Snapshot s, PenanceView h)
	{
		return 1 + (Math.max(0, s.walk(h.getTile()) - 1) + 1) / 2;
	}

	private static int knownHpUpper(Snapshot s, PenanceView h)
	{
		return PenanceDiagnostics.healer(s, h).getHpMax();
	}

	private static int hpUpper(Snapshot s, PenanceView h)
	{
		int known = knownHpUpper(s, h);
		return known >= 0 ? known : WaveInfo.healerHp(s.getWave());
	}

	/** Codes advise only between equally urgent targets; a future code entry never creates a wait. */
	private static boolean guideDue(PenanceView h, Snapshot s, HealCode code)
	{
		HealCode.Feed f = code == null ? null : code.feed(s.getCallPeriod(), h.getOrder());
		return f != null && f.getAfterSeconds() <= s.waveSeconds()
			&& (f.getCount() == HealCode.SPAM || f.getCount() > h.getFoodsThisPeriod());
	}

	/** Top up only when this many foods short of what the healers still to come need. */
	static final int TOPUP_SLACK = 2;

	/** HP left: the damage-based estimate when known, else the health bar times the wave's HP. */
	static double hpLeft(PenanceView h, int wave)
	{
		int est = h.estimatedHpLeft();
		return est >= 0 ? est : h.hpFraction() * WaveInfo.healerHp(wave);
	}

	/**
	 * One initial tag for each healer still to spawn, plus a refresh for each current healer the
	 * poison won't finish. Reserving two for every unseen future spawn sent the player back to the
	 * machine with enough food for coverage already in hand.
	 */
	static int foodToKeep(Snapshot s, List<PenanceView> healers)
	{
		int toSpawn = Math.max(0, WaveInfo.healersTotal(s.getWave()) - s.getHealersKilled() - healers.size());
		int tick = s.getTick();
		long short_ = healers.stream().filter(h -> h.poisonRemaining(tick) < hpLeft(h, s.getWave())).count();
		return toSpawn + (int) short_;
	}

	private static Instruction feed(PenanceView h, BaCall call, String why)
	{
		return Instruction.builder()
			.step(Step.HEAL_FEED)
			.headline(h.isUnseen()
				? "Head to healer #" + h.getOrder() + " (out of view)"
				: "Use " + call.getItemName() + " on healer #" + h.getOrder())
			.detail(h.isUnseen() ? why + ". Walk to where it was last seen" : why)
			.target(Target.npc(h.getNpcIndex(), h.getTile()))
			.items(Planner.items(call.getItemId()))
			.build();
	}

	/**
	 * Teammate healing. {@code urgentOnly}: only someone in real danger (half the configured
	 * threshold) interrupts the poisoning; otherwise anyone under the threshold, when idle.
	 */
	private static Instruction medic(Snapshot s, Settings c, boolean urgentOnly)
	{
		double limit = urgentOnly ? c.getHealTeammateBelow() / 2 : c.getHealTeammateBelow();
		TeammateView hurt = s.getTeammates().stream()
			.filter(t -> t.getMaxHp() > 0 && t.fraction() < limit)
			.min(Comparator.comparingDouble(TeammateView::fraction))
			.orElse(null);
		if (hurt == null)
		{
			return null;
		}
		boolean water = BaIds.VIALS_WITH_WATER.stream().anyMatch(s::has);
		if (!water)
		{
			ObjectView spring = s.nearestObject(BaIds.SPRING);
			if (!s.has(BaIds.VIAL_EMPTY) || spring == null)
			{
				return null;
			}
			return Instruction.builder()
				.step(Step.HEAL_FILL_VIAL)
				.headline("Fill the vial at the spring")
				.detail(hurt.getName() + " is at " + hurt.getHp() + "/" + hurt.getMaxHp())
				.target(Target.object(spring.getId(), spring.getTile()))
				.items(Planner.items(BaIds.VIAL_EMPTY))
				.urgency(urgentOnly ? Urgency.HIGH : Urgency.NORMAL)
				.build();
		}
		return Instruction.builder()
			.step(Step.HEAL_TEAMMATE)
			.headline("Heal " + hurt.getName() + " with the vial")
			.detail(hurt.getHp() + "/" + hurt.getMaxHp() + " HP. Heals, cures poison and restores run")
			.target(Target.player(hurt.getName()))
			.items(BaIds.VIALS_WITH_WATER)
			.urgency(urgentOnly ? Urgency.HIGH : Urgency.NORMAL)
			.build();
	}

	/** Your own HP is low: drink from the spring (only the healer can). Null when fine. */
	static Instruction selfDrink(Snapshot s, Settings c)
	{
		ObjectView spring = s.nearestObject(BaIds.SPRING);
		double self = c.getRetreatBelow();
		if (s.getMaxHp() > 0 && s.hpFraction() < self && spring != null)
		{
			return Instruction.builder()
				.step(Step.RETREAT)
				.headline("Drink from the spring (" + s.getHp() + " HP)")
				.detail("Only you can. Then back to the team")
				.target(Target.object(spring.getId(), spring.getTile()))
				.urgency(Urgency.HIGH)
				.build();
		}
		return null;
	}

	/** A teammate in real danger: the vial now. Null when nobody is. */
	static Instruction teammateDanger(Snapshot s, Settings c)
	{
		double critical = c.getHealTeammateBelow() / 2;
		TeammateView worst = s.getTeammates().stream()
			.filter(t -> t.getMaxHp() > 0 && t.fraction() < critical)
			.min(Comparator.comparingDouble(TeammateView::fraction))
			.orElse(null);
		if (worst == null)
		{
			return null;
		}
		int charges = BaIds.vialCharges(s::has);
		if (charges < 0)
		{
			ObjectView machine = s.nearestObject(BaIds.HEALER_MACHINE);
			return Instruction.builder()
				.step(Step.HEAL_FILL_VIAL)
				.headline("Take a vial: " + worst.getName() + " is at " + Math.round(worst.fraction() * 100) + "%")
				.detail("Healer item machine: Take-vial, then fill it at the spring")
				.leftClick("Take-vial")
				.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
				.urgency(Urgency.HIGH)
				.build();
		}
		return dose(s, worst, charges, Urgency.HIGH);
	}

	/** Heal one teammate, or refill first when the vial is dry. */
	private static Instruction dose(Snapshot s, TeammateView who, int charges, Urgency urgency)
	{
		String hp = who.getHp() + "/" + who.getMaxHp();
		if (charges <= 0)
		{
			ObjectView spring = s.nearestObject(BaIds.SPRING);
			return Instruction.builder()
				.step(Step.HEAL_FILL_VIAL)
				.headline("Refill the vial: " + who.getName() + " needs it")
				.detail(who.getName() + " " + hp + ". Use the vial on the spring")
				.target(spring == null ? Target.NONE : Target.object(spring.getId(), spring.getTile()))
				.items(Planner.items(BaIds.VIAL_EMPTY))
				.urgency(urgency)
				.build();
		}
		return Instruction.builder()
			.step(Step.HEAL_TEAMMATE)
			.headline("Heal " + who.getName() + " (" + Math.round(who.fraction() * 100) + "%)")
			.detail(hp + " HP. Vial " + charges + "/4")
			.target(Target.player(who.getName()))
			.items(BaIds.VIALS_WITH_WATER)
			.urgency(urgency)
			.build();
	}

	private static String staleFood(Snapshot s, int food)
	{
		int other = BaIds.POISON_FOOD.stream().filter(id -> id != food).mapToInt(s::count).sum();
		return other > 0 ? "Don't use your other " + other + " food: wrong type this call" : "";
	}
}
