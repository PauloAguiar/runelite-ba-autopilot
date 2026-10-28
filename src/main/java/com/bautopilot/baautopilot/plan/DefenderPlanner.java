package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.ArenaTiles.FoodSpot;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Defender: lure runners onto the trap with good (called) bait and kill them there.
 *
 * <p>Drop the wiki pattern (1-4-1-4-1, or 1-4-4-1 on wave 10) during the first call, grab logs and
	 * a hammer, then keep food and charges available while standing clear. Advanced multikills add
	 * holding and timed releases. The same plan with any team: the only thing a defender needs
 * from partners is the healer's call, and a late call already just shortens the pattern window.
 */
class DefenderPlanner
{
	/** Good bait to keep on the trap tile while runners are alive. */
	static final int TRAP_FOOD_MIN = 2;
	/** Runners within this many tiles of the trap food count as stacked. */
	static final int STACK_RADIUS = 2;
	/** Arrived runners wait immediately beside the blocked food, rather than two tiles out. */
	static final int RELEASE_RADIUS = 1;
	/** Close enough to the trap to fix it before chasing an escaping runner. */
	static final int NEAR_TRAP = 5;
	/** During the pattern, a missing bad-bait spot this close is dropped on the way. */
	static final int PASSING_BAD = 2;
	/** The pattern is a first-call job; after this it is maintenance only. */
	static final int PATTERN_DEADLINE_TICKS = WaveInfo.TICKS_PER_CALL;
	/**
	 * Slow multi-kill (BA Torso / Level 5 guide): runners leave their cave at 30, 24, 18, 12 and 6
	 * seconds on the call timer; step off the trap food at 27, 21, 15, 9 or 3, so the whole stack
	 * eats together. 6 seconds = 10 ticks, the first mark 3 seconds (5 ticks) into the call.
	 */
	static final int STEP_OFF_EVERY = 10;
	/** Logs taken in the opening trip (both log spawns lie together by the mainstack). */
	static final int OPENING_LOGS = 2;
	static final int STEP_OFF_OFFSET = 5;
	/** Give late or unconfirmed arrivals one runner cycle before releasing a ready batch. */
	static final int MAX_STACK_WAIT = STEP_OFF_EVERY;

	/** The settings of the current candidates() call (the planner runs on one thread, one call at a time). */
	private Settings settings = Settings.builder().build();

	/**
	 * Every step that applies now, each with its tier (see {@link Tier}); the {@link Chooser} picks.
	 * Multi-tick jobs (the pattern, the hammer-and-logs trip, restocks, a relure) are sticky tasks:
	 * once started they run until done unless something of a higher tier comes up.
	 */
	List<Candidate> candidates(Snapshot s, Settings c)
	{
		settings = c;
		List<Candidate> out = options(s, c);
		WorldPoint food = ArenaTiles.trapFood(s.getWave());
		List<PenanceView> runners = s.alive(PenanceType.RUNNER);
		if (!c.isDefenderMultiKill())
		{
			// Local trap work must not detour for an uncalled bait's routine reserve.
			if (ArenaTiles.distance(s.getPlayerTile(), food) <= NEAR_TRAP
				&& (!runners.isEmpty() || s.getRunnersKilled() < WaveInfo.runnersTotal(s.getWave())))
				out.removeIf(p -> "def.reserve".equals(p.getTask()));
			return out;
		}
		boolean holding = food.equals(s.getPlayerTile()) && runners.stream().anyMatch(p -> onApproach(p, food))
			&& (s.getWaveTick() >= PATTERN_DEADLINE_TICKS || runners.stream().anyMatch(p -> p.getTile() != null
				&& ArenaTiles.distance(p.getTile(), food) <= STACK_RADIUS));
		if (!holding) return out;
		int need = Math.max(TRAP_FOOD_MIN, (int) runners.stream().filter(p -> !p.isUnseen() && p.getTile() != null
			&& ArenaTiles.distance(p.getTile(), food) <= STACK_RADIUS).count());
		if (s.getTrapCharges() != 0 && goodAt(s, food) < need && out.stream().noneMatch(p -> "def.trapfood".equals(p.getTask())))
			trap(s, s.getListenCall(), s.getWave(), out);
		// Suggested routine trips must not keep a commitment that breaks an active hold.
		out.removeIf(p -> !List.of("def.repair", "def.fix", "def.trapfood", "def.last", "def.step", "def.guard").contains(p.getTask())
			&& !(p.getInstruction().getStep() == Step.DEF_DROP && food.equals(p.getInstruction().getTarget().getTile())));
		if (out.isEmpty())
			out.add(Candidate.of(Tier.TIME_CRITICAL, "def.guard", hold(s, approaching(s, food))).withDeadline(0));
		return out;
	}

