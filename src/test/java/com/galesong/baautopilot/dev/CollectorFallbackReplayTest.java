package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.plan.Step;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorFallbackReplayTest
{
	private static List<RoundReplay.Frame> wave(String name) throws IOException
	{
		return RoundReplay.run(RecordedRound.read(Path.of("src/test/resources/recordings", name)));
	}

	private static RoundReplay.Frame at(List<RoundReplay.Frame> frames, int tick)
	{
		return frames.stream().filter(f -> f.raw.t == tick).findFirst().orElseThrow();
	}

	@Test public void latestWaveFourCollectsVisibleRedsAndGreensInsteadOfWaiting() throws IOException
	{
		List<RoundReplay.Frame> frames = wave("2026-10-19_21-31-34-wave4.jsonl");
		for (int tick : new int[]{1285, 1301})
		{
			RoundReplay.Frame frame = at(frames, tick);
			assertEquals("COL_FOLLOW", frame.raw.live.step);
			assertEquals(Step.COL_COLLECT, frame.instruction.getStep());
			assertTrue(frame.instruction.getPriorities().stream().filter(p -> "col.follow".equals(p.getTask()))
				.allMatch(p -> p.getScore() < frame.instruction.getScore()));
		}
	}

	@Test public void latestWaveNineCollectsRedsAndBlueStacksWithoutCentreDetours() throws IOException
	{
		List<RoundReplay.Frame> frames = wave("2026-10-19_21-43-46-wave9.jsonl");
		for (int tick : new int[]{2482, 2507, 2555})
		{
			RoundReplay.Frame frame = at(frames, tick);
			assertEquals("COL_FOLLOW", frame.raw.live.step);
			assertEquals(Step.COL_COLLECT, frame.instruction.getStep());
		}
		assertTrue(at(frames, 2507).instruction.getPickupCount() >= 2);
		for (RoundReplay.Frame frame : frames)
		{
			assertNotEquals("col.scan", frame.instruction.getTask());
			assertTrue(frame.instruction.getPriorities().stream().noneMatch(p -> "col.scan".equals(p.getTask())));
		}
	}
}
