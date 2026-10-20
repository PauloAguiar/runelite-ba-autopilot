package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Splits fighters and rangers between attackers without any network: every client sees the same
 * team list (attackers sorted by name give each one the same slot everywhere) and the same NPC
 * indexes, so a fixed hash of the NPC index gives every client the same owner for every target.
 * The hash depends only on that NPC, so kills elsewhere never reshuffle who owns what.
 */
public final class TargetSplit
{
	/** Who you are among the attackers and how many there are; {@link #NONE} when not splitting. */
	public static final class Slot
	{
		public final int index;
		public final int of;
		public final List<String> attackers;

		Slot(int index, int of, List<String> attackers)
		{
			this.index = index;
			this.of = of;
			this.attackers = attackers;
		}
	}

	public static final Slot NONE = new Slot(0, 1, List.of());

	private TargetSplit()
	{
	}

	public static Slot slot(Snapshot s)
	{
		String self = s.getSelfName() == null ? null : key(s.getSelfName());
		List<String> attackers = s.getTeam().entrySet().stream()
			.filter(e -> e.getValue() == Role.ATTACKER)
			.map(e -> e.getKey())
			.sorted((a, b) -> key(a).compareTo(key(b)))
			.collect(Collectors.toList());
		if (self == null || attackers.size() < 2)
		{
			return NONE;
		}
		for (int i = 0; i < attackers.size(); i++)
		{
			if (key(attackers.get(i)).equals(self))
			{
				return new Slot(i, attackers.size(), attackers);
			}
		}
		return NONE;
	}

	/** The attacker slot that owns NPC {@code npcIndex}, out of {@code attackers}. */
	public static int owner(int npcIndex, int attackers)
	{
		// Fibonacci hashing: spreads consecutive indexes (penance spawn in index runs) evenly
		int h = npcIndex * 0x9E3779B1;
		return Integer.remainderUnsigned(h ^ (h >>> 16), attackers);
	}

	/** Names compare the way RuneScape does: case- and space-insensitive. */
	static String key(String name)
	{
		return name.replace(' ', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
