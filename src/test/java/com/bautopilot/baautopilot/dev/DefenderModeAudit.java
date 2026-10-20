package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.plan.*;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Compares continuous and opt-in multikill instructions on identical recorded observations. */
public final class DefenderModeAudit
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
		List<String> rows = new ArrayList<>();
		rows.add("capture,mode,ticks,hold_prompts,timed_releases,repair_position_prompts,repair_clicks,off_spot_repair_clicks,broken_other_work,nearby_reserve_trips,clear_food_prompts");
		for (boolean advanced : new boolean[]{false, true})
		{
			int[] total = new int[9];
			for (Path file : files)
			{
				Planner planner = new Planner();
				int[] count = new int[9];
				for (RoundReplay.Frame frame : RoundReplay.run(RecordedRound.read(file),
					(s, settings) -> planner.plan(s, settings.toBuilder().defenderMultiKill(advanced).build())))
				{
					Snapshot s = frame.snapshot;
					if (!s.isInArena() || s.getRole() != Role.DEFENDER || s.isQueenAlive()) continue;
					Instruction ins = frame.instruction;
					Step step = ins.getStep() == Step.CALL && ins.getThen() != null ? ins.getThen() : ins.getStep();
					count[0]++;
					if (step == Step.DEF_HOLD) count[1]++;
					if (step == Step.DEF_STEP && "def.step".equals(ins.getTask())) count[2]++;
					if (step == Step.DEF_REPAIR_POSITION) count[3]++;
					if (step == Step.DEF_REPAIR)
					{
						count[4]++;
						if (!ArenaTiles.trapFood(s.getWave()).equals(s.getPlayerTile())) count[5]++;
					}
					if (s.getTrapCharges() == 0 && !s.getCleared().contains(PenanceType.RUNNER)
						&& ins.getStep() != Step.CALL && ins.getStep() != Step.RETREAT && !"def.repair".equals(ins.getTask())) count[6]++;
					if ("def.reserve".equals(ins.getTask()) && ArenaTiles.distance(s.getPlayerTile(), ArenaTiles.trapFood(s.getWave())) <= 5) count[7]++;
					if (step == Step.DEF_SERVICE && ArenaTiles.trapFood(s.getWave()).equals(s.getPlayerTile())) count[8]++;
				}
				if (count[0] == 0) continue;
				for (int i = 0; i < total.length; i++) total[i] += count[i];
				rows.add(csv(file.getFileName(), advanced ? "advanced_multikill" : "continuous", count));
			}
			String totals = csv("TOTAL", advanced ? "advanced_multikill" : "continuous", total);
			rows.add(totals);
			System.out.println(rows.get(0));
			System.out.println(totals);
		}
		Files.write(output.resolve("summary.csv"), rows);
	}

	private static String csv(Object capture, String mode, int[] values)
	{
		return "\"" + capture + "\"," + mode + "," + Arrays.stream(values).mapToObj(Integer::toString).collect(Collectors.joining(","));
	}
}
