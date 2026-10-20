package com.bautopilot.baautopilot.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/** Small pieces of the box: the gold divider and the thin call-timer bar. */
final class BoxParts
{
	private BoxParts()
	{
	}

	/** A gold line across the box with a dark line under it, like the game's interfaces. */
	static LayoutableRenderableEntity divider()
	{
		return new Part(3)
		{
			@Override
			void draw(Graphics2D g, int x, int y, int w)
			{
				g.setColor(Palette.GOLD_EDGE);
				g.drawLine(x, y + 1, x + w - 1, y + 1);
				g.setColor(Palette.DARK_EDGE);
				g.drawLine(x, y + 2, x + w - 1, y + 2);
			}
		};
	}

	/** How much of the call is left, as a thin bar; red when nearly over. */
	static LayoutableRenderableEntity timer(double left, boolean late)
	{
		return new Part(5)
		{
			@Override
			void draw(Graphics2D g, int x, int y, int w)
			{
				g.setColor(Palette.DARK_EDGE);
				g.fillRect(x, y + 1, w, 4);
				g.setColor(late ? Palette.BAD : new Color(80, 150, 230));
				g.fillRect(x, y + 1, (int) Math.round(w * Math.max(0, Math.min(1, left))), 4);
			}
		};
	}

	private abstract static class Part implements LayoutableRenderableEntity
	{
		private final int height;
		@Getter
		private final Rectangle bounds = new Rectangle();
		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(200, 0);

		Part(int height)
		{
			this.height = height;
		}

		abstract void draw(Graphics2D g, int x, int y, int w);

		@Override
		public Dimension render(Graphics2D g)
		{
			draw(g, preferredLocation.x, preferredLocation.y, preferredSize.width);
			bounds.setLocation(preferredLocation);
			bounds.setSize(preferredSize.width, height);
			return new Dimension(preferredSize.width, height);
		}
	}
}
