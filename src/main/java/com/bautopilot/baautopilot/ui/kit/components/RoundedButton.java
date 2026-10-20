package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Flat rounded button with a subtle raise on hover.
 *
 * Three looks:
 * - default: SURFACE fill + BORDER stroke; communicate state through the
 *   foreground color (SUCCESS when a toggle is on, MUTED_TEXT when off)
 * - {@link #primary}: strong accent wash with a solid accent border, for
 *   the one main call to action on a panel
 * - {@link #accent}: translucent accent wash (StatusPill-style), for
 *   secondary emphasized actions such as a danger button
 */
public class RoundedButton extends JButton
{
	private Color fill = Style.SURFACE;
	private Color hoverFill = Style.SURFACE_RAISED;
	private Color stroke = Style.BORDER;
	private boolean hovered;

	public RoundedButton()
	{
		// Same size as row/item titles so button labels read as first-class text.
		setFont(FontManager.getRunescapeBoldFont());
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setBorder(new EmptyBorder(5, 9, 5, 9));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hovered = false;
				repaint();
			}
		});
	}

	public RoundedButton(String text)
	{
		this();
		setText(text);
	}

	/** Strong accent wash with a solid accent border — the main call to action. */
	public static RoundedButton primary(String text)
	{
		return primary(text, Style.GOLD);
	}

	public static RoundedButton primary(String text, Color accent)
	{
		RoundedButton button = new RoundedButton(text);
		button.fill = Style.withAlpha(accent, 42);
		button.hoverFill = Style.withAlpha(accent, 64);
		button.stroke = accent;
		button.setForeground(accent);
		return button;
	}

	/** Translucent accent wash, matching StatusPill — emphasized secondary action. */
	public static RoundedButton accent(String text, Color accent)
	{
		RoundedButton button = new RoundedButton(text);
		button.fill = Style.withAlpha(accent, 28);
		button.hoverFill = Style.withAlpha(accent, 48);
		button.stroke = Style.withAlpha(accent, 120);
		button.setForeground(accent);
		return button;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(hovered && isEnabled() ? hoverFill : fill);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
		if (stroke != null)
		{
			g.setColor(stroke);
			g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 10, 10);
		}
		g.dispose();
		super.paintComponent(graphics);
	}
}
