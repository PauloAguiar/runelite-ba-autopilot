package com.galesong.baautopilot.tutorial;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.overlay.ItemHighlightOverlay;
import com.galesong.baautopilot.overlay.Palette;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The page's inventory, in a panel beside the tutorial box: the items the moment calls for, outlined as the
 * plugin outlines them in a wave (ItemHighlightOverlay's own rules), and a right-click menu open
 * on the item the page is about, its option flashing.
 */
public class TutorialInventoryOverlay extends Overlay
{
	private static final int COLUMNS = 4;
	private static final int SLOTS = 28;
	/** The game's inventory grid: 42 x 36 cells, 36 x 32 items. */
	private static final int CELL_W = 42;
	private static final int CELL_H = 36;
	private static final int ITEM_W = 36;
	private static final int ITEM_H = 32;
	private static final Color COVER = new Color(62, 53, 41, 250);
	/** The game's menu: brown body, black title bar, white options, orange item names. */
	private static final Color MENU_BODY = new Color(93, 84, 71);
	private static final Color MENU_TITLE = new Color(0, 0, 0);
	private static final Color MENU_ITEM = new Color(255, 144, 64);
	private static final int MENU_ROW = 15;
	private static final int MENU_TITLE_H = 19;
	private static final long FLASH_MS = 1000;
	private static final long FLASH_ON_MS = 600;

	private final Client client;
	private final ItemManager itemManager;
	private final TutorialSteps steps;
	private final TutorialPilots pilots;
	private final BaAutopilotConfig config;
	/** The panel sits to its right. */
	private final TutorialBoxOverlay tutorialBox;
	private BooleanSupplier shown = () -> false;

	@Inject
	TutorialInventoryOverlay(Client client, ItemManager itemManager, TutorialSteps steps, TutorialPilots pilots,
		BaAutopilotConfig config, TutorialBoxOverlay tutorialBox)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.steps = steps;
		this.pilots = pilots;
		this.config = config;
		this.tutorialBox = tutorialBox;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void setShown(BooleanSupplier shown)
	{
		this.shown = shown;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!shown.getAsBoolean())
		{
			return null;
		}
		TutorialSteps.Page page = steps.current();
		TutorialPilots.View view = pilots.current();
		Map<Integer, Integer> items = new LinkedHashMap<>();
		if (view != null && view.getSnapshot().getRole() != null)
		{
			// Every player carries their role's horn, and a Collector the collection bag
			items.put(Tutorials.horn(view.getSnapshot().getRole()), 1);
			if (view.getSnapshot().getRole() == com.galesong.baautopilot.model.Role.COLLECTOR)
			{
				items.put(net.runelite.api.gameval.ItemID.BARBASSAULT_COLL_BAG_01, 1);
			}
			items.putAll(view.getSnapshot().getInventory());
		}
		else
		{
			items.putAll(page.getItems());
		}
		TutorialSteps.Menu menu = page.getMenu();
		// The column right of the tutorial box: the role panel on top, the inventory under it
		Rectangle box = tutorialBox.getBounds();
		boolean placed = box != null && box.width > 0;
		int columnX = placed ? Math.min(box.x + box.width + GAP, client.getCanvasWidth() - COLUMN_W - 2)
			: client.getCanvasWidth() - COLUMN_W - 10;
		int columnY = placed ? box.y : 10;
		if (page.getHud() != null)
		{
			columnY = tutorialBox.drawHud(g, page.getHud(), columnX, columnY, COLUMN_W) + GAP;
		}
		if (items.isEmpty() && menu == null)
		{
			return null;
		}
		if (menu != null)
		{
			items.putIfAbsent(menu.getItemId(), 1);
		}

		// Slots in inventory order: unstackable items take one slot each
		List<int[]> slots = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			boolean stacks = stackable(e.getKey());
			int copies = stacks ? 1 : Math.max(1, e.getValue());
			for (int c = 0; c < copies && slots.size() < SLOTS; c++)
			{
				slots.add(new int[]{e.getKey(), stacks ? e.getValue() : 1, stacks ? 1 : 0});
			}
		}

		// Beside the tutorial box, only as many rows as the items fill (reported 2026-10-19: over
		// the real inventory it was hard to see)
		int rows = (slots.size() + COLUMNS - 1) / COLUMNS;
		Rectangle panel = new Rectangle(columnX, columnY, COLUMN_W, TITLE_H + rows * CELL_H + PAD);
		g.setColor(COVER);
		g.fillRect(panel.x, panel.y, panel.width, panel.height);
		g.setColor(Palette.GOLD_EDGE);
		g.drawRect(panel.x + 1, panel.y + 1, panel.width - 3, panel.height - 3);
		g.setColor(Palette.DARK_EDGE);
		g.drawRect(panel.x, panel.y, panel.width - 1, panel.height - 1);
		g.setFont(FontManager.getRunescapeBoldFont());
		shadowed(g, "Inventory", panel.x + PAD, panel.y + TITLE_H - 4, Palette.GOLD);
		int gridX = panel.x + (COLUMN_W - COLUMNS * CELL_W) / 2;
		int gridY = panel.y + TITLE_H;

		Rectangle menuSlot = null;
		for (int i = 0; i < slots.size(); i++)
		{
			int id = slots.get(i)[0];
			int quantity = slots.get(i)[1];
			boolean stacks = slots.get(i)[2] == 1;
			Rectangle r = new Rectangle(gridX + (i % COLUMNS) * CELL_W + (CELL_W - ITEM_W) / 2,
				gridY + (i / COLUMNS) * CELL_H + (CELL_H - ITEM_H) / 2, ITEM_W, ITEM_H);
			BufferedImage image = itemManager.getImage(id, quantity, stacks && quantity > 1);
			g.drawImage(image, r.x, r.y, null);
			if (view != null)
			{
				ItemHighlightOverlay.draw(g, itemManager, id, quantity, r,
					ItemHighlightOverlay.highlight(id, view.getInstruction(), view.getSnapshot(), false), config);
			}
			if (menu != null && id == menu.getItemId() && menuSlot == null)
			{
				menuSlot = r;
			}
		}
		if (menu != null && menuSlot != null)
		{
			menu(g, menu, menuSlot);
		}
		return null;
	}

	private static final int PAD = 6;
	private static final int TITLE_H = 20;
	private static final int GAP = 8;
	/** The right-hand column's width: the role panel's call text fits, the inventory grid is centred. */
	private static final int COLUMN_W = 230;

	/** The menu as the game draws it, opened beside the item (not over it); the page's option flashes. */
	private void menu(Graphics2D g, TutorialSteps.Menu menu, Rectangle on)
	{
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		String name = name(menu.getItemId());
		int width = fm.stringWidth("Choose Option");
		for (String option : menu.getOptions())
		{
			width = Math.max(width, fm.stringWidth(option + " " + name));
		}
		width += 8;
		int height = MENU_TITLE_H + menu.getOptions().size() * MENU_ROW + 3;
		// Beside the item so it stays in view (left of it when there's no room on the right)
		int x = on.x + on.width + 6;
		if (x + width > client.getCanvasWidth() - 2)
		{
			x = Math.max(2, on.x - width - 6);
		}
		int y = Math.max(2, Math.min(on.y - 2, client.getCanvasHeight() - height - 2));
		g.setColor(MENU_BODY);
		g.fillRect(x, y, width, height);
		g.setColor(MENU_TITLE);
		g.fillRect(x + 1, y + 1, width - 2, MENU_TITLE_H - 3);
		g.drawRect(x, y, width - 1, height - 1);
		g.setColor(MENU_BODY);
		g.drawString("Choose Option", x + 3, y + MENU_TITLE_H - 5);
		boolean flash = System.currentTimeMillis() % FLASH_MS < FLASH_ON_MS;
		for (int i = 0; i < menu.getOptions().size(); i++)
		{
			int rowY = y + MENU_TITLE_H + i * MENU_ROW;
			String option = menu.getOptions().get(i);
			boolean pick = i == menu.getPick();
			if (pick && flash)
			{
				g.setColor(Palette.fill(Palette.GOLD, 90));
				g.fillRect(x + 1, rowY, width - 2, MENU_ROW);
			}
			int base = rowY + MENU_ROW - 3;
			shadowed(g, option, x + 3, base, pick && flash ? Palette.GOLD : Color.WHITE);
			shadowed(g, name, x + 3 + fm.stringWidth(option + " "), base, MENU_ITEM);
		}
	}

	private boolean stackable(int id)
	{
		ItemComposition item = itemManager.getItemComposition(id);
		return item != null && item.isStackable();
	}

	private String name(int id)
	{
		ItemComposition item = itemManager.getItemComposition(id);
		return item == null ? "" : item.getName();
	}

	private static void shadowed(Graphics2D g, String text, int x, int baseline, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}
}
