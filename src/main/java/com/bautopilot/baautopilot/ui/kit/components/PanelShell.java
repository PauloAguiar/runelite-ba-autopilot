package com.bautopilot.baautopilot.ui.kit.components;

import com.bautopilot.baautopilot.ui.kit.Style;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;

/**
 * Panel skeleton with pinned regions: components added to
 * {@link #getHeader()} stay fixed at the top and {@link #getFooter()}
 * at the bottom, while {@link #getContent()} scrolls between them with
 * the kit scrollbar.
 *
 * Use inside a {@code PluginPanel(false)} (unwrapped) so RuneLite does
 * not add its own outer scroll pane around the pinned regions.
 */
public class PanelShell extends JPanel
{
	private final JPanel header = Style.transparentPanel();
	private final JPanel footer = Style.transparentPanel();
	private final ScrollableColumn content = new ScrollableColumn();

	public PanelShell()
	{
		super(new BorderLayout(0, 8));
		setBackground(Style.PANEL_BG);
		setBorder(new EmptyBorder(5, 3, 9, 3));

		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

		JScrollPane scroll = new JScrollPane(
			content,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		Style.styleScrollPane(scroll);

		add(header, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
	}

	/** Pinned area above the scroll region. */
	public JPanel getHeader()
	{
		return header;
	}

	/** The scrolling middle column (vertical BoxLayout). */
	public JPanel getContent()
	{
		return content;
	}

	/** Pinned area below the scroll region. */
	public JPanel getFooter()
	{
		return footer;
	}

	/** Column that fits the viewport width so cards never scroll sideways. */
	private static final class ScrollableColumn extends JPanel implements Scrollable
	{
		private ScrollableColumn()
		{
			setOpaque(false);
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 18;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return 90;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
