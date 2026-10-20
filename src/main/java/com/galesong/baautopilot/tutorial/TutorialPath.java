package com.galesong.baautopilot.tutorial;

import java.util.List;
import net.runelite.api.coords.LocalPoint;

/** A point part way along a line through several points, by distance, for camera tours. */
public final class TutorialPath
{
	private TutorialPath()
	{
	}

	/** The point {@code fraction} (0 to 1) of the way along the line through {@code points}. */
	public static LocalPoint along(List<LocalPoint> points, double fraction)
	{
		double total = 0;
		for (int i = 1; i < points.size(); i++)
		{
			total += length(points.get(i - 1), points.get(i));
		}
		double left = Math.max(0, Math.min(1, fraction)) * total;
		for (int i = 1; i < points.size(); i++)
		{
			LocalPoint a = points.get(i - 1);
			LocalPoint b = points.get(i);
			double step = length(a, b);
			if (left <= step && step > 0)
			{
				double f = left / step;
				return new LocalPoint((int) Math.round(a.getX() + (b.getX() - a.getX()) * f),
					(int) Math.round(a.getY() + (b.getY() - a.getY()) * f), a.getWorldView());
			}
			left -= step;
		}
		return points.get(points.size() - 1);
	}

	private static double length(LocalPoint a, LocalPoint b)
	{
		return Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
	}
}
