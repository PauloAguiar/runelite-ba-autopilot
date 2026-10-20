package com.galesong.baautopilot.ui;

import com.galesong.baautopilot.ui.kit.components.CollapsibleSection;
import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.Snapshot;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaAutopilotPanelTest
{
	@Test public void defenderMultikillsAreOffInsideTheCollapsedAdvancedOptions() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BaAutopilotPanel panel = new BaAutopilotPanel();
			javax.swing.AbstractButton multi = (javax.swing.AbstractButton) named(panel, "defenderMultiKill");
			assertNotNull(multi);
			assertFalse(multi.isSelected());
			CollapsibleSection advanced = (CollapsibleSection) named(panel, "DEFENDER.advanced");
			assertFalse(advanced.getComponent(1).isVisible());
			assertTrue(SwingUtilities.isDescendingFrom(multi, advanced));
			List<String> writes = new ArrayList<>();
			panel.setOnOption((key, value) -> writes.add(key + "=" + value));
			advanced.setExpanded(true);
			multi.doClick();
			assertEquals(List.of("defenderMultiKill=true"), writes);
		});
	}
	@Test public void configurationRefreshDoesNotWriteBackButEditingTheEggCountDoes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BaAutopilotPanel panel = new BaAutopilotPanel();
			List<String> writes = new ArrayList<>();
			panel.setOnOption((key, value) -> writes.add(key + "=" + value));
			panel.setOptions(new BaAutopilotConfig() {
				public int queenEggBatch() { return 3; }
				public int queenEggBuffer() { return 0; }
				public int collectorLoadAt() { return 15; }
				public boolean queenSingleLoads() { return false; }
			});
			assertTrue(writes.toString(), writes.isEmpty());
			assertEquals(3, ((JSpinner) named(panel, "queenEggBatch")).getValue());
			assertEquals(0, ((JSpinner) named(panel, "queenEggBuffer")).getValue());
			assertEquals(15, ((JSpinner) named(panel, "collectorLoadAt")).getValue());
			((JSpinner) named(panel, "queenEggBatch")).setValue(6);
			((JSpinner) named(panel, "queenEggBuffer")).setValue(2);
			assertEquals(List.of("queenEggBatch=6", "queenEggBuffer=2"), writes);
		});
	}

	@Test public void advancedOptionsStartCollapsedAndRefreshKeepsTheUsersExpansion() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BaAutopilotPanel panel = new BaAutopilotPanel();
			for (Role role : Role.values())
			{
				assertNotNull(named(panel, role.name() + ".settings"));
				assertFalse(((CollapsibleSection) named(panel, role.name() + ".advanced")).getComponent(1).isVisible());
			}
			assertFalse(((CollapsibleSection) named(panel, "general.advanced")).getComponent(1).isVisible());
			CollapsibleSection collector = (CollapsibleSection) named(panel, "COLLECTOR.advanced");
			collector.setExpanded(true);
			panel.setOptions(new BaAutopilotConfig() { });
			panel.updateProgress(Snapshot.builder().build());
			assertTrue(collector.getComponent(1).isVisible());
		});
	}

	@Test public void progressUsesObservedLevelsAndHonourPointsAndClearsMissingData() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BaAutopilotPanel panel = new BaAutopilotPanel();
			panel.updateProgress(Snapshot.builder().roleLevels(Map.of(Role.HEALER, 3)).rolePoints(Map.of(Role.HEALER, 1200)).build());
			assertEquals("Level 3 · 1,200 Honour points", ((JLabel) named(panel, "HEALER.progress")).getText());
			assertEquals("Level — · — Honour points", ((JLabel) named(panel, "COLLECTOR.progress")).getText());
			panel.updateProgress(null);
			assertEquals("Level — · — Honour points", ((JLabel) named(panel, "HEALER.progress")).getText());
		});
	}

	private static Component named(Container root, String name)
	{
		for (Component child : root.getComponents())
		{
			if (name.equals(child.getName())) return child;
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** Paint the real Swing view without opening or restarting the game client. */
	public static void main(String[] args) throws Exception
	{
		net.runelite.client.ui.laf.RuneLiteLAF.setup();
		Path directory = Path.of("build/reports/role-settings");
		Files.createDirectories(directory);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BaAutopilotPanel panel = new BaAutopilotPanel();
				panel.updateProgress(Snapshot.builder()
					.roleLevels(Map.of(Role.ATTACKER, 3, Role.DEFENDER, 4, Role.COLLECTOR, 2, Role.HEALER, 3))
					.rolePoints(Map.of(Role.ATTACKER, 120, Role.DEFENDER, 340, Role.COLLECTOR, 85, Role.HEALER, 260)).build());
				paint(panel, directory.resolve("collapsed.png"), 225, 780);
				((CollapsibleSection) named(panel, "COLLECTOR.advanced")).setExpanded(true);
				paint(panel, directory.resolve("collector-advanced.png"), 225, 920);
			}
			catch (Exception e) { throw new RuntimeException(e); }
		});
	}

	private static void paint(BaAutopilotPanel panel, Path file, int width, int height) throws Exception
	{
		invalidate(panel);
		panel.setSize(new Dimension(width, height));
		layout(panel);
		layout(panel);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.printAll(graphics);
		graphics.dispose();
		ImageIO.write(image, "png", file.toFile());
	}

	private static void invalidate(Container container)
	{
		for (Component child : container.getComponents()) if (child instanceof Container) invalidate((Container) child);
		container.invalidate();
	}

	private static void layout(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
	}
}
