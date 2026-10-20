package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Fixed-observation navigation checks for the October 18 Collector run. */
public final class CollectorNavigationAudit
{
	public static void main(String[] args) throws Exception
	{
		Path output = Path.of(args[1]);
		Files.createDirectories(output);
		List<String> rows = new ArrayList<>();
		rows.add("capture,tick,step,call_left,walk,nearest_equivalent,late_pickup,far_egg,far_hopper,nearby_target_switch,stack,headline,target_x,target_y,free,bag_red,bag_green,bag_blue,level,pickup_available,wait_with_pickup,best_pickup_score,chosen_score");
		int ticks = 0, late = 0, farEggs = 0, farHoppers = 0, switches = 0;
		int longEggTrips = 0, longScans = 0;
		int centreChecks = 0, waitsWithPickups = 0;
		boolean allCaptures = args.length > 2 && args[2].equals("all");
		List<Path> files;
		try (java.util.stream.Stream<Path> stream = Files.list(Path.of(args[0])))
		{
			files = stream.filter(p -> p.toString().endsWith(".jsonl") && (allCaptures
				|| p.getFileName().toString().startsWith("2026-10-18_13-") && !p.toString().endsWith("wave10.jsonl")))
				.sorted().collect(Collectors.toList());
		}
		for (Path file : files)
		{
			RoundReplay.Frame previous = null;
			for (RoundReplay.Frame f : RoundReplay.run(RecordedRound.read(file)))
			{
				Snapshot s = f.snapshot;
				if (s.getRole() != Role.COLLECTOR || !s.isInArena()) continue;
				ticks++;
				Target target = f.instruction.getTarget();
				boolean collect = f.instruction.getStep() == Step.COL_COLLECT;
				int walk = s.walk(target.getTile());
				int nearest = collect ? s.getGroundItems().stream().filter(g -> g.getItemId() == target.getId()
					&& feasible(s, g) && safe(s, g.getTile()) == safe(s, target.getTile())).mapToInt(g -> s.walk(g.getTile())).min().orElse(walk)
					: f.instruction.getStep() == Step.COL_LOAD ? s.getObjects().stream().filter(o -> BaIds.contains(BaIds.HOPPERS, o.getId()))
					.mapToInt(o -> s.walk(o.getTile())).min().orElse(walk) : walk;
				boolean lateCall = collect && s.getTicksToCallChange() >= 0 && s.getTicksToCallChange() <= (walk + 1) / 2 + 3;
				boolean farEgg = collect && walk > nearest + 4;
				boolean farHopper = f.instruction.getStep() == Step.COL_LOAD && walk > nearest + 2;
				Target old = previous == null ? Target.NONE : previous.instruction.getTarget();
				boolean switched = collect && previous != null && previous.instruction.getStep() == Step.COL_COLLECT
					&& old.getId() == target.getId() && !java.util.Objects.equals(old.getTile(), target.getTile()) && s.walk(old.getTile()) <= 2
					&& s.getGroundItems().stream().anyMatch(g -> g.getItemId() == old.getId() && g.getTile().equals(old.getTile()) && feasible(s, g));
				int stack = collect ? s.getGroundItems().stream().filter(g -> g.getItemId() == target.getId() && g.getTile().equals(target.getTile()))
					.mapToInt(GroundItemView::getQuantity).sum() : 0;
				if (lateCall) late++;
				if (farEgg) farEggs++;
				if (farHopper) farHoppers++;
				if (switched) switches++;
				if (collect && walk > 12) longEggTrips++;
				if ("col.scan".equals(f.instruction.getTask()) && walk > 6) longScans++;
				if ("col.scan".equals(f.instruction.getTask())) centreChecks++;
				double bestPickup = f.instruction.getPriorities().stream().filter(p -> "col.collect".equals(p.getTask()) && p.getScore() != null)
					.mapToDouble(p -> p.getScore()).max().orElse(Double.NaN);
				boolean pickupAvailable = Double.isFinite(bestPickup);
				boolean waitWithPickup = f.instruction.getStep() == Step.COL_FOLLOW && pickupAvailable;
				if (waitWithPickup) waitsWithPickups++;
				rows.add(String.join(",", file.getFileName().toString(), "" + s.getTick(), f.instruction.getStep().name(),
					"" + s.getTicksToCallChange(), "" + walk, "" + nearest, "" + lateCall, "" + farEgg, "" + farHopper, "" + switched,
					"" + stack, "\"" + f.instruction.getHeadline().replace("\"", "\"\"") + "\"",
					target.getTile() == null ? "" : "" + target.getTile().getX(), target.getTile() == null ? "" : "" + target.getTile().getY(),
					"" + s.getFreeSlots(), "" + s.getBagRed(), "" + s.getBagGreen(), "" + s.getBagBlue(), "" + s.getRoleLevels().getOrDefault(Role.COLLECTOR, 0),
					"" + pickupAvailable, "" + waitWithPickup, "" + bestPickup, "" + f.instruction.getScore()));
				previous = f;
			}
		}
		Files.write(output.resolve("decisions.csv"), rows);
		String summary = "ticks,late_pickups,far_equivalent_eggs,far_shared_hoppers,nearby_target_switches,egg_walks_over_12,centre_walks_over_6,centre_checks,waits_with_pickups\n"
			+ ticks + "," + late + "," + farEggs + "," + farHoppers + "," + switches + "," + longEggTrips + "," + longScans + "," + centreChecks + "," + waitsWithPickups + "\n";
		Files.writeString(output.resolve("summary.csv"), summary);
		System.out.print(summary);
	}

	private static boolean feasible(Snapshot s, GroundItemView g)
	{
		int limit = 200 - (s.getTick() - g.getSpawnTick());
		if (s.getTicksToCallChange() >= 0) limit = Math.min(limit, s.getTicksToCallChange());
		return g.getQuantity() > 0 && limit > (s.walk(g.getTile()) + 1) / 2 + 3;
	}
	private static boolean safe(Snapshot s, net.runelite.api.coords.WorldPoint tile)
	{
		return s.getPenance().stream().noneMatch(p -> (p.getType() == PenanceType.RUNNER || p.getType() == PenanceType.HEALER)
			&& ArenaTiles.distance(p.getTile(), tile) <= 2);
	}
}
