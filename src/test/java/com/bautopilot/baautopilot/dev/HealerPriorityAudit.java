package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Decision audit on the recorded path; deliberately makes no claim about counterfactual kills. */
public final class HealerPriorityAudit
{
	private HealerPriorityAudit() { }

	public static void main(String[] args) throws Exception
	{
		Path directory = Path.of(args.length > 0 ? args[0] : "src/test/resources/recordings");
		Path output = Path.of(args.length > 1 ? args[1] : "build/reports/healer-priority");
		Files.createDirectories(output);
		List<String> summary = new ArrayList<>(List.of("recording,ticks,feeding_opportunities,missed_refresh,wrong_poison_priority,wounded_before_healthy,full_poison_feeds,reserve_finish_ticks,unjustified_full_poison_feeds"));
		List<String> decisions = new ArrayList<>(List.of("recording,tick,step,target_order,target_poison,target_hp_fraction,lowest_poison,missed_refresh,wrong_poison_priority,wounded_before_healthy,healers,detail,task,killed,reserve_kills_needed,held_food,ticks_to_call,recorded_headline"));
		List<Path> files;
		try (Stream<Path> listing = Files.list(directory))
		{
			files = listing.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> frames = RoundReplay.run(round).stream()
				.filter(f -> f.snapshot.isInArena() && f.snapshot.getRole() == Role.HEALER).collect(Collectors.toList());
			if (frames.isEmpty()) continue;
			int opportunities = 0, missed = 0, wrong = 0, wounded = 0, full = 0, finishes = 0, unjustified = 0;
			for (RoundReplay.Frame frame : frames)
			{
				Snapshot s = frame.snapshot;
				Step step = frame.instruction.getStep();
				List<PenanceView> healers = s.alive(PenanceType.HEALER);
				PenanceView target = step != Step.HEAL_FEED ? null : healers.stream()
					.filter(h -> h.getNpcIndex() == frame.instruction.getTarget().getId()).findFirst().orElse(null);
				int poison = target == null ? -1 : target.nextPoisonHit(s.getTick());
				int lowest = healers.stream().mapToInt(h -> h.nextPoisonHit(s.getTick())).min().orElse(4);
				boolean work = Set.of(Step.HEAL_FEED, Step.HEAL_WAIT, Step.HEAL_SUPPORT, Step.HEAL_TAKE_FOOD).contains(step)
					&& s.isListenFresh() && s.getListenCall() != null && s.has(s.getListenCall().getItemId());
				boolean missedTick = work && lowest < 4 && target == null;
				boolean wrongTick = work && target != null && poison > lowest;
				boolean woundedTick = work && target != null && poison == 0 && target.hpFraction() <= 0.5
					&& healers.stream().anyMatch(h -> !h.isUnseen() && h.nextPoisonHit(s.getTick()) == 0 && h.hpFraction() >= 0.9);
				if (work && lowest < 4) opportunities++;
				if (missedTick) missed++;
				if (wrongTick) wrong++;
				if (woundedTick) wounded++;
				if (poison >= 4) full++;
				boolean finish = "heal.reserve".equals(frame.instruction.getTask());
				int reserveKills = Math.max(0, WaveInfo.healersTotal(s.getWave()) - WaveInfo.healersInitial(s.getWave()) - s.getHealersKilled());
				int held = s.getListenCall() == null ? 0 : s.count(s.getListenCall().getItemId());
				if (finish) finishes++;
				if (poison >= 4 && (!finish || reserveKills == 0 || lowest < 4 || target.isUnseen()
					|| s.walk(target.getTile()) > 8 || held <= Math.max(0, WaveInfo.healersTotal(s.getWave()) - s.getHealersKilled() - healers.size()))) unjustified++;
				String state = healers.stream().map(h -> h.getOrder() + ":p" + h.nextPoisonHit(s.getTick())
					+ ":hp" + h.getHealthRatio() + "/" + h.getHealthScale() + ":last" + h.getLastFoodTick()
					+ ":food" + h.getFoods() + ":d" + s.walk(h.getTile()) + (h.isUnseen() ? ":unseen" : "")).collect(Collectors.joining(";"));
				decisions.add(round.name() + "," + s.getTick() + "," + step + "," + (target == null ? -1 : target.getOrder())
					+ "," + poison + "," + (target == null ? -1 : target.hpFraction()) + "," + lowest + "," + missedTick
					+ "," + wrongTick + "," + woundedTick + "," + state + "," + clean(frame.instruction.getDetail())
					+ "," + frame.instruction.getTask() + "," + s.getHealersKilled() + "," + reserveKills + "," + held
					+ "," + s.getTicksToCallChange() + "," + clean(frame.raw.live == null ? "" : frame.raw.live.headline));
			}
			String line = round.name() + "," + frames.size() + "," + opportunities + "," + missed + "," + wrong + "," + wounded + "," + full + "," + finishes + "," + unjustified;
			summary.add(line);
			System.out.println(line);
		}
		Files.write(output.resolve("summary.csv"), summary);
		Files.write(output.resolve("decisions.csv"), decisions);
	}

	private static String clean(String text)
	{
		return text == null ? "" : text.replace(',', ';').replace('\n', '|').replace('\r', ' ');
	}
}
