package com.galesong.baautopilot.ui.kit.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Rounded-rectangle container with an optional 1px stroke. */
public class RoundedPanel extends JPanel
{
	private Color fill;
	private final int radius;
	private final Color stroke;

	public RoundedPanel(Color fill, int radius, Color stroke)
	{
		this.fill = fill;
		this.radius = radius;
		this.stroke = stroke;
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
	}

	/** Swaps the background fill, e.g. for hover states. */
	public void setFill(Color fill)
	{
		this.fill = fill;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(fill);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
		if (stroke != null)
		{
			g.setColor(stroke);
			g.setStroke(new BasicStroke(1f));
			g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), radius, radius);
		}
		g.dispose();
		super.paintComponent(graphics);
	}
}
