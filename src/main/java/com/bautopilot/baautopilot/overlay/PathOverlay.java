package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The walking route to the current target: a line through tile centres in the action's colour. */
public class PathOverlay extends Overlay
{
	private final Client client;
	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;
	private final RouteFinder routes;

	@Inject
	PathOverlay(Client client, BaAutopilotPlugin plugin, BaAutopilotConfig config, RouteFinder routes)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.routes = routes;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Instruction ins = plugin.getInstruction();
		List<LocalPoint> route = routes.route();
		if (!config.showPath() || ins == null || route.size() < 2)
		{
			return null;
		}
		Color colour = Palette.action(ins, ins.getStep() == Step.CALL ? config.urgentColor() : ins.getStep().getKind().getColor());
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int plane = client.getTopLevelWorldView().getPlane();

		// The line itself, with a dark under-stroke so it reads on any floor
		Path2D line = new Path2D.Double();
		boolean started = false;
		for (LocalPoint lp : route)
		{
			Point p = Perspective.localToCanvas(client, lp, plane);
			if (p == null)
			{
				started = false;
				continue;
			}
			if (!started)
			{
				line.moveTo(p.getX(), p.getY());
				started = true;
			}
			else
			{
				line.lineTo(p.getX(), p.getY());
			}
		}
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 120));
		g.draw(line);
		g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(colour);
		g.draw(line);
		return null;
	}
}
