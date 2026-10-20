package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.StrategyPolicies;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.state.PenanceView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Paired decisions on historical trajectories. This does not simulate changed player actions. */
public final class StrategyComparison
{
	private static final Set<Step> CALL_WORK = EnumSet.of(Step.ATT_ATTACK, Step.ATT_EQUIP,
		Step.HEAL_FEED, Step.COL_COLLECT, Step.DEF_DROP, Step.DEF_RELURE);

	private StrategyComparison()
	{
	}

	private static final class Counts
	{
		int ticks;
		int changed;
		int staleWork;
		int riskyChase;
		int lastRunnerHeld;
		int stepFlips;
		int targetFlips;
		int untaggedBypassed;
		int fullPoisonFeeds;

		void add(Counts c)
		{
			ticks += c.ticks;
			changed += c.changed;
			staleWork += c.staleWork;
			riskyChase += c.riskyChase;
			lastRunnerHeld += c.lastRunnerHeld;
			stepFlips += c.stepFlips;
			targetFlips += c.targetFlips;
			untaggedBypassed += c.untaggedBypassed;
			fullPoisonFeeds += c.fullPoisonFeeds;
		}
	}

	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args.length > 0 && !args[0].isEmpty() ? args[0] : RecordedRoundsTest.RECORDINGS.toString());
		Path output = Paths.get(args.length > 1 && !args[1].isEmpty() ? args[1] : "build/reports/strategy-comparison");
		Files.createDirectories(output);
		Map<String, Supplier<BiFunction<Snapshot, Settings, Instruction>>> policies = new LinkedHashMap<>();
		policies.put("current", () -> new Planner()::plan);
		policies.put("strict-calls", () -> StrategyPolicies.proposal(true, false, false));
		policies.put("trap-first", () -> StrategyPolicies.proposal(false, true, false));
		policies.put("ordinary-kills", () -> StrategyPolicies.proposal(false, false, true));
		policies.put("combined", () -> StrategyPolicies.proposal(true, true, true));
		Map<String, Counts> totals = new LinkedHashMap<>();
		List<String> summary = new ArrayList<>();
		List<String> decisions = new ArrayList<>();
		summary.add("recording,policy,role,wave,ticks,changed_decisions,stale_call_work_ticks,risky_chase_ticks,last_runner_held_ticks,step_flips,target_flips,first_changed_tick,untagged_bypassed_ticks,full_poison_feed_ticks");
		decisions.add("recording,policy,tick,wave_tick,baseline_step,candidate_step,baseline_target,candidate_target,baseline_headline,candidate_headline,listen_fresh,trap_charges,good_kill_bait");
		List<Path> files;
		try (Stream<Path> stream = Files.list(input))
		{
			files = stream.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		if (files.isEmpty())
		{
			throw new IllegalArgumentException("No captures in " + input.toAbsolutePath());
		}
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> baseline = RoundReplay.run(round);
			for (Map.Entry<String, Supplier<BiFunction<Snapshot, Settings, Instruction>>> policy : policies.entrySet())
			{
				List<RoundReplay.Frame> frames = policy.getKey().equals("current") ? baseline
					: RoundReplay.run(round, policy.getValue().get());
				Counts counts = new Counts();
				int firstChanged = -1;
				for (int i = 0; i < frames.size(); i++)
				{
					RoundReplay.Frame f = frames.get(i);
					Snapshot s = f.snapshot;
					Instruction ins = f.instruction;
					Instruction before = baseline.get(i).instruction;
					if (!s.isInArena() || s.getRole() == null)
					{
						continue;
					}
					counts.ticks++;
					if (s.getRole() == Role.HEALER && ins.getStep() == Step.HEAL_FEED)
					{
						PenanceView target = s.alive(PenanceType.HEALER).stream()
							.filter(h -> h.getNpcIndex() == ins.getTarget().getId()).findFirst().orElse(null);
						if (target != null && target.poisoned())
						{
							if (s.alive(PenanceType.HEALER).stream().anyMatch(h -> !h.poisoned())) counts.untaggedBypassed++;
							if (target.nextPoisonHit(s.getTick()) >= WaveInfo.FOOD_DAMAGE) counts.fullPoisonFeeds++;
						}
					}
					if (!s.isListenFresh() && CALL_WORK.contains(ins.getStep())
						&& (ins.getStep() != Step.DEF_RELURE || !ins.getItems().isEmpty()))
					{
						counts.staleWork++;
					}
					if (s.getRole() == Role.DEFENDER && ins.getStep() == Step.DEF_RELURE && StrategyPolicies.mainSetupAtRisk(s))
					{
						counts.riskyChase++;
					}
					if (s.getRole() == Role.DEFENDER && ins.getStep() == Step.DEF_HOLD
						&& ArenaTiles.trapFood(s.getWave()).equals(s.getPlayerTile()) && s.getTrapCharges() > 0
						&& StrategyPolicies.goodKillBait(s) > 0 && s.alive(PenanceType.RUNNER).size() == 1
						&& s.getRunnersKilled() == WaveInfo.runnersTotal(s.getWave()) - 1)
					{
						counts.lastRunnerHeld++;
					}
					if (!identity(ins).equals(identity(before)))
					{
						counts.changed++;
						if (firstChanged < 0)
						{
							firstChanged = s.getTick();
						}
						decisions.add(csv(round.name(), policy.getKey(), s.getTick(), s.getWaveTick(), before.getStep(),
							ins.getStep(), target(before.getTarget()), target(ins.getTarget()), before.getHeadline(), ins.getHeadline(),
							s.isListenFresh(), s.getTrapCharges(), StrategyPolicies.goodKillBait(s)));
					}
				}
				ReplayMetrics.Wave metrics = ReplayMetrics.measure(frames);
				counts.stepFlips = metrics.stepFlips;
				counts.targetFlips = metrics.targetFlips;
				summary.add(csv(round.name(), policy.getKey(), metrics.role, metrics.wave, counts.ticks, counts.changed,
					counts.staleWork, counts.riskyChase, counts.lastRunnerHeld, counts.stepFlips, counts.targetFlips, firstChanged,
					counts.untaggedBypassed, counts.fullPoisonFeeds));
				totals.computeIfAbsent(policy.getKey(), p -> new Counts()).add(counts);
			}
		}
		Files.write(output.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
		Files.write(output.resolve("changed-decisions.csv"), decisions, StandardCharsets.UTF_8);
		System.out.println(files.size() + " captures; each policy keeps its own previous instruction and target.");
		System.out.println("All counts are historical decision ticks, NOT predicted kills, escapes prevented, or completions.");
		System.out.println("policy,ticks,changed_decisions,stale_call_work_ticks,risky_chase_ticks,last_runner_held_ticks,step_flips,target_flips,untagged_bypassed_ticks,full_poison_feed_ticks");
		totals.forEach((name, c) -> System.out.println(csv(name, c.ticks, c.changed, c.staleWork,
			c.riskyChase, c.lastRunnerHeld, c.stepFlips, c.targetFlips, c.untaggedBypassed, c.fullPoisonFeeds)));
		System.out.println("CSV evidence: " + output.toAbsolutePath());
	}

	private static String identity(Instruction ins)
	{
		return ins.getStep() + "|" + target(ins.getTarget()) + "|" + new TreeSet<>(ins.getItems());
	}

	private static String target(Target t)
	{
		if (t.getKind() == Target.Kind.NPC)
		{
			return "NPC:" + t.getId();
		}
		return t.getKind() + ":" + t.getId() + ":" + t.getLabel() + ":" + t.getTile();
	}

	private static String csv(Object... fields)
	{
		return Arrays.stream(fields).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
			.collect(Collectors.joining(","));
	}
}
