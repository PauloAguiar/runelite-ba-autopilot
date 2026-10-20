package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import net.runelite.client.ui.FontManager;

/**
 * Dropdown in the kit look: rounded SURFACE field with a chevron, dark
 * popup list with ROW_HOVER selection. Non-editable use is assumed.
 */
public class StyledComboBox<T> extends JComboBox<T>
{
	public StyledComboBox(T[] items)
	{
		super(items);
		setOpaque(false);
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(Style.TEXT);
		setBackground(Style.SURFACE);
		setBorder(new EmptyBorder(1, 4, 1, 1));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumRowCount(8);
		setRenderer(new Renderer());
		setUI(new KitComboBoxUI());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Style.SURFACE);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
		g.setColor(hasFocus() || isPopupVisible() ? Style.GOLD : Style.BORDER);
		g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 10, 10);
		g.dispose();
		super.paintComponent(graphics);
	}

	private static final class KitComboBoxUI extends BasicComboBoxUI
	{
		@Override
		protected JButton createArrowButton()
		{
			JButton button = new JButton()
			{
				@Override
				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g = (Graphics2D) graphics.create();
					g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g.setColor(Style.MUTED_TEXT);
					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					g.drawLine(cx - 3, cy - 2, cx, cy + 2);
					g.drawLine(cx, cy + 2, cx + 3, cy - 2);
					g.dispose();
				}
			};
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setFocusPainted(false);
			button.setBorder(new EmptyBorder(0, 4, 0, 6));
			return button;
		}

		@Override
		public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus)
		{
			// The rounded field is painted by the component itself.
		}

		@Override
		protected ComboPopup createPopup()
		{
			BasicComboPopup popup = (BasicComboPopup) super.createPopup();
			popup.setBorder(new LineBorder(Style.BORDER));
			JList<?> list = popup.getList();
			list.setBackground(Style.SURFACE);
			list.setSelectionBackground(Style.ROW_HOVER);
			list.setSelectionForeground(Style.TEXT);
			return popup;
		}
	}

	private static final class Renderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(
			JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			setFont(FontManager.getRunescapeSmallFont());
			setBorder(new EmptyBorder(4, 8, 4, 8));
			if (index == -1)
			{
				// Current value inside the field: transparent over the rounded fill.
				setOpaque(false);
				setForeground(Style.TEXT);
			}
			else
			{
				setOpaque(true);
				setBackground(isSelected ? Style.ROW_HOVER : Style.SURFACE);
				setForeground(isSelected ? Style.TEXT : Style.SOFT_TEXT);
			}
			return this;
		}
	}
}
