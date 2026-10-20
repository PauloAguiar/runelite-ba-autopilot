package com.galesong.baautopilot.ui;

import com.galesong.baautopilot.ui.kit.Style;
import com.galesong.baautopilot.ui.kit.components.CollapsibleSection;
import com.galesong.baautopilot.ui.kit.components.DisclosureButton;
import com.galesong.baautopilot.ui.kit.components.PanelShell;
import com.galesong.baautopilot.ui.kit.components.PluginHeader;
import com.galesong.baautopilot.ui.kit.components.RoundedButton;
import com.galesong.baautopilot.ui.kit.components.RoundedPanel;
import com.galesong.baautopilot.ui.kit.components.StyledComboBox;
import com.galesong.baautopilot.ui.kit.components.ToggleSwitch;
import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.model.DefenderGuide;
import com.galesong.baautopilot.model.HealCodeSet;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.overlay.Palette;
import com.galesong.baautopilot.plan.AttackMethod;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.tutorial.Tutorial;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Role settings and observed BA progress. All updates run on the Swing thread. */
public class BaAutopilotPanel extends PluginPanel
{
	private final Map<Role, JLabel> progress = new EnumMap<>(Role.class);
	private final List<Consumer<BaAutopilotConfig>> readers = new ArrayList<>();
	private BiConsumer<String, Object> onOption = (key, value) -> { };
	/** Config refreshes must not write options back or rebuild expanded sections. */
	private boolean syncing;
	private final RoundedButton tutorialButton = RoundedButton.primary("Start tutorial");
	private final JLabel tutorialStatus = Style.wrappedLabel("", Style.SOFT_TEXT, Style.WRAP_WIDTH);
	private Consumer<Boolean> onTutorial = on -> { };
	private Consumer<Tutorial> onTutorialChoice = t -> { };
	private boolean showingTutorial;
	private DisclosureButton tutorialChevron;
	private final StyledComboBox<Tutorial> tutorialChoice = new StyledComboBox<>(Tutorial.values());
	private boolean tutorialOn;
	private final PluginHeader header;
	private final JLabel tutorialBadge = new JLabel();

