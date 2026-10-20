package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/**
 * Expand/collapse control: a small triangle arrow — down when the
 * content is hidden ("open me"), up when shown — with a soft circular
 * highlight under the cursor.
 */
public class DisclosureButton extends JComponent
{
	private static final int SIZE = 18;
	private static final Color HOVER_CIRCLE = new Color(255, 255, 255, 22);

	private final Runnable onToggle;
	private boolean expanded;
	private boolean hovered;

	public DisclosureButton(Runnable onToggle)
	{
		this.onToggle = onToggle;
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setPreferredSize(new Dimension(SIZE, SIZE));
		setMinimumSize(new Dimension(SIZE, SIZE));
		setMaximumSize(new Dimension(SIZE, SIZE));
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

			@Override
			public void mousePressed(MouseEvent event)
			{
				onToggle.run();
			}
		});
	}

	public void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (hovered)
		{
			// Always a true circle, even if a layout stretched the button.
			int diameter = Math.min(getWidth(), getHeight());
			g.setColor(HOVER_CIRCLE);
			g.fillOval((getWidth() - diameter) / 2, (getHeight() - diameter) / 2, diameter, diameter);
		}
		// Center the arrow in actual bounds, not the nominal SIZE box.
		int ox = (getWidth() - SIZE) / 2;
		int oy = (getHeight() - SIZE) / 2;
		g.translate(ox, oy);
		g.setColor(expanded ? Style.TEAL : Style.MUTED_TEXT);
		if (expanded)
		{
			g.fillPolygon(new int[]{5, 13, 9}, new int[]{11, 11, 6}, 3);
		}
		else
		{
			g.fillPolygon(new int[]{5, 13, 9}, new int[]{7, 7, 12}, 3);
		}
		g.dispose();
	}
}
