package com.galesong.baautopilot.ui.kit.components;

import com.galesong.baautopilot.ui.kit.Style;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Section heading that folds its content away: same typography as
 * {@link SectionHeading} with a chevron, clicking toggles everything
 * added via {@link #addContent}. Collapsed by default.
 */
public class CollapsibleSection extends JPanel
{
	private final JPanel content;
	private final DisclosureButton chevron;
	private boolean expanded;

	public CollapsibleSection(String title, String subtitle)
	{
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(new EmptyBorder(8, 0, 0, 0));

		JPanel header = Style.transparentPanel(new BorderLayout(6, 0));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JPanel text = Style.transparentPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JPanel titleRow = Style.transparentPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(new HeadingLabel(title));
		titleRow.add(javax.swing.Box.createHorizontalStrut(6));
		titleRow.add(new HeadingRule());
		text.add(titleRow);
		if (subtitle != null && !subtitle.isEmpty())
		{
			JLabel subtitleLabel = new JLabel(subtitle);
			subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
			subtitleLabel.setForeground(Style.FAINT_TEXT);
			text.add(subtitleLabel);
		}
		header.add(text, BorderLayout.CENTER);

		chevron = new DisclosureButton(() -> setExpanded(!expanded));
		header.add(chevron, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				setExpanded(!expanded);
			}
		});
		add(header);

		content = Style.transparentPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(5, 0, 0, 0));
		content.setVisible(false);
		add(content);
	}

	/** Removes the built-in top padding — for a section at the top of a panel. */
	public CollapsibleSection noTopGap()
	{
		setBorder(new EmptyBorder(0, 0, 0, 0));
		return this;
	}

	public CollapsibleSection addContent(Component component)
	{
		content.add(component);
		return this;
	}

	public void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		content.setVisible(expanded);
		chevron.setExpanded(expanded);
		revalidate();
		repaint();
	}
}
