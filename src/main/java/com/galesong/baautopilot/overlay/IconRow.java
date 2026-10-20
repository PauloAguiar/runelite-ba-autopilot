package com.galesong.baautopilot.overlay;

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
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * One overlay line of icons and short texts, left to right, e.g. [egg] 24 [bag] 4 [crosshair] 1.
 * Item sprites are drawn scaled to the line height; an item image that is still loading simply
 * draws once it arrives.
 */
public class IconRow implements LayoutableRenderableEntity
{
	static final int ICON = 20;
	/** Light rounded backing behind item sprites, so dark items read on the dark box. */
	private static final Color ICON_BACKING = new Color(255, 255, 255, 46);
	/** Sprites at least this big are items (symbols are drawn smaller and get no backing). */
	private static final int ITEM_MIN = 16;
	private static final int GAP = 3;
	private static final int GROUP_GAP = 7;
	/**
	 * Item sprites trimmed to their visible pixels (game item images are 36x32 with a wide clear
	 * border, which made each icon take far more room than it shows). Only cached once loaded.
	 */
	private static final java.util.Map<BufferedImage, BufferedImage> TRIMMED = new java.util.WeakHashMap<>();

	private static final class Segment
	{
		final BufferedImage image;
		final int size;
		final String text;
		final Color color;
		final Font font;
		final boolean groupStart;

		Segment(BufferedImage image, int size, String text, Color color, Font font, boolean groupStart)
		{
			this.image = image;
			this.size = size;
			this.text = text;
			this.color = color;
			this.font = font;
			this.groupStart = groupStart;
		}
	}

	private final List<Segment> segments = new ArrayList<>();
	private boolean nextStartsGroup;
	/** Text-only rows as tall as their font, not an icon: for tables of small numbers. */
	private boolean tight;
	private String right;
	private Color rightColor;

	@Getter
	private final Rectangle bounds = new Rectangle();
	@Setter
	private Point preferredLocation = new Point();
	@Setter
	private Dimension preferredSize = new Dimension(200, 0);

	/** Continues at least this far from the left edge, so rows line up in a column. */
	public IconRow column(int x)
	{
		segments.add(new Segment(null, x, null, null, null, false));
		return this;
	}

	/** Lines as tall as the text (no icons on this row). */
	public IconRow tight()
	{
		tight = true;
		return this;
	}

	/** Starts a new visual group (wider gap before it). */
	public IconRow group()
	{
		nextStartsGroup = !segments.isEmpty();
		return this;
	}

	public IconRow icon(BufferedImage image)
	{
		return icon(image, ICON);
	}

	public IconRow icon(BufferedImage image, int size)
	{
		if (image != null)
		{
			segments.add(new Segment(image, size, null, null, null, take()));
		}
		return this;
	}

	public IconRow text(String text, Color color)
	{
		return text(text, color, null);
	}

	public IconRow text(String text, Color color, Font font)
	{
		if (text != null && !text.isEmpty())
		{
			segments.add(new Segment(null, 0, text, color, font, take()));
		}
		return this;
	}

	/** Right-aligned text at the end of the line. */
	public IconRow right(String text, Color color)
	{
		right = text;
		rightColor = color;
		return this;
	}

	public boolean isEmpty()
	{
		return segments.isEmpty() && right == null;
	}

	/** The image cut down to its non-transparent pixels; the original while it is still loading. */
	static BufferedImage trimmed(BufferedImage image)
	{
		BufferedImage cached = TRIMMED.get(image);
		if (cached != null)
		{
			return cached;
		}
		int w = image.getWidth();
		int h = image.getHeight();
		int minX = w;
		int minY = h;
		int maxX = -1;
		int maxY = -1;
		for (int yy = 0; yy < h; yy++)
		{
			for (int xx = 0; xx < w; xx++)
			{
				if ((image.getRGB(xx, yy) >>> 24) > 16)
				{
					minX = Math.min(minX, xx);
					minY = Math.min(minY, yy);
					maxX = Math.max(maxX, xx);
					maxY = Math.max(maxY, yy);
				}
			}
		}
		if (maxX < 0)
		{
			// Not loaded yet (or empty): try again next frame
			return image;
		}
		BufferedImage out = image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
		TRIMMED.put(image, out);
		return out;
	}

