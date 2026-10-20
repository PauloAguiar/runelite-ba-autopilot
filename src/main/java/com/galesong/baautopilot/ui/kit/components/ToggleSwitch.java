package com.galesong.baautopilot.ui.kit.components;

import com.galesong.baautopilot.ui.kit.Style;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import net.runelite.client.ui.FontManager;

/**
 * Pill-shaped on/off switch: SUCCESS track with the thumb right when
 * on, raised surface with a muted thumb when off.
 */
public class ToggleSwitch extends JToggleButton
{
	public ToggleSwitch(String text)
	{
		super(text);
		setOpaque(false);
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(Style.SOFT_TEXT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setIconTextGap(7);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setIcon(new TrackIcon(false));
		setSelectedIcon(new TrackIcon(true));
	}

	private static final class TrackIcon implements Icon
	{
		private static final int WIDTH = 27;
		private static final int HEIGHT = 15;
		private final boolean on;

		private TrackIcon(boolean on)
		{
			this.on = on;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (on)
			{
				g.setColor(Style.withAlpha(Style.SUCCESS, 55));
				g.fillRoundRect(x, y, WIDTH - 1, HEIGHT - 1, HEIGHT - 1, HEIGHT - 1);
				g.setColor(Style.SUCCESS);
				g.drawRoundRect(x, y, WIDTH - 1, HEIGHT - 1, HEIGHT - 1, HEIGHT - 1);
				g.fillOval(x + WIDTH - HEIGHT + 2, y + 2, HEIGHT - 5, HEIGHT - 5);
			}
			else
			{
				g.setColor(Style.SURFACE_RAISED);
				g.fillRoundRect(x, y, WIDTH - 1, HEIGHT - 1, HEIGHT - 1, HEIGHT - 1);
				g.setColor(Style.BORDER);
				g.drawRoundRect(x, y, WIDTH - 1, HEIGHT - 1, HEIGHT - 1, HEIGHT - 1);
				g.setColor(Style.MUTED_TEXT);
				g.fillOval(x + 2, y + 2, HEIGHT - 5, HEIGHT - 5);
			}
			g.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return HEIGHT;
		}
	}
}
