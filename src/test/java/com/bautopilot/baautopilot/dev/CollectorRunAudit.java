package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.RunnerState;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.Tier;
import com.bautopilot.baautopilot.plan.TurretChoice;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Capture audit of cannon interruptions; does not simulate different player actions. */
public final class CollectorRunAudit
{
	private CollectorRunAudit() { }

	public static void main(String[] args) throws Exception
	{
		Path input = Paths.get(args[0]);
		Path output = Paths.get(args[1]);
		Files.createDirectories(output);
		List<Path> files;
		try (Stream<Path> stream = Files.list(input))
		{
			files = stream.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		List<String> rows = new ArrayList<>();
		List<String> summary = new ArrayList<>();
		rows.add("recording,tick,wave,call,fresh,step,task,tier,detail,reds,greens,held_red,held_green,free_slots,shot_age,escaping_runners,healers,escape_in_range,low_red_active,unsupported_interrupt,live_step,call_period,opening_red_cannon");
		summary.add("recording,ticks,load_ticks,fire_ticks,team_interrupt_ticks,unsupported_interrupt_ticks,red_collect_ticks,violations,opening_red_cannon_ticks,opening_red_collect_ticks,live_opening_red_cannon_ticks,live_opening_red_collect_ticks");
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> frames = RoundReplay.run(round);
			int ticks = 0, loads = 0, fires = 0, interrupts = 0, unsupported = 0, redCollect = 0;
			int openingCannon = 0, openingCollect = 0, liveOpeningCannon = 0, liveOpeningCollect = 0;
			for (RoundReplay.Frame frame : frames)
			{
				Snapshot s = frame.snapshot;
				if (!s.isInArena() || s.getRole() != Role.COLLECTOR) continue;
				ticks++;
				Step step = frame.instruction.getStep();
				if (step == Step.COL_LOAD) loads++;
				if (step == Step.COL_FIRE) fires++;
				if (step == Step.COL_COLLECT && s.getListenCall() == BaCall.RED_EGG) redCollect++;
				int shotAge = s.getLastLauncherShotTick() < 0 ? -1 : s.getTick() - s.getLastLauncherShotTick();
				List<PenanceView> escaping = s.getPenance().stream().filter(p -> p.getType() == PenanceType.RUNNER
					&& !p.isDying() && !p.isUnseen() && RunnerState.of(s, p) == RunnerState.ESCAPING).collect(Collectors.toList());
				boolean escapeInRange = escaping.stream().anyMatch(p -> s.getObjects().stream()
					.anyMatch(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId())
						&& ArenaTiles.distance(o.getTile(), p.getTile()) <= TurretChoice.RANGE));
				boolean lowRedActive = s.getEggsRed() <= 2 && shotAge >= 0 && shotAge <= 8;
				boolean interrupt = (step == Step.COL_LOAD || step == Step.COL_FIRE) && frame.instruction.getTier() == Tier.TEAM_BLOCKING;
				boolean invalid = interrupt && !escapeInRange && !lowRedActive;
				boolean opening = s.getCallPeriod() < 2 && s.getListenCall() == BaCall.RED_EGG && s.isListenFresh() && s.getFreeSlots() > 0;
				boolean openingDetour = opening && (step == Step.COL_LOAD || step == Step.COL_FIRE);
				if (openingDetour) openingCannon++;
				if (opening && step == Step.COL_COLLECT) openingCollect++;
				if (opening && frame.raw.live != null)
				{
					if ("COL_LOAD".equals(frame.raw.live.step) || "COL_FIRE".equals(frame.raw.live.step)) liveOpeningCannon++;
					if ("COL_COLLECT".equals(frame.raw.live.step)) liveOpeningCollect++;
				}
				if (interrupt) interrupts++;
				if (invalid) unsupported++;
				rows.add(csv(round.name(), s.getTick(), s.getWave(), s.getListenCall(), s.isListenFresh(), step,
					frame.instruction.getTask(), frame.instruction.getTier(), frame.instruction.getDetail(), s.getEggsRed(), s.getEggsGreen(),
					s.count(BaIds.EGG_RED) + s.getBagRed(), s.count(BaIds.EGG_GREEN) + s.getBagGreen(), s.getFreeSlots(), shotAge,
					escaping.size(), s.alive(PenanceType.HEALER).size(), escapeInRange, lowRedActive, invalid,
					frame.raw.live == null ? "" : frame.raw.live.step, s.getCallPeriod(), openingDetour));
			}
			RoundInvariants.Report report = RoundInvariants.check(round, frames);
			summary.add(csv(round.name(), ticks, loads, fires, interrupts, unsupported, redCollect, report.violations.size(),
				openingCannon, openingCollect, liveOpeningCannon, liveOpeningCollect));
			System.out.println(round.name() + ": " + ticks + " ticks, " + interrupts + " cannon interrupts, " + unsupported
				+ " without escaping runners/recent low-red firing; " + report.violations.size() + " invariant violations");
			report.violations.forEach(v -> System.out.println("VIOLATION: " + v));
		}
		Files.write(output.resolve("decisions.csv"), rows);
		Files.write(output.resolve("summary.csv"), summary);
	}

	private static String csv(Object... values)
	{
		return Arrays.stream(values).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"").collect(Collectors.joining(","));
	}
}
