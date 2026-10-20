package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.client.ui.FontManager;

/**
 * Stylized section-title text: letter-spaced bold caps with a soft
 * drop shadow — the heading voice shared by {@link SectionHeading}
 * and {@link CollapsibleSection}.
 */
public class HeadingLabel extends JLabel
{
	private static final Color SHADOW = new Color(0, 0, 0, 160);
	// Measured string width undershoots on tracked fonts; keep slack so
	// the last glyph is never clipped.
	private static final int WIDTH_SLACK = 8;

	public HeadingLabel(String text)
	{
		super(text);
		setFont(headingFont());
		setForeground(Style.TEXT);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/** RuneScape bold at 14pt with a touch of tracking, for all-caps headings. */
	public static Font headingFont()
	{
		Map<TextAttribute, Object> attributes = new HashMap<>();
		attributes.put(TextAttribute.TRACKING, 0.07f);
		return FontManager.getRunescapeBoldFont().deriveFont(14f).deriveFont(attributes);
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics metrics = getFontMetrics(getFont());
		return new Dimension(
			metrics.stringWidth(getText()) + WIDTH_SLACK,
			metrics.getHeight() + 1);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(
			RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(getFont());
		int baseline = g.getFontMetrics().getAscent();
		g.setColor(SHADOW);
		g.drawString(getText(), 1, baseline + 1);
		g.setColor(getForeground());
		g.drawString(getText(), 0, baseline);
		g.dispose();
	}
}
