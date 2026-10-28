package com.bautopilot.baautopilot;

import com.bautopilot.baautopilot.state.BaTracker;
import com.bautopilot.baautopilot.state.Performance;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.tutorial.TutorialSteps;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.ui.BaAutopilotPanel;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaAutopilotIdleTest
{
	private static class ProgressPanel extends BaAutopilotPanel
	{
		int updates;
		Snapshot progress;
		@Override public void updateProgress(Snapshot snapshot)
		{
			updates++;
			progress = snapshot;
			super.updateProgress(snapshot);
		}
	}

	@Test public void sidebarProgressUpdatesOutsideTheArenaWithoutRepeatedSwingUpdates() throws Exception
	{
		BaAutopilotPlugin plugin = new BaAutopilotPlugin();
		Snapshot inactive = Snapshot.builder().build();
		Snapshot[] progress = {Snapshot.builder().roleLevels(Map.of(Role.HEALER, 3)).rolePoints(Map.of(Role.HEALER, 1200)).build()};
		set(plugin, "tracker", new BaTracker()
		{
			@Override public Snapshot snapshot() { return inactive; }
			@Override public Snapshot progress() { return progress[0]; }
		});
		ProgressPanel[] panel = new ProgressPanel[1];
		SwingUtilities.invokeAndWait(() -> { panel[0] = new ProgressPanel(); panel[0].updates = 0; });
		set(plugin, "panel", panel[0]);
		for (int i = 0; i < 1000; i++) plugin.onGameTick(null);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(1, panel[0].updates);
		assertSame(progress[0], panel[0].progress);
		assertNull(plugin.getSnapshot());
		assertNull(plugin.getInstruction());
		progress[0] = progress[0].toBuilder().rolePoints(Map.of(Role.HEALER, 1000)).build();
		plugin.onGameTick(null);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(2, panel[0].updates);
		assertSame(progress[0], panel[0].progress);
		GameStateChanged logout = new GameStateChanged();
		logout.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(logout);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(3, panel[0].updates);
		assertNull(panel[0].progress);
	}
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
