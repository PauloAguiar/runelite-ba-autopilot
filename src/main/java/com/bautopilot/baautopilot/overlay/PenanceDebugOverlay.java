package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.state.BaTracker;
import com.bautopilot.baautopilot.state.PenanceDiagnostics;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Debug-only world labels and fixed runner search zones, independent of the primary instruction. */
public class PenanceDebugOverlay extends Overlay
{
	private final Client client;
	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;
	private final BaTracker tracker;

	@Inject
	PenanceDebugOverlay(Client client, BaAutopilotPlugin plugin, BaAutopilotConfig config, BaTracker tracker)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.tracker = tracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Snapshot s = plugin.getSnapshot();
		if (!config.debugPriorities() || s == null || !s.isInArena()) return null;
		Font previous = g.getFont();
		g.setFont(FontManager.getRunescapeSmallFont());
		zones(g, s);
		for (PenanceDiagnostics.Label label : PenanceDiagnostics.labels(s))
		{
			NPC npc = tracker.getNpcs().get(label.getNpcIndex());
			if (npc == null) continue;
			Point at = npc.getCanvasTextLocation(g, label.getTitle(), npc.getLogicalHeight() + 105);
			if (at == null) continue;
			Color color = label.getTone().equals("good") ? Palette.GOOD : label.getTone().equals("bad") ? Palette.BAD : Palette.TEXT;
			label(g, at, label.getTitle(), label.getDetail(), color);
		}
		g.setFont(previous);
		return null;
	}

	private void zones(Graphics2D g, Snapshot s)
	{
		if (s.getPlayerTile() == null) return;
		int region = s.getPlayerTile().getRegionID();
		java.awt.Stroke previous = g.getStroke();
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 5}, 0));
		for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++)
		{
			LocalPoint tile = Scenes.toLocal(client, ArenaTiles.p(region, x * 8 + 4, y * 8 + 4));
			if (tile == null) continue;
			// Area centre is between the four middle tiles, rather than at a tile centre.
			LocalPoint centre = tile.dx(-64).dy(-64);
			Polygon area = Perspective.getCanvasTileAreaPoly(client, centre, PenanceDiagnostics.ZONE_SIZE);
			if (area == null) continue;
			g.setColor(new Color(150, 180, 205, 105));
			g.drawPolygon(area);
			String text = "Z" + x + "," + y;
			Point at = Perspective.getCanvasTextLocation(client, g, centre, text, 0);
			if (at != null) net.runelite.client.ui.overlay.OverlayUtil.renderTextLocation(g, at, text, Palette.MUTED);
		}
		g.setStroke(previous);
	}

	private static void label(Graphics2D g, Point at, String title, String detail, Color color)
	{
		int line = g.getFontMetrics().getHeight();
		int width = Math.max(g.getFontMetrics().stringWidth(title), g.getFontMetrics().stringWidth(detail));
		int x = at.getX() + (g.getFontMetrics().stringWidth(title) - width) / 2;
		int y = at.getY();
		g.setColor(new Color(0, 0, 0, 185));
		g.fillRoundRect(x - 3, y - line + 2, width + 6, line * 2 + 3, 5, 5);
		net.runelite.client.ui.overlay.OverlayUtil.renderTextLocation(g, new Point(x, y), title, color);
		net.runelite.client.ui.overlay.OverlayUtil.renderTextLocation(g, new Point(x, y + line), detail, color);
	}
}
