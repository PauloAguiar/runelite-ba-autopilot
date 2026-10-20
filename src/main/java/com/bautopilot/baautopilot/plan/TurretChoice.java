package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.Comparator;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID.BarbassaultTurret;

/**
 * Which launcher to use and which button to press in its aiming window (one button per egg and
 * target): omega at the queen when loaded; otherwise red at runners (5 HP, about 3 per hit plus
 * splash, and every escape costs the team), then red at healers. With only green, runners still
 * come first: green poison ticks every 3 s on runners but every 18 s on healers (OSRS Wiki). Never
 * stun, and never fighters or rangers (too much HP; the attackers have them).
 *
 * <p>Each launcher only reaches targets within {@link #RANGE} tiles (recorded 2026-10-15/16: hits up
 * to 15 tiles away, "no targetable Penance Runners within firing range" at 16), and a dying runner
 * can't be targeted.
 */
public final class TurretChoice
{
	public static final int NONE = -1;
	/** Chebyshev tiles a launcher reaches. */
	public static final int RANGE = 15;

	/** The stun buttons, to mark as "don't". */
	public static final int[] STUN = {
		BarbassaultTurret.STUN_HEALER, BarbassaultTurret.STUN_RUNNER,
		BarbassaultTurret.STUN_FIGHTER, BarbassaultTurret.STUN_RANGER,
	};

	private TurretChoice()
	{
	}

	/** The button for the launcher nearest to you (the one whose aiming window is open). */
	public static int pick(Snapshot s)
	{
		ObjectView launcher = s.nearestObject(BaIds.LAUNCHERS);
		return pick(s, launcher == null ? null : launcher.getTile());
	}

	/** The button for a launcher at {@code from} (template tile; null = ignore range). */
	public static int pick(Snapshot s, WorldPoint from)
	{
		if (s.isQueenAlive() && !s.isQueenDead() && (s.getEggsOmega() > 0
			|| s.getRole() == Role.ATTACKER && QueenPlanner.cannonReady(s)))
		{
			return BarbassaultTurret.OMEGA_QUEEN;
		}
		boolean runners = inRange(s, PenanceType.RUNNER, from);
		boolean healers = inRange(s, PenanceType.HEALER, from);
		boolean red = s.getEggsRed() > 0;
		boolean green = s.getEggsGreen() > 0;
		if (runners && red)
		{
			return BarbassaultTurret.EXPLOSIVE_RUNNER;
		}
		if (healers && red)
		{
			return BarbassaultTurret.EXPLOSIVE_HEALER;
		}
		if (runners && green)
		{
			return BarbassaultTurret.POISON_RUNNER;
		}
		if (healers && green)
		{
			return BarbassaultTurret.POISON_HEALER;
		}
		return NONE;
	}

	/**
	 * The launcher to walk to: the one with the best shot (runners beat healers), nearest to you on a
	 * tie; null when neither reaches anything worth firing at.
	 */
	public static ObjectView bestLauncher(Snapshot s)
	{
		return s.getObjects().stream()
			.filter(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId()))
			.filter(o -> pick(s, o.getTile()) != NONE)
			.min(Comparator.comparingInt((ObjectView o) -> rank(pick(s, o.getTile())))
				.thenComparingInt(o -> ArenaTiles.distance(s.getPlayerTile(), o.getTile())))
			.orElse(null);
	}

	private static int rank(int button)
	{
		switch (button)
		{
			case BarbassaultTurret.OMEGA_QUEEN:
				return 0;
			case BarbassaultTurret.EXPLOSIVE_RUNNER:
				return 1;
			case BarbassaultTurret.EXPLOSIVE_HEALER:
				return 2;
			case BarbassaultTurret.POISON_RUNNER:
				return 3;
			default:
				return 4;
		}
	}

	private static boolean inRange(Snapshot s, PenanceType type, WorldPoint from)
	{
		for (PenanceView p : s.penance(type))
		{
			if (from == null || ArenaTiles.distance(from, p.getTile()) <= RANGE)
			{
				return true;
			}
		}
		return false;
	}

	/** Short label for a button, e.g. "Red > Runner". */
	public static String label(int component)
	{
		switch (component)
		{
			case BarbassaultTurret.OMEGA_QUEEN:
				return "Omega > Queen";
			case BarbassaultTurret.EXPLOSIVE_RUNNER:
				return "Red > Runner";
			case BarbassaultTurret.EXPLOSIVE_HEALER:
				return "Red > Healer";
			case BarbassaultTurret.POISON_HEALER:
				return "Green > Healer";
			case BarbassaultTurret.POISON_RUNNER:
				return "Green > Runner";
			default:
				return "";
		}
	}
}
