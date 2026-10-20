package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.plan.*;
import com.bautopilot.baautopilot.state.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/** Describes observed Defender outcomes and guidance on the fixed recorded trajectory. */
public final class DefenderRunEvaluation
{
	public static void main(String[] args) throws Exception
	{
		Path input = Path.of(args[0]), output = Path.of(args[1]);
		Files.createDirectories(output);
		List<Path> files;
		try (java.util.stream.Stream<Path> stream = Files.list(input))
		{
			files = stream.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		List<String> summary = new ArrayList<>(), timeline = new ArrayList<>(), episodes = new ArrayList<>();
		summary.add("capture,wave,ticks,runner_clear_tick,runner_clear_wave_tick,unique_southbound_despawns,team_runners_passed,step_changes,target_flips,recorded_early_support_ticks,current_early_support_ticks,broken_ticks,repair_work_ticks,broken_call_ticks,broken_without_repair_or_call_ticks,near_empty_bait_ticks,empty_bait_drop_work_ticks,prepare_ticks,changed_work_steps,max_uncertain_runners");
		timeline.add("capture,tick,wave_tick,recorded_step,current_step,task,player_x,player_y,target_x,target_y,charges,good_on_trap,near_runners,live_runners,unseen_runners,killed,uncertain,held_called,logs,hammer,fresh,call_left,headline,detail");
		episodes.add("capture,kind,start_tick,end_tick,ticks,first_headline,first_detail");
		for (Path file : files)
		{
			List<RoundReplay.Frame> all = RoundReplay.run(RecordedRound.read(file));
			List<RoundReplay.Frame> frames = all.stream().filter(f -> f.snapshot.isInArena()
				&& f.snapshot.getRole() == Role.DEFENDER && f.snapshot.getWaveTick() >= 0 && !f.snapshot.isQueenAlive()).collect(Collectors.toList());
			if (frames.isEmpty()) continue;
			ReplayMetrics.Wave metrics = ReplayMetrics.measure(frames);
			int clear = -1, clearWave = -1, oldSupport = 0, support = 0, broken = 0, repair = 0, calls = 0, delayed = 0,
				empty = 0, emptyWork = 0, prep = 0, changes = 0, uncertain = 0;
			Set<Integer> southbound = new HashSet<>();
			Map<Integer, PenanceView> previousNpcs = new HashMap<>();
			Integer passed = null;
			for (RoundReplay.Frame f : all) if (f.snapshot.getWaveStats() != null && f.snapshot.getWaveStats().getTeamRunnersPassed() != null)
				passed = f.snapshot.getWaveStats().getTeamRunnersPassed();
			Map<String, List<RoundReplay.Frame>> active = new LinkedHashMap<>();
			for (RoundReplay.Frame f : frames)
			{
				Snapshot s = f.snapshot;
				for (Rec.Event event : RoundInvariants.events(f.raw, "npcDespawn"))
				{
					PenanceView last = previousNpcs.get(event.npc);
					if (PenanceType.forNpcId(event.id) == PenanceType.RUNNER && last != null && last.getTile() != null && !last.isDying()
						&& last.getTile().getY() <= ArenaTiles.safety(s.getWave()).getY() - 5) southbound.add(event.npc);
				}
				previousNpcs.clear();
				s.getPenance().forEach(n -> previousNpcs.put(n.getNpcIndex(), n));
				Instruction ins = f.instruction;
				Step work = work(ins);
				WorldPoint food = ArenaTiles.trapFood(s.getWave()), target = ins.getTarget().getTile();
				boolean finished = s.getCleared().contains(PenanceType.RUNNER);
				if (finished && clear < 0) { clear = s.getTick(); clearWave = s.getWaveTick(); }
				String recorded = f.raw.live == null ? "" : f.raw.live.step;
				if (!finished && "DEF_SUPPORT".equals(recorded)) oldSupport++;
				if (!finished && work == Step.DEF_SUPPORT) support++;
				if (!Objects.equals(work.name(), recorded) && !"CALL".equals(recorded)) changes++;
				List<PenanceView> runners = s.alive(PenanceType.RUNNER);
				int near = (int) runners.stream().filter(p -> !p.isUnseen() && p.getTile() != null && ArenaTiles.distance(p.getTile(), food) <= 5).count();
				int live = (int) runners.stream().filter(p -> !p.isUnseen()).count(), unseen = runners.size() - live;
				int good = s.getGroundItems().stream().filter(g -> BaIds.BAIT.contains(g.getItemId()) && g.isGood() && food.equals(g.getTile())).mapToInt(GroundItemView::getQuantity).sum();
				boolean repairWork = work == Step.DEF_REPAIR || "def.repair".equals(ins.getTask()) || "def.fix".equals(ins.getTask());
				boolean isBroken = !finished && s.getTrapCharges() == 0;
				boolean nearEmpty = !finished && near > 0 && good == 0;
				boolean localDrop = work == Step.DEF_DROP && food.equals(target) || "def.trapfood".equals(ins.getTask());
				boolean owedCall = ins.getStep() == Step.CALL;
				if (isBroken) { broken++; if (owedCall) calls++; else if (repairWork) repair++; else delayed++; }
				if (nearEmpty) { empty++; if (localDrop) emptyWork++; }
				if ("def.prepare".equals(ins.getTask())) prep++;
				uncertain = Math.max(uncertain, s.getUncertainRunners());
				Map<String, Boolean> flags = new LinkedHashMap<>();
				flags.put("broken_no_repair", isBroken && !repairWork && !owedCall);
				flags.put("near_empty", nearEmpty);
				flags.put("current_early_support", !finished && work == Step.DEF_SUPPORT);
				flags.put("recorded_early_support", !finished && "DEF_SUPPORT".equals(recorded));
				flags.put("uncertain_runners", !finished && s.getUncertainRunners() > 0);
				for (Map.Entry<String, Boolean> flag : flags.entrySet())
				{
					if (flag.getValue()) active.computeIfAbsent(flag.getKey(), k -> new ArrayList<>()).add(f);
					else finish(episodes, file.getFileName().toString(), flag.getKey(), active.remove(flag.getKey()));
				}
				timeline.add(csv(file.getFileName(), s.getTick(), s.getWaveTick(), recorded, work, ins.getTask(),
					s.getPlayerTile().getX() & 63, s.getPlayerTile().getY() & 63, target == null ? "" : target.getX() & 63,
					target == null ? "" : target.getY() & 63, s.getTrapCharges(), good, near, live, unseen,
					s.getRunnersKilled(), s.getUncertainRunners(), s.getListenCall() == null ? 0 : s.count(s.getListenCall().getItemId()),
					s.count(BaIds.LOGS), s.isHasHammer(), s.isListenFresh(), s.getTicksToCallChange(), ins.getHeadline(), ins.getDetail()));
			}
			for (Map.Entry<String, List<RoundReplay.Frame>> entry : active.entrySet()) finish(episodes, file.getFileName().toString(), entry.getKey(), entry.getValue());
			String row = csv(file.getFileName(), metrics.wave, frames.size(), clear, clearWave, southbound.size(), passed == null ? "" : passed,
				metrics.stepChanges, metrics.targetFlips, oldSupport, support, broken, repair, calls, delayed, empty, emptyWork, prep, changes, uncertain);
			summary.add(row);
			System.out.println(row);
		}
		Files.write(output.resolve("summary.csv"), summary);
		Files.write(output.resolve("timeline.csv"), timeline);
		Files.write(output.resolve("episodes.csv"), episodes);
	}

	private static Step work(Instruction ins) { return ins.getStep() == Step.CALL && ins.getThen() != null ? ins.getThen() : ins.getStep(); }

	private static void finish(List<String> rows, String name, String kind, List<RoundReplay.Frame> frames)
	{
		if (frames == null || frames.isEmpty()) return;
		RoundReplay.Frame first = frames.get(0), last = frames.get(frames.size() - 1);
		rows.add(csv(name, kind, first.raw.t, last.raw.t, frames.size(), first.instruction.getHeadline(), first.instruction.getDetail()));
	}

	private static String csv(Object... values)
	{
		return Arrays.stream(values).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"").collect(Collectors.joining(","));
	}
}
