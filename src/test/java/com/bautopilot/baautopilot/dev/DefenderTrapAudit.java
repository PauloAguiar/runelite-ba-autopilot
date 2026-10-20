package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.plan.*;
import com.bautopilot.baautopilot.state.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/** Fixed-observation audit of trap releases, off-food hold markers and routine departures. */
public final class DefenderTrapAudit
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
		List<String> rows = new ArrayList<>(), summary = new ArrayList<>();
		rows.add("capture,tick,wave,step,task,charges,on_food,stacked,arriving,unseen,recent_unseen,bounded_release,committed_release,routine_departure,release_with_arrivals,multi_without_two,off_food_hold,headline,detail");
		summary.add("capture,ticks,routine_departures,release_with_arrivals,multi_without_two,off_food_holds");
		for (Path file : files)
		{
			int ticks = 0, departures = 0, releases = 0, charges = 0, holds = 0;
			for (RoundReplay.Frame frame : RoundReplay.run(RecordedRound.read(file)))
			{
				Snapshot s = frame.snapshot;
				if (!s.isInArena() || s.getRole() != Role.DEFENDER || s.isQueenAlive()) continue;
				ticks++;
				WorldPoint food = ArenaTiles.trapFood(s.getWave());
				Instruction ins = frame.instruction;
				Step step = ins.getStep();
				List<PenanceView> runners = s.alive(PenanceType.RUNNER);
				long stacked = runners.stream().filter(p -> !p.isUnseen() && p.getTile() != null && ArenaTiles.distance(p.getTile(), food) <= 1).count();
				long arriving = runners.stream().filter(p -> !p.isUnseen() && p.getTile() != null
					&& ArenaTiles.distance(p.getTile(), food) > 1 && p.getTile().getY() >= food.getY() - 1).count();
				long unseen = runners.stream().filter(PenanceView::isUnseen).count();
				// One runner cycle: older unseen locations are unconfirmed, not an arrival deadline.
				long recentUnseen = runners.stream().filter(p -> p.isUnseen() && p.getUnseenTicks() <= 10).count();
				boolean onFood = food.equals(s.getPlayerTile());
				boolean multi = "def.step".equals(ins.getTask()) && step == Step.DEF_STEP;
				TaskMemory.Progress stack = s.getTasks().getEntries().get("def.stack");
				boolean bounded = multi && stack != null && s.getTick() - stack.getLastProgressTick() >= 10;
				Target committed = s.getTasks().target("def.step");
				boolean continuing = multi && committed != null && ArenaTiles.trapStep(s.getWave()).equals(committed.getTile());
				boolean premature = multi && arriving + recentUnseen > 0 && !bounded && !continuing;
				boolean one = multi && s.getTrapCharges() != 2;
				boolean offHold = step == Step.DEF_HOLD && onFood && ins.getTarget().getTile() != null
					&& !food.equals(ins.getTarget().getTile());
				boolean departure = onFood && stacked + arriving + unseen > 0 && step != Step.DEF_REPAIR
					&& ins.getTarget().getTile() != null && ArenaTiles.distance(food, ins.getTarget().getTile()) > 1;
				if (departure) departures++;
				if (premature) releases++;
				if (one) charges++;
				if (offHold) holds++;
				rows.add(csv(file.getFileName(), s.getTick(), s.getWave(), step, ins.getTask(), s.getTrapCharges(), onFood,
					stacked, arriving, unseen, recentUnseen, bounded, continuing, departure, premature, one, offHold, ins.getHeadline(), ins.getDetail()));
			}
			if (ticks > 0) summary.add(csv(file.getFileName(), ticks, departures, releases, charges, holds));
		}
		Files.write(output.resolve("decisions.csv"), rows);
		Files.write(output.resolve("summary.csv"), summary);
		System.out.println(String.join("\n", summary));
	}
	private static String csv(Object... values)
	{
		return Arrays.stream(values).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"").collect(Collectors.joining(","));
	}
}
