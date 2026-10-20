package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenderContinuousReplayTest
{
	@Test public void oldSettingsUseContinuousModeAndExplicitAdvancedSettingsSurvive()
	{
		assertFalse(RecordedRound.settings(new JsonObject()).isDefenderMultiKill());
		JsonObject explicit = new JsonObject();
		explicit.addProperty("defenderMultiKill", true);
		assertTrue(RecordedRound.settings(explicit).isDefenderMultiKill());
	}

	@Test public void allDefenderCapturesUseContinuousUpkeepWithoutHoldingOrOffSpotRepairClicks() throws Exception
	{
		int ticks = 0, positioning = 0, repairs = 0;
		try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/test/resources/recordings")))
		{
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".jsonl")).sorted()::iterator)
			{
				Planner planner = new Planner();
				for (RoundReplay.Frame frame : RoundReplay.run(RecordedRound.read(file),
					(s, settings) -> planner.plan(s, settings.toBuilder().defenderMultiKill(false).build())))
				{
					if (!frame.snapshot.isInArena() || frame.snapshot.getRole() != Role.DEFENDER || frame.snapshot.isQueenAlive()) continue;
					ticks++;
					Instruction ins = frame.instruction;
					Step step = ins.getStep() == Step.CALL && ins.getThen() != null ? ins.getThen() : ins.getStep();
					String context = file.getFileName() + " tick " + frame.snapshot.getTick();
					assertNotEquals(context, Step.DEF_HOLD, step);
					assertNotEquals(context, Step.DEF_STEP, step);
					if (step == Step.DEF_REPAIR_POSITION)
					{
						positioning++;
						assertEquals(context, ArenaTiles.trapFood(frame.snapshot.getWave()), ins.getTarget().getTile());
					}
					if (step == Step.DEF_REPAIR)
					{
						repairs++;
						assertEquals(context, ArenaTiles.trapFood(frame.snapshot.getWave()), frame.snapshot.getPlayerTile());
					}
				}
			}
		}
		assertTrue(ticks > 4000);
		assertTrue(positioning > 0);
		assertTrue(repairs > 0);
	}
}