	public BaAutopilotPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(Style.PANEL_BG);
		PanelShell shell = new PanelShell();
		header = new PluginHeader("BA Autopilot", "Role settings");
		header.getIconLabel().setIcon(new ImageIcon(navIcon()));
		shell.getHeader().add(discordLink());
		shell.getHeader().add(header);
		shell.getContent().add(tutorialCard());
		for (Role role : Role.values()) shell.getContent().add(roleSection(role));
		shell.getContent().add(generalOptions());
		add(shell, BorderLayout.CENTER);
		setOptions(new BaAutopilotConfig() { });
		setTutorial(false, "");
		updateProgress(null);
	}

	/** The community Discord, shared with the GPS plugin: feedback while the plugin is in testing. */
	static final String DISCORD_URL = "https://discord.gg/7VAbrPsUzT";

	/** "Join the Discord" with the Discord mark, at the panel's top left; opens the invite in the browser. */
	private JComponent discordLink()
	{
		JLabel link = new JLabel("Join the Discord");
		link.setName("discord");
		java.awt.image.BufferedImage mark = net.runelite.client.util.ImageUtil.loadImageResource(BaAutopilotPanel.class,
			"/com/galesong/baautopilot/discord.png");
		if (mark != null)
		{
			link.setIcon(new ImageIcon(net.runelite.client.util.ImageUtil.resizeImage(mark, 16, 16)));
		}
		link.setIconTextGap(5);
		link.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		link.setForeground(Style.MUTED_TEXT);
		link.setToolTipText("BA Autopilot is in testing: share feedback and suggestions on Discord");
		link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		link.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				net.runelite.client.util.LinkBrowser.browse(DISCORD_URL);
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				link.setForeground(Style.TEXT);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				link.setForeground(Style.MUTED_TEXT);
			}
		});
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(0, 0, 6, 0));
		row.add(link);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	public void setOnOption(BiConsumer<String, Object> onOption)
	{
		this.onOption = onOption;
	}

	public void setOnTutorial(Consumer<Boolean> onTutorial)
	{
		this.onTutorial = onTutorial;
	}

	/** Shows the tutorial running (Next went on to the next one) without reporting it back as a pick. */
	public void showTutorial(Tutorial tutorial)
	{
		if (tutorialChoice.getSelectedItem() == tutorial)
		{
			return;
		}
		showingTutorial = true;
		try
		{
			tutorialChoice.setSelectedItem(tutorial);
		}
		finally
		{
			showingTutorial = false;
		}
	}

	/** The tutorial picked in the dropdown. */
	public void setOnTutorialChoice(Consumer<Tutorial> onTutorialChoice)
	{
		this.onTutorialChoice = onTutorialChoice;
	}

	/** Tutorial on/off and where it stands ("Enter wave 1 to begin", "Running"). */
	public void setTutorial(boolean on, String status)
	{
		tutorialOn = on;
		tutorialButton.setText(on ? "Stop tutorial" : "Start tutorial");
		tutorialStatus.setText("<html><body style='width:" + Style.WRAP_WIDTH + "px'>" + Style.escape(status) + "</body></html>");
		tutorialStatus.setVisible(status != null && !status.isEmpty());
	}

	private JComponent tutorialCard()
	{
		JPanel body = column();
		// Header: badge, title and a chevron; clicking it folds the controls away
		JPanel content = column();
		DisclosureButton chevron = new DisclosureButton(() -> foldTutorial(content));
		tutorialChevron = chevron;
		JPanel title = Style.transparentPanel(new BorderLayout(6, 0));
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		title.add(tutorialBadge, BorderLayout.WEST);
		title.add(Style.smallCaps("TUTORIAL", Style.GOLD), BorderLayout.CENTER);
		title.add(chevron, BorderLayout.EAST);
		title.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent event)
			{
				foldTutorial(content);
			}
		});
		body.add(title);
		content.add(Box.createVerticalStrut(6));
		tutorialChoice.setName("tutorial.choice");
		tutorialChoice.getAccessibleContext().setAccessibleName("Tutorial");
		tutorialChoice.setAlignmentX(LEFT_ALIGNMENT);
		tutorialChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		tutorialChoice.addActionListener(event ->
		{
			Object picked = tutorialChoice.getSelectedItem();
			if (!showingTutorial && picked instanceof Tutorial)
			{
				onTutorialChoice.accept((Tutorial) picked);
			}
		});
		content.add(tutorialChoice);
		content.add(Box.createVerticalStrut(6));
		tutorialButton.setName("tutorial.toggle");
		tutorialButton.setAlignmentX(LEFT_ALIGNMENT);
		tutorialButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		tutorialButton.addActionListener(event -> onTutorial.accept(!tutorialOn));
		content.add(tutorialButton);
		content.add(Box.createVerticalStrut(5));
		tutorialStatus.setAlignmentX(LEFT_ALIGNMENT);
		content.add(tutorialStatus);
		body.add(content);
		chevron.setExpanded(true);
		RoundedPanel card = Style.card();
		card.setLayout(new BorderLayout());
		card.setBorder(new EmptyBorder(9, 9, 8, 9));
		card.add(body, BorderLayout.CENTER);
		card.setName("tutorial");
		JPanel wrap = column();
		wrap.add(card);
		wrap.add(Box.createVerticalStrut(8));
		return wrap;
	}

	/** Opens or folds the tutorial card's controls. */
	private void foldTutorial(JPanel content)
	{
		boolean open = !content.isVisible();
		content.setVisible(open);
		tutorialChevron.setExpanded(open);
		revalidate();
		repaint();
	}

	public void setOptions(BaAutopilotConfig config)
	{
		syncing = true;
		try { readers.forEach(read -> read.accept(config)); }
		finally { syncing = false; }
	}

	public void updateProgress(Snapshot snapshot)
	{
		for (Role role : Role.values())
		{
			int level = snapshot == null ? 0 : snapshot.getRoleLevels().getOrDefault(role, 0);
			Integer points = snapshot == null || level <= 0 ? null : snapshot.getRolePoints().get(role);
			progress.get(role).setText("Level " + (level > 0 ? level : "—") + " · "
				+ (points == null ? "—" : String.format(java.util.Locale.ROOT, "%,d", points)) + " Honour points");
		}
	}

	private JComponent roleSection(Role role)
	{
		JPanel body = column();
		body.add(Style.smallCaps(role.getLabel().toUpperCase(java.util.Locale.ROOT), Palette.role(role)));
		JLabel level = label("");
		level.setName(role.name() + ".progress");
		level.setForeground(Style.MUTED_TEXT);
		level.setToolTipText("Role level and available Honour points read from the game");
		progress.put(role, level);
		body.add(level);
		body.add(Box.createVerticalStrut(8));
		CollapsibleSection advanced = advanced(role.name() + ".advanced", "Advanced");
		switch (role)
		{
			case ATTACKER:
				body.add(choice("attackMethod", "Attack method", AttackMethod.values(), BaAutopilotConfig::attackMethod));
				advanced.addContent(toggle("splitTargets", "Split targets with other attacker", BaAutopilotConfig::splitTargets));
				break;
			case DEFENDER:
				body.add(choice("defenderGuide", "Bait guide", DefenderGuide.values(), BaAutopilotConfig::defenderGuide));
				advanced.addContent(toggle("defenderPattern", "Opening bait pattern", BaAutopilotConfig::defenderPattern));
				advanced.addContent(toggle("defenderMultiKill", "Hold for timed multikills", BaAutopilotConfig::defenderMultiKill));
				advanced.addContent(toggle("safetyBait", "Safety bait", BaAutopilotConfig::safetyBait));
				advanced.addContent(toggle("showMarks", "Show pattern tiles", BaAutopilotConfig::showMarks));
				advanced.addContent(toggle("showLureRange", "Show bait range", BaAutopilotConfig::showLureRange));
				advanced.addContent(toggle("runnerOutline", "Runner state outlines", BaAutopilotConfig::runnerOutline));
				advanced.addContent(toggle("baitCounts", "Bait counts on tiles", BaAutopilotConfig::baitCounts));
				break;
			case COLLECTOR:
				body.add(number("collectorLoadAt", "Blue-call batch", 1, 20, BaAutopilotConfig::collectorLoadAt,
					"Useful eggs held in inventory and bag; a preference for loading during blue calls"));
				body.add(Box.createVerticalStrut(5));
				body.add(number("queenEggBatch", "Queen yellow batch", 1, 8, BaAutopilotConfig::queenEggBatch,
					"Yellow eggs to gather before passing the batch to one Healer"));
				body.add(Box.createVerticalStrut(5));
				body.add(number("queenEggBuffer", "Queen reserve eggs", 0, 8, BaAutopilotConfig::queenEggBuffer,
					"Extra yellows held until the first batch's omegas are fired and the Queen survives"));
				advanced.addContent(toggle("queenSingleLoads", "One omega at a time", BaAutopilotConfig::queenSingleLoads));
				advanced.addContent(toggle("colourEggs", "Colour egg names and spots", BaAutopilotConfig::colourEggs));
				break;
			default:
				body.add(number("healTeammateBelow", "Heal below (%)", 10, 95, BaAutopilotConfig::healTeammateBelow,
					"Teammate healing threshold during ordinary waves"));
				advanced.addContent(choice("healCodeSet", "Timing guide", HealCodeSet.values(), BaAutopilotConfig::healCodeSet));
				advanced.addContent(toggle("poisonOutline", "Poison level outlines", BaAutopilotConfig::poisonOutline));
				advanced.addContent(toggle("healerTable", "Show Penance Healer table", BaAutopilotConfig::healerTable));
				break;
		}
		body.add(advanced);
		RoundedPanel card = Style.card();
		card.setLayout(new BorderLayout());
		card.setBorder(new EmptyBorder(9, 9, 8, 9));
		card.add(body, BorderLayout.CENTER);
		card.setName(role.name() + ".settings");
		JPanel wrap = column();
		wrap.add(card);
		wrap.add(Box.createVerticalStrut(8));
		return wrap;
	}

	private JComponent generalOptions()
	{
		CollapsibleSection general = advanced("general.advanced", "General & display");
		general.addContent(number("retreatBelow", "Retreat below (%)", 5, 80, BaAutopilotConfig::retreatBelow,
			"Retreat threshold for non-Healer roles"));
		general.addContent(toggle("skipCoveredCalls", "Skip calls a teammate made", BaAutopilotConfig::skipCoveredCalls));
		general.addContent(toggle("queenStack", "Team stacks at Queen cannon", BaAutopilotConfig::queenStack));
		general.addContent(toggle("hornLeftClick", "Horn left-click makes the call", BaAutopilotConfig::hornLeftClick));
		general.addContent(toggle("hideOffRoleAttack", "Hide off-role Attack options", BaAutopilotConfig::hideOffRoleAttack));
		general.addContent(toggle("hideBlockers", "Hide Penance over pickups", BaAutopilotConfig::hideBlockers));
		general.addContent(toggle("compactHud", "Compact instruction box", BaAutopilotConfig::compactHud));
		general.addContent(toggle("showPath", "Show walking route", BaAutopilotConfig::showPath));
		general.addContent(toggle("highlightTarget", "Highlight targets", BaAutopilotConfig::highlightTarget));
		general.addContent(toggle("highlightItems", "Highlight inventory items", BaAutopilotConfig::highlightItems));
		general.addContent(toggle("debugPriorities", "Show debug overlays", BaAutopilotConfig::debugPriorities));
		return general;
	}

	private ToggleSwitch toggle(String key, String text, Function<BaAutopilotConfig, Boolean> read)
	{
		ToggleSwitch control = new ToggleSwitch(text);
		control.setText("<html><body style='width:140px'>" + Style.escape(text) + "</body></html>");
		control.setMargin(new Insets(3, 0, 3, 0));
		control.setHorizontalAlignment(SwingConstants.LEFT);
		control.setMaximumSize(new Dimension(Integer.MAX_VALUE, control.getPreferredSize().height));
		control.setName(key);
		control.getAccessibleContext().setAccessibleName(text);
		control.addActionListener(event -> option(key, control.isSelected()));
		readers.add(config -> control.setSelected(read.apply(config)));
		return control;
	}

	private <E> JComponent choice(String key, String text, E[] values, Function<BaAutopilotConfig, E> read)
	{
		StyledComboBox<E> control = new StyledComboBox<>(values);
		control.setName(key);
		control.getAccessibleContext().setAccessibleName(text);
		control.setAlignmentX(LEFT_ALIGNMENT);
		control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		control.addActionListener(event -> option(key, control.getSelectedItem()));
		readers.add(config -> control.setSelectedItem(read.apply(config)));
		JPanel field = column();
		field.add(label(text));
		field.add(Box.createVerticalStrut(3));
		field.add(control);
		return field;
	}

	private JComponent number(String key, String text, int min, int max, Function<BaAutopilotConfig, Integer> read, String hint)
	{
		JSpinner control = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
		control.setName(key);
		control.setToolTipText(hint);
		control.getAccessibleContext().setAccessibleName(text);
		control.setPreferredSize(new Dimension(58, 25));
		control.setBorder(new LineBorder(Style.BORDER));
		control.setBackground(Style.SURFACE_RAISED);
		JSpinner.NumberEditor editor = new JSpinner.NumberEditor(control, "0");
		control.setEditor(editor);
		editor.getTextField().setFont(FontManager.getRunescapeSmallFont());
		editor.getTextField().setForeground(Style.TEXT);
		editor.getTextField().setBackground(Style.SURFACE_RAISED);
		editor.getTextField().setCaretColor(Style.TEXT);
		editor.getTextField().setBorder(new EmptyBorder(1, 3, 1, 3));
		control.addChangeListener(event -> option(key, control.getValue()));
		readers.add(config -> control.setValue(Math.max(min, Math.min(max, read.apply(config)))));
		JPanel row = Style.transparentPanel(new BorderLayout(6, 0));
		JLabel caption = label(text);
		caption.setLabelFor(control);
		caption.setToolTipText(hint);
		row.add(caption, BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		return row;
	}

	private void option(String key, Object value)
	{
		if (!syncing && value != null) onOption.accept(key, value);
	}

	private static CollapsibleSection advanced(String name, String title)
	{
		CollapsibleSection section = new CollapsibleSection(title, "");
		section.setName(name);
		section.setExpanded(false);
		return section;
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Style.SOFT_TEXT);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel column()
	{
		JPanel panel = Style.transparentPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** The game's Barbarian Assault icon, once loaded: in the header (24 px) and the tutorial card (20 px). */
	public void setBaIcon(BufferedImage icon)
	{
		header.getIconLabel().setIcon(new ImageIcon(scaled(icon, 24)));
		tutorialBadge.setIcon(new ImageIcon(scaled(icon, 20)));
		revalidate();
		repaint();
	}

	public static BufferedImage scaled(BufferedImage icon, int size)
	{
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(icon, 0, 0, size, size, null);
		g.dispose();
		return out;
	}

	/** Painted BA glyph shared by the sidebar button and header. */
	public static BufferedImage navIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(150, 40, 35));
		g.fillRoundRect(1, 1, 14, 14, 5, 5);
		g.setColor(Style.GOLD);
		g.setStroke(new BasicStroke(1.4f));
		g.drawRoundRect(1, 1, 14, 14, 5, 5);
		g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 11f));
		g.drawString("BA", 3, 12);
		g.dispose();
		return image;
	}
}
