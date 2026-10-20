package com.bautopilot.baautopilot.tutorial;

import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.overlay.Palette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * The tutorial page box: page count, title, text, and Back / Next buttons. Clicks on the box are
 * the box's own (consumed), so they never walk you or click the game behind it.
 */
@javax.inject.Singleton
public class TutorialBoxOverlay extends Overlay
{
	private static final int WIDTH = 380;
	private static final int PAD = 12;
	/** Wide enough for "< Introduction", the longest tutorial name on a button. */
	private static final int BUTTON_W = 104;
	private static final int BUTTON_H = 26;
	/** The role panel stand-in (drawn in the column right of the box). */
	private static final int HUD_PAD = 6;
	private static final int HUD_ROW = 26;
	private static final int HUD_HEIGHT = HUD_PAD * 2 + HUD_ROW * 3;
	/** Space kept between the dots and each button. */
	private static final int DOT_MARGIN = 8;
	private static final int HEADER_H = 40;
	/** Space above and below the control row. */
	private static final int CONTROLS_PAD = 8;
	/** The BA badge, drawn at half its 64 px. */
	private static final int BADGE = 36;
	/** Stone and gold, after the game's own interfaces. */
	private static final Color BACKGROUND = new Color(46, 39, 30, 240);
	private static final Color HEADER = new Color(62, 53, 41);
	private static final Color EDGE_DARK = new Color(14, 12, 9);
	private static final Color EDGE_GOLD = new Color(146, 118, 66);
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color TEXT = new Color(240, 230, 205);
	private static final Color MUTED = new Color(150, 138, 115);
	/** Egg names in the box, bright enough to read on the stone. */
	private static final Color EGG_RED = new Color(255, 95, 85);
	private static final Color EGG_GREEN = new Color(110, 225, 110);
	private static final Color EGG_BLUE = new Color(120, 170, 255);
	private static final Color EGG_YELLOW = new Color(250, 220, 80);
	private static final Color SHADOW = Color.BLACK;
	private static final Color BUTTON = new Color(74, 63, 47);
	private static final Color BUTTON_HOVER = new Color(104, 88, 60);
	private static final Color BUTTON_OFF = new Color(52, 45, 36);
	private static final Color DOT_ON = new Color(255, 152, 31);
	private static final Color DOT_OFF = new Color(96, 84, 64);

	private final TutorialSteps steps;
	private final SpriteManager sprites;
	private BufferedImage badge;
	private BooleanSupplier shown = () -> false;

	/** Button areas inside the box, from the last frame drawn. */
	private final Rectangle back = new Rectangle();
	private final Rectangle next = new Rectangle();
	/** Each page dot's click area inside the box, from the last frame drawn (read on the input thread). */
	private volatile List<Rectangle> dotAreas = new ArrayList<>();
	/** The mouse, in canvas coordinates. */
	private volatile java.awt.Point mouse = new java.awt.Point(-1, -1);
	private final TooltipManager tooltips;

