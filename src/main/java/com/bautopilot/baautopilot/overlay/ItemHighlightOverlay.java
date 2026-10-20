package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.BaTracker;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Current-call knowledge stays visible alongside the primary action's inventory items. */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	public enum Highlight { NONE, ACTION, CALL, CORRECT, GOOD_BAIT, BAD_BAIT }

	private final BaAutopilotPlugin plugin;
	private final BaAutopilotConfig config;
	private final ItemManager itemManager;
	private final BaTracker tracker;

	@Inject
	ItemHighlightOverlay(BaAutopilotPlugin plugin, BaAutopilotConfig config, ItemManager itemManager, BaTracker tracker)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.tracker = tracker;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		Instruction ins = plugin.getInstruction();
		Snapshot s = plugin.getSnapshot();
		if (!config.highlightItems() || s == null)
		{
			return;
		}
		Highlight highlight = highlight(itemId, ins, s, tracker.callTextsChanging(s));
		if (highlight == Highlight.NONE) return;
		draw(g, itemManager, itemId, widgetItem.getQuantity(), widgetItem.getCanvasBounds(), highlight, config);
	}

	/** The outline (and GOOD / BAD label) for an item drawn at {@code bounds}; the tutorial's inventory uses it too. */
	public static void draw(Graphics2D g, ItemManager itemManager, int itemId, int quantity, Rectangle bounds,
		Highlight highlight, BaAutopilotConfig config)
	{
		if (highlight == Highlight.NONE) return;
		Color color = color(highlight, config);
		BufferedImage outline = itemManager.getItemOutline(itemId, quantity, color);
		g.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		if (highlight == Highlight.GOOD_BAIT || highlight == Highlight.BAD_BAIT)
		{
			Font previous = g.getFont();
			g.setFont(FontManager.getRunescapeSmallFont());
			OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(bounds.x + 1, bounds.y + bounds.height - 1),
				highlight == Highlight.GOOD_BAIT ? "GOOD" : "BAD", color);
			g.setFont(previous);
		}
	}

	static Color color(Highlight highlight, BaAutopilotConfig config)
	{
		return highlight == Highlight.CALL ? config.urgentColor()
			: highlight == Highlight.BAD_BAIT ? Palette.BAD_BAIT
			: highlight == Highlight.CORRECT || highlight == Highlight.GOOD_BAIT ? Palette.GOOD
			: config.highlightColor();
	}

	/** Context is derived afresh from the call, never from a suspended or previous instruction. */
	public static Highlight highlight(int itemId, Instruction ins, Snapshot s, boolean callChanging)
	{
		if (ins == null || s == null || !s.isInArena()) return Highlight.NONE;
		BaCall call = s.getListenCall();
		boolean roleItem = BaCall.isItemOf(s.getRole(), itemId);
		boolean current = !s.isQueenAlive() && !callChanging && s.isListenFresh()
			&& call != null && call.getReceiver() == s.getRole();
		if (current && roleItem)
		{
			if (s.getRole() == Role.DEFENDER)
			{
				// Either uncalled bait is valid bad food, regardless of the planner's example item.
				return itemId == call.getItemId() ? Highlight.GOOD_BAIT : Highlight.BAD_BAIT;
			}
			if (itemId == call.getItemId()) return Highlight.CORRECT;
		}
		// A late HUD update or stale call must not leave old food/arrows marked as usable.
		// Collector deletion/loading is independent of the called egg colour.
		if (roleItem && !current && s.getRole() != Role.COLLECTOR) return Highlight.NONE;
		if (!ins.getItems().contains(itemId)) return Highlight.NONE;
		return ins.getStep() == Step.CALL && BaIds.HORNS.contains(itemId) ? Highlight.CALL : Highlight.ACTION;
	}
}
