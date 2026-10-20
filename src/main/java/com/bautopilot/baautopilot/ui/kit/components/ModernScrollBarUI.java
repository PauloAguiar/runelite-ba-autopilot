package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Slim rounded scrollbar without arrow buttons. Usually applied through
 * {@link Style#styleScrollPane}.
 */
public class ModernScrollBarUI extends BasicScrollBarUI
{
	@Override
	protected void configureScrollBarColors()
	{
		trackColor = Style.PANEL_BG;
		thumbColor = new Color(82, 85, 92);
	}

	@Override
	protected JButton createDecreaseButton(int orientation)
	{
		return zeroButton();
	}

	@Override
	protected JButton createIncreaseButton(int orientation)
	{
		return zeroButton();
	}

	@Override
	protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setColor(Style.PANEL_BG);
		g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		g.dispose();
	}

	@Override
	protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds)
	{
		if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0)
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(isDragging ? new Color(118, 122, 132) : new Color(78, 81, 89));
		int x = bounds.x + 1;
		int width = Math.max(3, bounds.width - 2);
		g.fillRoundRect(x, bounds.y + 1, width, Math.max(4, bounds.height - 2), 7, 7);
		g.dispose();
	}

	private static JButton zeroButton()
	{
		JButton button = new JButton();
		button.setPreferredSize(new Dimension(0, 0));
		button.setMinimumSize(new Dimension(0, 0));
		button.setMaximumSize(new Dimension(0, 0));
		return button;
	}
}
