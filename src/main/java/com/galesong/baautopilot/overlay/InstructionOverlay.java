package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.BaAutopilotPlugin;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.state.BaTracker;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.TeammateView;
import com.galesong.baautopilot.state.WaveStats;
import com.galesong.baautopilot.tutorial.TutorialPilots;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Setter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * The instruction box, icons first:
 * <ol>
 * <li>role and wave, wave time on the right;</li>
 * <li>the item to use + action tag + headline, in the action's colour;</li>
 * <li>explanation lines, only while the step is new (or urgent);</li>
 * <li>calls: [out] item ✓/! &nbsp; [in] item;</li>
 * <li>a bar counting down to the next call;</li>
 * <li>stats: stopwatch call speed, then role counters as item icons with numbers.</li>
 * </ol>
 */
public class InstructionOverlay extends OverlayPanel
{
	private static final int WIDTH = 230;
	private static final int WARN_TICKS = 8;

	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;
	/** Null in previews. */
	@Nullable
	private final BaTracker tracker;

	/** Whether the box being drawn is the live one (not a tutorial page's). */
	private boolean live = true;

	/** Item sprites; swappable so previews can render without a client. */
	@Setter
	private IntFunction<BufferedImage> itemImages;

	/** The role panel's mouth and ear; null in previews. */
	@Nullable
	private final SpriteManager sprites;

	/** The tutorial page's box, shown instead of the live one while the tutorial runs; null in previews. */
	@Nullable
	private final TutorialPilots tutorialPilots;

