package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.PriorityLine;
import com.bautopilot.baautopilot.plan.Tier;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Priorities debugger: every candidate the chooser weighed this tick, best first, with its tier,
 * deadline (ticks), walking cost (tiles) and whether it is a committed task; the shown step and
 * the reason it won (or the layer that overrode the chooser: survival, the Queen, an owed call).
 */
public class PriorityOverlay extends OverlayPanel
{
	private static final int WIDTH = 270;
	private static final int MAX_ROWS = 10;
	private static final int HEADLINE_CHARS = 22;

	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;

	@Inject
	PriorityOverlay(BaAutopilotPlugin plugin, BaAutopilotConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Instruction ins = plugin.getInstruction();
		if (!config.debugPriorities() || ins == null || plugin.getSnapshot() == null || !plugin.getSnapshot().isInArena())
		{
			return null;
		}
		panelComponent.setBackgroundColor(Palette.BACKGROUND);
		Font bold = FontManager.getRunescapeBoldFont();
		panelComponent.getChildren().add(new IconRow().text("PRIORITIES", Palette.TEXT, bold)
			.right(ins.getTask() == null ? "" : ins.getTask(), Palette.MUTED));
		panelComponent.getChildren().add(LineComponent.builder()
			.left(ins.getDecidedBy() == null ? "Outside the chooser" : ins.getDecidedBy())
			.leftColor(Palette.MUTED).build());
		ins.getTasks().getEntries().forEach((id, task) -> panelComponent.getChildren().add(LineComponent.builder()
			.left(id + ": " + task.getStatus().name().toLowerCase())
			.leftColor(task.getStatus() == com.bautopilot.baautopilot.plan.TaskMemory.Status.RUNNING ? Palette.GOOD : Palette.MUTED)
			.build()));

		int shown = 0;
		for (PriorityLine p : ins.getPriorities())
		{
			if (shown++ == MAX_ROWS)
			{
				panelComponent.getChildren().add(new IconRow().text("+" + (ins.getPriorities().size() - MAX_ROWS) + " more", Palette.MUTED));
				break;
			}
			String headline = p.getHeadline() == null ? "" : p.getHeadline();
			if (headline.length() > HEADLINE_CHARS)
			{
				headline = headline.substring(0, HEADLINE_CHARS - 1) + "…";
			}
			StringBuilder right = new StringBuilder();
			if (p.getScore() != null) right.append(String.format(java.util.Locale.ROOT, "%.1f  ", p.getScore()));
			if (p.getDeadline() != Integer.MAX_VALUE)
			{
				right.append("dl ").append(p.getDeadline()).append("  ");
			}
			right.append(p.getWalk()).append("t");
			if (p.isSticky())
			{
				right.append(" ★");
			}
			panelComponent.getChildren().add(new IconRow()
				.text(p.isChosen() ? "▶" : "·", p.isChosen() ? Palette.GOOD : Palette.MUTED, bold)
				.text(p.getScore() == null ? abbrev(p.getTier()) : "VAL", colour(p.getTier()), bold)
				.text(headline, p.isChosen() ? Palette.TEXT : Palette.MUTED, p.isChosen() ? bold : null)
				.right(right.toString(), Palette.MUTED));
		}
		return super.render(g);
	}

	private static String abbrev(Tier t)
	{
		switch (t)
		{
			case SAFETY:
				return "SAFE";
			case TEAM_BLOCKING:
				return "TEAM";
			case TIME_CRITICAL:
				return "TIME";
			case MAINTENANCE:
				return "MAINT";
			default:
				return "IDLE";
		}
	}

	private static Color colour(Tier t)
	{
		switch (t)
		{
			case SAFETY:
				return Palette.BAD;
			case TEAM_BLOCKING:
				return new Color(240, 140, 60);
			case TIME_CRITICAL:
				return Palette.OK;
			case MAINTENANCE:
				return new Color(95, 155, 255);
			default:
				return Palette.MUTED;
		}
	}
}
