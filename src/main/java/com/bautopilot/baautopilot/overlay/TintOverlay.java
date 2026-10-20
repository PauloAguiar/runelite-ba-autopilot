package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.TintMode;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Tints the game view red only while the primary instruction is an owed horn call.
 * Drawn under the interfaces, so the inventory itself stays clear.
 */
public class TintOverlay extends Overlay
{
	private static final Color RED = new Color(220, 30, 30);
	private static final int EDGE = 70;

	private final Client client;
	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;

	@Inject
	TintOverlay(Client client, BaAutopilotPlugin plugin, BaAutopilotConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		TintMode mode = config.inventoryTint();
		Instruction ins = plugin.getInstruction();
		Snapshot s = plugin.getSnapshot();
		if (mode == TintMode.OFF || ins == null || s == null || !s.isInArena() || ins.getStep() != Step.CALL)
		{
			return null;
		}
		int x = client.getViewportXOffset();
		int y = client.getViewportYOffset();
		int w = client.getViewportWidth();
		int h = client.getViewportHeight();
		int alpha = Math.max(0, Math.min(255, config.tintStrength() * 255 / 100));
		Color solid = Palette.fill(RED, alpha);
		if (mode == TintMode.FULL)
		{
			g.setColor(solid);
			g.fillRect(x, y, w, h);
			return null;
		}
		Color clear = Palette.fill(RED, 0);
		Paint old = g.getPaint();
		int e = Math.min(EDGE, Math.min(w, h) / 4);
		g.setPaint(new GradientPaint(x, 0, solid, x + e, 0, clear));
		g.fillRect(x, y, e, h);
		g.setPaint(new GradientPaint(x + w, 0, solid, x + w - e, 0, clear));
		g.fillRect(x + w - e, y, e, h);
		g.setPaint(new GradientPaint(0, y, solid, 0, y + e, clear));
		g.fillRect(x, y, w, e);
		g.setPaint(new GradientPaint(0, y + h, solid, 0, y + h - e, clear));
		g.fillRect(x, y + h - e, w, e);
		g.setPaint(old);
		return null;
	}
}
