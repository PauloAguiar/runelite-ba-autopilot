package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Fixed-observation audit of Queen healing versus egg prompts. */
public final class QueenHealerAudit
{
	public static void main(String[] args) throws Exception
	{
		RecordedRound round = RecordedRound.read(Path.of(args[0]));
		Path output = Path.of(args[1]);
		Files.createDirectories(output);
		List<String> rows = new ArrayList<>();
		rows.add("tick,step,own_hp,own_max,worst_name,worst_hp,worst_max,vial_charges,yellow,poisoned,egg_with_team_below_60,egg_with_self_below_60,score,reason");
		int ticks = 0, eggs = 0, own = 0, team = 0, fills = 0, riskyTeam = 0, riskySelf = 0;
		for (RoundReplay.Frame f : RoundReplay.run(round))
		{
			Snapshot s = f.snapshot;
			if (s.getRole() != Role.HEALER || !s.isInArena() || !s.isQueenAlive()) continue;
			ticks++;
			Step step = f.instruction.getStep();
			boolean egg = step == Step.QUEEN_DUNK || step == Step.QUEEN_PASS;
			TeammateView worst = s.getTeammates().stream().filter(t -> t.getMaxHp() > 0)
				.min(Comparator.comparingDouble(TeammateView::fraction)).orElse(null);
			boolean teamLow = egg && worst != null && worst.fraction() < .6;
			boolean selfLow = egg && s.hpFraction() < .6;
			if (egg) eggs++;
			if (step == Step.RETREAT) own++;
			if (step == Step.HEAL_TEAMMATE) team++;
			if (step == Step.HEAL_FILL_VIAL) fills++;
			if (teamLow) riskyTeam++;
			if (selfLow) riskySelf++;
			rows.add(csv(s.getTick(), step, s.getHp(), s.getMaxHp(), worst == null ? "" : worst.getName(),
				worst == null ? -1 : worst.getHp(), worst == null ? -1 : worst.getMaxHp(), BaIds.vialCharges(s::has),
				s.count(BaIds.EGG_YELLOW), s.count(BaIds.EGG_POISONED), teamLow, selfLow,
				f.instruction.getScore(), f.instruction.getDecidedBy()));
		}
		Files.write(output.resolve("decisions.csv"), rows);
		String summary = "queen_ticks,egg_ticks,self_heal_ticks,team_heal_ticks,vial_ticks,eggs_with_team_below_60,eggs_with_self_below_60\n"
			+ csv(ticks, eggs, own, team, fills, riskyTeam, riskySelf) + "\n";
		Files.writeString(output.resolve("summary.csv"), summary);
		System.out.print(summary);
	}
	private static String csv(Object... values)
	{
		return Arrays.stream(values).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"").collect(Collectors.joining(","));
	}
}
