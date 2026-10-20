package com.galesong.baautopilot.ui.kit.components;

import com.galesong.baautopilot.ui.kit.Style;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Section divider: stylized title with a rule running from the last
 * letter to the right edge, an optional faint subtitle, and room for a
 * trailing control such as a Show backups toggle. Ships with breathing
 * room above it — call {@link #noTopGap()} on the first heading of a
 * panel. Alignment defaults to left; pass {@code SwingConstants.CENTER}
 * or {@code RIGHT} for the others (the rule mirrors accordingly).
 */
public class SectionHeading extends JPanel
{
	private static final int TOP_GAP = 8;

	private SectionControls controls;

	public SectionHeading(String title, String subtitle)
	{
		this(title, subtitle, SwingConstants.LEFT);
	}

	public SectionHeading(String title, String subtitle, int alignment)
	{
		super(new BorderLayout(6, 0));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(new EmptyBorder(TOP_GAP, 0, 0, 0));

		JPanel text = Style.transparentPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JPanel titleRow = Style.transparentPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		HeadingLabel titleLabel = new HeadingLabel(title);
		if (alignment == SwingConstants.CENTER)
		{
			titleRow.add(new HeadingRule());
			titleRow.add(Box.createHorizontalStrut(6));
			titleRow.add(titleLabel);
			titleRow.add(Box.createHorizontalStrut(6));
			titleRow.add(new HeadingRule());
		}
		else if (alignment == SwingConstants.RIGHT)
		{
			titleRow.add(new HeadingRule());
			titleRow.add(Box.createHorizontalStrut(6));
			titleRow.add(titleLabel);
		}
		else
		{
			titleRow.add(titleLabel);
			titleRow.add(Box.createHorizontalStrut(6));
			titleRow.add(new HeadingRule());
		}
		text.add(titleRow);

		if (subtitle != null && !subtitle.isEmpty())
		{
			JLabel subtitleLabel = new JLabel(subtitle);
			subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
			subtitleLabel.setForeground(Style.FAINT_TEXT);
			// A full-width row with glue, not alignmentX: BoxLayout misplaces
			// children whose alignments differ from their siblings'.
			JPanel subtitleRow = Style.transparentPanel();
			subtitleRow.setLayout(new BoxLayout(subtitleRow, BoxLayout.X_AXIS));
			subtitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (alignment != SwingConstants.LEFT)
			{
				subtitleRow.add(Box.createHorizontalGlue());
			}
			subtitleRow.add(subtitleLabel);
			if (alignment != SwingConstants.RIGHT)
			{
				subtitleRow.add(Box.createHorizontalGlue());
			}
			text.add(subtitleRow);
		}
		add(text, BorderLayout.CENTER);
	}

	/** Removes the built-in top padding — for the first heading of a panel. */
	public SectionHeading noTopGap()
	{
		setBorder(new EmptyBorder(0, 0, 0, 0));
		return this;
	}

	/**
	 * Attaches this section's control row under the title, inside the
	 * heading block. With {@code compact}, the row starts hidden behind a
	 * gear button on the heading (muted closed, teal open).
	 */
	public SectionHeading setControls(SectionControls controls, boolean compact)
	{
		this.controls = controls;
		add(controls, BorderLayout.SOUTH);
		if (compact)
		{
			controls.setVisible(false);
			IconButton gear = new IconButton(new GearIcon(Style.MUTED_TEXT), "Section controls");
			gear.addActionListener(event ->
			{
				boolean show = !controls.isVisible();
				controls.setVisible(show);
				gear.setIcon(new GearIcon(show ? Style.TEAL : Style.MUTED_TEXT));
				revalidate();
				repaint();
			});
			setTrailing(gear);
		}
		return this;
	}

	@Override
	public Dimension getMaximumSize()
	{
		// Grows with subtitle and controls row instead of a fixed cap.
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	/**
	 * Places a control (usually a {@link RoundedButton}) at the right edge.
	 * Trailing controls get the compact bold font — at full button size the
	 * pair "title + control" no longer fits the panel width and the title
	 * clips.
	 */
	public void setTrailing(Component component)
	{
		component.setFont(FontManager.getRunescapeSmallFont().deriveFont(java.awt.Font.BOLD));
		// Top-aligned wrapper: BorderLayout EAST would otherwise stretch the
		// control across the whole heading (title + subtitle + controls).
		JPanel wrap = Style.transparentPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.add(component);
		wrap.add(Box.createVerticalGlue());
		add(wrap, BorderLayout.EAST);
	}
}
