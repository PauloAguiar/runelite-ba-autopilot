package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.plan.Step;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HealerReserveReplayTest
{
	private static List<RoundReplay.Frame> wave(String name) throws IOException
	{
		return RoundReplay.run(RecordedRound.read(Path.of("src/test/resources/recordings", name)));
	}

	private static RoundReplay.Frame at(List<RoundReplay.Frame> frames, int tick)
	{
		return frames.stream().filter(f -> f.raw.t == tick).findFirst().orElseThrow();
	}

	@Test
	public void latestQueenWaveFinishesTheWoundedInitialHealerWhileReservesWait() throws IOException
	{
		List<RoundReplay.Frame> frames = wave("2026-10-18_20-38-20-wave10.jsonl");
		for (int tick : new int[]{3204, 3230, 3235})
		{
			RoundReplay.Frame frame = at(frames, tick);
			assertEquals("HEAL_SUPPORT", frame.raw.live.step);
			assertEquals(Step.HEAL_FEED, frame.instruction.getStep());
			assertEquals("heal.reserve", frame.instruction.getTask());
			assertEquals(0, frame.snapshot.getHealersKilled());
			assertTrue(frame.instruction.getDetail().contains("3 reserves"));
			assertTrue(frame.snapshot.alive(PenanceType.HEALER).stream().anyMatch(h -> h.getOrder() == 1
				&& h.getNpcIndex() == frame.instruction.getTarget().getId() && h.nextPoisonHit(tick) == 4));
		}
		// One tick before the call change, the capture's attempted old-food click is unsafe.
		assertNotEquals("heal.reserve", at(frames, 3211).instruction.getTask());
	}

	@Test
	public void latestWaveFiveKeepsOneFinishTargetAsItsHealthFalls() throws IOException
	{
		List<RoundReplay.Frame> frames = wave("2026-10-18_20-28-21-wave5.jsonl");
		int id = at(frames, 2199).instruction.getTarget().getId();
		for (int tick : new int[]{2199, 2200, 2201, 2202})
		{
			RoundReplay.Frame frame = at(frames, tick);
			assertEquals("HEAL_SUPPORT", frame.raw.live.step);
			assertEquals("heal.reserve", frame.instruction.getTask());
			assertEquals(id, frame.instruction.getTarget().getId());
			assertTrue(frame.snapshot.alive(PenanceType.HEALER).stream().anyMatch(h -> h.getOrder() == 2
				&& h.getNpcIndex() == id));
		}
	}
}
