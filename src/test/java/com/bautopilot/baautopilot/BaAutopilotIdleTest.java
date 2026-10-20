package com.bautopilot.baautopilot;

import com.bautopilot.baautopilot.state.BaTracker;
import com.bautopilot.baautopilot.state.Performance;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.tutorial.TutorialSteps;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaAutopilotIdleTest
{
	private static void set(BaAutopilotPlugin plugin, String name, Object value) throws Exception
	{
		Field field = BaAutopilotPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	@Test public void idleGameAndClientTicksNeedNoPlannerRendererCameraOrUiDependencies() throws Exception
	{
		BaAutopilotPlugin plugin = new BaAutopilotPlugin();
		Snapshot inactive = Snapshot.builder().build();
		set(plugin, "tracker", new BaTracker()
		{
			@Override public Snapshot snapshot() { return inactive; }
			@Override public Performance getPerformance() { throw new AssertionError("Repeated idle bookkeeping"); }
		});
		// Every other injected service remains null: any accidental hot-path call fails this test.
		for (int i = 0; i < 1000; i++)
		{
			plugin.onGameTick(null);
			plugin.onClientTick(null);
		}
		assertNull(plugin.getInstruction());
		assertNull(plugin.getSnapshot());
		assertFalse(plugin.isTutorialShown());
	}

	@Test public void aDisabledTutorialCannotTickEvenIfItsOldShownFlagRemains() throws Exception
	{
		BaAutopilotPlugin plugin = new BaAutopilotPlugin();
		set(plugin, "tutorialShown", true);
		plugin.onClientTick(null);
	}

	@Test public void enabledTutorialWaitingForItsLobbySceneDoesNotAnimate() throws Exception
	{
		BaAutopilotPlugin plugin = new BaAutopilotPlugin();
		set(plugin, "tutorialOn", true);
		plugin.onClientTick(null);
	}

	private static class TutorialTickReached extends RuntimeException { }

	@Test(expected = TutorialTickReached.class)
	public void enabledAndShownTutorialTicksWithNoArenaSnapshot() throws Exception
	{
		BaAutopilotPlugin plugin = new BaAutopilotPlugin();
		set(plugin, "tutorialOn", true);
		set(plugin, "tutorialShown", true);
		set(plugin, "tutorialSteps", new TutorialSteps()
		{
			@Override public void tick() { throw new TutorialTickReached(); }
		});
		assertNull(plugin.getSnapshot());
		plugin.onClientTick(null);
	}
}