	/** Width of the group starting at {@code from} when it has an icon (icon groups are short); 0 for text groups. */
	private int iconGroupWidth(Graphics2D g, Font base, int from)
	{
		int width = 0;
		boolean icon = false;
		for (int i = from; i < segments.size(); i++)
		{
			Segment s = segments.get(i);
			if (i > from && s.groupStart)
			{
				break;
			}
			if (s.image != null)
			{
				icon = true;
				width += s.size + GAP;
			}
			else if (s.text != null)
			{
				width += g.getFontMetrics(s.font != null ? s.font : base).stringWidth(s.text) + GAP;
			}
		}
		return icon ? width : 0;
	}

	private boolean take()
	{
		boolean g = nextStartsGroup;
		nextStartsGroup = false;
		return g;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		// The regular RuneScape font, not the small one: easier to read at a glance
		Font base = net.runelite.client.ui.FontManager.getRunescapeFont();
		g.setFont(base);
		FontMetrics fm = g.getFontMetrics(base);
		int lineHeight = tight ? fm.getHeight() - 2 : Math.max(ICON, fm.getHeight()) + 2;
		int left = preferredLocation.x;
		int maxX = left + preferredSize.width;
		int x = left;
		int y = preferredLocation.y;
		for (int i = 0; i < segments.size(); i++)
		{
			Segment s = segments.get(i);
			if (s.image == null && s.text == null)
			{
				x = Math.max(x, left + s.size);
				continue;
			}
			if (s.groupStart)
			{
				x += GROUP_GAP - GAP;
				// An icon and its number stay together: a group that doesn't fit starts the next line
				int gw = iconGroupWidth(g, base, i);
				if (gw > 0 && x > left && x + gw > maxX)
				{
					x = left;
					y += lineHeight;
				}
			}
			if (s.image != null)
			{
				// Fitted inside a size x size square, keeping its shape; the slot is as wide as it shows
				BufferedImage img = s.size >= ITEM_MIN ? trimmed(s.image) : s.image;
				int iw = Math.max(1, img.getWidth());
				int ih = Math.max(1, img.getHeight());
				float scale = Math.min(s.size / (float) iw, s.size / (float) ih);
				int w = Math.max(1, Math.round(iw * scale));
				int h = Math.max(1, Math.round(ih * scale));
				if (x > left && x + w > maxX)
				{
					// Out of room: the icon and what follows go on the next line
					x = left;
					y += lineHeight;
				}
				int iy = y + (lineHeight - s.size) / 2;
				if (s.size >= ITEM_MIN)
				{
					g.setColor(ICON_BACKING);
					g.fillRoundRect(x - 1, iy - 1, w + 2, s.size + 2, 7, 7);
				}
				g.drawImage(img, x, iy + (s.size - h) / 2, w, h, null);
				x += w + GAP;
				continue;
			}
			// Text wraps under itself (hanging indent at the column where it started)
			Font f = s.font != null ? s.font : base;
			g.setFont(f);
			FontMetrics m = g.getFontMetrics(f);
			int indent = x;
			int space = m.stringWidth(" ");
			for (String word : s.text.split(" "))
			{
				int w = m.stringWidth(word);
				if (x > indent && x + w > maxX)
				{
					x = indent;
					y += m.getHeight();
				}
				int baseline = y + (lineHeight + m.getAscent() - m.getDescent()) / 2;
				g.setColor(Color.BLACK);
				g.drawString(word, x + 1, baseline + 1);
				g.setColor(s.color);
				g.drawString(word, x, baseline);
				x += w + space;
			}
			x += GAP - space;
			g.setFont(base);
		}
		if (right != null)
		{
			int w = fm.stringWidth(right);
			int rx = maxX - w;
			int lineY = preferredLocation.y;
			if (y == preferredLocation.y && x + GROUP_GAP > rx)
			{
				// It would run into the line's own text: put it on a line of its own
				y += lineHeight;
				lineY = y;
			}
			int baseline = lineY + (lineHeight + fm.getAscent() - fm.getDescent()) / 2;
			g.setColor(Color.BLACK);
			g.drawString(right, rx + 1, baseline + 1);
			g.setColor(rightColor);
			g.drawString(right, rx, baseline);
		}
		int height = y - preferredLocation.y + lineHeight;
		bounds.setLocation(preferredLocation);
		bounds.setSize(preferredSize.width, height);
		return new Dimension(preferredSize.width, height);
	}
}
