package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Small rounded chip: accent-colored text over a translucent wash of the
 * same accent ("PACKED", "BANK", "MISSING", combat style tags, ...).
 */
public class StatusPill extends JLabel
{
	private Color accent;

	public StatusPill(String text, Color accent)
	{
		super(text);
		this.accent = accent;
		setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		setForeground(accent);
		setHorizontalAlignment(SwingConstants.CENTER);
		setOpaque(false);
		// Uneven insets: pill text is all-caps, so the font's descent space
		// below the baseline is dead — biasing the text down a pixel puts
		// the caps on the optical center of the wash.
		setBorder(new EmptyBorder(4, 6, 2, 6));
	}

	public void setAccent(Color accent)
	{
		this.accent = accent;
		setForeground(accent);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Style.withAlpha(accent, 28));
		g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
		g.dispose();
		super.paintComponent(graphics);
	}
}
