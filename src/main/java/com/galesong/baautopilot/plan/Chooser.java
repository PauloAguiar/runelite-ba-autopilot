package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.state.Snapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Picks one candidate: the highest tier present; a committed task (still listed and sticky),
 * including one suspended by an interruption; otherwise the earliest deadline, then the shortest
 * walk, then listing order. Weighted roles instead compare scores directly, with continuity in
 * the score rather than an unconditional commitment. All history comes through the snapshot.
 */
final class Chooser
{
	private Chooser()
	{
	}

	static Instruction choose(Snapshot s, List<Candidate> candidates)
	{
		if (candidates.stream().filter(Objects::nonNull).anyMatch(c -> c.getInstruction().getScore() != null))
			return weighted(s, candidates);
		Candidate best = pick(s, candidates);
		if (best == null)
		{
			return null;
		}
		List<PriorityLine> lines = candidates.stream()
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt((Candidate c) -> c.getTier().ordinal())
				.thenComparingInt(Candidate::getDeadline).thenComparingInt(c -> cost(s, c)))
			.map(c -> new PriorityLine(c.getTier(), c.getTask(), c.getInstruction().getHeadline(), c.getDeadline(),
				cost(s, c), c.isSticky(), c == best, null, c.getInstruction().getScoreTerms()))
			.collect(Collectors.toList());
		return best.getInstruction().toBuilder().task(best.getTask()).tier(best.getTier())
			.priorities(lines).decidedBy(reason(s, candidates, best)).build();
	}

	private static Instruction weighted(Snapshot s, List<Candidate> candidates)
	{
		List<Candidate> ranked = candidates.stream().filter(Objects::nonNull).collect(Collectors.toList());
		if (ranked.stream().anyMatch(c -> c.getInstruction().getScore() == null || !Double.isFinite(c.getInstruction().getScore())))
			throw new IllegalArgumentException("Weighted choices require a finite score on every candidate");
		ranked.sort(Comparator.comparingDouble((Candidate c) -> c.getInstruction().getScore()).reversed()
			.thenComparingInt(c -> cost(s, c)));
		Candidate best = ranked.get(0);
		List<PriorityLine> lines = ranked.stream().map(c -> new PriorityLine(c.getTier(), c.getTask(),
			c.getInstruction().getHeadline(), c.getDeadline(), cost(s, c), false, c == best,
			c.getInstruction().getScore(), c.getInstruction().getScoreTerms())).collect(Collectors.toList());
		return best.getInstruction().toBuilder().task(best.getTask()).tier(best.getTier()).priorities(lines)
			.decidedBy(String.format(java.util.Locale.ROOT, "Highest score %.1f%s", best.getInstruction().getScore(),
				ranked.size() < 2 ? "" : String.format(java.util.Locale.ROOT, " vs %.1f", ranked.get(1).getInstruction().getScore())))
			.build();
	}

	/** Why {@code best} won, in the chooser's own order of rules. */
	static String reason(Snapshot s, List<Candidate> candidates, Candidate best)
	{
		if (best.isSticky() && best.getTask() != null && best.getTask().equals(s.getLastTask()))
		{
			return "Committed task " + best.getTask() + " kept (no higher tier)";
		}
		if (best.isSticky() && s.getTasks().lastActive(best.getTask()) >= 0)
		{
			return "Resume task " + best.getTask() + " after interruption";
		}
		List<Candidate> rivals = candidates.stream()
			.filter(c -> c != null && c != best && c.getTier() == best.getTier())
			.collect(Collectors.toList());
		if (rivals.isEmpty())
		{
			return "Only " + best.getTier() + " candidate";
		}
		if (rivals.stream().allMatch(c -> c.getDeadline() > best.getDeadline()))
		{
			return "Earliest deadline in " + best.getTier();
		}
		if (rivals.stream().allMatch(c -> cost(s, c) > cost(s, best)))
		{
			return "Shortest walk in " + best.getTier();
		}
		return "First listed in " + best.getTier();
	}

	static Candidate pick(Snapshot s, List<Candidate> candidates)
	{
		Tier top = null;
		Candidate committed = null;
		for (Candidate c : candidates)
		{
			if (c == null)
			{
				continue;
			}
			if (top == null || c.getTier().ordinal() < top.ordinal())
			{
				top = c.getTier();
			}
			if (c.isSticky() && c.getTask() != null && commitment(s, c) >= 0
				&& (committed == null || c.getTier().ordinal() < committed.getTier().ordinal()
					|| c.getTier() == committed.getTier() && commitment(s, c) > commitment(s, committed)))
			{
				committed = c;
			}
		}
		if (top == null)
		{
			return null;
		}
		if (committed != null && committed.getTier().ordinal() <= top.ordinal())
		{
			return committed;
		}
		final Tier tier = top;
		return candidates.stream()
			.filter(Objects::nonNull)
			.filter(c -> c.getTier() == tier)
			.min(Comparator.comparingInt(Candidate::getDeadline).thenComparingInt(c -> cost(s, c)))
			.orElse(null);
	}

	private static int commitment(Snapshot s, Candidate c)
	{
		return c.getTask().equals(s.getLastTask()) ? Integer.MAX_VALUE : s.getTasks().lastActive(c.getTask());
	}

	/** Tiles to walk to the target; inventory clicks and unknown tiles cost nothing. */
	static int cost(Snapshot s, Candidate c)
	{
		WorldPoint tile = c.getInstruction().getTarget().getTile();
		return tile == null ? 0 : s.walk(tile);
	}
}
