package com.bautopilot.baautopilot.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * The box's main line, to read at a glance: the item to use, large, then the step's tag as a
 * chip in the action's colour and the instruction after it ("[LOAD] your eggs into the hopper"),
 * wrapping beside the icon.
 */
class ActionRow implements LayoutableRenderableEntity
{
	private static final int ICON = 30;
	private static final int ICON_GAP = 7;
	private static final int CHIP_PAD = 4;
	private static final Color ICON_BACKING = new Color(255, 255, 255, 40);

	private final BufferedImage icon;
	private final String tag;
	private final String text;
	private final Color colour;

	@Getter
	private final Rectangle bounds = new Rectangle();
	@Setter
	private Point preferredLocation = new Point();
	@Setter
	private Dimension preferredSize = new Dimension(200, 0);

	ActionRow(BufferedImage icon, String tag, String text, Color colour)
	{
		this.icon = icon;
		this.tag = tag;
		this.text = text;
		this.colour = colour;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Font bold = FontManager.getRunescapeBoldFont();
		Font small = FontManager.getRunescapeSmallFont();
		FontMetrics fm = g.getFontMetrics(bold);
		FontMetrics sm = g.getFontMetrics(small);
		int x0 = preferredLocation.x;
		int y0 = preferredLocation.y;
		int textX = x0;
		if (icon != null)
		{
			BufferedImage img = IconRow.trimmed(icon);
			float scale = Math.min(ICON / (float) Math.max(1, img.getWidth()), ICON / (float) Math.max(1, img.getHeight()));
			int w = Math.max(1, Math.round(img.getWidth() * scale));
			int h = Math.max(1, Math.round(img.getHeight() * scale));
			g.setColor(ICON_BACKING);
			g.fillRoundRect(x0, y0, ICON + 4, ICON + 4, 9, 9);
			g.drawImage(img, x0 + 2 + (ICON - w) / 2, y0 + 2 + (ICON - h) / 2, w, h, null);
			textX = x0 + ICON + 4 + ICON_GAP;
		}
		int right = x0 + preferredSize.width;
		int line = fm.getHeight() + 1;

		// The tag chip starts the first line; the words flow after it and wrap under the text column
		int x = textX;
		int y = y0;
		if (tag != null && !tag.isEmpty())
		{
			int cw = sm.stringWidth(tag) + 2 * CHIP_PAD;
			int ch = sm.getAscent() + 4;
			int cy = y + (line - ch) / 2;
			g.setColor(colour);
			g.fillRoundRect(x, cy, cw, ch, 5, 5);
			g.setFont(small);
			g.setColor(new Color(20, 16, 10));
			g.drawString(tag, x + CHIP_PAD, cy + ch - 3);
			x += cw + 5;
		}
		g.setFont(bold);
		int space = fm.stringWidth(" ");
		for (String word : wordsOf(text))
		{
			int w = fm.stringWidth(word);
			if (x > textX && x + w > right)
			{
				x = textX;
				y += line;
			}
			int base = y + (line + fm.getAscent() - fm.getDescent()) / 2;
			g.setColor(Color.BLACK);
			g.drawString(word, x + 1, base + 1);
			g.setColor(Palette.CREAM);
			g.drawString(word, x, base);
			x += w + space;
		}
		int height = Math.max(icon == null ? 0 : ICON + 4, y - y0 + line);
		bounds.setLocation(preferredLocation);
		bounds.setSize(preferredSize.width, height);
		return new Dimension(preferredSize.width, height);
	}

	private static List<String> wordsOf(String text)
	{
		List<String> out = new ArrayList<>();
		if (text != null)
		{
			for (String w : text.split(" "))
			{
				if (!w.isEmpty())
				{
					out.add(w);
				}
			}
		}
		return out;
	}
}
