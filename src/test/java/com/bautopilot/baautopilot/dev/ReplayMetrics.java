package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;

/**
 * Stability and timeliness of the guidance over one replayed wave: how often the instruction and
 * its target change (and flip back), how many ticks into each call period the call is first
 * prompted, and for the defender how escaping runners were answered. The player's moves come from
 * the recording, so these measure the planner on the path actually walked.
 *
 * <pre>gradlew metrics [-Pdir=src/test/resources/recordings]</pre>
 */
public final class ReplayMetrics
{
	/** A change back to what was shown before, after the other one lasted at most this long. */
	static final int FLIP_TICKS = 5;
	/** Same rule as the defender's relure: a runner this far south of the trap food is heading out. */
	static final int ESCAPING_SOUTH = 2;
	static final int STACK_RADIUS = 2;
	/** Same rule as RoundLogic.escaped: last seen this far south of the safety bait. */
	static final int ESCAPE_SOUTH_OF_SAFETY = 5;

	/** One wave's numbers. */
	public static final class Wave
	{
		public Role role;
		public int wave;
		public int ticks;
		/** Work-step changes (calls left out: a call is a horn click on the way, not a new task). */
		public int stepChanges;
		public int stepFlips;
		public int targetChanges;
		public int targetFlips;
		/** Ticks into each call period of the first call prompt, -1 when never prompted. */
		public final TreeMap<Integer, Integer> callPrompt = new TreeMap<>();
		/** Defender: runners that left through the exit, escape episodes, relure prompts, latency. */
		public int escapes;
		public int escaping;
		public int relured;
		public int relureLatency;

		public String line(String name)
		{
			String calls = callPrompt.entrySet().stream()
				.map(e -> (e.getKey() + 1) + ":" + (e.getValue() < 0 ? "-" : "+" + e.getValue()))
				.collect(Collectors.joining(" "));
			String def = role != Role.DEFENDER ? "" : String.format(" | runners heading out %d, relured %d (avg %.1f ticks to prompt), escaped %d",
				escaping, relured, relured == 0 ? 0.0 : relureLatency / (double) relured, escapes);
			return String.format("%-28s %-9s w%-2d %4d ticks | steps %3d (flips %2d) | targets %3d (flips %2d) | call prompt %s%s",
				name, role == null ? "-" : role.name().toLowerCase(), wave, ticks, stepChanges, stepFlips, targetChanges, targetFlips,
				calls, def);
		}
	}

	private ReplayMetrics()
	{
	}

	public static Wave measure(List<RoundReplay.Frame> frames)
	{
		Wave w = new Wave();
		List<Object> steps = new ArrayList<>();
		List<Object> targets = new ArrayList<>();
		int period = -1;
		List<int[]> heading = new ArrayList<>(); // {npcIndex, firstTick}
		java.util.Map<Integer, PenanceView> lastSeen = new java.util.HashMap<>();
		for (RoundReplay.Frame f : frames)
		{
			Snapshot s = f.snapshot;
			Instruction ins = f.instruction;
			if (!s.isInArena() || s.getRole() == null)
			{
				continue;
			}
			w.ticks++;
			w.role = s.getRole();
			w.wave = Math.max(w.wave, s.getWave());
			boolean prompting = ins.getStep() == Step.CALL || ins.getAlsoCall() != null;
			if (ins.getStep() != Step.CALL)
			{
				steps.add(ins.getStep());
			}
			targets.add(identity(ins.getTarget()));

			if (s.getTicksToCallChange() >= 0 && s.getCallPeriod() != period)
			{
				period = s.getCallPeriod();
				w.callPrompt.put(period, -1);
			}
			if (prompting && period >= 0 && w.callPrompt.get(period) < 0)
			{
				w.callPrompt.put(period, Math.max(0, s.ticksIntoCall()));
			}

			if (s.getRole() == Role.DEFENDER && s.getWave() > 0)
			{
				defender(w, f, heading, lastSeen);
			}
		}
		count(steps, true, w);
		count(targets, false, w);
		return w;
	}

