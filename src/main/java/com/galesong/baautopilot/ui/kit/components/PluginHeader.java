package com.galesong.baautopilot.ui.kit.components;

import com.galesong.baautopilot.ui.kit.Style;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * Panel identity block: 34px icon, bold title, muted one-line subtitle
 * ("BETA 2  •  owned loadout optimizer" style).
 *
 * The icon label is exposed so callers can attach an item image:
 * {@code itemManager.getImage(itemId).addTo(header.getIconLabel())}.
 */
public class PluginHeader extends JPanel
{
	private final JLabel icon = new JLabel();

	public PluginHeader(String title, String subtitle)
	{
		super(new BorderLayout(9, 0));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));

		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(34, 34));
		add(icon, BorderLayout.WEST);

		JPanel names = Style.transparentPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 15f));
		titleLabel.setForeground(Style.TEXT);
		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subtitleLabel.setForeground(Style.MUTED_TEXT);
		names.add(titleLabel);
		names.add(Box.createVerticalStrut(1));
		names.add(subtitleLabel);
		add(names, BorderLayout.CENTER);
	}

	public JLabel getIconLabel()
	{
		return icon;
	}
}
