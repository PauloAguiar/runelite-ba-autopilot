package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import java.awt.Color;

/** Colours shared by the in-game box, the world highlights and the side panel. */
public final class Palette
{
	public static final Color GOOD = new Color(90, 220, 120);
	/** Defender bait which does not match the current call; distinct from an urgent horn call. */
	public static final Color BAD_BAIT = new Color(190, 120, 255);

	public static Color action(Instruction instruction, Color fallback)
	{
		return instruction.isBadBait() && (instruction.getStep() != Step.CALL || instruction.getThen() != null)
			? BAD_BAIT : fallback;
	}
	public static final Color OK = new Color(240, 190, 70);
	public static final Color BAD = new Color(240, 90, 80);
	public static final Color MUTED = new Color(160, 160, 160);
	public static final Color TEXT = new Color(235, 235, 235);
	public static final Color BACKGROUND = new Color(20, 20, 22, 215);
	/** The instruction box, after the game's stone-and-gold interfaces; dark red stone while a call is owed. */
	public static final Color STONE = new Color(46, 39, 30, 235);
	public static final Color STONE_CALL = new Color(82, 26, 20, 238);
	public static final Color GOLD_EDGE = new Color(146, 118, 66);
	public static final Color DARK_EDGE = new Color(14, 12, 9);
	public static final Color CREAM = new Color(240, 230, 205);
	public static final Color GOLD = new Color(255, 152, 31);
	/** Secondary numbers under the divider. */
	public static final Color DIM = new Color(150, 138, 115);

	/** A call made within this many seconds is good, within OK_SECONDS acceptable. */
	public static final double GOOD_SECONDS = 1.5;
	public static final double OK_SECONDS = 3.0;

	private Palette()
	{
	}

	public static Color role(Role role)
	{
		if (role == null)
		{
			return MUTED;
		}
		switch (role)
		{
			case ATTACKER:
				return new Color(235, 95, 85);
			case COLLECTOR:
				return new Color(240, 205, 70);
			case DEFENDER:
				return new Color(95, 155, 255);
			default:
				return new Color(95, 215, 125);
		}
	}

	/** Green / amber / red for a reaction time in seconds; muted when there is none (-1). */
	public static Color seconds(double seconds)
	{
		if (seconds < 0)
		{
			return MUTED;
		}
		return seconds <= GOOD_SECONDS ? GOOD : seconds <= OK_SECONDS ? OK : BAD;
	}

	/** Green at zero, amber up to {@code okUpTo}, red beyond: for mistakes (wrong eggs, runners passed). */
	public static Color mistakes(int count, int okUpTo)
	{
		return count <= 0 ? GOOD : count <= okUpTo ? OK : BAD;
	}

	/** An egg's own colour (red, green, blue, yellow), or null for anything else. */
	public static Color egg(int itemId)
	{
		switch (itemId)
		{
			case BaIds.EGG_RED:
				return new Color(235, 60, 55);
			case BaIds.EGG_GREEN:
				return new Color(80, 215, 80);
			case BaIds.EGG_BLUE:
				return new Color(85, 145, 255);
			case BaIds.EGG_YELLOW:
				return new Color(245, 220, 60);
			default:
				return null;
		}
	}

	/** The same colour, slightly see-through, for fills. */
	public static Color fill(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
