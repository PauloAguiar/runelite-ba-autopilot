package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.state.CallClock;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Settings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The replay harness itself, on a synthetic healer wave written in the recorder's format: it must
 * read back, replay, pass the invariants, and flag a broken clock and an unreadable horn line.
 * This is not evidence about the game; real rounds go in src/test/resources/recordings.
 */
public class ReplayHarnessTest
{
	/** HUD flips below are at 155/205, so the wave message sits START_OFFSET + 50 ticks before 155. */
	private static final int WAVE_MSG = 155 - 50 - CallClock.START_OFFSET;
	private static final int HEALER_NPC = 50;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void syntheticHealerWaveReplaysClean() throws IOException
	{
		RecordedRound round = roundTrip(healerWave(WAVE_MSG, null));
		assertEquals(5, round.header.settings.getCollectorLoadAt());

		List<RoundReplay.Frame> frames = RoundReplay.run(round);
		RoundInvariants.Report report = RoundInvariants.check(round, frames);
		assertEquals(report.violations.toString(), 0, report.violations.size());

		RoundReplay.Frame afterChat = frame(frames, 111);
		assertEquals(Role.HEALER, afterChat.snapshot.getRole());
		assertEquals(1, afterChat.snapshot.getWave());
		assertEquals(BaCall.POISON_WORMS, afterChat.snapshot.getListenCall());
		assertTrue(afterChat.snapshot.isListenFresh());

		// Own food is counted once; neither poison animation establishes another feed.
		assertEquals(1, frame(frames, 120).snapshot.getPenance().get(0).getFoods());
		assertEquals(1, frame(frames, 141).snapshot.getPenance().get(0).getFoods());

		// Second call: stale until the defender calls meat
		assertEquals(1, frame(frames, 157).snapshot.getCallPeriod());
		assertTrue(!frame(frames, 157).snapshot.isListenFresh());
		assertEquals(BaCall.POISON_MEAT, frame(frames, 161).snapshot.getListenCall());
		assertTrue(report.notes.toString(), report.notes.stream().anyMatch(n -> n.contains("call 1: +0")));
	}

	@Test
	public void poisonAnimationBeforeOurClickDoesNotCountAsFood() throws IOException
	{
		// An uninjured healer animates at 114; our food is only confirmed at 116.
		RecordedRound round = roundTrip(healerWave(WAVE_MSG, ticks -> at(ticks, 114).ev = events(graphic(114))));
		List<RoundReplay.Frame> frames = RoundReplay.run(round);
		assertEquals(0, frame(frames, 114).snapshot.getPenance().get(0).nextPoisonHit(114));
		assertEquals(1, frame(frames, 120).snapshot.getPenance().get(0).getFoods());
		RoundInvariants.Report report = RoundInvariants.check(round, frames);
		assertTrue(report.violations.toString(), report.violations.isEmpty());
	}

	@Test
	public void flagsACallJumpTheClockCannotFollow()
	{
		// The second flip comes 15 ticks early: beyond the clock's 10-tick re-anchor window
		Role me = Role.HEALER;
		List<RoundReplay.Frame> frames = RoundReplay.run(roundTripUnchecked(healerWave(WAVE_MSG, ticks ->
		{
			for (int t = 190; t < 205; t++)
			{
				at(ticks, t).hud.put(me.getShoutComponent(), "Crackers");
			}
		})));
		RoundInvariants.Report report = RoundInvariants.check(null, frames);
		assertTrue(report.violations.toString(), report.violations.stream().anyMatch(v -> v.startsWith("clock:")));
	}

