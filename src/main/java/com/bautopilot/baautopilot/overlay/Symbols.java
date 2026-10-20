package com.bautopilot.baautopilot.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Small painted symbols for the overlay (the RuneScape font has no check marks or arrows).
 * 14 px, built once.
 */
public final class Symbols
{
	static final int SIZE = 14;

	/** Call made. */
	public static final BufferedImage CHECK = paint(g ->
	{
		g.setColor(Palette.GOOD);
		g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawPolyline(new int[]{2, 5, 12}, new int[]{7, 11, 3}, 3);
	});

	/** Call owed / attention. */
	public static final BufferedImage ALERT = paint(g ->
	{
		g.setColor(Palette.BAD);
		g.fillOval(0, 0, SIZE - 1, SIZE - 1);
		g.setColor(Color.WHITE);
		g.fillRect(6, 3, 2, 5);
		g.fillRect(6, 9, 2, 2);
	});

	/** Your call speed. */
	public static final BufferedImage STOPWATCH = paint(g ->
	{
		g.setColor(Palette.TEXT);
		g.setStroke(new BasicStroke(1.6f));
		g.drawOval(1, 2, 11, 11);
		g.fillRect(5, 0, 4, 2);
		g.drawLine(7, 7, 7, 4);
		g.drawLine(7, 7, 10, 7);
	});

	/** Shots fired. */
	public static final BufferedImage CROSSHAIR = paint(g ->
	{
		g.setColor(new Color(255, 125, 55));
		g.setStroke(new BasicStroke(1.6f));
		g.drawOval(2, 2, 9, 9);
		g.drawLine(7, 0, 7, 4);
		g.drawLine(7, 9, 7, 13);
		g.drawLine(0, 7, 4, 7);
		g.drawLine(9, 7, 13, 7);
	});

	/** A mistake (wrong egg, wrong food, blocked hit). */
	public static final BufferedImage CROSS = paint(g ->
	{
		g.setColor(Palette.BAD);
		g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(3, 3, 11, 11);
		g.drawLine(11, 3, 3, 11);
	});

	/** A coloured dot, e.g. poisoned / clean. */
	public static BufferedImage dot(Color c)
	{
		return paint(g ->
		{
			g.setColor(c);
			g.fillOval(3, 3, 8, 8);
		});
	}

	private Symbols()
	{
	}

	private interface Painter
	{
		void paint(Graphics2D g);
	}

	private static BufferedImage paint(Painter p)
	{
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		p.paint(g);
		g.dispose();
		return img;
	}
}
