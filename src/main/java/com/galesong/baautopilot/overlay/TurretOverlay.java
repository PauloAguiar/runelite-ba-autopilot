package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.BaAutopilotPlugin;
import com.galesong.baautopilot.plan.ActionKind;
import com.galesong.baautopilot.plan.TurretChoice;
import com.galesong.baautopilot.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** In the egg launcher's aiming window: outline the button to press, cross out the stun buttons. */
public class TurretOverlay extends Overlay
{
	private final Client client;
	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;

	@Inject
	TurretOverlay(Client client, BaAutopilotPlugin plugin, BaAutopilotConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Snapshot s = plugin.getSnapshot();
		if (!config.highlightTarget() || s == null || !s.isInArena())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (int stun : TurretChoice.STUN)
		{
			Rectangle r = bounds(stun);
			if (r != null)
			{
				g.setColor(Palette.fill(Palette.BAD, 170));
				g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(r.x + 4, r.y + 4, r.x + r.width - 4, r.y + r.height - 4);
				g.drawLine(r.x + r.width - 4, r.y + 4, r.x + 4, r.y + r.height - 4);
			}
		}
		Rectangle r = bounds(TurretChoice.pick(s));
		if (r != null)
		{
			Color c = ActionKind.FIGHT.getColor();
			g.setColor(Palette.fill(c, 60));
			g.fillRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 8, 8);
			g.setColor(c);
			g.setStroke(new BasicStroke(3f));
			g.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 8, 8);
		}
		return null;
	}

	private Rectangle bounds(int component)
	{
		if (component == TurretChoice.NONE)
		{
			return null;
		}
		Widget w = client.getWidget(component);
		if (w == null || w.isHidden())
		{
			return null;
		}
		Rectangle r = w.getBounds();
		return r == null || r.width <= 0 ? null : r;
	}
}
