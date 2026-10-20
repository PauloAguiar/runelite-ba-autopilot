package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.BaAutopilotPlugin;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.plan.TileMark;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.state.BaTracker;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/** Highlights the current target and the plan tiles in the world. */
public class SceneOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 4;
	private static final int OUTLINE_FEATHER = 2;
	private static final int BADGE_ICON = 18;
	private static final Color MARK_DONE = new Color(60, 220, 90, 160);
	/** Defender bait range outline. */
	private static final Color LURE_RANGE = new Color(95, 155, 255, 110);
	/** Poison's next hit 0..4: red, orange, yellow, light green, green. */
	private static final Color[] POISON_LEVEL = {
		new Color(230, 50, 50), new Color(255, 150, 40), new Color(240, 220, 60), new Color(150, 230, 90), new Color(40, 200, 70)};
	/** Runner states in {@link com.galesong.baautopilot.plan.RunnerState} order: escaping .. stacked. */
	private static final Color[] RUNNER_STATE = {
		new Color(230, 50, 50), new Color(255, 150, 40), new Color(240, 220, 60), new Color(150, 230, 90), new Color(40, 200, 70)};
	private static final Color LURE_GOOD = new Color(90, 220, 120, 190);
	private static final Color LURE_BAD = Palette.fill(Palette.BAD_BAIT, 190);
	private static final Color BAIT_GOOD_TEXT = new Color(90, 220, 120);
	private static final Color BAIT_BAD_TEXT = Palette.BAD_BAIT;
	private static final Color MARK_GOOD = new Color(255, 200, 40, 200);
	private static final Color MARK_BAD = new Color(190, 120, 255, 200);

	private final Client client;
	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;
	private final BaTracker tracker;
	private final ModelOutlineRenderer outlines;

	private final IntFunction<BufferedImage> itemImages;
	private BufferedImage badgeIcon;

	@Inject
	SceneOverlay(Client client, BaAutopilotPlugin plugin, BaAutopilotConfig config, BaTracker tracker,
		ModelOutlineRenderer outlines, ItemManager itemManager)
	{
		this.itemImages = itemManager::getImage;
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.tracker = tracker;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Instruction ins = plugin.getInstruction();
		if (ins == null || plugin.getSnapshot() == null || !plugin.getSnapshot().isInArena())
		{
			return null;
		}
		if (config.showMarks())
		{
			for (TileMark mark : ins.getMarks())
			{
				Color c = markColour(mark);
				tile(g, mark.getTile(), c, mark.getLabel());
			}
		}
		if (config.colourEggs())
		{
			calledEggs(g, plugin.getSnapshot());
		}
		if (config.showLureRange())
		{
			lureRange(g, plugin.getSnapshot());
		}
		if (config.baitCounts() && plugin.getSnapshot().getRole() == Role.DEFENDER)
		{
			baitCounts(g, plugin.getSnapshot());
		}
		if (config.poisonOutline() && plugin.getSnapshot().getRole() == Role.HEALER)
		{
			poisonOutlines(plugin.getSnapshot());
		}
		if (config.runnerOutline() && plugin.getSnapshot().getRole() == Role.DEFENDER)
		{
			runnerOutlines(plugin.getSnapshot());
		}
		if (config.highlightTarget())
		{
			List<Target> attackWarnings = attackWarnings(plugin.getSnapshot(), ins, tracker.callTextsChanging(plugin.getSnapshot()));
			// A call is made from wherever you are: its target is the next step's, marked as that step
			boolean calling = ins.getStep() == Step.CALL && ins.getThen() != null;
			Step shown = calling ? ins.getThen() : ins.getStep();
			Color color = ins.getStep() == Step.CALL && !calling ? config.urgentColor()
				: config.colourByAction() ? shown.getKind().getColor() : config.highlightColor();
			color = Palette.action(ins, color);
			int key = calling ? -1 : InstructionOverlay.keyItem(ins, plugin.getSnapshot());
			badgeIcon = key > 0 ? itemImages.apply(key) : null;
			if (!attackWarnings.contains(ins.getTarget()))
			{
				target(g, ins.getTarget(), color, targetTag(ins));
			}
			// Missing call context survives gear, calls and movement instructions. During the
			// short HUD/snapshot mismatch, replace any old ATTACK badge with the same warning.
			badgeIcon = null;
			for (Target warning : attackWarnings)
			{
				target(g, warning, Palette.MUTED, "DON'T ATTACK");
			}
		}
		return null;
	}

	static Color markColour(TileMark mark)
	{
		return !mark.isGood() ? Palette.fill(MARK_BAD, mark.isDone() ? 125 : 200) : mark.isDone() ? MARK_DONE : MARK_GOOD;
	}

	/** Use the planner's safe count rather than the static step tag or parsing its headline. */
	static String targetTag(Instruction instruction)
	{
		Step shown = instruction.getStep() == Step.CALL && instruction.getThen() != null
			? instruction.getThen() : instruction.getStep();
		boolean eggs = (shown == Step.COL_COLLECT || shown == Step.QUEEN_COLLECT)
			&& instruction.getTarget().getKind() == Target.Kind.GROUND_ITEM;
		return shown.getTag() + (eggs && instruction.getPickupCount() > 1 ? " ×" + instruction.getPickupCount() : "")
			+ (eggs && instruction.isRightClick() ? " (right-click)" : "");
	}

	/** Only warn over living, visible Attacker enemies, preferring actual and suggested targets. */
	static List<Target> attackWarnings(Snapshot s, Instruction ins, boolean callChanging)
	{
		List<Target> warnings = new ArrayList<>();
		if (s == null || ins == null || !s.isInArena() || s.getRole() != Role.ATTACKER || s.isQueenAlive()) return warnings;
		BaCall call = s.getListenCall();
		if (!callChanging && s.isListenFresh() && call != null && call.getReceiver() == Role.ATTACKER) return warnings;
		int suggested = ins.getTarget().getKind() == Target.Kind.NPC ? ins.getTarget().getId() : -1;
		for (int id : new int[]{s.getInteractingNpc(), suggested})
		{
			addAttackWarning(warnings, s, id);
		}
		if (warnings.isEmpty()) addAttackWarning(warnings, s, s.getLastTargetNpc());
		return warnings;
	}

	private static void addAttackWarning(List<Target> warnings, Snapshot s, int npcIndex)
	{
		for (PenanceView p : s.getPenance())
		{
			if (p.getNpcIndex() == npcIndex && !p.isDying() && !p.isUnseen()
				&& (p.getType() == PenanceType.FIGHTER || p.getType() == PenanceType.RANGER)
				&& warnings.stream().noneMatch(t -> t.getId() == npcIndex))
			{
				warnings.add(Target.npc(p.getNpcIndex(), p.getTile()));
				return;
			}
		}
	}

	private void target(Graphics2D g, Target t, Color color, String tag)
	{
		switch (t.getKind())
		{
			case NPC:
				NPC npc = tracker.getNpcs().get(t.getId());
				if (npc != null)
				{
					safeOutline(() -> outlines.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER));
					badge(g, npc.getCanvasTextLocation(g, tag, npc.getLogicalHeight() + 40), tag, color);
				}
				else if (t.getTile() != null)
				{
					// Out of view: mark where it was last seen
					tile(g, t.getTile(), color, tag + ": last seen", true);
				}
				break;
			case OBJECT:
				TileObject obj = t.getTile() == null ? null : tracker.findObject(t.getId(), t.getTile());
				Shape hull = obj == null ? null : obj.getClickbox();
				if (hull != null)
				{
					OverlayUtil.renderPolygon(g, hull, color, Palette.fill(color, 50), new BasicStroke(3));
					badge(g, obj.getCanvasTextLocation(g, tag, 160), tag, color);
				}
				else
				{
					tile(g, t.getTile(), color, tag, true);
				}
				break;
			case GROUND_ITEM:
			{
				// Drawn where the game put the item, falling back to converting its tile
				LocalPoint live = tracker.groundItemLocation(t.getId(), t.getTile());
				if (live != null)
				{
					tileAt(g, live, color, t.getLabel() == null ? tag : tag + ": " + t.getLabel(), true);
					break;
				}
				tile(g, t.getTile(), color, t.getLabel() == null ? tag : tag + ": " + t.getLabel(), true);
				break;
			}
			case TILE:
				tile(g, t.getTile(), color, t.getLabel() == null ? tag : tag + ": " + t.getLabel(), true);
				break;
			case PLAYER:
				for (Player p : client.getTopLevelWorldView().players())
				{
					if (p != null && t.getLabel() != null && t.getLabel().equalsIgnoreCase(p.getName()))
					{
						safeOutline(() -> outlines.drawOutline(p, OUTLINE_WIDTH, color, OUTLINE_FEATHER));
						badge(g, p.getCanvasTextLocation(g, tag, p.getLogicalHeight() + 40), tag, color);
					}
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Defender: each bait on the ground with the square of tiles in which a runner notices it (4 at
	 * defender level 1, 5 from level 2) - green for good bait, red for bad. While you're told to
	 * drop bait, a fainter square around you previews where the next one lands.
	 */
	private void lureRange(Graphics2D g, Snapshot s)
	{
		if (s.getRole() != Role.DEFENDER)
		{
			return;
		}
		int size = 2 * s.baitRange() + 1;
		BasicStroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);
		java.util.Set<WorldPoint> drawn = new java.util.HashSet<>();
		for (com.galesong.baautopilot.state.GroundItemView bait : s.getGroundItems())
		{
			if (!com.galesong.baautopilot.model.BaIds.BAIT.contains(bait.getItemId()) || !drawn.add(bait.getTile()))
			{
				continue;
			}
			LocalPoint lp = tracker.groundItemLocation(bait.getItemId(), bait.getTile());
			if (lp == null)
			{
				lp = Scenes.toLocal(client, bait.getTile());
			}
			Polygon area = lp == null ? null : Perspective.getCanvasTileAreaPoly(client, lp, size);
			if (area != null)
			{
				g.setStroke(dashed);
				g.setColor(bait.isGood() ? LURE_GOOD : LURE_BAD);
				g.draw(area);
			}
		}
		Instruction ins = plugin.getInstruction();
		boolean dropping = ins != null && (ins.getStep() == Step.DEF_DROP || ins.getStep() == Step.DEF_RELURE);
		LocalPoint me = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getLocalLocation();
		if (dropping && me != null)
		{
			Polygon area = Perspective.getCanvasTileAreaPoly(client, me, size);
			if (area != null)
			{
				g.setStroke(dashed);
				g.setColor(LURE_RANGE);
				g.draw(area);
			}
		}
	}

	/**
	 * Healer: every penance healer outlined by its poison's next hit, so you see at a glance which
	 * one needs food (red: none left or never fed) and which is still working (green: fresh).
	 */
	/** A Penance Healer's poison outline colour: red at 0 (unfed or worn off) up to green at 4. */
	public static Color poisonColour(int poison)
	{
		return POISON_LEVEL[Math.max(0, Math.min(4, poison))];
	}

	private void poisonOutlines(Snapshot s)
	{
		for (com.galesong.baautopilot.state.PenanceView h : s.penance(com.galesong.baautopilot.model.PenanceType.HEALER))
		{
			NPC npc = tracker.getNpcs().get(h.getNpcIndex());
			if (npc == null)
			{
				continue;
			}
			Color c = poisonColour(h.nextPoisonHit(s.getTick()));
			safeOutline(() -> outlines.drawOutline(npc, 2, c, 2));
		}
	}

	/** Defender: every runner outlined by how well the food trail holds it ({@link RunnerState}). */
	private void runnerOutlines(Snapshot s)
	{
		for (com.galesong.baautopilot.state.PenanceView r : s.penance(com.galesong.baautopilot.model.PenanceType.RUNNER))
		{
			NPC npc = tracker.getNpcs().get(r.getNpcIndex());
			if (npc == null)
			{
				continue;
			}
			Color c = RUNNER_STATE[com.galesong.baautopilot.plan.RunnerState.of(s, r).ordinal()];
			safeOutline(() -> outlines.drawOutline(npc, 2, c, 2));
		}
	}

	/**
	 * Defender: how many good and bad bait lie on each tile ("4" green, "1" red), on a dark
	 * backing just above the tile, so a spot's count reads at a glance without hovering the pile.
	 */
	private void baitCounts(Graphics2D g, Snapshot s)
	{
		java.util.Map<WorldPoint, int[]> counts = new java.util.LinkedHashMap<>();
		for (GroundItemView bait : s.getGroundItems())
		{
			if (bait.getTile() != null && com.galesong.baautopilot.model.BaIds.BAIT.contains(bait.getItemId()))
			{
				counts.computeIfAbsent(bait.getTile(), t -> new int[2])[bait.isGood() ? 0 : 1] += Math.max(1, bait.getQuantity());
			}
		}
		Font old = g.getFont();
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		for (java.util.Map.Entry<WorldPoint, int[]> e : counts.entrySet())
		{
			LocalPoint lp = Scenes.toLocal(client, e.getKey());
			if (lp == null)
			{
				continue;
			}
			int good = e.getValue()[0];
			int bad = e.getValue()[1];
			String goodText = good > 0 ? good + " good" : "";
			String badText = bad > 0 ? bad + " bad" : "";
			String gap = good > 0 && bad > 0 ? "  " : "";
			Point at = Perspective.getCanvasTextLocation(client, g, lp, goodText + gap + badText, 30);
			if (at == null)
			{
				continue;
			}
			int w = fm.stringWidth(goodText + gap + badText);
			int h = fm.getHeight();
			g.setColor(new Color(0, 0, 0, 175));
			g.fillRoundRect(at.getX() - 4, at.getY() - h + 2, w + 8, h + 2, 6, 6);
			int x = at.getX();
			x = shadowed(g, goodText, x, at.getY(), BAIT_GOOD_TEXT);
			x = shadowed(g, gap, x, at.getY(), Color.WHITE);
			shadowed(g, badText, x, at.getY(), BAIT_BAD_TEXT);
		}
		g.setFont(old);
	}

	/** Draws text with a 1px shadow; returns the x after it. */
	private static int shadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		if (text.isEmpty())
		{
			return x;
		}
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
		return x + g.getFontMetrics().stringWidth(text);
	}

	/** A called egg a left-click takes. */
	public static final Color EGG_LEFT_CLICK = new Color(90, 220, 120);
	/** A called egg under another item: right-click the pile and choose Take. */
	public static final Color EGG_RIGHT_CLICK = new Color(245, 215, 60);

	/** The dot on a called egg: green when a left-click takes it, yellow when it needs a right-click. */
	public static Color eggDotColour(boolean underneath)
	{
		return underneath ? EGG_RIGHT_CLICK : EGG_LEFT_CLICK;
	}

	/**
	 * Collector: a dot on every egg of the called colour, so they stand out of the pile: green when a
	 * left-click takes it, yellow when another item is on top and it needs a right-click and Take.
	 * (Reported 2026-10-19: replaces reordering the menu, which may not pass a Plugin Hub review.)
	 */
	private void calledEggs(Graphics2D g, Snapshot s)
	{
		BaCall call = s.getListenCall();
		if (s.getRole() != Role.COLLECTOR || call == null || !s.isListenFresh() || Palette.egg(call.getItemId()) == null)
		{
			return;
		}
		for (GroundItemView egg : s.getGroundItems())
		{
			if (egg.getItemId() != call.getItemId())
			{
				continue;
			}
			LocalPoint lp = Scenes.toLocal(client, egg.getTile());
			Point p = lp == null ? null : Perspective.localToCanvas(client, lp, client.getTopLevelWorldView().getPlane(), 20);
			if (p == null)
			{
				continue;
			}
			eggDot(g, p, eggDotColour(egg.isUnderneath()));
		}
	}

	private void tile(Graphics2D g, WorldPoint template, Color color, String label)
	{
		tile(g, template, color, label, false);
	}

	private void tile(Graphics2D g, WorldPoint template, Color color, String label, boolean asBadge)
	{
		LocalPoint lp = Scenes.toLocal(client, template);
		if (lp != null)
		{
			tileAt(g, lp, color, label, asBadge);
		}
	}

	private void tileAt(Graphics2D g, LocalPoint lp, Color color, String label, boolean asBadge)
	{
		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly != null)
		{
			OverlayUtil.renderPolygon(g, poly, color, Palette.fill(color, asBadge ? 70 : 40), new BasicStroke(asBadge ? 3 : 2));
		}
		if (label != null && !label.isEmpty())
		{
			Point text = Perspective.getCanvasTextLocation(client, g, lp, label, asBadge ? 60 : 0);
			if (asBadge)
			{
				badge(g, text, label, color);
			}
			else if (text != null)
			{
				OverlayUtil.renderTextLocation(g, text, label, color);
			}
		}
	}

	private void badge(Graphics2D g, Point at, String text, Color color)
	{
		badge(g, at, text, color, badgeIcon);
	}

	/**
	 * [item icon] + bold coloured tag on a dark rounded backing, readable against any part of the
	 * arena. The tutorial's marks use it too, so they look like the plugin's.
	 */
	public static void badge(Graphics2D g, Point at, String text, Color color, BufferedImage badgeIcon)
	{
		if (at == null || text == null || text.isEmpty())
		{
			return;
		}
		Font old = g.getFont();
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int icon = badgeIcon == null ? 0 : BADGE_ICON + 3;
		int w = fm.stringWidth(text) + icon;
		int h = Math.max(fm.getHeight(), icon == 0 ? 0 : BADGE_ICON + 2);
		int x = at.getX() - icon / 2;
		int y = at.getY();
		g.setColor(new Color(0, 0, 0, 175));
		g.fillRoundRect(x - 4, y - h + 2, w + 8, h + 2, 6, 6);
		g.setColor(color);
		g.drawRoundRect(x - 4, y - h + 2, w + 8, h + 2, 6, 6);
		if (badgeIcon != null)
		{
			g.drawImage(badgeIcon, x, y - h + 3, BADGE_ICON, BADGE_ICON, null);
		}
		g.setColor(Color.BLACK);
		g.drawString(text, x + icon + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x + icon, y);
		g.setFont(old);
	}

	/** The dot the plugin puts on every egg of the called colour, centred on a canvas point. */
	public static void eggDot(Graphics2D g, Point p, Color colour)
	{
		g.setColor(Color.BLACK);
		g.fillOval(p.getX() - 6, p.getY() - 6, 12, 12);
		g.setColor(colour);
		g.fillOval(p.getX() - 5, p.getY() - 5, 10, 10);
	}

	private static void safeOutline(Runnable r)
	{
		try
		{
			r.run();
		}
		catch (RuntimeException ignored)
		{
			// the model can unload between the lookup and the draw
		}
	}
}
