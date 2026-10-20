package com.galesong.baautopilot.tutorial;

import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.overlay.Palette;
import com.google.gson.Gson;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A page's arena map, over the middle of the game view, north up: the floor the recordings saw
 * walked, and the arena's landmarks (spawn caves, traps, launchers and hoppers, the Healer spring,
 * dispensers, logs and hammer; in wave 10 the craters, mushrooms and the Queen). The map data is
 * arena-maps.json, built from recorded rounds.
 */
@Slf4j
public class TutorialMapOverlay extends Overlay
{
	/** One arena: floor tiles (region x, y) and its landmarks. */
	static class ArenaMap
	{
		List<int[]> floor = new ArrayList<>();
		List<Element> elements = new ArrayList<>();
	}

	static class Element
	{
		String kind;
		String label;
		double x;
		double y;
		/** Width and height in tiles, for areas (the team's start). */
		int w;
		int h;
	}

	private static final Color PANEL = new Color(30, 26, 20, 235);
	private static final Color EDGE_DARK = new Color(14, 12, 9);
	private static final Color EDGE_GOLD = new Color(146, 118, 66);
	private static final Color FLOOR = new Color(122, 104, 74);
	private static final Color TEXT = new Color(240, 230, 205);
	private static final Color MUTED = new Color(170, 158, 130);
	private static final Color TRAP = new Color(205, 160, 90);
	private static final Color LAUNCHER = new Color(150, 150, 160);
	private static final Color HOPPER = new Color(150, 110, 60);
	private static final Color SPRING = new Color(90, 170, 255);
	private static final Color POISON = new Color(110, 220, 90);
	private static final Color LAVA = new Color(255, 120, 40);
	private static final Color MUSHROOM = new Color(200, 190, 175);
	private static final Color LOGS = new Color(170, 120, 70);
	private static final Color HAMMER = new Color(190, 190, 200);
	private static final int PAD = 14;
	private static final Color START = new Color(255, 200, 80);
	private static final Color START_FILL = new Color(255, 200, 80, 60);
	private static final int TITLE_H = 46;
	private static final Color TITLE_BAND = new Color(62, 53, 41);
	private static final Color TITLE_GOLD = new Color(255, 152, 31);
	private static final Color HILL = new Color(160, 140, 100);
	private static final Color HILL_EDGE = new Color(190, 170, 125);
	/** Tiles from a launcher to its hill's edge. */
	private static final int HILL_RADIUS = 3;
	/** Share of the game view the map may take. */
	private static final double FIT = 0.72;

	private final Client client;
	private final TutorialSteps steps;
	private final Map<String, ArenaMap> maps;
	private final net.runelite.client.game.SpriteManager sprites;
	private BooleanSupplier shown = () -> false;

