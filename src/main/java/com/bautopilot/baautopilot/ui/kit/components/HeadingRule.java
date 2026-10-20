package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

/**
 * The thin rule that runs from a heading's last letter to the right
 * edge. Stretches horizontally inside a BoxLayout row.
 */
class HeadingRule extends JComponent
{
	HeadingRule()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(12, 15));
		// Short.MAX_VALUE, not Integer: BoxLayout sums child maximums, and
		// two Integer.MAX_VALUE rules overflow int — all space then lands
		// on one rule instead of splitting evenly.
		setMaximumSize(new Dimension(Short.MAX_VALUE, 15));
		setAlignmentY(CENTER_ALIGNMENT);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		graphics.setColor(Style.BORDER);
		// +3, not center: RuneScape glyphs sit low in their line box, so the
		// rule must drop below box center to hit the caps' optical middle.
		int y = getHeight() / 2 + 3;
		graphics.drawLine(0, y, getWidth(), y);
	}
}
