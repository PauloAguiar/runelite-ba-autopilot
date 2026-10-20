package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.RoundLogic;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.runelite.client.util.Text;

/**
 * Prints a recorded round tick by tick: the raw call texts and chat beside what the current code
 * tells the player (and, where it differs, what the live plugin told them).
 *
 * <pre>gradlew replay -Pfile=path/to/recording.jsonl [-Pall]</pre>
 * Without -Pall only ticks where something changed are printed.
 */
public final class ReplayTimeline
{
	private ReplayTimeline()
	{
	}

	public static void main(String[] args) throws Exception
	{
		String file = null;
		boolean all = false;
		for (String a : args)
		{
			if ("--all".equals(a))
			{
				all = true;
			}
			else if (!a.isEmpty())
			{
				file = a;
			}
		}
		if (file == null)
		{
			System.err.println("usage: gradlew replay -Pfile=path/to/recording.jsonl [-Pall]");
			System.exit(2);
		}
		Path path = Paths.get(file);
		if (!Files.exists(path))
		{
			System.err.println("no such file: " + path.toAbsolutePath());
			System.exit(2);
		}

		RecordedRound round = RecordedRound.read(path);
		List<RoundReplay.Frame> frames = RoundReplay.run(round);
		System.out.println(round.name() + " - " + frames.size() + " ticks, strategy " + round.header.strategy);
		System.out.printf("%-6s %-8s %-14s %-16s %-14s %-24s %s%n",
			"tick", "call", "shout(hud)", "listen(hud)", "horn", "listen call", "instruction");

		String last = null;
		for (RoundReplay.Frame f : frames)
		{
			Snapshot s = f.snapshot;
			Role role = s.getRole();
			RoundLogic.Hud hud = RoundReplay.hud(f.raw.hud);
			String shout = role == null ? null : hud.text(role.getShoutComponent());
			String listen = s.getListenText();
			String horn = role == null ? null : hud.text(role.getHornListenComponent());
			String call = s.getCallPeriod() + 1 + (s.getTicksToCallChange() < 0 ? "" : "+" + s.ticksIntoCall());
			String heard = s.getListenCall() == null ? "-"
				: s.getListenCall().name() + "(" + s.getListenSource() + (s.isListenFresh() ? "" : ",old") + ")";
			String step = f.instruction.getStep() + ": " + f.instruction.getHeadline();
			String liveNote = "";
			if (f.raw.live != null && !Objects.equals(f.raw.live.step, f.instruction.getStep() == null ? null : f.instruction.getStep().name()))
			{
				liveNote = "   [live: " + f.raw.live.step + ": " + f.raw.live.headline + "]";
			}

			List<String> events = new ArrayList<>();
			for (Rec.Event e : RoundInvariants.events(f.raw, null))
			{
				String line = describe(e);
				if (line != null)
				{
					events.add(line);
				}
			}

			String key = String.join("|", String.valueOf(shout), String.valueOf(listen), String.valueOf(horn), heard, step, liveNote,
				String.valueOf(s.getCallPeriod()));
			if (all || !events.isEmpty() || !key.equals(last))
			{
				for (String e : events)
				{
					System.out.printf("%-6d   %s%n", f.raw.t, e);
				}
				System.out.printf("%-6d %-8s %-14s %-16s %-14s %-24s %s%s%n", f.raw.t, call, cut(shout, 14), cut(listen, 16),
					cut(horn, 14), cut(heard, 24), step, liveNote);
			}
			last = key;
		}

		RoundInvariants.Report report = RoundInvariants.check(round, frames);
		System.out.println();
		report.notes.forEach(n -> System.out.println("note: " + n));
		report.violations.forEach(v -> System.out.println("VIOLATION: " + v));
		System.out.println(report.violations.isEmpty() ? "invariants hold" : report.violations.size() + " violations");
	}

	private static String describe(Rec.Event e)
	{
		switch (e.k)
		{
			case "chat":
				return "chat " + e.type + (e.name == null ? "" : " " + e.name) + ": " + Text.removeTags(e.text);
			case "menu":
				return "click " + e.option + " " + e.target + (e.selected == null ? "" : " (using " + e.selected + ")");
			case "npcSpawn":
			case "npcDespawn":
			case "death":
				return e.npc == null ? e.k + " " + e.name : e.k + " npc " + e.npc + " (" + e.id + ")";
			case "graphic":
				return "graphic npc " + e.npc + " spotanims " + Arrays.toString(e.spot);
			case "itemSpawn":
				return "item " + e.id + " x" + e.qty + " at " + (e.tile == null ? "?" : e.tile.x + "," + e.tile.y) + " (#" + e.uid + ")";
			case "gameState":
				return "game state " + e.type;
			default:
				return null;
		}
	}

	private static String cut(String s, int n)
	{
		if (s == null)
		{
			return "";
		}
		return s.length() <= n ? s : s.substring(0, n - 1) + "~";
	}
}