	@Inject
	TutorialMapOverlay(Client client, TutorialSteps steps, Gson gson, net.runelite.client.game.SpriteManager sprites)
	{
		this.client = client;
		this.steps = steps;
		this.maps = load(gson);
		this.sprites = sprites;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void setShown(BooleanSupplier shown)
	{
		this.shown = shown;
	}

	private static Map<String, ArenaMap> load(Gson gson)
	{
		try (InputStream in = TutorialMapOverlay.class.getResourceAsStream("arena-maps.json"))
		{
			Map<String, ArenaMap> m = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
				new com.google.gson.reflect.TypeToken<Map<String, ArenaMap>>()
				{
				}.getType());
			return m == null ? Collections.emptyMap() : m;
		}
		catch (Exception e)
		{
			log.warn("Arena maps unreadable", e);
			return Collections.emptyMap();
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String which = shown.getAsBoolean() ? steps.current().getMap() : null;
		ArenaMap map = which == null ? null : maps.get(which);
		if (map == null || map.floor.isEmpty())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int[] t : map.floor)
		{
			minX = Math.min(minX, t[0]);
			maxX = Math.max(maxX, t[0]);
			minY = Math.min(minY, t[1]);
			maxY = Math.max(maxY, t[1]);
		}
		int tilesX = maxX - minX + 1;
		int tilesY = maxY - minY + 1;
		int viewW = client.getViewportWidth();
		int viewH = client.getViewportHeight();
		int legend = 22;
		int tile = (int) Math.max(4, Math.min(14, Math.min(viewW * FIT / tilesX, (viewH * FIT - legend - TITLE_H) / tilesY)));
		int w = tilesX * tile + 2 * PAD;
		int h = TITLE_H + tilesY * tile + 2 * PAD + legend;
		int left = client.getViewportXOffset() + (viewW - w) / 2;
		int top = client.getViewportYOffset() + (viewH - h) / 2;

		g.setColor(PANEL);
		g.fillRect(left, top, w, h);
		g.setColor(EDGE_GOLD);
		g.drawRect(left + 1, top + 1, w - 3, h - 3);
		g.setColor(EDGE_DARK);
		g.drawRect(left, top, w - 1, h - 1);
		title(g, which, left, top, w);

		int ox = left + PAD;
		int oy = top + TITLE_H + PAD;
		final int fMinX = minX;
		final int fMaxY = maxY;
		java.util.function.DoubleUnaryOperator sx = x -> ox + (x - fMinX) * tile;
		java.util.function.DoubleUnaryOperator sy = y -> oy + (fMaxY - y) * tile;

		g.setColor(FLOOR);
		for (int[] t : map.floor)
		{
			g.fillRect((int) sx.applyAsDouble(t[0]), (int) sy.applyAsDouble(t[1]), tile, tile);
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		Set<String> labelled = new HashSet<>();
		boolean craters = false;
		for (Element e : map.elements)
		{
			int cx = (int) Math.round(sx.applyAsDouble(e.x) + tile / 2.0);
			int cy = (int) Math.round(sy.applyAsDouble(e.y) + tile / 2.0);
			Color c = colour(e.kind);
			int r;
			switch (e.kind)
			{
				case "start":
				{
					// The team's starting tiles: a dashed gold box
					int bw = Math.max(1, e.w) * tile;
					int bh = Math.max(1, e.h) * tile;
					int bx = cx - bw / 2;
					int by = cy - bh / 2;
					g.setColor(START_FILL);
					g.fillRoundRect(bx, by, bw, bh, 6, 6);
					g.setColor(START);
					g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 3f}, 0f));
					g.drawRoundRect(bx, by, bw, bh, 6, 6);
					g.setStroke(new BasicStroke(1f));
					centred(g, e.label, cx, by - 4, START);
					continue;
				}
				case "hill":
					// A raised mound about three tiles round its launcher, under everything else
					r = HILL_RADIUS * tile;
					g.setColor(HILL);
					g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
					g.setColor(HILL_EDGE);
					g.setStroke(new BasicStroke(1.5f));
					g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
					if (labelled.add(e.kind))
					{
						centred(g, "Hill (safer)", cx, cy + r + 12, TEXT);
					}
					continue;
				case "poison":
				case "lava":
				case "mushroom":
					craters = true;
					dot(g, cx, cy, Math.max(3, tile / 2), c);
					continue;
				case "trap":
				case "hopper":
				case "logs":
				case "hammer":
					r = Math.max(3, tile / 2);
					g.setColor(Color.BLACK);
					g.fillRect(cx - r - 1, cy - r - 1, 2 * r + 2, 2 * r + 2);
					g.setColor(c);
					g.fillRect(cx - r, cy - r, 2 * r, 2 * r);
					break;
				default:
					if (e.kind.startsWith("disp-"))
					{
						r = Math.max(4, tile / 2 + 1);
						g.setColor(Color.BLACK);
						g.fillRect(cx - r - 1, cy - r - 1, 2 * r + 2, 2 * r + 2);
						g.setColor(c);
						g.fillRect(cx - r, cy - r, 2 * r, 2 * r);
						centred(g, e.label, cx, cy + 4, Color.BLACK);
						if (labelled.add("disp"))
						{
							// One label for the row
							shadowed(g, "Dispensers", cx - r, cy + r + 12, TEXT);
						}
						continue;
					}
					r = e.kind.startsWith("spawn-") || "queen".equals(e.kind) || "spring".equals(e.kind)
						? Math.max(5, tile) : Math.max(4, tile * 2 / 3);
					dot(g, cx, cy, r, c);
					break;
			}
			// Each launcher and each trap is named; the hopper shares its launcher's label (they're
			// side by side), and the second log pile goes unnamed
			if ("launcher".equals(e.kind))
			{
				centred(g, "Egg launcher + hopper", cx + tile / 2, cy - r - 4, TEXT);
			}
			else if ("trap".equals(e.kind))
			{
				centred(g, e.label, cx, cy - r - 4, TEXT);
			}
			else if (!"hopper".equals(e.kind) && e.label != null && !e.label.isEmpty() && labelled.add(e.kind))
			{
				centred(g, e.label, cx, cy - r - 4, TEXT);
			}
		}

		// Compass, and a legend for the unlabelled dots
		centred(g, "N", left + w - PAD - 4, top + TITLE_H + PAD + 10, MUTED);
		g.setColor(MUTED);
		g.setStroke(new BasicStroke(2f));
		g.drawLine(left + w - PAD - 4, top + TITLE_H + PAD + 14, left + w - PAD - 4, top + TITLE_H + PAD + 28);
		int ly = top + h - PAD;
		int lx = left + PAD;
		if (craters)
		{
			lx = legend(g, lx, ly, POISON, "Poison crater");
			lx = legend(g, lx, ly, LAVA, "Lava crater");
			legend(g, lx, ly, MUSHROOM, "Petrified mushroom");
		}
		return null;
	}

	/** The title band: the BA badge, "Barbarian Assault" small above the arena's name in gold. */
	private void title(Graphics2D g, String which, int left, int top, int w)
	{
		g.setColor(TITLE_BAND);
		g.fillRect(left + 2, top + 2, w - 4, TITLE_H - 2);
		g.setColor(EDGE_GOLD);
		g.drawLine(left + 2, top + TITLE_H, left + w - 3, top + TITLE_H);
		String name = "1-9".equals(which) ? "The arena: waves 1 to 9" : "The arena: wave 10";
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
		FontMetrics big = g.getFontMetrics();
		java.awt.image.BufferedImage badge = sprites.getSprite(BaIcon.SPRITE, 0);
		int badgeSize = TITLE_H - 8;
		int textW = big.stringWidth(name);
		int blockW = (badge == null ? 0 : badgeSize + 8) + textW;
		int x = left + (w - blockW) / 2;
		if (badge != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(badge, x, top + 4, badgeSize, badgeSize, null);
			x += badgeSize + 8;
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		shadowed(g, "BARBARIAN ASSAULT", x, top + 15, MUTED);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
		shadowed(g, name, x, top + TITLE_H - 8, TITLE_GOLD);
		g.setFont(FontManager.getRunescapeSmallFont());
	}

	private int legend(Graphics2D g, int x, int y, Color c, String text)
	{
		dot(g, x + 4, y - 4, 4, c);
		shadowed(g, text, x + 12, y, TEXT);
		return x + 12 + g.getFontMetrics().stringWidth(text) + 14;
	}

	private static Color colour(String kind)
	{
		switch (kind)
		{
			case "spawn-fighter":
				return Tutorials.FIGHTER;
			case "spawn-ranger":
				return Tutorials.RANGER;
			case "spawn-runner":
				return Tutorials.RUNNER;
			case "spawn-healer":
				return Tutorials.HEALER;
			case "queen":
				return Tutorials.QUEEN;
			case "launcher":
				return LAUNCHER;
			case "hopper":
				return HOPPER;
			case "trap":
				return TRAP;
			case "spring":
				return SPRING;
			case "disp-attacker":
				return Palette.role(Role.ATTACKER);
			case "disp-defender":
				return Palette.role(Role.DEFENDER);
			case "disp-healer":
				return Palette.role(Role.HEALER);
			case "disp-collector":
				return Palette.role(Role.COLLECTOR);
			case "poison":
				return POISON;
			case "lava":
				return LAVA;
			case "mushroom":
				return MUSHROOM;
			case "logs":
				return LOGS;
			case "hammer":
				return HAMMER;
			default:
				return TEXT;
		}
	}

	private static void dot(Graphics2D g, int x, int y, int r, Color c)
	{
		g.setColor(Color.BLACK);
		g.fillOval(x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2);
		g.setColor(c);
		g.fillOval(x - r, y - r, 2 * r, 2 * r);
	}

	private static void centred(Graphics2D g, String text, int x, int baseline, Color c)
	{
		FontMetrics fm = g.getFontMetrics();
		shadowed(g, text, x - fm.stringWidth(text) / 2, baseline, c);
	}

	private static void shadowed(Graphics2D g, String text, int x, int baseline, Color c)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(c);
		g.drawString(text, x, baseline);
	}
}
