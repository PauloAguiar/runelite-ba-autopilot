package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Separates task progress from proximity: vary only position, retaining every other observed fact. */
public final class DefenderProximityAudit
{
	private DefenderProximityAudit() { }

	public static void main(String[] args) throws Exception
	{
		Path directory = Path.of(args.length > 0 ? args[0] : "src/test/resources/recordings");
		Path output = Path.of(args.length > 1 ? args[1] : "build/reports/defender-proximity");
		Files.createDirectories(output);
		List<String> summary = new ArrayList<>(List.of("recording,ticks,step_changes,step_flips,target_changes,target_flips,position_probes,position_only_target_changes"));
		List<String> changes = new ArrayList<>(List.of("recording,tick,task,from_x,from_y,to_x,to_y,before,after"));
		List<Path> files;
		try (Stream<Path> listing = Files.list(directory))
		{
			files = listing.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> frames = RoundReplay.run(round).stream()
				.filter(f -> f.snapshot.isInArena() && f.snapshot.getRole() == Role.DEFENDER).collect(Collectors.toList());
			if (frames.isEmpty()) continue;
			Settings settings = round.header.settings == null ? Settings.builder().build() : round.header.settings;
			Planner planner = new Planner();
			int probes = 0, switched = 0;
			for (int i = 0; i + 1 < frames.size(); i++)
			{
				Snapshot s = frames.get(i).snapshot;
				Instruction current = frames.get(i).instruction;
				if (!Set.of("def.pattern", "def.tools", "def.repair").contains(current.getTask() == null ? "" : current.getTask())
					|| !(current.getStep() == Step.DEF_DROP || current.getStep() == Step.DEF_HAMMER || current.getStep() == Step.DEF_LOGS)
					|| current.getTarget().getTile() == null || s.getPlayerTile().equals(frames.get(i + 1).snapshot.getPlayerTile())) continue;
				Snapshot moved = s.toBuilder().playerTile(frames.get(i + 1).snapshot.getPlayerTile())
					.lastTask(current.getTask()).lastTargetTile(current.getTarget().getTile()).tasks(current.getTasks()).build();
				Instruction next = planner.plan(moved, settings);
				probes++;
				if (!current.getTarget().equals(next.getTarget()))
				{
					switched++;
					changes.add(round.name() + "," + s.getTick() + "," + current.getTask() + "," + (s.getPlayerTile().getX() & 63)
						+ "," + (s.getPlayerTile().getY() & 63) + "," + (moved.getPlayerTile().getX() & 63) + "," + (moved.getPlayerTile().getY() & 63)
						+ "," + current.getHeadline().replace(',', ';') + "," + next.getHeadline().replace(',', ';'));
				}
			}
			ReplayMetrics.Wave metrics = ReplayMetrics.measure(frames);
			summary.add(round.name() + "," + metrics.ticks + "," + metrics.stepChanges + "," + metrics.stepFlips + ","
				+ metrics.targetChanges + "," + metrics.targetFlips + "," + probes + "," + switched);
			System.out.println(round.name() + ": " + switched + "/" + probes + " position-only target changes; "
				+ metrics.targetFlips + " short target flip-backs on the recorded path");
		}
		Files.write(output.resolve("summary.csv"), summary);
		Files.write(output.resolve("position-changes.csv"), changes);
	}
}
