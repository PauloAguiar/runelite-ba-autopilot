package com.galesong.baautopilot.ui.kit.components;

import com.galesong.baautopilot.ui.kit.Style;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Row of compact controls belonging to one section — attach it with
 * {@link SectionHeading#setControls} so it renders inside the heading
 * block, directly under the title. Mix text chips, state toggles and
 * icon buttons; align the row left, center or right.
 */
public class SectionControls extends JPanel
{
	private boolean hasControls;
	private int insertIndex;

	public SectionControls()
	{
		this(SwingConstants.LEFT);
	}

	public SectionControls(int alignment)
	{
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(new EmptyBorder(4, 0, 0, 0));

		if (alignment != SwingConstants.LEFT)
		{
			add(Box.createHorizontalGlue());
			insertIndex = 1;
		}
		if (alignment != SwingConstants.RIGHT)
		{
			add(Box.createHorizontalGlue());
		}
	}

	/** Compact text-chip action. */
	public SectionControls addButton(String text, Runnable action)
	{
		RoundedButton button = compactButton(text);
		button.setForeground(Style.SOFT_TEXT);
		button.addActionListener(event -> action.run());
		addControl(button);
		return this;
	}

	/** Text chip carrying an on/off state (teal when on, muted when off). */
	public SectionControls addToggleButton(String text, boolean initial, Consumer<Boolean> onChange)
	{
		boolean[] state = {initial};
		RoundedButton button = compactButton(text);
		button.setForeground(state[0] ? Style.TEAL : Style.MUTED_TEXT);
		button.addActionListener(event ->
		{
			state[0] = !state[0];
			button.setForeground(state[0] ? Style.TEAL : Style.MUTED_TEXT);
			onChange.accept(state[0]);
		});
		addControl(button);
		return this;
	}

	/** Icon control with a hover circle (e.g. a {@link GearIcon}). */
	public SectionControls addIcon(Icon icon, String tooltip, Runnable action)
	{
		IconButton button = new IconButton(icon, tooltip);
		button.addActionListener(event -> action.run());
		addControl(button);
		return this;
	}

	private static RoundedButton compactButton(String text)
	{
		RoundedButton button = new RoundedButton(text);
		button.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		button.setBorder(new EmptyBorder(3, 8, 3, 8));
		return button;
	}

	private void addControl(JComponent control)
	{
		if (hasControls)
		{
			add(Box.createHorizontalStrut(4), insertIndex);
			insertIndex++;
		}
		// Chips and icon buttons differ in height; center them on one line.
		control.setAlignmentY(Component.CENTER_ALIGNMENT);
		add(control, insertIndex);
		insertIndex++;
		hasControls = true;
		revalidate();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