	private List<Candidate> options(Snapshot s, Settings c)
	{
		settings = c;
		List<Candidate> out = new ArrayList<>();
		BaCall call = s.getListenCall();
		int wave = s.getWave();
		boolean firstCall = s.getWaveTick() >= 0 && s.getWaveTick() < PATTERN_DEADLINE_TICKS;
		int callLeft = firstCall ? PATTERN_DEADLINE_TICKS - s.getWaveTick() : Candidate.NO_DEADLINE;
		boolean runnersLeft = !s.alive(PenanceType.RUNNER).isEmpty() || s.getRunnersKilled() < WaveInfo.runnersTotal(wave);

		// Stock-Up fills the inventory with all three baits in one click and needs no call: do it
		// from the first tick (recorded: the pattern started at wave tick 15-17 and the mainstack
		// was reached at 47-48, after the runners had left their cave)
		if (!holdsBait(s) && (call == null || firstCall))
		{
			out.add(Candidate.task(Tier.TIME_CRITICAL, "def.stock", stockUp(s, call)).withDeadline(callLeft));
		}
		// Cleanup must not pull the defender away from an unfinished or broken main setup.
		Candidate relure = relure(s, call, wave);
		if (relure != null)
		{
			out.add(relure);
		}
		// Runners walk straight past a broken trap: due before the next one reaches it, and at once
		// when it's a click away (tools in hand, next to it), even before chasing an escaping runner
		if (runnersLeft && s.getTrapCharges() == 0)
		{
			// Only fetching the tools is a trip to see through; the fix itself is one click
			ObjectView trap = trap(s);
			boolean equipped = s.isHasHammer() && s.has(BaIds.LOGS);
			boolean oneClick = equipped && trap != null && s.walk(trap.getTile()) <= NEAR_TRAP;
			out.add(Candidate.of(Tier.TEAM_BLOCKING, "def.repair", repair(s))
				.withSticky(!equipped).withDeadline(oneClick ? 0 : runnerArrival(s, wave)));
			out.removeIf(p -> !"def.repair".equals(p.getTask()));
			return out;
		}
		Candidate singleton = c.isDefenderMultiKill() ? releaseSingleton(s, wave) : null;
		if (singleton != null)
		{
			out.add(singleton);
		}
		if (call != null && c.isDefenderPattern() && firstCall)
		{
			Instruction pattern = pattern(s, call);
			if (pattern != null)
			{
				// The pattern owns the first call, and a spot stays
				// done once runners eat it (recorded: eaten trap bait sent the defender back and the
				// mainstack never came up), so the trap's own upkeep waits until it's down
				out.add(Candidate.task(Tier.TIME_CRITICAL, "def.pattern", pattern).withDeadline(callLeft));
				return out;
			}
		}
		if (!runnersLeft)
		{
			out.add(Candidate.of(Tier.IDLE, "def.support", Instruction.builder()
				.step(Step.DEF_SUPPORT)
				.headline("All runners are dead")
				.detail("Hide behind the corner east of the trap so healers stick on the wall, then help on the launcher")
				.urgency(Urgency.INFO)
				.build()));
			return out;
		}

		// Start walking to the first bait spot before the call arrives. It still takes a fresh call
		// to distinguish good bait from bad bait; do not start the tools trip while waiting.
		boolean opening = firstCall && call == null;
		if (opening && c.isDefenderPattern())
		{
			boolean safety = c.isSafetyBait() && placed(s, ArenaTiles.defenderPattern(wave).get(0)) == 0;
			out.add(Candidate.of(Tier.TIME_CRITICAL, "def.wait", Instruction.builder().step(Step.DEF_WAIT_CALL)
				.headline(safety ? "Head to the safety bait south of the trap" : "Head to the trap: drop when the healer calls")
				.detail(safety ? "Wait for the healer's call, then drop 1 of either uncalled bait type" : "Wait for the healer's call before dropping good food")
				.target(Target.tile(safety ? ArenaTiles.safety(wave) : ArenaTiles.trapFood(wave), safety ? "Safety" : "Trap food"))
				.urgency(Urgency.INFO).build()));
			return out;
		}
		Candidate tools = opening ? null : toolsTrip(s, wave);
		if (tools != null)
		{
			out.add(tools);
		}
		if (call == null)
		{
			// Old ground bait keeps its classification. Repairs and releasing a ready trap do not
			// require a new drop, so keep servicing them while the partner's call is missing.
			trap(s, null, wave, out);
			if (out.stream().noneMatch(p -> "def.wait".equals(p.getTask()))) out.add(Candidate.of(Tier.IDLE, "def.wait", Instruction.builder()
				.step(Step.DEF_WAIT_CALL)
				.headline(opening ? "Head to the trap: drop when the healer calls" : "Wait for the healer's call")
				.detail("Bait only works if it matches the call when it hits the ground")
				.target(opening ? Target.tile(ArenaTiles.trapFood(wave), "Trap food") : Target.NONE)
				.urgency(Urgency.INFO)
				.build()));
			return out;
		}
		trap(s, call, wave, out);
		Instruction restock = restockMainstack(s, call, wave);
		if (restock != null)
		{
			out.add(Candidate.task(Tier.MAINTENANCE, "def.restock", restock));
		}
		Instruction trail = restockTrail(s, call, wave);
		if (trail != null)
		{
			out.add(Candidate.task(Tier.MAINTENANCE, "def.trail", trail));
		}
		// Keep a reserve of every bait kind: the call rotates through all three (recorded waves 5-9:
		// 20-118 ticks without the called bait)
		Instruction reserve = topUpBait(s, call);
		if (reserve != null)
		{
			out.add(Candidate.task(Tier.MAINTENANCE, "def.reserve", reserve));
		}
		return out;
	}

	/**
	 * Tending the trap, in the wiki's order: enough good food on it for every runner stacked, step
	 * south on a stack of 2+ with both charges, repair a worn trap to full, otherwise stand on the
	 * trap food. An active hold keeps routine trips from releasing an incomplete arriving group.
	 */
	private void trap(Snapshot s, BaCall call, int wave, List<Candidate> out)
	{
		if (!settings.isDefenderMultiKill())
		{
			continuousTrap(s, call, wave, out);
			return;
		}
		int good = call == null ? -1 : call.getItemId();
		WorldPoint trapFood = ArenaTiles.trapFood(wave);
		boolean runnersOut = !s.alive(PenanceType.RUNNER).isEmpty();
		List<PenanceView> stacked = s.getPenance().stream()
			.filter(p -> p.getType() == PenanceType.RUNNER && !p.isDying()
				&& ArenaTiles.distance(p.getTile(), trapFood) <= STACK_RADIUS)
			.collect(ImmutableList.toImmutableList());
		// Wiki: before stepping off, enough good food under you for every runner in the stack
		int need = Math.max(TRAP_FOOD_MIN, stacked.size());
		int onTrap = goodAt(s, trapFood);
		boolean onFood = trapFood.equals(s.getPlayerTile());
		boolean releasePending = releaseInProgress(s);
		if (releasePending && !onFood && onTrap > 0 && s.getTrapCharges() > 0 && readyRunners(s) > 0)
		{
			out.add(Candidate.task(Tier.TIME_CRITICAL, "def.step", Instruction.builder().step(Step.DEF_HOLD)
				.headline("Stay off the food while the runners eat")
				.detail("Let this batch finish before standing back on the food")
				.target(Target.tile(ArenaTiles.trapStep(wave), "Stay beside trap")).build()).withDeadline(0));
			return;
		}
		if (onTrap < need)
		{
			if (call == null)
			{
				return;
			}
			Instruction stock = !s.has(good)
				? takeBait(s, call, Math.max(4, need - onTrap), 0)
				: Instruction.builder()
					.step(Step.DEF_DROP)
					.headline("Drop " + call.getItemName() + " on the trap food tile")
					.detail(onTrap + " good bait there now; " + (need > TRAP_FOOD_MIN
						? stacked.size() + " runners stacked: one each before you step off"
						: "keep at least " + TRAP_FOOD_MIN))
					.target(Target.tile(trapFood, "Trap food"))
					.items(Planner.items(good))
					.build();
			// An empty trap tile with runners out kills nothing: that outranks even a tools trip
			out.add(!runnersOut ? Candidate.task(Tier.MAINTENANCE, "def.trapfood", stock)
				: Candidate.task(onTrap == 0 ? Tier.TEAM_BLOCKING : Tier.TIME_CRITICAL, "def.trapfood", stock)
					.withDeadline(runnerArrival(s, wave)));
			return;
		}

		boolean waited = stackWait(s) >= MAX_STACK_WAIT;
		if (onFood && readyRunners(s) >= 2 && s.getTrapCharges() == 2
			&& (approaching(s, trapFood) == 0 || waited || releasePending))
		{
			int wait = releasePending ? 0 : ticksToStepOff(s);
			out.add(Candidate.of(Tier.TIME_CRITICAL, "def.step", wait == 0
				? Instruction.builder()
					.step(Step.DEF_STEP)
					.headline("Step one tile south: let them eat")
					.detail(readyRunners(s) + " runners ready, trap has 2 charges. Step back on after the kill"
						+ (approaching(s, trapFood) > 0 ? "; handle late or out-of-view runners in the next batch" : ""))
					.target(Target.tile(ArenaTiles.trapStep(wave), "Step"))
					.urgency(Urgency.HIGH)
					.build()
				: Instruction.builder()
					.step(Step.DEF_HOLD)
					.headline("Stay on the food: step off at " + stepOffSecond(s) + "s")
					.detail(stacked.size() + " runners stacked. Step south when the call timer reads "
						+ stepOffSecond(s) + " (in " + wait + " ticks), so they all eat together")
					.target(Target.tile(trapFood, "Hold here"))
					.urgency(wait <= 2 ? Urgency.HIGH : Urgency.NORMAL)
					.build()).withDeadline(wait));
			return;
		}

		// A fix uses a log whatever the charges, so at 1 charge it only pays with a stack waiting
		// (a stack step needs both charges) or a log to spare; otherwise let the trap take its
		// second runner and fix it broken (guide: "we want the trap to be fully broken")
		if (s.getTrapCharges() == 1 && (stacked.size() >= 2
			|| s.isHasHammer() && s.has(BaIds.LOGS) && s.count(BaIds.LOGS) > logsNeeded(s, wave)))
		{
			if (!s.isHasHammer() || !s.has(BaIds.LOGS))
			{
				Instruction repair = repair(s);
				Instruction tools = repair.toBuilder().detail("The trap has 1 charge. Get the missing repair tools before any multi-kill. "
					+ repair.getDetail()).build();
				out.add(Candidate.task(Tier.TIME_CRITICAL, "def.fix", tools).withDeadline(0));
				return;
			}
			Instruction fix = repair(s);
			if (fix.getStep() == Step.DEF_REPAIR) fix = fix.toBuilder().headline("Fix the trap to full")
				.detail("Stay on the food tile and click the trap's Fix option. " + (stacked.size() >= 2
					? "It has 1 charge; a stack step needs 2" : "You have a log to spare for the runners left"))
				.urgency(stacked.size() >= 2 ? Urgency.HIGH : Urgency.NORMAL).build();
			// Under a stack it's now: they're eating and the next two need the charges
			out.add(stacked.size() >= 2
				? Candidate.task(Tier.TIME_CRITICAL, "def.fix", fix).withDeadline(0)
				: Candidate.task(Tier.MAINTENANCE, "def.fix", fix));
			return;
		}

		// Use a hold's downtime to prepare bait for the arriving group, without a supply trip or
		// spending the last two of a currently useful bait. Ready releases and repairs go first.
		int arrivingGroup = (int) s.alive(PenanceType.RUNNER).stream().filter(p -> onApproach(p, trapFood)).count();
		int prepared = Math.min(arrivingGroup, onTrap + Math.max(0, s.count(good) - BAIT_RESERVE));
		if (onFood && s.getTrapCharges() == 2 && call != null && approaching(s, trapFood) > 0 && prepared > onTrap)
		{
			out.add(Candidate.of(Tier.MAINTENANCE, "def.prepare", Instruction.builder().step(Step.DEF_DROP)
				.headline("Top up trap food: drop " + (prepared - onTrap) + " " + call.getItemName())
				.detail(onTrap + "/" + prepared + " good bait for the arriving group. Stay on this tile; keep "
					+ BAIT_RESERVE + " " + call.getItemName() + " in reserve")
				.target(Target.tile(trapFood, "Trap food")).items(Planner.items(good)).build()));
			return;
		}

		// With the trap coping, the one thing left before standing on it: the safety bait, after all
		// upkeep (it is optional in most guides)
		Instruction safety = call != null && settings.isSafetyBait() ? safetyBait(s, call, wave, trapFood) : null;
		if (safety != null)
		{
			out.add(Candidate.task(Tier.IDLE, "def.safety", safety));
			return;
		}
		Tier guard = runnersOut && !trapCopes(s, trapFood) ? Tier.MAINTENANCE : Tier.IDLE;
		out.add(Candidate.of(guard, "def.guard", onFood
			? hold(s, approaching(s, trapFood))
			: Instruction.builder()
				.step(Step.DEF_STAND)
				.headline("Stand on the trap food")
				.detail("Runners gather around you; you block them until you step off")
				.target(Target.tile(trapFood, "Trap food"))
				.build()));
	}