	@Inject
	InstructionOverlay(BaAutopilotPlugin plugin, BaAutopilotConfig config, @Nullable ItemManager itemManager,
		@Nullable BaTracker tracker, @Nullable SpriteManager sprites, @Nullable TutorialPilots tutorialPilots)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.tracker = tracker;
		this.sprites = sprites;
		this.tutorialPilots = tutorialPilots;
		this.itemImages = itemManager == null ? id -> null : itemManager::getImage;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (tutorialPilots != null && plugin.isTutorialShown())
		{
			// The tutorial's page, in the box's own place: moving it here moves the real one. Pages
			// without a box hide it, so the lobby's own line doesn't read as part of the lesson
			TutorialPilots.View page = tutorialPilots.current();
			if (page == null)
			{
				return null;
			}
			Dimension size = render(g, page.getInstruction(), page.getSnapshot(), false);
			if (page.isHighlight())
			{
				spotlight(g, size);
			}
			return size;
		}
		return render(g, plugin.getInstruction(), plugin.getSnapshot(), true);
	}

	/**
	 * The box for any instruction: the live one, or a tutorial page's (live false: no tracker, no
	 * last-wave row), so the tutorial shows exactly what the plugin would.
	 */
	public Dimension render(Graphics2D g, Instruction ins, Snapshot s, boolean live)
	{
		if (ins == null || s == null || ins.getStep() == Step.NOT_IN_GAME || live && !s.isInArena())
		{
			return null;
		}
		this.live = live;
		Font bold = FontManager.getRunescapeBoldFont();
		// Red is for the owed call alone: the one moment a partner is waiting on you (reported
		// 2026-10-17: the box went red too often to mean anything)
		boolean urgent = ins.getStep() == Step.CALL;
		panelComponent.setBackgroundColor(urgent ? Palette.STONE_CALL : Palette.STONE);
		panelComponent.setBorder(new Rectangle(8, 6, 8, 7));
		panelComponent.setGap(new Point(0, 3));

		// Read in a second (reported 2026-10-19: too dense for steps that last a few seconds): who
		// and when, then the one thing to do and the calls; numbers come last. No explanation lines
		// (reported 2026-10-19: nobody has time to read them): what matters stays as numbers below
		Role role = s.getRole();
		if (s.isInArena())
		{
			WaveStats w = s.getWaveStats();
			add(new IconRow()
				.icon(roleBadge(role), 18)
				.text(role == null ? "BA" : role.getLabel().toUpperCase(), Palette.role(role), bold)
				.text(s.getWave() > 0 ? "Wave " + s.getWave() : "", Palette.DIM)
				.right(w == null ? "" : WaveStats.clock(w.durationTicks(s.getTick())), Palette.DIM));
			add(BoxParts.divider());
		}

		String tag = ins.getStep().getTag();
		add(new ActionRow(item(keyItem(ins, s)), tag, withoutTag(tag, ins.getHeadline()),
			urgent ? Palette.BAD : Palette.action(ins, ins.getStep().getKind().getColor())));
		if (live && s.isInLobby() && plugin.getLastWave() != null)
		{
			lastWave(plugin.getLastWave(), bold);
		}

		if (ins.getAdvice() != null)
		{
			// Low HP: alongside the step, not instead of it
			add(new IconRow().icon(Symbols.ALERT, 14).text(ins.getAdvice(), Palette.OK, bold));
		}
		if (ins.getAlsoCall() != null)
		{
			// The call rides along with the step: one horn click, on the move
			add(new IconRow()
				.icon(item(ins.getAlsoCall().getItemId()), 18)
				.text("+ Call " + ins.getAlsoCall().getKeyword() + "! (horn)", Palette.BAD, bold));
		}

		boolean calls = s.isInArena() && !s.isQueenAlive();
		boolean compact = !s.isInArena() || config.compactHud();
		if (calls)
		{
			// The call remains useful during healing, tool trips and other primary actions,
			// including in compact mode or when the called item is missing from inventory.
			// Calls stop mattering once the Queen is out
			calls(s, !compact);
		}
		if (!compact)
		{
			int mark = panelComponent.getChildren().size();
			if (config.liveStats())
			{
				stats(s);
			}
			if (config.waveInfo() && !s.isQueenAlive())
			{
				waveInfo(s);
			}
			if (config.liveCombat())
			{
				CombatRows.rows(s, ins, this::item, config.defenderGuide()).forEach(this::add);
			}
			if (role == Role.HEALER)
			{
				vialAndTeam(s);
			}
			if (role == Role.HEALER && config.healerTable())
			{
				healerTable(s);
			}
			if (panelComponent.getChildren().size() > mark)
			{
				// Numbers under a line of their own, apart from what to do
				panelComponent.getChildren().add(mark, BoxParts.divider());
			}
		}
		Dimension size = super.render(g);
		frame(g, size);
		return size;
	}

	/**
	 * "This box", for tutorial pages about it: a bright gold border and a light gold wash flashing
	 * on and off (on for most of each second, so it reads as a flash, not a flicker).
	 */
	private static void spotlight(Graphics2D g, Dimension size)
	{
		if (size == null || size.width <= 8 || size.height <= 8
			|| System.currentTimeMillis() % SPOTLIGHT_MS >= SPOTLIGHT_ON_MS)
		{
			return;
		}
		g.setColor(Palette.fill(Palette.GOLD, 38));
		g.fillRect(2, 2, size.width - 4, size.height - 4);
		java.awt.Stroke old = g.getStroke();
		g.setStroke(new java.awt.BasicStroke(3f));
		g.setColor(Palette.GOLD);
		g.drawRect(3, 3, size.width - 7, size.height - 7);
		g.setStroke(old);
	}

	private static final long SPOTLIGHT_MS = 1000;
	private static final long SPOTLIGHT_ON_MS = 600;

	/** The gold inner edge and dark outer edge of the game's interfaces. */
	private static void frame(Graphics2D g, Dimension size)
	{
		if (size == null || size.width <= 2 || size.height <= 2)
		{
			return;
		}
		g.setColor(Palette.GOLD_EDGE);
		g.drawRect(1, 1, size.width - 3, size.height - 3);
		g.setColor(Palette.DARK_EDGE);
		g.drawRect(0, 0, size.width - 1, size.height - 1);
	}

	/** "LOAD" + "Load your eggs" -> "your eggs" (the tag chip says the rest); otherwise the headline. */
	static String withoutTag(String tag, String headline)
	{
		if (headline == null)
		{
			return "";
		}
		if (!tag.isEmpty() && headline.toUpperCase().startsWith(tag + " "))
		{
			return headline.substring(tag.length() + 1);
		}
		return headline;
	}

	/**
	 * [out] shout item ✓ or !     [in] heard item (faded "old" when it is last call's)     18s,
	 * then the call's time left as a thin bar.
	 */
	private void calls(Snapshot s, boolean timer)
	{
		IconRow row = new IconRow();
		int left = s.getTicksToCallChange();
		boolean showTimer = timer && left >= 0;
		if (showTimer)
		{
			row.right((left * 3 / 5) + "s", left <= WARN_TICKS ? Palette.BAD : Palette.DIM);
		}
		callsRows(s, row);
		if (showTimer)
		{
			add(BoxParts.timer(left / (double) WaveInfo.TICKS_PER_CALL, left <= WARN_TICKS));
		}
	}

	/**
	 * The two calls, one a line, as the game's role panel shows them (reported 2026-10-19: the
	 * arrows didn't say which was which): the mouth and "Call" for the one you give, the ear and
	 * "Heard" for the one you act on, each with its item and word.
	 */
	private void callsRows(Snapshot s, IconRow first)
	{
		Font bold = FontManager.getRunescapeBoldFont();
		Font small = FontManager.getRunescapeSmallFont();
		boolean changing = live && tracker != null && tracker.callTextsChanging(s);
		IconRow mine = label(first, SpriteID.BarbassaultIcons.MOUTH, "Call");
		BaCall shout = s.getShoutCall();
		if (changing || shout == null)
		{
			// The HUD already shows the new call; the snapshot catches up next tick
			mine.text("...", Palette.DIM);
		}
		else
		{
			boolean covered = s.getShoutCoveredBy() != null;
			boolean done = s.isShoutDone() || covered;
			mine.icon(item(shout.getItemId())).text(shout.getKeyword(), done ? Palette.CREAM : Palette.BAD, bold)
				.icon(done ? Symbols.CHECK : Symbols.ALERT, 14);
			if (covered && !s.isShoutDone())
			{
				mine.text("by " + s.getShoutCoveredBy(), Palette.DIM, small);
			}
		}
		add(mine);

		IconRow heardRow = label(new IconRow(), SpriteID.BarbassaultIcons.EAR, "Heard");
		BaCall heard = s.getListenCall();
		if (changing || heard == null)
		{
			heardRow.text("...", Palette.DIM);
		}
		else
		{
			heardRow.icon(item(heard.getItemId()))
				.text(heard.getKeyword(), s.isListenFresh() ? Palette.CREAM : Palette.DIM, bold);
			if (!s.isListenFresh())
			{
				heardRow.text("old", Palette.OK, small);
			}
		}
		add(heardRow);
	}

	/** The role panel's own badge for a role, once loaded; null otherwise. */
	private BufferedImage roleBadge(Role role)
	{
		if (role == null || sprites == null)
		{
			return null;
		}
		switch (role)
		{
			case ATTACKER:
				return sprites.getSprite(SpriteID.BarbassaultIcons.HORN_FOR_ATTACKER, 0);
			case COLLECTOR:
				return sprites.getSprite(SpriteID.BarbassaultIcons.HORN_FOR_COLLECTOR, 0);
			case DEFENDER:
				return sprites.getSprite(SpriteID.BarbassaultIcons.HORN_FOR_DEFENDER, 0);
			default:
				return sprites.getSprite(SpriteID.BarbassaultIcons.HORN_FOR_HEALER, 0);
		}
	}

	/** The role panel's mouth or ear (once loaded), then the word, as a call line's label. */
	private IconRow label(IconRow row, int sprite, String word)
	{
		BufferedImage icon = sprites == null ? null : sprites.getSprite(sprite, 0);
		return row.icon(icon, 18).text(word, Palette.DIM, FontManager.getRunescapeSmallFont()).column(CALL_COLUMN);
	}

	/** Where both call lines' items start, so the two calls line up. */
	private static final int CALL_COLUMN = 56;

	/**
	 * Two penance types a line: how many are out, dead of the wave's total, and "+N" reserves (out
	 * only after a kill; OSRS Wiki wave table).
	 */
	private void waveInfo(Snapshot s)
	{
		int wave = s.getWave();
		if (!WaveInfo.valid(wave))
		{
			return;
		}
		// Two types a line: "Fighters 2 out 1/6 +1     Rangers - 0/6 +3"
		PenanceType[][] lines = {{PenanceType.FIGHTER, PenanceType.RANGER}, {PenanceType.RUNNER, PenanceType.HEALER}};
		Font small = FontManager.getRunescapeSmallFont();
		for (PenanceType[] pair : lines)
		{
			IconRow row = new IconRow().tight();
			for (PenanceType type : pair)
			{
				int total = WaveInfo.total(type, wave);
				int initial = WaveInfo.initial(type, wave);
				int dead = Math.min(total, s.getKillsByType().getOrDefault(type, 0));
				int out = s.alive(type).size();
				int spawned = Math.min(total, dead + out);
				int reserves = Math.min(total - initial, total - spawned);
				// The next-spawn seconds were dropped from here (2026-10-19): two types a line didn't fit them
				String count = dead + "/" + total + (reserves > 0 ? " +" + reserves : "");
				row.group().text(WAVE_NAMES[type.ordinal()], Palette.DIM, small)
					.text(out > 0 ? String.valueOf(out) : "-", out > 0 ? Palette.CREAM : Palette.DIM, out > 0 ? FontManager.getRunescapeBoldFont() : small)
					.text(count, dead >= total ? Palette.GOOD : Palette.DIM, small);
				if (type == pair[0])
				{
					row.group().text(" ", Palette.DIM, small);
				}
			}
			add(row);
		}
	}

	/** Short names in {@link PenanceType} order. */
	private static final String[] WAVE_NAMES = waveNames();

	private static String[] waveNames()
	{
		String[] names = new String[PenanceType.values().length];
		for (PenanceType t : PenanceType.values())
		{
			String n = t.name();
			names[t.ordinal()] = n.charAt(0) + n.substring(1).toLowerCase() + "s";
		}
		return names;
	}

	private void stats(Snapshot s)
	{
		WaveStats w = s.getWaveStats();
		if (w == null)
		{
			return;
		}
		double avg = WaveStats.meanSeconds(w.getCallReactions());
		IconRow row = new IconRow()
			.icon(Symbols.STOPWATCH, 14)
			.text(avg < 0 ? "-" : String.format("%.1fs", avg), Palette.seconds(avg));
		if (w.getCallsWrong() > 0)
		{
			row.icon(Symbols.ALERT, 12).text(String.valueOf(w.getCallsWrong()), Palette.BAD);
		}
		int mistakes = 0;
		switch (s.getRole())
		{
			case COLLECTOR:
				// Points count picked minus exploded, up to 60 (OSRS Wiki)
				int counted = Math.max(0, w.getEggsPicked() - w.getEggsWrong());
				row.group().icon(item(BaIds.EGG_RED)).text(s.getWave() == 10 ? String.valueOf(w.getEggsPicked()) : Math.min(counted, 60) + "/60",
						counted >= 60 && s.getWave() != 10 ? Palette.GOOD : Palette.TEXT)
					.group().icon(item(ItemID.BARBASSAULT_COLL_BAG_01)).text(String.valueOf(w.getLoads()), Palette.TEXT)
					.group().icon(Symbols.CROSSHAIR, 14).text(String.valueOf(w.getShots()), Palette.TEXT);
				mistakes = w.getEggsWrong();
				break;
			case HEALER:
				row.group().icon(item(ItemID.BARBASSAULT_POISION_03)).text(String.valueOf(w.getFoods()), Palette.TEXT);
				if (w.getHpHealed() > 0)
				{
					row.group().text("+" + w.getHpHealed() + " hp", Palette.GOOD);
				}
				mistakes = w.getWrongFoods();
				break;
			case DEFENDER:
				row.group().icon(item(ItemID.BARBASSAULT_RUNNER_FOOD)).text(String.valueOf(w.getRunnerKills()), Palette.TEXT);
				break;
			default:
				row.group().icon(item(ItemID.BARBASSAULT_BARBED_ARROW)).text(String.valueOf(w.getFighterRangerKills()), Palette.TEXT);
				mistakes = w.getImmuneHits();
				break;
		}
		if (mistakes > 0)
		{
			row.group().icon(Symbols.CROSS, 14).text(String.valueOf(mistakes), Palette.BAD);
		}
		add(row);
	}

	/** Between waves: W3 Collector 2:14 [stopwatch] 3.6s [egg] 19 [x] 1 */
	private void lastWave(WaveStats w, Font bold)
	{
		double calls = WaveStats.meanSeconds(w.getCallReactions());
		IconRow row = new IconRow()
			.text("W" + w.getWave(), Palette.role(w.getRole()), bold)
			.text(WaveStats.clock(w.durationTicks(0)), Palette.TEXT)
			.group().icon(Symbols.STOPWATCH, 14)
			.text(calls < 0 ? "-" : String.format("%.1fs", calls), Palette.seconds(calls));
		int mistakes = 0;
		switch (w.getRole())
		{
			case COLLECTOR:
				row.group().icon(item(BaIds.EGG_RED)).text(String.valueOf(w.getEggsPicked()), Palette.TEXT);
				mistakes = w.getEggsWrong();
				break;
			case HEALER:
				row.group().icon(item(ItemID.BARBASSAULT_POISION_03)).text(String.valueOf(w.getFoods()), Palette.TEXT);
				mistakes = w.getWrongFoods();
				break;
			case DEFENDER:
				row.group().icon(item(ItemID.BARBASSAULT_RUNNER_FOOD)).text(String.valueOf(w.getRunnerKills()), Palette.TEXT);
				break;
			default:
				row.group().icon(item(ItemID.BARBASSAULT_BARBED_ARROW)).text(String.valueOf(w.getFighterRangerKills()), Palette.TEXT);
				mistakes = w.getImmuneHits();
				break;
		}
		if (mistakes > 0)
		{
			row.group().icon(Symbols.CROSS, 14).text(String.valueOf(mistakes), Palette.BAD);
		}
		add(row);
	}

	/** [vial] 3/4   Zezima 45%   Lynx 70%: doses left and the two most hurt teammates. */
	private void vialAndTeam(Snapshot s)
	{
		int charges = BaIds.vialCharges(s::has);
		IconRow row = new IconRow();
		if (charges >= 0)
		{
			row.icon(item(BaIds.vialItem(s::has)), 22)
				.text(charges + "/4", charges == 0 ? Palette.BAD : charges == 1 ? Palette.OK : Palette.TEXT,
					FontManager.getRunescapeBoldFont());
		}
		s.getTeammates().stream()
			.filter(t -> t.getMaxHp() > 0)
			.sorted(Comparator.comparingDouble(TeammateView::fraction))
			.limit(2)
			.forEach(t -> row.group()
				.text(shortName(t.getName()), Palette.MUTED)
				.text(Math.round(t.fraction() * 100) + "%",
					t.fraction() < 0.35 ? Palette.BAD : t.fraction() < 0.70 ? Palette.OK : Palette.GOOD));
		if (!row.isEmpty())
		{
			add(row);
		}
	}

	private static String shortName(String name)
	{
		return name.length() <= 8 ? name : name.substring(0, 7) + ".";
	}

	/** #1 ● 2 [food] 80% per penance healer. */
	private void healerTable(Snapshot s)
	{
		BaCall food = s.getListenCall();
		List<PenanceView> healers = s.penance(PenanceType.HEALER);
		healers.sort(Comparator.comparingInt(PenanceView::getOrder));
		for (PenanceView h : healers)
		{
			add(new IconRow()
				.text("#" + h.getOrder(), Palette.MUTED)
				.icon(Symbols.dot(h.nextPoisonHit(s.getTick()) > 0 ? Palette.GOOD : Palette.OK), 14)
				.text(String.valueOf(h.getFoods()), Palette.TEXT)
				.icon(food == null ? null : item(food.getItemId()), 14)
				.right(Math.round(h.hpFraction() * 100) + "%", Palette.TEXT));
		}
	}

	/** The item the instruction is about, for its icon: what to use, pick up or call. */
	public static int keyItem(Instruction ins, Snapshot s)
	{
		if (ins.getStep() == Step.CALL && s.getShoutCall() != null)
		{
			return s.getShoutCall().getItemId();
		}
		if (ins.getTarget().getKind() == Target.Kind.GROUND_ITEM)
		{
			return ins.getTarget().getId();
		}
		for (int id : ins.getItems())
		{
			if (!BaIds.HORNS.contains(id))
			{
				return id;
			}
		}
		switch (ins.getStep())
		{
			case COL_FIRE:
				return s.getEggsRed() > 0 ? BaIds.EGG_RED : BaIds.EGG_GREEN;
			case QUEEN_FIRE:
				return BaIds.EGG_OMEGA;
			case ATT_ATTACK:
			case ATT_GET_ARROWS:
			case DEF_TAKE_BAIT:
			case HEAL_TAKE_FOOD:
				return s.getListenCall() == null ? -1 : s.getListenCall().getItemId();
			case DEF_LOGS:
			case DEF_REPAIR:
				return BaIds.LOGS;
			case DEF_HAMMER:
				return BaIds.HAMMER;
			default:
				return -1;
		}
	}

	private BufferedImage item(int id)
	{
		return id <= 0 ? null : itemImages.apply(id);
	}

	private void add(LayoutableRenderableEntity e)
	{
		panelComponent.getChildren().add(e);
	}
}
