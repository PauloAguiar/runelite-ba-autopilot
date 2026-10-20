package com.galesong.baautopilot.ui.kit.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import javax.swing.Icon;

/**
 * Painted settings gear in any accent color: a filled silhouette —
 * body, six teeth, punched-out hub — which stays readable at small
 * sizes where stroked spokes would blur into a sun.
 */
public class GearIcon implements Icon
{
	private static final int SIZE = 14;

	private final Color color;

	public GearIcon(Color color)
	{
		this.color = color;
	}

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);

		double cx = x + SIZE / 2.0;
		double cy = y + SIZE / 2.0;

		Area gear = new Area(new Ellipse2D.Double(cx - 4.6, cy - 4.6, 9.2, 9.2));
		Rectangle2D tooth = new Rectangle2D.Double(cx + 3.2, cy - 1.5, 3.6, 3.0);
		for (int i = 0; i < 6; i++)
		{
			AffineTransform rotate = AffineTransform.getRotateInstance(Math.PI / 3 * i, cx, cy);
			gear.add(new Area(rotate.createTransformedShape(tooth)));
		}
		gear.subtract(new Area(new Ellipse2D.Double(cx - 1.9, cy - 1.9, 3.8, 3.8)));

		g.fill(gear);
		g.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return SIZE;
	}

	@Override
	public int getIconHeight()
	{
		return SIZE;
	}
}