	@Test
	public void flagsAnUnreadableHornLine()
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(roundTripUnchecked(healerWave(WAVE_MSG,
			ticks -> at(ticks, 130).ev = events(chat(130, "PUBLICCHAT", "Player2", "Healer: Poison cake!")))));
		RoundInvariants.Report report = RoundInvariants.check(null, frames);
		assertTrue(report.violations.toString(), report.violations.stream().anyMatch(v -> v.contains("not understood")));
	}

	// ------------------------------------------------------------------ synthetic wave

	/** Ticks 90-229 of a wave-1 healer in the arena; {@code tweak} edits the lines before writing. */
	private static List<Object> healerWave(int waveMsg, Consumer<List<Rec.Tick>> tweak)
	{
		Role me = Role.HEALER;
		WorldPoint spot = WorldPoint.fromRegion(7509, 30, 30, 0);
		List<Rec.Tick> ticks = new ArrayList<>();
		int worms = 0;
		for (int t = 90; t < 230; t++)
		{
			Rec.Tick k = new Rec.Tick();
			k.t = t;
			k.instance = true;
			k.tile = new Rec.Pos(spot.getX(), spot.getY(), 0);
			k.hp = 99;
			k.maxHp = 99;
			k.free = 28;
			k.hud = new TreeMap<>();
			if (t >= 101)
			{
				k.hud.put(WidgetUtil.packComponentId(me.getHudGroup(), 0), "");
				k.hud.put(WidgetUtil.packComponentId(me.getHudGroup(), 5), "Wave 1");
				k.hud.put(me.getShoutComponent(), t < 105 ? "-" : t < 155 ? "Tofu" : t < 205 ? "Worms" : "Crackers");
				k.hud.put(me.getListenComponent(), t < 110 ? "-" : t < 160 ? "Pois. Worms" : "Pois. Meat");
			}
			if (t == 112)
			{
				worms = 3;
			}
			if (t == 116)
			{
				worms = 2;
			}
			k.inv = new TreeMap<>();
			if (worms > 0)
			{
				k.inv.put(ItemID.BARBASSAULT_POISION_02, worms);
			}
			if (t >= 106)
			{
				Rec.Npc n = new Rec.Npc();
				n.i = HEALER_NPC;
				n.id = NpcID.BARBASSAULT_PEN_HEALER_LV1;
				n.tile = new Rec.Pos(spot.getX() + 3, spot.getY(), 0);
				n.hr = -1;
				n.hs = -1;
				k.npcs = new ArrayList<>(List.of(n));
			}
			ticks.add(k);
		}
		at(ticks, waveMsg).ev = events(chat(waveMsg, "GAMEMESSAGE", null, "<col=ef1020>---- Wave: 1 ----</col>"));
		Rec.Event spawn = new Rec.Event();
		spawn.t = 106;
		spawn.k = "npcSpawn";
		spawn.npc = HEALER_NPC;
		spawn.id = NpcID.BARBASSAULT_PEN_HEALER_LV1;
		at(ticks, 106).ev = events(spawn);
		at(ticks, 110).ev = events(chat(110, "PUBLICCHAT", "Player2", "Healer: Poison worms!"));
		Rec.Event use = new Rec.Event();
		use.t = 115;
		use.k = "menu";
		use.type = "WIDGET_TARGET_ON_NPC";
		use.option = "Use";
		use.target = "Poisoned worms -> Penance Healer";
		use.npc = HEALER_NPC;
		use.id = NpcID.BARBASSAULT_PEN_HEALER_LV1;
		use.selected = ItemID.BARBASSAULT_POISION_02;
		at(ticks, 115).ev = events(use);
		at(ticks, 117).ev = events(graphic(117));
		at(ticks, 140).ev = events(graphic(140));
		at(ticks, 160).ev = events(chat(160, "PUBLICCHAT", "Player2", "Healer: Poison meat!"));
		if (tweak != null)
		{
			tweak.accept(ticks);
		}

		Rec.Header h = new Rec.Header();
		h.started = "synthetic";
		h.settings = Settings.builder().build();
		List<Object> lines = new ArrayList<>();
		lines.add(h);
		lines.addAll(ticks);
		return lines;
	}

	private static Rec.Tick at(List<Rec.Tick> ticks, int t)
	{
		return ticks.get(t - 90);
	}

	private static Rec.Event chat(int t, String type, String name, String text)
	{
		Rec.Event e = new Rec.Event();
		e.t = t;
		e.k = "chat";
		e.type = type;
		e.name = name;
		e.text = text;
		return e;
	}

	private static Rec.Event graphic(int t)
	{
		Rec.Event e = new Rec.Event();
		e.t = t;
		e.k = "graphic";
		e.npc = HEALER_NPC;
		e.id = NpcID.BARBASSAULT_PEN_HEALER_LV1;
		e.spot = new int[]{863};
		return e;
	}

	private static List<Rec.Event> events(Rec.Event... e)
	{
		return new ArrayList<>(List.of(e));
	}

	private static RoundReplay.Frame frame(List<RoundReplay.Frame> frames, int tick)
	{
		return frames.stream().filter(f -> f.raw.t == tick).findFirst().orElseThrow(AssertionError::new);
	}

	private RecordedRound roundTrip(List<Object> lines) throws IOException
	{
		Path file = tmp.newFile("synthetic.jsonl").toPath();
		try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
		{
			for (Object line : lines)
			{
				w.write(Rec.GSON.toJson(line));
				w.newLine();
			}
		}
		return RecordedRound.read(file);
	}

	private RecordedRound roundTripUnchecked(List<Object> lines)
	{
		try
		{
			return roundTrip(lines);
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}
}
