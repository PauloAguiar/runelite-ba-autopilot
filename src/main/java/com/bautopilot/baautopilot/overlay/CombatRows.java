package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.model.DefenderGuide;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import net.runelite.client.ui.FontManager;

/**
 * Live numbers inside the instruction box, only what your role acts on (one box, not two):
 * <ul>
 * <li>attacker: damage per second (you / team) and a row per fighter and ranger: HP %, damage per
 * second on it, seconds to kill, your share; the current target marked;</li>
 * <li>collector: eggs loaded in the launcher per colour (out of 5), omega when there is one;</li>
 * <li>defender: trap charges, logs taken of the wave's target, bait held per kind.</li>
 * </ul>
 * The healer's vial and healer table are drawn by the box itself.
 */
final class CombatRows
{
	private static final int MAX_ROWS = 6;

	private CombatRows()
	{
	}

	static List<IconRow> rows(Snapshot s, Instruction ins, IntFunction<BufferedImage> items, DefenderGuide guide)
	{
		List<IconRow> out = new ArrayList<>();
		if (s.getRole() == null)
		{
			return out;
		}
		Font bold = FontManager.getRunescapeBoldFont();
		switch (s.getRole())
		{
			case COLLECTOR:
				out.add(launcher(s, items, bold));
				out.add(carrying(s, items, bold));
				break;
			case DEFENDER:
				out.add(defender(s, items, bold, guide));
				break;
			case ATTACKER:
				out.add(new IconRow()
					.text("DPS", Palette.MUTED)
					.group().text("you " + oneDecimal(s.getMyDps()), s.getMyDps() > 0 ? Palette.TEXT : Palette.MUTED, bold)
					.group().text("team " + oneDecimal(s.getTeamDps()), Palette.TEXT)
					.right(s.getMyDamage() + " dmg", Palette.MUTED));
				penance(out, s, ins, bold, PenanceType.FIGHTER, PenanceType.RANGER);
				break;
			default:
				break;
		}
		return out;
	}

	/**
	 * The eggs you carry, inventory and bag together (loading empties both), and the free slots:
	 * kept in view instead of the explanation lines that used to spell them out.
	 */
	private static IconRow carrying(Snapshot s, IntFunction<BufferedImage> items, Font bold)
	{
		IconRow row = new IconRow().text("Carrying", Palette.MUTED);
		int[][] eggs = {
			{BaIds.EGG_RED, s.count(BaIds.EGG_RED) + s.getBagRed()},
			{BaIds.EGG_GREEN, s.count(BaIds.EGG_GREEN) + s.getBagGreen()},
			{BaIds.EGG_BLUE, s.count(BaIds.EGG_BLUE) + s.getBagBlue()}};
		for (int[] egg : eggs)
		{
			row.group().icon(items.apply(egg[0])).text(String.valueOf(egg[1]), egg[1] > 0 ? Palette.TEXT : Palette.MUTED, egg[1] > 0 ? bold : null);
		}
		int free = s.getFreeSlots();
		return row.right(free + " free", free <= 3 ? Palette.BAD : Palette.MUTED);
	}

	private static IconRow launcher(Snapshot s, IntFunction<BufferedImage> items, Font bold)
	{
		IconRow row = new IconRow().text("Launcher", Palette.MUTED)
			.group().icon(items.apply(BaIds.EGG_RED)).text(s.getEggsRed() + "/5", eggColour(s.getEggsRed()), bold)
			.group().icon(items.apply(BaIds.EGG_GREEN)).text(s.getEggsGreen() + "/5", eggColour(s.getEggsGreen()), bold);
		if (s.getEggsOmega() > 0)
		{
			row.group().icon(items.apply(BaIds.EGG_OMEGA)).text(String.valueOf(s.getEggsOmega()), Palette.GOOD, bold);
		}
		return row;
	}

	private static IconRow defender(Snapshot s, IntFunction<BufferedImage> items, Font bold, DefenderGuide guide)
	{
		IconRow row = new IconRow();
		int charges = s.getTrapCharges();
		if (charges >= 0)
		{
			row.text("Trap", Palette.MUTED).text(charges + "/2", charges == 0 ? Palette.BAD : charges == 1 ? Palette.OK : Palette.GOOD, bold);
		}
		int want = WaveInfo.defenderLogs(s.getWave(), guide);
		int taken = Math.max(s.getLogsTaken(), s.count(BaIds.LOGS));
		row.group().icon(items.apply(BaIds.LOGS)).text(taken + "/" + want, taken >= want ? Palette.TEXT : Palette.OK, bold);
		BaCall call = s.getListenCall();
		for (BaCall bait : new BaCall[]{BaCall.BAIT_TOFU, BaCall.BAIT_CRACKERS, BaCall.BAIT_WORMS})
		{
			int n = s.count(bait.getItemId());
			boolean called = call == bait;
			row.group().icon(items.apply(bait.getItemId()))
				.text(String.valueOf(n), n == 0 ? Palette.BAD : called ? Palette.GOOD : Palette.TEXT, called ? bold : null);
		}
		return row;
	}

	private static void penance(List<IconRow> out, Snapshot s, Instruction ins, Font bold, PenanceType... types)
	{
		int target = ins != null && ins.getTarget().getKind() == Target.Kind.NPC ? ins.getTarget().getId() : -1;
		List<PenanceView> list = new ArrayList<>();
		for (PenanceType type : types)
		{
			List<PenanceView> of = s.alive(type);
			of.sort(Comparator.comparing(PenanceView::isUnseen).thenComparingDouble(PenanceView::hpFraction));
			list.addAll(of);
		}
		int shown = 0;
		for (PenanceView p : list)
		{
			if (shown++ == MAX_ROWS)
			{
				out.add(new IconRow().text("+" + (list.size() - MAX_ROWS) + " more", Palette.MUTED));
				break;
			}
			out.add(row(p, p.getNpcIndex() == target, bold));
		}
	}

	private static IconRow row(PenanceView p, boolean target, Font bold)
	{
		Color name = p.isUnseen() ? Palette.MUTED : Palette.role(Role.ATTACKER);
		IconRow row = new IconRow().text((target ? "▶ " : "") + (p.getType() == PenanceType.FIGHTER ? "Fighter" : "Ranger"),
			name, target ? bold : null);
		row.group().text(Math.round(p.hpFraction() * 100) + "%", p.isUnseen() ? Palette.MUTED : hpColour(p.hpFraction()), bold);
		if (p.getDps() > 0)
		{
			row.group().text(oneDecimal(p.getDps()) + "/s", Palette.TEXT);
		}
		double eta = p.secondsToKill();
		if (eta >= 0)
		{
			row.group().text("~" + Math.round(eta) + "s", Palette.MUTED);
		}
		if (p.isUnseen())
		{
			row.right("out of view", Palette.MUTED);
		}
		else if (p.getDamageTaken() > 0)
		{
			row.right("you " + Math.round(100.0 * p.getDamageMine() / p.getDamageTaken()) + "%", Palette.MUTED);
		}
		return row;
	}

	private static Color hpColour(double fraction)
	{
		return fraction > 0.6 ? Palette.GOOD : fraction > 0.3 ? Palette.OK : Palette.BAD;
	}

	private static Color eggColour(int loaded)
	{
		// Empty: nothing to fire. One left: the next shot empties it
		return loaded == 0 ? Palette.BAD : loaded == 1 ? Palette.OK : Palette.TEXT;
	}

	private static String oneDecimal(double v)
	{
		return String.format(Locale.ROOT, "%.1f", v);
	}
}
