package com.bautopilot.baautopilot.ui.kit;

import com.bautopilot.baautopilot.ui.kit.components.ModernScrollBarUI;
import com.bautopilot.baautopilot.ui.kit.components.RoundedPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import net.runelite.client.ui.FontManager;

/**
 * Design tokens and tiny factories for the FreeArcanes panel look:
 * dark layered surfaces, rounded cards and rows, small-caps eyebrows,
 * accent pills and a slim scrollbar.
 *
 * The visual language originates in Slayer Best in Bank (beta 2 panel)
 * and is shared verbatim so sibling plugins render as one family.
 */
public final class Style
{
	// Surfaces, darkest to lightest. PANEL_BG is the panel root, SURFACE the
	// standard card, SURFACE_RAISED for tiles/hover states layered on a card,
	// ROW for list rows sitting directly on PANEL_BG.
	public static final Color PANEL_BG = new Color(25, 26, 29);
	public static final Color SURFACE = new Color(31, 33, 37);
	public static final Color SURFACE_RAISED = new Color(36, 38, 43);
	public static final Color ROW = new Color(34, 36, 40);
	public static final Color ROW_HOVER = new Color(41, 44, 49);
	public static final Color BORDER = new Color(50, 53, 59);

	// Text, brightest to faintest.
	public static final Color TEXT = new Color(238, 239, 241);
	public static final Color SOFT_TEXT = new Color(193, 196, 201);
	public static final Color MUTED_TEXT = new Color(132, 136, 144);
	public static final Color FAINT_TEXT = new Color(96, 100, 108);
	public static final Color HEADING_TEXT = new Color(165, 165, 165);

	// Accents. GOLD marks the primary pick, TEAL secondary info/locations,
	// BLUE banked/neutral counts, SUCCESS/WARNING/DANGER carry status.
	public static final Color GOLD = new Color(239, 181, 74);
	public static final Color TEAL = new Color(72, 201, 190);
	public static final Color BLUE = new Color(100, 157, 235);
	public static final Color SUCCESS = new Color(97, 194, 123);
	public static final Color WARNING = new Color(231, 166, 74);
	public static final Color DANGER = new Color(220, 103, 93);

	public static final int CARD_RADIUS = 12;
	public static final int ROW_RADIUS = 9;
	// Hard wrap width for body copy inside a card at default panel width.
	public static final int WRAP_WIDTH = 178;

	private Style()
	{
	}

	/** Standard card: SURFACE fill, CARD_RADIUS corners, 1px BORDER stroke. */
	public static RoundedPanel card()
	{
		return card(SURFACE);
	}

	public static RoundedPanel card(Color fill)
	{
		RoundedPanel panel = new RoundedPanel(fill, CARD_RADIUS, BORDER);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	/** Borderless list row: ROW fill, ROW_RADIUS corners. */
	public static RoundedPanel rowCard()
	{
		RoundedPanel panel = new RoundedPanel(ROW, ROW_RADIUS, null);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	public static JPanel transparentPanel()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		return panel;
	}

	public static JPanel transparentPanel(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	/** Small-caps eyebrow label ("CURRENT TASK", "LOADOUT", ...). */
	public static JLabel smallCaps(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Small body-copy label with a hard wrap width in pixels. */
	public static JLabel wrappedLabel(String text, Color color, int width)
	{
		JLabel label = new JLabel(
			"<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Inline "✓ Name" / "! Name" readiness check. */
	public static JLabel checkLabel(String name, boolean ok)
	{
		JLabel label = new JLabel((ok ? "✓ " : "! ") + name);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ok ? SUCCESS : WARNING);
		return label;
	}

	/**
	 * Applies the slim borderless scrollbar to a scroll pane. PluginPanel
	 * subclasses call this with their protected getScrollPane() result.
	 */
	public static void styleScrollPane(JScrollPane scrollPane)
	{
		if (scrollPane == null)
		{
			return;
		}
		scrollPane.setBorder(null);
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.getViewport().setBackground(PANEL_BG);
		JScrollBar bar = scrollPane.getVerticalScrollBar();
		bar.setUI(new ModernScrollBarUI());
		bar.setPreferredSize(new Dimension(7, 0));
		bar.setMinimumSize(new Dimension(7, 0));
		bar.setUnitIncrement(18);
		bar.setBlockIncrement(90);
		bar.setOpaque(false);
	}

	/**
	 * Dark surface tinted toward an accent — the attention-grabbing card
	 * background (e.g. the warm amber behind an emphasized warning).
	 * Amount around 0.12 keeps it clearly dark but visibly colored.
	 */
	public static Color tinted(Color accent, float amount)
	{
		return new Color(
			PANEL_BG.getRed() + Math.round((accent.getRed() - PANEL_BG.getRed()) * amount),
			PANEL_BG.getGreen() + Math.round((accent.getGreen() - PANEL_BG.getGreen()) * amount),
			PANEL_BG.getBlue() + Math.round((accent.getBlue() - PANEL_BG.getBlue()) * amount));
	}

	public static Color withAlpha(Color color, int alpha)
	{
		return new Color(
			color.getRed(),
			color.getGreen(),
			color.getBlue(),
			Math.max(0, Math.min(255, alpha)));
	}

	public static String shorten(String value, int max)
	{
		if (value == null || value.length() <= max)
		{
			return value == null ? "" : value;
		}
		return value.substring(0, Math.max(1, max - 1)) + "…";
	}

	public static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}
}
