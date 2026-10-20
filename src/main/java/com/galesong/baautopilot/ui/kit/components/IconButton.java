package com.galesong.baautopilot.ui.kit.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * Small square button that shows just an icon, with the same soft
 * circular hover highlight as {@link DisclosureButton}. Swap the icon
 * with {@code setIcon} to reflect state (e.g. gear muted → teal).
 */
public class IconButton extends JButton
{
	private static final int SIZE = 20;
	private static final Color HOVER_CIRCLE = new Color(255, 255, 255, 22);

	private boolean hovered;

	public IconButton(Icon icon, String tooltip)
	{
		super(icon);
		setToolTipText(tooltip);
		setPreferredSize(new Dimension(SIZE, SIZE));
		setMinimumSize(new Dimension(SIZE, SIZE));
		setMaximumSize(new Dimension(SIZE, SIZE));
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (hovered && isEnabled())
		{
			// Always a true circle, even if a layout stretched the button.
			int diameter = Math.min(getWidth(), getHeight());
			g.setColor(HOVER_CIRCLE);
			g.fillOval((getWidth() - diameter) / 2, (getHeight() - diameter) / 2, diameter, diameter);
		}
		Icon icon = getIcon();
		if (icon != null)
		{
			icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
		}
		g.dispose();
	}
}