	/** Keep the trap working; stand on its food only for dropping bait or doing a repair. */
	private void continuousTrap(Snapshot s, BaCall call, int wave, List<Candidate> out)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		int there = goodAt(s, food);
		boolean onFood = food.equals(s.getPlayerTile());
		if (there == 0 && call == null)
		{
			String task = s.getWaveTick() >= 0 && s.getWaveTick() < PATTERN_DEADLINE_TICKS ? "def.wait" : "def.trapfood";
			out.add(Candidate.of(Tier.TIME_CRITICAL, task, Instruction.builder().step(Step.DEF_WAIT_CALL)
				.headline(onFood ? "Wait here for the healer's call, then add food" : "Go to the food tile; drop after the healer calls")
				.detail("The trap food is empty. Block it only while waiting to add correctly called bait, then step aside")
				.target(Target.tile(food, "Restock food")).urgency(Urgency.INFO).build()));
			return;
		}
		int incoming = (int) s.alive(PenanceType.RUNNER).stream().filter(p -> onApproach(p, food)).count();
		int desired = Math.max(TRAP_FOOD_MIN, Math.min(4, incoming));
		if (there >= TRAP_FOOD_MIN && call != null)
			desired = Math.min(desired, there + Math.max(0, s.count(call.getItemId()) - BAIT_RESERVE));
		if (call != null && there < desired)
		{
			Instruction bait = !s.has(call.getItemId()) ? takeBait(s, call, Math.max(4, desired - there), 0)
				: Instruction.builder().step(Step.DEF_DROP)
					.headline("Drop " + call.getItemName() + " on the trap food tile")
					.detail(there + "/" + desired + " good food. " + (onFood ? "Drop here, then step off the food" : "Walk to the marked tile, drop the food, then step aside"))
					.target(Target.tile(food, "Trap food")).items(Planner.items(call.getItemId())).build();
			out.add(Candidate.task(there == 0 ? Tier.TEAM_BLOCKING : Tier.TIME_CRITICAL, "def.trapfood", bait).withDeadline(runnerArrival(s, wave)));
			return;
		}
		// A spare log can restore the worn trap early; otherwise use both charges before repairing.
		if (s.getTrapCharges() == 1 && s.isHasHammer() && s.count(BaIds.LOGS) > logsNeeded(s, wave))
		{
			out.add(Candidate.of(Tier.TIME_CRITICAL, "def.fix", repair(s)).withDeadline(0));
			return;
		}
		if (onFood)
		{
			out.add(Candidate.of(Tier.TIME_CRITICAL, "def.clear", Instruction.builder().step(Step.DEF_SERVICE)
				.headline("Step off the food: let runners eat")
				.detail("Walk one tile north to the marked spot. Keep the food tile clear; repair when the trap breaks")
				.target(Target.tile(ArenaTiles.defenderService(wave), "Stand clear")).build()).withDeadline(0));
			return;
		}
		Instruction safety = call != null && settings.isSafetyBait() ? safetyBait(s, call, wave, food) : null;
		if (safety != null) out.add(Candidate.task(Tier.IDLE, "def.safety", safety));
		boolean nearby = ArenaTiles.distance(s.getPlayerTile(), food) <= 2;
		out.add(Candidate.of(Tier.IDLE, "def.watch", Instruction.builder().step(nearby ? Step.DEF_WATCH : Step.DEF_SERVICE)
			.headline(nearby ? "Keep the trap working; leave the food clear" : "Return beside the trap")
			.detail(there + " good food. " + (s.getTrapCharges() < 0 ? "Trap charges are out of view; check them when nearby"
				: "Trap has " + s.getTrapCharges() + " charge(s). Watch for the next repair")
				+ (call == null && there < TRAP_FOOD_MIN ? ". Wait for the healer's call before adding food" : ""))
			.target(nearby ? Target.NONE : Target.tile(ArenaTiles.defenderService(wave), "Stand clear")).urgency(Urgency.INFO).build()));
	}

	private static long approaching(Snapshot s, WorldPoint food)
	{
		return s.alive(PenanceType.RUNNER).stream().filter(p -> onApproach(p, food)
			&& (p.isUnseen() ? p.getUnseenTicks() <= MAX_STACK_WAIT
				: p.getTile() == null || ArenaTiles.distance(p.getTile(), food) > RELEASE_RADIUS)).count();
	}

	private static Instruction hold(Snapshot s, long incoming)
	{
		String detail = s.getTrapCharges() < 0 ? "Trap charges are unknown. Confirm 2 charges before releasing a stack"
			: s.getTrapCharges() < 2 ? "Trap has " + s.getTrapCharges() + " charge(s). A multi-kill needs 2; repair before releasing a stack"
			: incoming > 0 ? incoming + " runner(s) still approaching or out of view. Give them up to "
				+ Math.max(0, MAX_STACK_WAIT - stackWait(s)) + " more ticks once a runner is ready, then release the gathered batch"
			: "Wait for at least two runners to gather beside the food. Keep holding until the stack is ready";
		return Instruction.builder().step(Step.DEF_HOLD).headline("Hold on the trap food")
			.detail(detail + ". Routine restocking can wait")
			.target(Target.tile(ArenaTiles.trapFood(s.getWave()), "Hold here")).urgency(Urgency.INFO).build();
	}

	/** Release the last member of the main group, including when only deferred outliers remain. */
	private static Candidate releaseSingleton(Snapshot s, int wave)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		List<PenanceView> alive = s.alive(PenanceType.RUNNER);
		if (wave < 1 || wave > 10 || s.getTrapCharges() <= 0 || goodAt(s, food) == 0
			|| readyRunners(s) != 1)
		{
			return null;
		}
		boolean last = s.getRunnersKilled() + s.getUncertainRunners() + alive.size() >= WaveInfo.runnersTotal(wave)
			&& alive.stream().noneMatch(PenanceView::isUnseen) && approaching(s, food) == 0;
		if (!last && stackWait(s) < MAX_STACK_WAIT)
		{
			return null;
		}
		boolean blocking = food.equals(s.getPlayerTile());
		WorldPoint beside = ArenaTiles.trapStep(wave);
		return Candidate.of(Tier.TEAM_BLOCKING, "def.last", Instruction.builder()
			.step(blocking ? Step.DEF_STEP : beside.equals(s.getPlayerTile()) ? Step.DEF_HOLD : Step.DEF_STAND)
			.headline(blocking ? "Step off the food: let the " + (last ? "last" : "waiting") + " runner eat"
				: "Let the last runner reach the trap")
			.detail("The trap has a charge and good bait. Stay beside it; don't wait for a second runner"
				+ (last ? "" : ". Handle late or out-of-view runners in the next batch"))
			.target(Target.tile(beside, "Beside trap"))
			.urgency(blocking ? Urgency.HIGH : Urgency.NORMAL)
			.build()).withDeadline(0);
	}

	/** Observed arrivals at the blocked food, excluding stale positions of unseen runners. */
	static int readyRunners(Snapshot s)
	{
		WorldPoint food = ArenaTiles.trapFood(s.getWave());
		return (int) s.alive(PenanceType.RUNNER).stream().filter(p -> !p.isUnseen() && p.getTile() != null
			&& ArenaTiles.distance(p.getTile(), food) <= RELEASE_RADIUS).count();
	}

	static boolean waitingStack(Snapshot s, Settings c)
	{
		int ready = readyRunners(s);
		return c.isDefenderMultiKill() && !s.isQueenAlive() && s.getTrapCharges() > 0 && ready > 0
			&& ArenaTiles.trapFood(s.getWave()).equals(s.getPlayerTile()) && goodAt(s, s.getPlayerTile()) >= ready;
	}

	static boolean releaseInProgress(Snapshot s)
	{
		return sameGoal(s.getTasks().target("def.step"), Target.tile(ArenaTiles.trapStep(s.getWave()), "Step"));
	}

	private static int stackWait(Snapshot s)
	{
		TaskMemory.Progress progress = s.getTasks().getEntries().get("def.stack");
		return progress == null ? 0 : Math.max(0, s.getTick() - progress.getLastProgressTick());
	}

	private static boolean onApproach(PenanceView p, WorldPoint food)
	{
		return p.isUnseen() || p.getTile() == null || p.getTile().getY() >= food.getY() - 1
			|| ArenaTiles.distance(p.getTile(), food) <= STACK_RADIUS;
	}

	/**
	 * The hammer-and-logs trip, one sticky task so the two pickups are one walk (they lie together
	 * on the north wall). Upkeep, except once it is overdue (two calls in without them) or the trap
	 * is worn and you couldn't fix it: then it outranks holding the trap.
	 */
	private Candidate toolsTrip(Snapshot s, int wave)
	{
		Instruction tools = tools(s, wave);
		if (tools == null)
		{
			return null;
		}
		// Before the trap needs them, not after: once runners are out the trip outranks guarding (the
		// trap takes two on its own while you're away; recorded 2026-10-16 waves 7 and 9: tools came
		// after the trap broke under a stack of 5, and the rest walked past it). A stack step at its
		// mark still goes first (it has a deadline); with nothing out it's upkeep, nearest first
		boolean runnersOut = !s.alive(PenanceType.RUNNER).isEmpty();
		// The opening trip (no hammer, or no log used yet) is time-critical; a later log is upkeep
		boolean opening = !s.isHasHammer() || s.getLogsTaken() <= s.count(BaIds.LOGS);
		return Candidate.task(runnersOut && opening ? Tier.TIME_CRITICAL : Tier.MAINTENANCE, "def.tools", tools);
	}

	/** Ticks until the next step-off mark on the call timer; 0 when the timer is unknown (step now). */
	static int ticksToStepOff(Snapshot s)
	{
		int into = s.ticksIntoCall();
		if (into < 0)
		{
			return 0;
		}
		return Math.floorMod(STEP_OFF_OFFSET - into, STEP_OFF_EVERY);
	}

	/** The call timer's seconds at the next step-off mark (27, 21, 15, 9 or 3). */
	static int stepOffSecond(Snapshot s)
	{
		// The next mark can fall in the next call (27s on its timer, not -3s on this one)
		int at = (Math.max(0, s.ticksIntoCall()) + ticksToStepOff(s)) % WaveInfo.TICKS_PER_CALL;
		return (int) Math.round((WaveInfo.TICKS_PER_CALL - at) * 0.6);
	}

	/** Logs still needed: one fix (2 charges) per two runners the trap's charges don't cover. */
	static int logsNeeded(Snapshot s, int wave)
	{
		int left = Math.max(0, WaveInfo.runnersTotal(wave) - s.getRunnersKilled());
		// Charges not seen yet: the trap starts full
		int uncovered = Math.max(0, left - (s.getTrapCharges() < 0 ? 2 : s.getTrapCharges()));
		return (uncovered + 1) / 2;
	}

	/** Runners walk about a tile a tick: ticks until the nearest one in view reaches the trap food. */
	private static int runnerArrival(Snapshot s, int wave)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		return s.penance(PenanceType.RUNNER).stream()
			.filter(p -> p.getTile() != null)
			.mapToInt(p -> ArenaTiles.distance(p.getTile(), food))
			.min().orElse(Candidate.NO_DEADLINE);
	}

	/**
	 * Wiki: "If good food near the runner spawn runs out, drop more there as soon as possible" -
	 * while more runners are still to spawn.
	 */
	private static Instruction restockMainstack(Snapshot s, BaCall call, int wave)
	{
		int coming = WaveInfo.runnersTotal(wave) - s.getRunnersKilled() - s.getUncertainRunners() - s.alive(PenanceType.RUNNER).size();
		WorldPoint mainstack = ArenaTiles.mainstack(wave);
		if (coming <= 0 || s.getGroundItems().stream()
			.anyMatch(g -> BaIds.BAIT.contains(g.getItemId()) && g.isGood() && ArenaTiles.distance(g.getTile(), mainstack) <= 1))
		{
			return null;
		}
		if (!s.has(call.getItemId()))
		{
			return takeBait(s, call, 4, 0).toBuilder().detail("For the runner spawn: its good food is gone").build();
		}
		return Instruction.builder()
			.step(Step.DEF_DROP)
			.headline("Restock the runner spawn: drop " + call.getItemName())
			.detail("Its good food is gone and " + coming + " more runner" + (coming == 1 ? "" : "s") + " will spawn")
			.target(Target.tile(mainstack, "Mainstack"))
			.items(Planner.items(call.getItemId()))
			.build();
	}

	/**
	 * The trail's bad bait (between the mainstack and the trap) pulls runners from the spawn to the
	 * trap; once eaten, drop another while runners are still to spawn (recorded 2026-10-17 wave 7:
	 * eaten at tick 81, re-dropped by hand at 113).
	 */
	private Instruction restockTrail(Snapshot s, BaCall call, int wave)
	{
		FoodSpot trail = ArenaTiles.defenderPattern(wave, settings.getDefenderGuide(), settings.isSafetyBait()).stream()
			.filter(f -> f.getName().equals("Trail")).findFirst().orElse(null);
		int coming = WaveInfo.runnersTotal(wave) - s.getRunnersKilled() - s.getUncertainRunners() - s.alive(PenanceType.RUNNER).size();
		// Only a trail you laid this wave and that is gone now
		if (trail == null || coming <= 0 || placed(s, trail) == 0 || s.getGroundItems().stream()
			.anyMatch(g -> BaIds.BAIT.contains(g.getItemId()) && ArenaTiles.distance(g.getTile(), trail.getTile()) <= 1))
		{
			return null;
		}
		int bad = firstBad(s, call.getItemId());
		if (bad <= 0)
		{
			return null;
		}
		return Instruction.builder()
			.step(Step.DEF_DROP)
			.headline("Restock the trail: drop 1 bad")
			.detail("Its bait is gone and " + coming + " more runner" + (coming == 1 ? "" : "s") + " will spawn: it leads them to the trap")
			.target(Target.tile(trail.getTile(), "Trail"))
			.badBait(true)
			.items(Planner.items(bad))
			.build();
	}

	/** A runner this many tiles south of the trap food (and not stacked on you) is heading out. */
	static final int RELURE_SOUTH = 2;
	/** Drop the bad bait once the runner is this close: bait range is 4 tiles (5 from level 2). */
	static final int RELURE_DROP_RANGE = 3;
	/** Stand this many tiles north of the runner, between it and the trap. */
	static final int RELURE_AHEAD = 2;

	/**
	 * Lure a runner that is heading for the exit back north with bad bait (wiki: bad bait sends a
	 * runner several tiles north). Recorded 2026-10-16, 30 drops: bait 2-5 tiles north of the runner
	 * turned it back every time, often all the way to the trap; bait on or south of it, or once it
	 * was ~15 tiles south of the trap, didn't. So: start as soon as it's 2 tiles south of the trap
	 * food, get ahead of it (north), drop within 3 tiles, then let it eat before dropping another.
	 */
	private static Candidate relure(Snapshot s, BaCall call, int wave)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		List<PenanceView> alive = s.alive(PenanceType.RUNNER);
		int repairs = logsNeeded(s, wave);
		if (wave < 1 || wave > 10 || call == null || !s.isListenFresh() || s.getWaveTick() < PATTERN_DEADLINE_TICKS
			|| s.getTrapCharges() <= 0 || goodAt(s, food) < TRAP_FOOD_MIN
			|| (repairs > 0 && (!s.isHasHammer() || !s.has(BaIds.LOGS)))
			|| s.getRunnersKilled() + s.getUncertainRunners() + alive.size() < WaveInfo.runnersTotal(wave)
			|| alive.stream().anyMatch(p -> onApproach(p, food)))
		{
			return null;
		}
		Instruction ins = relureStep(s, call, wave);
		if (ins == null)
		{
			return null;
		}
		// Ticks before it walks out of the exit: runners move about a tile a tick
		int exitY = ArenaTiles.safety(wave).getY() - ESCAPE_SOUTH_OF_SAFETY;
		return Candidate.task(Tier.MAINTENANCE, RELURE_TASK, ins)
			.withDeadline(Math.max(0, escapingRunner(s, wave).getTile().getY() - exitY));
	}

	/** Same line as RoundLogic's escape check: last seen this far south of the safety bait. */
	static final int ESCAPE_SOUTH_OF_SAFETY = 5;

	static final String RELURE_TASK = "def.relure";
	/** A runner out of view this long no longer counts as escaping: it may be gone. */
	static final int RELURE_UNSEEN_TICKS = 3;

	/**
	 * The southernmost runner heading for the exit, or null. During a relure, one that just walked
	 * out of view still counts at its last seen tile (recorded: one tick out of view dropped the
	 * relure for a bait trip); one long out of view, or one that left view before any relure,
	 * doesn't (recorded: a 36-tick relure for a runner never seen again).
	 */
	private static PenanceView escapingRunner(Snapshot s, int wave)
	{
		WorldPoint trapFood = ArenaTiles.trapFood(wave);
		int unseenOk = RELURE_TASK.equals(s.getLastTask()) ? RELURE_UNSEEN_TICKS : -1;
		return s.alive(PenanceType.RUNNER).stream()
			.filter(p -> p.getTile() != null && (!p.isUnseen() || p.getUnseenTicks() <= unseenOk) && p.getTile().getY() <= trapFood.getY() - RELURE_SOUTH
				&& ArenaTiles.distance(p.getTile(), trapFood) > STACK_RADIUS)
			.min(Comparator.comparingInt(p -> p.getTile().getY()))
			.orElse(null);
	}

	private static Instruction relureStep(Snapshot s, BaCall call, int wave)
	{
		PenanceView escaping = escapingRunner(s, wave);
		int bad = escaping == null ? -1 : firstBad(s, call == null ? -1 : call.getItemId());
		if (bad < 0)
		{
			return null;
		}
		WorldPoint runner = escaping.getTile();
		boolean baitWaiting = s.getGroundItems().stream()
			.anyMatch(g -> BaIds.BAIT.contains(g.getItemId()) && !g.isGood()
				&& g.getTile().getY() >= runner.getY() && ArenaTiles.distance(g.getTile(), runner) <= RELURE_DROP_RANGE + 1);
		if (baitWaiting)
		{
			return Instruction.builder()
				.step(Step.DEF_RELURE)
				.headline("Runner going for your bad bait: wait")
				.detail("It eats it and turns north, back to the trap. Drop another if it keeps going south")
				.target(Target.npc(escaping.getNpcIndex(), runner))
				.urgency(Urgency.INFO)
				.build();
		}
		WorldPoint me = s.getPlayerTile();
		// One tile inside the runner's notice range (4 tiles, 5 from defender level 2)
		int dropRange = Math.max(RELURE_DROP_RANGE, s.baitRange() - 1);
		boolean inRange = me != null && ArenaTiles.distance(me, runner) <= dropRange && me.getY() >= runner.getY();
		if (inRange)
		{
			return Instruction.builder()
				.step(Step.DEF_RELURE)
				.headline("Drop bad bait now: the runner is in range")
				.detail("Bad bait sends it north, back to the trap. An escape costs the team 3 points")
				.target(Target.tile(me, "Drop here"))
				.badBait(true)
				.items(Planner.items(bad))
				.urgency(Urgency.HIGH)
				.build();
		}
		return Instruction.builder()
			.step(Step.DEF_RELURE)
			.headline("Runner escaping: get ahead of it")
			.detail("Stand on the marked tile north of it, then drop bad bait (within " + Math.max(RELURE_DROP_RANGE, s.baitRange() - 1) + " tiles)")
			.target(Target.tile(runner.dy(RELURE_AHEAD), "Get here"))
			.badBait(true)
			.items(Planner.items(bad))
			.urgency(Urgency.HIGH)
			.build();
	}

	/**
	 * Put safety bait south of the trap first, then work north through trap, trail, mainstack and
	 * west relure. The no-safety guides begin at the trap instead.
	 */
	static final List<String> ROUTE = ImmutableList.of("Safety", "Trap", "Trail", "Mainstack", "West relure");
	/** With this few ticks left in the call, skip bad bait to reach the remaining good spots. */
	static final int SHORT_TICKS = 12;

	/**
	 * The pattern as one route: its next unfinished spot, and once the good bait is all down, the
	 * hammer and logs when they're a few tiles off it (they spawn by the mainstack; Gau Cho's guide:
	 * mainstack, then logs, then hammer. Reported 2026-10-16: from the middle bait the hammer is ~6
	 * tiles away too, and it was sent there before the mainstack).
	 */
	private Instruction pattern(Snapshot s, BaCall call)
	{
		Instruction spot = patternSpot(s, call);
		if (spot == null)
		{
			return null;
		}
		// Guide: drop the good food, then grab the hammer and logs (they lie by the mainstack) before
		// going back to the trap - recorded 2026-10-16 wave 7: the trip flipped back to the relure
		// once the walk passed 6 tiles, and the tools only came after the trap broke under a stack
		if (!goodSpotsDone(s))
		{
			return spot;
		}
		// The opening tools trip, the player's route (2026-10-17): from the mainstack to the log on
		// the West relure tile - take it and drop the bad bait there in the same stop - then the
		// other log, then the hammer, which is on the way back to the trap
		boolean badHere = spot.getStep() == Step.DEF_DROP && !spot.getItems().contains(call.getItemId())
			&& spot.getTarget().getTile() != null && ArenaTiles.distance(s.getPlayerTile(), spot.getTarget().getTile()) <= PASSING_BAD;
		Instruction tools = tools(s, s.getWave(), false, true);
		Target committed = rememberedTarget(s, "def.pattern");
		// The pattern is one task, but its selected stop must also survive a proximity boundary.
		if (tools != null && sameGoal(committed, tools.getTarget()))
		{
			return tools.toBuilder().marks(spot.getMarks()).build();
		}
		if (sameGoal(committed, spot.getTarget())) return spot;
		if (tools != null && !badHere)
		{
			return tools.toBuilder().marks(spot.getMarks()).build();
		}
		return spot;
	}

	/** The food pattern: next unfinished spot, or restock first if the inventory can't cover it. */
	private Instruction patternSpot(Snapshot s, BaCall call)
	{
		List<FoodSpot> spots = ArenaTiles.defenderPattern(s.getWave(), settings.getDefenderGuide(), settings.isSafetyBait());
		ImmutableList.Builder<TileMark> marks = ImmutableList.builder();
		FoodSpot next = null;
		List<FoodSpot> route = new java.util.ArrayList<>(spots);
		route.sort(Comparator.comparingInt(f -> ROUTE.indexOf(f.getName())));
		boolean shortOnTime = s.getTicksToCallChange() >= 0 && s.getTicksToCallChange() < SHORT_TICKS;
		int needGood = 0;
		int needBad = 0;
		for (FoodSpot spot : spots)
		{
			int there = placed(s, spot);
			int missing = Math.max(0, spot.getCount() - there);
			marks.add(new TileMark(spot.getTile(), spot.getName() + " " + Math.min(there, spot.getCount()) + "/" + spot.label(),
				missing == 0, spot.isGood()));
			if (missing > 0)
			{
				if (spot.isGood())
				{
					needGood += missing;
				}
				else
				{
					needBad += missing;
				}
			}
		}
		boolean goodLeft = route.stream().anyMatch(f -> f.isGood() && placed(s, f) < f.getCount());
		Target committed = rememberedTarget(s, "def.pattern");
		// Safety is the first opening stop even when another tile happens to be nearer. Once it
		// has been dropped, walking back past it cannot interrupt the next committed stop.
		if (!(shortOnTime && goodLeft))
			next = route.stream().filter(f -> f.getName().equals("Safety") && placed(s, f) < f.getCount()).findFirst().orElse(null);
		// Once chosen, finish the spot's observed missing count.
		// A short remaining call can still drop optional bad bait in favour of unfinished good bait.
		for (FoodSpot spot : route)
		{
			if (next != null) break;
			if (sameGoal(committed, Target.tile(spot.getTile(), spot.getName()))
				&& placed(s, spot) < spot.getCount() && !(shortOnTime && !spot.isGood() && goodLeft))
			{
				next = spot;
				break;
			}
		}
		// A nearby unfinished spot is useful when starting a stop, not a reason to turn back midway.
		route.sort(Comparator.comparing((FoodSpot f) -> ArenaTiles.distance(s.getPlayerTile(), f.getTile()) > (f.isGood() ? 1 : PASSING_BAD)));
		for (FoodSpot spot : route)
		{
			if (next != null) break;
			int there = placed(s, spot);
			if (there < spot.getCount() && !(shortOnTime && !spot.isGood() && goodLeft))
			{
				next = spot;
				break;
			}
		}
		if (next == null)
		{
			return null;
		}

		int haveGood = s.count(call.getItemId());
		int haveBad = badInInventory(s, call.getItemId());
		if (haveGood < needGood || haveBad < needBad)
		{
			return takeBait(s, call, Math.max(0, needGood - haveGood), Math.max(0, needBad - haveBad))
				.toBuilder().marks(marks.build()).build();
		}

		int badItem = firstBad(s, call.getItemId());
		int dropItem = next.isGood() ? call.getItemId() : badItem;
		return Instruction.builder()
			.step(Step.DEF_DROP)
			.headline("Drop " + next.label() + " at " + next.getName())
			.detail(next.isGood() ? call.getItemName() + " (the call)" : "Any other bait: it only pulls runners along")
			.target(Target.tile(next.getTile(), next.getName()))
			.badBait(!next.isGood())
			.items(dropItem > 0 ? Planner.items(dropItem) : Planner.items())
			.marks(marks.build())
			.build();
	}

	/** A missing tool, keeping the chosen item through the trip; null when you have them all. */
	private Instruction tools(Snapshot s, int wave)
	{
		return tools(s, wave, false, false);
	}

	private Instruction tools(Snapshot s, int wave, boolean forRepair)
	{
		return tools(s, wave, forRepair, false);
	}

	/**
	 * The wave's log target counts logs picked up this wave, not logs held: repairs use them up,
	 * and that's not a reason to go back (reported 2026-10-16). {@code forRepair}: the trap needs
	 * fixing and you hold none, so one more whatever the target.
	 */
	private Instruction tools(Snapshot s, int wave, boolean forRepair, boolean logsFirst)
	{
		// Logs from what the wave needs (a fix per two runners the charges won't cover), all of them
		// in the opening trip: both logs, the hammer, then wait at the spawn for the respawn and take
		// the extra ones (waves 8-9) before going back - never a trip back mid-wave for them (player,
		// 2026-10-17). Only 2 lie out at a time; they respawn 7-10 ticks after pickup. Once repairs
		// have started, one more only if you run out
		int logs = s.count(BaIds.LOGS);
		int need = logsNeeded(s, wave);
		boolean opening = s.getLogsTaken() <= logs;
		int wantLogs = logTarget(s, wave);
		boolean needLog = logs < wantLogs || (logs == 0 && (need > 0 || forRepair));
		boolean needHammer = !s.isHasHammer() && (need > 0 || forRepair || logs > 0);
		Target committed = rememberedTarget(s, logsFirst ? "def.pattern" : forRepair ? "def.repair" : "def.tools");
		if (committed == null && ("def.pattern".equals(s.getLastTask()) || "def.tools".equals(s.getLastTask())
			|| "def.repair".equals(s.getLastTask())) && s.getLastTargetTile() != null)
		{
			// A broken trap or a finished pattern can rename the trip without completing its item.
			committed = new Target(Target.Kind.NONE, -1, s.getLastTargetTile(), null);
		}
		GroundItemView hammer = needHammer ? toolItem(s, BaIds.HAMMER, committed) : null;
		GroundItemView log = needLog ? toolItem(s, BaIds.LOGS, committed) : null;
		boolean keepHammer = hammer != null && sameGoal(committed, Target.groundItem(BaIds.HAMMER, hammer.getTile()));
		boolean waitingForLogs = needLog && opening && logs > 0 && committed != null
			&& committed.getKind() == Target.Kind.TILE && ArenaTiles.logSpawn(wave).equals(committed.getTile());
		boolean keepLog = log != null && (waitingForLogs || sameGoal(committed, Target.groundItem(BaIds.LOGS, log.getTile())));
		if (waitingForLogs && log == null) return waitForLogs(wave, logs, wantLogs);
		boolean hammerCloser = hammer != null && log != null && !logsFirst
			&& ArenaTiles.distance(s.getPlayerTile(), hammer.getTile()) <= ArenaTiles.distance(s.getPlayerTile(), log.getTile());
		if (hammer != null && (keepHammer || !keepLog && (log == null || hammerCloser)))
		{
			return Instruction.builder()
				.step(Step.DEF_HAMMER)
				.headline("Pick up the hammer")
				.detail(log != null ? "Then the logs next to it, in the same trip" : "You need it to repair the trap")
				.target(Target.groundItem(hammer.getItemId(), hammer.getTile()))
				.build();
		}
		if (log != null)
		{
			return Instruction.builder()
				.step(Step.DEF_LOGS)
				.headline(logs < wantLogs ? "Pick up logs (" + logs + "/" + wantLogs + ")" : "Pick up a log for the repair")
				.detail(logs + 1 < wantLogs ? "Then the other log" + (hammer != null ? " and the hammer" : "") + ", in the same trip"
					: hammer != null ? "Then the hammer, on the way back to the trap" : "Each repair takes one log")
				.target(Target.groundItem(log.getItemId(), log.getTile()))
				// Bait dropped on the same tile (the West relure) would otherwise sit on top
				.build();
		}
		if (needLog && opening && logs > 0)
		{
			// Both taken: wait by the spawn for the respawn rather than come back later
			return waitForLogs(wave, logs, wantLogs);
		}
		return null;
	}

	private static Instruction waitForLogs(int wave, int logs, int wantLogs)
	{
		return Instruction.builder().step(Step.DEF_LOGS)
			.headline("Wait for the logs to respawn (" + logs + "/" + wantLogs + ")")
			.detail("They come back within a few seconds: take them now, no trip back later")
			.target(Target.tile(ArenaTiles.logSpawn(wave), "Logs")).build();
	}

	private static GroundItemView toolItem(Snapshot s, int id, Target committed)
	{
		GroundItemView previous = s.getGroundItems().stream()
			.filter(g -> g.getItemId() == id && sameGoal(committed, Target.groundItem(id, g.getTile())))
			.findFirst().orElse(null);
		return previous == null ? nearest(s, id) : previous;
	}

	private static boolean sameGoal(Target previous, Target next)
	{
		return previous != null && previous.getTile() != null && previous.getTile().equals(next.getTile())
			&& (previous.getKind() == Target.Kind.NONE || previous.getKind() == next.getKind()
				&& (previous.getKind() != Target.Kind.GROUND_ITEM || previous.getId() == next.getId()));
	}

	private static Target rememberedTarget(Snapshot s, String task)
	{
		Target remembered = s.getTasks().target(task);
		if (remembered != null && remembered.getTile() != null) return remembered;
		boolean openingWalk = "def.pattern".equals(task) && ("def.wait".equals(s.getLastTask())
			|| "call".equals(s.getLastTask()) && s.getCallPeriod() == 0);
		return s.getLastTargetTile() != null && (task.equals(s.getLastTask()) || openingWalk)
			? new Target(Target.Kind.NONE, -1, s.getLastTargetTile(), null) : null;
	}

	/**
	 * The trap handles the runners that are out without you: a charge and a good bait for each
	 * (runners out of view count too).
	 */
	private static boolean trapCopes(Snapshot s, WorldPoint trapFood)
	{
		int out = s.alive(PenanceType.RUNNER).size();
		return out == 0 || (s.getTrapCharges() >= out && goodAt(s, trapFood) >= out);
	}

	/** Every good-bait spot of the pattern holds its count. */
	private boolean goodSpotsDone(Snapshot s)
	{
		return ArenaTiles.defenderPattern(s.getWave(), settings.getDefenderGuide(), settings.isSafetyBait()).stream()
			.filter(FoodSpot::isGood)
			.allMatch(f -> placed(s, f) >= f.getCount());
	}

	/** The defender's trap: the trap object next to the trap food (the west one is never used). */
	private static ObjectView trap(Snapshot s)
	{
		WorldPoint food = ArenaTiles.trapFood(s.getWave());
		return s.getObjects().stream()
			.filter(o -> o.getId() == BaIds.TRAP_FULL || o.getId() == BaIds.TRAP_ONE || o.getId() == BaIds.TRAP_BROKEN)
			.min(Comparator.comparingInt(o -> ArenaTiles.distance(o.getTile(), food)))
			.orElse(null);
	}

	private Instruction repair(Snapshot s)
	{
		ObjectView trap = trap(s);
		if (!s.isHasHammer() || !s.has(BaIds.LOGS))
		{
			Instruction tools = tools(s, s.getWave(), true);
			if (tools != null)
			{
				return tools.toBuilder().detail("The trap needs repairing: " + tools.getDetail()).urgency(Urgency.HIGH).build();
			}
			boolean logs = !s.has(BaIds.LOGS);
			return Instruction.builder().step(logs ? Step.DEF_LOGS : Step.DEF_HAMMER)
				.headline(logs ? "Get a log before repairing" : "Find the hammer before repairing")
				.detail("The missing repair tool is not visible. Check the tool area by the log spawn, then return to the repair spot")
				.target(Target.tile(ArenaTiles.logSpawn(s.getWave()), "Repair supplies")).urgency(Urgency.HIGH).build();
		}
		WorldPoint food = ArenaTiles.trapFood(s.getWave());
		if (s.isHasHammer() && s.has(BaIds.LOGS) && !food.equals(s.getPlayerTile()))
		{
			return Instruction.builder().step(Step.DEF_REPAIR_POSITION)
				.headline("Walk onto the marked food tile, then fix the trap")
				.detail("Stand on the repair spot (" + (food.getX() & 63) + ", " + (food.getY() & 63)
					+ ") to block runners briefly. Then click the trap's Fix option")
				.target(Target.tile(food, "Repair spot")).urgency(Urgency.HIGH).build();
		}
		return Instruction.builder()
			.step(Step.DEF_REPAIR)
			.headline("Fix the trap")
			.detail("Stay on the food tile and click the trap's Fix option with logs and hammer in your inventory."
				+ (settings.isDefenderMultiKill() ? " Keep holding until the stack-release prompt"
					: " Once repaired, top up food if needed, then step aside so runners can eat"))
			.target(trap == null ? Target.NONE : Target.object(trap.getId(), trap.getTile()))
			.urgency(Urgency.HIGH)
			.build();
	}

	/**
	 * The safety bait (bad bait several tiles south of the trap, OSRS Wiki 1-4-1-4-1) catches a
	 * runner that walks past the trap and sends it back north. Optional in other guides, so it's
	 * maintenance: re-dropped when gone (eaten, or never dropped), only while runners remain, you
	 * hold bad bait and the trap can do without you. Recorded 2026-10-16: dropped in 2 of 22
	 * defender waves; the one time a runner reached it, it ate it and walked back to the trap.
	 */
	private static Instruction safetyBait(Snapshot s, BaCall call, int wave, WorldPoint trapFood)
	{
		boolean runnersLeft = !s.alive(PenanceType.RUNNER).isEmpty() || s.getRunnersKilled() < WaveInfo.runnersTotal(wave);
		WorldPoint tile = ArenaTiles.safety(wave);
		int bad = firstBad(s, call.getItemId());
		if (!runnersLeft || bad < 0 || badAt(s, tile) > 0 || !trapCopes(s, trapFood))
		{
			return null;
		}
		return Instruction.builder()
			.step(Step.DEF_DROP)
			.headline("Re-drop the safety bait")
			.detail("Bad bait south of the trap: a runner that walks past eats it and turns back north")
			.target(Target.tile(tile, "Safety"))
			.badBait(true)
			.items(Planner.items(bad))
			.urgency(Urgency.INFO)
			.build();
	}

	/** Hold at least this many of each bait kind. */
	static final int BAIT_RESERVE = 2;

	/** Machine trip for whichever bait kinds are under the reserve; null when all are stocked. */
	private static Instruction topUpBait(Snapshot s, BaCall call)
	{
		List<BaCall> low = new java.util.ArrayList<>();
		for (BaCall bait : new BaCall[]{BaCall.BAIT_TOFU, BaCall.BAIT_CRACKERS, BaCall.BAIT_WORMS})
		{
			if (s.count(bait.getItemId()) < BAIT_RESERVE)
			{
				low.add(bait);
			}
		}
		if (low.isEmpty() || s.getFreeSlots() < 3)
		{
			return null;
		}
		if (low.size() > 1)
		{
			return stockUp(s, call).toBuilder()
				.detail("Low on " + low.size() + " baits: the call can switch to any of them").build();
		}
		ObjectView machine = s.nearestObject(BaIds.DEFENDER_MACHINE);
		BaCall bait = low.get(0);
		return Instruction.builder()
			.step(Step.DEF_TAKE_BAIT)
			.headline("Take-" + bait.getKeyword() + " at the defender machine")
			.detail(s.count(bait.getItemId()) + " left; the call can switch to it")
			.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
			.urgency(Urgency.INFO)
			.build();
	}

	private static boolean holdsBait(Snapshot s)
	{
		return BaIds.BAIT.stream().anyMatch(s::has);
	}

	private static Instruction stockUp(Snapshot s, BaCall call)
	{
		ObjectView machine = s.nearestObject(BaIds.DEFENDER_MACHINE);
		return Instruction.builder()
			.step(Step.DEF_TAKE_BAIT)
			.headline("Stock-Up at the defender item machine")
			.detail("One click fills your inventory with all three baits"
				+ (call == null ? "; the healer's call says which is good" : ". The call is " + call.getItemName()))
			.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
			.build();
	}

	private static Instruction takeBait(Snapshot s, BaCall call, int good, int bad)
	{
		if (bad > 0 || good > 5)
		{
			// Several kinds or a big batch: Stock-Up is one click instead of one per kind
			return stockUp(s, call);
		}
		ObjectView machine = s.nearestObject(BaIds.DEFENDER_MACHINE);
		String what = good > 0 && bad > 0
			? good + " " + call.getItemName() + " + " + bad + " other bait"
			: good > 0 ? good + " " + call.getItemName() : bad + " bait of another kind";
		return Instruction.builder()
			.step(Step.DEF_TAKE_BAIT)
			.headline("Take " + what)
			.detail("From the defender item machine. The call is " + call.getItemName())
			.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
			.build();
	}

	/** Good bait on a tile, of any kind: bait keeps its good status after the call changes (wiki). */
	private static int goodAt(Snapshot s, WorldPoint tile)
	{
		return s.getGroundItems().stream()
			.filter(g -> BaIds.BAIT.contains(g.getItemId()) && g.isGood() && ArenaTiles.distance(g.getTile(), tile) == 0)
			.mapToInt(GroundItemView::getQuantity).sum();
	}

	/**
	 * Bait of the spot's kind put down on it this wave: what lies there now, or what was dropped
	 * there if more (runners eat it). Recorded 2026-10-16: runners ate the trap bait early, the
	 * trap counted as unfinished again and the mainstack never came up.
	 */
	static int placed(Snapshot s, FoodSpot spot)
	{
		int now = spot.isGood() ? goodAt(s, spot.getTile()) : badAt(s, spot.getTile());
		Integer landed = (spot.isGood() ? s.getGoodBaitLanded() : s.getBadBaitLanded()).get(spot.getTile());
		return Math.max(now, landed == null ? 0 : landed);
	}

	private static int logTarget(Snapshot s, int wave)
	{
		int need = logsNeeded(s, wave);
		return s.getLogsTaken() <= s.count(BaIds.LOGS) ? need : Math.min(OPENING_LOGS, need);
	}

	static boolean toolsReady(Snapshot s)
	{
		int logs = s.count(BaIds.LOGS);
		return logs >= logTarget(s, s.getWave())
			&& (s.isHasHammer() || logsNeeded(s, s.getWave()) == 0 && logs == 0);
	}

	private static int badAt(Snapshot s, WorldPoint tile)
	{
		return s.getGroundItems().stream()
			.filter(g -> BaIds.BAIT.contains(g.getItemId()) && !g.isGood() && ArenaTiles.distance(g.getTile(), tile) == 0)
			.mapToInt(GroundItemView::getQuantity).sum();
	}

	private static int badInInventory(Snapshot s, int good)
	{
		return BaIds.BAIT.stream().filter(id -> id != good).mapToInt(s::count).sum();
	}

	private static int firstBad(Snapshot s, int good)
	{
		return BaIds.BAIT.stream().filter(id -> id != good && s.has(id)).findFirst().orElse(-1);
	}

	private static GroundItemView nearest(Snapshot s, int itemId)
	{
		return s.getGroundItems().stream()
			.filter(g -> g.getItemId() == itemId)
			.min(Comparator.comparingInt(g -> ArenaTiles.distance(s.getPlayerTile(), g.getTile())))
			.orElse(null);
	}
}