	private static void defender(Wave w, RoundReplay.Frame f, List<int[]> heading, java.util.Map<Integer, PenanceView> lastSeen)
	{
		Snapshot s = f.snapshot;
		for (Rec.Event e : RoundInvariants.events(f.raw, "npcDespawn"))
		{
			PenanceView last = e.npc == null ? null : lastSeen.get(e.npc);
			if (PenanceType.forNpcId(e.id) == PenanceType.RUNNER && last != null && last.getTile() != null && !last.isDying()
				&& last.getTile().getY() <= ArenaTiles.safety(s.getWave()).getY() - ESCAPE_SOUTH_OF_SAFETY)
			{
				w.escapes++;
			}
		}
		lastSeen.clear();
		s.getPenance().forEach(p -> lastSeen.put(p.getNpcIndex(), p));

		WorldPoint trap = ArenaTiles.trapFood(s.getWave());
		boolean relure = f.instruction.getStep() == Step.DEF_RELURE;
		for (PenanceView p : s.penance(PenanceType.RUNNER))
		{
			boolean out = p.getTile() != null && p.getTile().getY() <= trap.getY() - ESCAPING_SOUTH
				&& ArenaTiles.distance(p.getTile(), trap) > STACK_RADIUS;
			int[] episode = heading.stream().filter(h -> h[0] == p.getNpcIndex()).findFirst().orElse(null);
			if (out && episode == null)
			{
				heading.add(new int[]{p.getNpcIndex(), f.raw.t, 0});
				w.escaping++;
				episode = heading.get(heading.size() - 1);
			}
			else if (!out && episode != null)
			{
				heading.remove(episode);
				episode = null;
			}
			if (episode != null && relure && episode[2] == 0)
			{
				episode[2] = 1;
				w.relured++;
				w.relureLatency += f.raw.t - episode[1];
			}
		}
	}

	/** Changes, and changes straight back after a short detour (A, B for at most FLIP_TICKS, A). */
	private static void count(List<Object> seq, boolean steps, Wave w)
	{
		int changes = 0;
		int flips = 0;
		Object before = null;
		Object current = null;
		int heldSince = 0;
		for (int i = 0; i < seq.size(); i++)
		{
			Object now = seq.get(i);
			if (i == 0)
			{
				current = now;
				continue;
			}
			if (!Objects.equals(now, current))
			{
				changes++;
				if (Objects.equals(now, before) && i - heldSince <= FLIP_TICKS)
				{
					flips++;
				}
				before = current;
				current = now;
				heldSince = i;
			}
		}
		if (steps)
		{
			w.stepChanges = changes;
			w.stepFlips = flips;
		}
		else
		{
			w.targetChanges = changes;
			w.targetFlips = flips;
		}
	}

	/** NPC targets by index (they move), the rest by kind and tile. */
	private static Object identity(Target t)
	{
		if (t.getKind() == Target.Kind.NPC)
		{
			return "npc" + t.getId();
		}
		if (t.getKind() == Target.Kind.PLAYER)
		{
			return "player" + t.getLabel();
		}
		return t.getKind() + "@" + (t.getTile() == null ? "-" : t.getTile().getX() + "," + t.getTile().getY());
	}

	public static void main(String[] args) throws Exception
	{
		Path dir = Paths.get(args.length > 0 && !args[0].isEmpty() ? args[0] : RecordedRoundsTest.RECORDINGS.toString());
		List<Path> files;
		try (Stream<Path> s = Files.list(dir))
		{
			files = s.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		// -Pdump=<dir>: also one line per in-arena tick per wave, to diff two versions of the planner
		Path dump = args.length > 1 && !args[1].isEmpty() ? Paths.get(args[1]) : null;
		if (dump != null)
		{
			Files.createDirectories(dump);
		}
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> frames = RoundReplay.run(round);
			System.out.println(measure(frames).line(round.name()));
			if (dump != null)
			{
				List<String> lines = new ArrayList<>();
				for (RoundReplay.Frame f : frames)
				{
					if (f.snapshot.isInArena() && f.snapshot.getRole() != null)
					{
						Instruction ins = f.instruction;
						WorldPoint me = f.snapshot.getPlayerTile();
						lines.add(f.raw.t + " w" + f.snapshot.getWaveTick() + (me == null ? "" : " @" + me.getX() + "," + me.getY())
							+ " " + ins.getStep() + " | " + ins.getHeadline()
							+ " | " + identity(ins.getTarget()) + (f.snapshot.getRole() != Role.DEFENDER ? ""
							: " | runners " + f.snapshot.alive(PenanceType.RUNNER).stream()
								.map(p -> p.getTile() == null ? "?" : p.getTile().getX() + "," + p.getTile().getY() + (p.isUnseen() ? "u" : ""))
								.collect(Collectors.joining(" "))));
					}
				}
				Files.write(dump.resolve(round.name() + ".txt"), lines);
			}
		}
	}
}