	@Inject
	TutorialBoxOverlay(TutorialSteps steps, SpriteManager sprites, TooltipManager tooltips)
	{
		this.steps = steps;
		this.sprites = sprites;
		this.tooltips = tooltips;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void setShown(BooleanSupplier shown)
	{
		this.shown = shown;
	}

	/** What Finish on the very last page does: end the tutorial (input thread). */
	private Runnable onFinish = () -> { };

	public void setOnFinish(Runnable onFinish)
	{
		this.onFinish = onFinish;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!shown.getAsBoolean())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		TutorialSteps.Page page = steps.current();
		Font bodyFont = FontManager.getRunescapeFont();
		Font titleFont = FontManager.getRunescapeBoldFont();
		g.setFont(bodyFont);
		FontMetrics body = g.getFontMetrics();
		// Paragraphs are separated by a blank line in the text; null marks the gap between two
		List<String> lines = new ArrayList<>();
		int gaps = 0;
		for (String paragraph : page.getBody().split("\n\n"))
		{
			if (!lines.isEmpty())
			{
				lines.add(null);
				gaps++;
			}
			lines.addAll(wrap(paragraph.replace('\n', ' ').trim(), body, WIDTH - 2 * PAD));
		}
		int paragraphGap = body.getHeight() / 2;
		// Header, then the controls (fixed under it), a divider, then the text
		int controlsTop = HEADER_H + CONTROLS_PAD;
		int divider = controlsTop + BUTTON_H + CONTROLS_PAD;
		int textTop = divider + PAD;
		int height = textTop + (lines.size() - gaps) * body.getHeight() + gaps * paragraphGap + PAD;

		// Frame: dark edge, gold inner edge, stone fill, a lighter header band
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, WIDTH, height);
		g.setColor(HEADER);
		g.fillRect(2, 2, WIDTH - 4, HEADER_H - 2);
		g.setColor(EDGE_GOLD);
		g.drawLine(2, HEADER_H, WIDTH - 3, HEADER_H);
		g.setStroke(new BasicStroke(1f));
		g.drawRect(1, 1, WIDTH - 3, height - 3);
		g.setColor(EDGE_DARK);
		g.drawRect(0, 0, WIDTH - 1, height - 1);

		// Header: the BA badge, title; the page count on the right
		int x = PAD;
		BufferedImage badge = badge();
		if (badge != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(badge, x - 4, (HEADER_H - BADGE) / 2 + 1, BADGE, BADGE, null);
			x += BADGE + 2;
		}
		g.setFont(titleFont);
		FontMetrics title = g.getFontMetrics();
		int titleBase = (HEADER_H + title.getAscent() - title.getDescent()) / 2 + 1;
		shadowed(g, page.getTitle(), x, titleBase, TITLE);
		g.setFont(FontManager.getRunescapeSmallFont());
		String count = (steps.index() + 1) + " / " + steps.pages().size();
		int right = WIDTH - PAD - g.getFontMetrics().stringWidth(count);
		shadowed(g, count, right, titleBase, MUTED);

		// Controls: Back, progress dots, Next - always in the same place
		back.setBounds(PAD, controlsTop, BUTTON_W, BUTTON_H);
		next.setBounds(WIDTH - PAD - BUTTON_W, controlsTop, BUTTON_W, BUTTON_H);
		button(g, back, steps.backLabel(), steps.hasBack());
		button(g, next, steps.nextLabel(), steps.hasNext() || steps.isLast());
		dots(g, controlsTop + BUTTON_H / 2);
		g.setColor(EDGE_GOLD);
		g.setStroke(new BasicStroke(1f));
		g.drawLine(PAD, divider, WIDTH - PAD, divider);

		// Body
		g.setFont(bodyFont);
		int y = textTop;
		boolean emphasis = false;
		for (String line : lines)
		{
			if (line == null)
			{
				// A new paragraph: a half-line gap, and any emphasis left open ends with its paragraph
				y += paragraphGap;
				emphasis = false;
				continue;
			}
			emphasis = drawLine(g, line, PAD, y + body.getAscent(), emphasis);
			y += body.getHeight();
		}
		// The role panel and inventory go in the column on the right (TutorialInventoryOverlay); the
		// page's autopilot box is the plugin's own (InstructionOverlay), where it always sits
		return new Dimension(WIDTH, height);
	}

	/** The Barbarian Assault icon from the minigame teleports; null until the sprite has loaded. */
	private BufferedImage badge()
	{
		if (badge == null)
		{
			badge = sprites.getSprite(BaIcon.SPRITE, 0);
		}
		return badge;
	}

	/**
	 * A stand-in for the game's role panel, at the given place (the column right of the box): the
	 * role and the call timer, the call to make (mouth) and the call heard (ear), with the game's
	 * icons. Returns the new bottom.
	 */
	int drawHud(Graphics2D g, TutorialSteps.Hud hud, int left, int top, int width)
	{
		int row = HUD_ROW;
		int height = HUD_HEIGHT;
		g.setColor(BACKGROUND);
		g.fillRect(left, top, width, height);
		g.setColor(EDGE_GOLD);
		g.drawRect(left + 1, top + 1, width - 3, height - 3);
		g.setColor(EDGE_DARK);
		g.drawRect(left, top, width - 1, height - 1);

		int x = left + HUD_PAD;
		int y = top + HUD_PAD;
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int base = (row + fm.getAscent() - fm.getDescent()) / 2;
		// Role and the round's timer, counting down from 30 on a loop
		icon(g, net.runelite.api.gameval.SpriteID.BarbassaultIcons.WAVE, x, y, row);
		shadowed(g, hud.getRole().getLabel(), x + row + 4, y + base, Palette.role(hud.getRole()));
		String timer = String.format("0:%02d", 30 - (int) (steps.elapsed() / 1000 % 30));
		shadowed(g, timer, left + width - HUD_PAD - fm.stringWidth(timer), y + base, MUTED);
		y += row;
		icon(g, net.runelite.api.gameval.SpriteID.BarbassaultIcons.MOUTH, x, y, row);
		g.setFont(FontManager.getRunescapeFont());
		shadowed(g, "Call: " + hud.getCall(), x + row + 4, y + base, TITLE);
		y += row;
		icon(g, net.runelite.api.gameval.SpriteID.BarbassaultIcons.EAR, x, y, row);
		shadowed(g, "Heard: " + hud.getHeard(), x + row + 4, y + base, TEXT);
		return top + height;
	}


	/** A game sprite fitted into a square, when loaded. */
	private void icon(Graphics2D g, int sprite, int x, int y, int size)
	{
		BufferedImage image = sprites.getSprite(sprite, 0);
		if (image == null)
		{
			return;
		}
		double scale = Math.min(1, (size - 2) / (double) Math.max(image.getWidth(), image.getHeight()));
		int w = (int) Math.round(image.getWidth() * scale);
		int h = (int) Math.round(image.getHeight() * scale);
		g.drawImage(image, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
	}

	/**
	 * One dot per page, the current one lit. Each is a button to its page: hovering lights it and
	 * shows the page's title; its click area is as wide as the spacing, so packed dots stay clickable.
	 */
	private void dots(Graphics2D g, int centreY)
	{
		List<TutorialSteps.Page> pages = steps.pages();
		int n = pages.size();
		// As far apart as fits between the buttons (10 px at most), smaller dots when packed
		int room = WIDTH - 2 * PAD - 2 * BUTTON_W - 2 * DOT_MARGIN;
		double step = n <= 1 ? 0 : Math.min(10, room / (double) (n - 1));
		int small = step >= 8 ? 3 : 2;
		double x = (WIDTH - (n - 1) * step) / 2;
		int areaW = Math.max(4, (int) Math.floor(step));
		List<Rectangle> areas = new ArrayList<>(n);
		for (int i = 0; i < n; i++)
		{
			int cx = (int) Math.round(x + i * step);
			Rectangle area = new Rectangle(cx - areaW / 2, centreY - DOT_HIT / 2, areaW, DOT_HIT);
			areas.add(area);
			boolean hover = onCanvas(area).contains(mouse);
			int r = i == steps.index() || hover ? small + 1 : small;
			g.setColor(i == steps.index() ? DOT_ON : hover ? DOT_HOVER : DOT_OFF);
			g.fillOval(cx - r, centreY - r, 2 * r, 2 * r);
			if (hover)
			{
				tooltips.add(new Tooltip((i + 1) + ". " + pages.get(i).getTitle()));
			}
		}
		dotAreas = areas;
	}

	/** A dot's click area is this tall, well beyond the dot itself. */
	private static final int DOT_HIT = 16;
	private static final Color DOT_HOVER = new Color(200, 160, 90);

	private void button(Graphics2D g, Rectangle r, String text, boolean enabled)
	{
		g.setColor(!enabled ? BUTTON_OFF : onCanvas(r).contains(mouse) ? BUTTON_HOVER : BUTTON);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(EDGE_DARK);
		g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		g.setColor(enabled ? EDGE_GOLD : DOT_OFF);
		g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		shadowed(g, text, r.x + (r.width - fm.stringWidth(text)) / 2,
			r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2, enabled ? TITLE : MUTED);
	}

	/** Text with the game's one-pixel black shadow, for reading over anything. */
	private static void shadowed(Graphics2D g, String text, int x, int baseline, Color color)
	{
		g.setColor(SHADOW);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	/**
	 * A line of body text: role and egg names in their colours, and *starred* words (which may
	 * run over several lines) in the title gold, for the line a page leans on. Returns whether the
	 * emphasis is still open at the end of the line. "Penance Healers" are monsters, not the role:
	 * plain, like the other Penance (reported 2026-10-19: only they were coloured).
	 */
	private static boolean drawLine(Graphics2D g, String line, int x, int baseline, boolean emphasis)
	{
		FontMetrics fm = g.getFontMetrics();
		String[] words = line.split(" ");
		for (int i = 0; i < words.length; i++)
		{
			String raw = words[i];
			boolean opens = raw.startsWith("*");
			boolean closes = raw.endsWith("*") && raw.length() > 1;
			String bare = raw.replace("*", "");
			emphasis |= opens;
			String word = i < words.length - 1 ? bare + " " : bare;
			boolean penance = i > 0 && words[i - 1].replaceAll("[^A-Za-z]", "").equals("Penance");
			shadowed(g, word, x, baseline, emphasis ? TITLE : penance ? TEXT : colourOf(bare));
			x += fm.stringWidth(word);
			if (closes)
			{
				emphasis = false;
			}
		}
		return emphasis;
	}

	/** Role names in their role's colour, egg colours in the egg's; everything else plain. */
	private static Color colourOf(String word)
	{
		Role role = roleNamed(word);
		if (role != null)
		{
			return Palette.role(role);
		}
		switch (word.replaceAll("[^A-Za-z]", ""))
		{
			case "Red":
				return EGG_RED;
			case "Green":
				return EGG_GREEN;
			case "Blue":
				return EGG_BLUE;
			case "Yellow":
				return EGG_YELLOW;
			default:
				return TEXT;
		}
	}

	/** "Attacker", "Healers,", "Collector." name a role; anything else doesn't. */
	private static Role roleNamed(String word)
	{
		String bare = word.replaceAll("[^A-Za-z]", "");
		for (Role role : Role.values())
		{
			if (bare.equals(role.getLabel()) || bare.equals(role.getLabel() + "s"))
			{
				return role;
			}
		}
		return null;
	}

	/** A rectangle inside the box, moved to where the box was last drawn on the canvas. */
	private Rectangle onCanvas(Rectangle inBox)
	{
		Rectangle box = getBounds();
		return new Rectangle(box.x + inBox.x, box.y + inBox.y, inBox.width, inBox.height);
	}

	private static List<String> wrap(String text, FontMetrics fm, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String tryLine = line.length() == 0 ? word : line + " " + word;
			if (fm.stringWidth(tryLine.replace("*", "")) > width && line.length() > 0)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(tryLine);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}

	/** Back / Next on press; any click on the box stays with the box. */
	public final MouseListener mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (!onBox(e))
			{
				return e;
			}
			if (e.getButton() == MouseEvent.BUTTON1)
			{
				if (steps.hasBack() && onCanvas(back).contains(e.getPoint()))
				{
					steps.back();
				}
				else if (steps.isLast() && onCanvas(next).contains(e.getPoint()))
				{
					onFinish.run();
				}
				else if (steps.hasNext() && onCanvas(next).contains(e.getPoint()))
				{
					steps.next();
				}
				else
				{
					List<Rectangle> areas = dotAreas;
					for (int i = 0; i < areas.size(); i++)
					{
						if (onCanvas(areas.get(i)).contains(e.getPoint()))
						{
							steps.open(i);
							break;
						}
					}
				}
			}
			e.consume();
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			if (onBox(e))
			{
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			if (onBox(e))
			{
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent e)
		{
			mouse = e.getPoint();
			return e;
		}
	};

	private boolean onBox(MouseEvent e)
	{
		return shown.getAsBoolean() && getBounds().contains(e.getPoint());
	}
}
