package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import java.util.HashMap;
import java.util.Map;

/**
 * Eggs in the collector's collection bag, which the inventory doesn't show. Recorded behaviour
 * (8 waves, 2026-10-15): the first eggs picked go into the bag ("You put the egg in the bag.", 2 at
 * collector level 1), the rest into the inventory, and "Load" on a hopper puts bag and inventory
 * eggs in together ("You load the hopper."), each colour up to 5.
 */
public class EggBag
{
	/** "You put the egg in the bag." credits the egg taken this many ticks before. */
	static final int TAKE_WINDOW_TICKS = 6;
	static final int HOPPER_CAP = 5;

	private static final String PUT_IN_BAG = "You put the egg in the bag.";
	private static final String LOADED = "You load the hopper.";

	private final Map<Integer, Integer> eggs = new HashMap<>();
	private int lastTake = -1;
	private int lastTakeTick = Integer.MIN_VALUE;
	private int hopperGreen;
	private int hopperRed;
	private int hopperBlue;
	private Map<Integer, Integer> inventory = new HashMap<>();
	private Map<Integer, Integer> emptyFrom;
	private int emptyTick;

	public void reset()
	{
		eggs.clear();
		lastTake = -1;
		lastTakeTick = Integer.MIN_VALUE;
		inventory.clear();
		emptyFrom = null;
	}

	/** Hopper loads as of the latest tick (the egg-count varbits). */
	public void hoppers(int green, int red, int blue)
	{
		hopperGreen = green;
		hopperRed = red;
		hopperBlue = blue;
	}

	/** A menu click, tags removed: Take names a colour; Empty starts a transfer confirmation. */
	public void menu(String option, String target, int tick)
	{
		if (option == null || target == null)
		{
			return;
		}
		if ("Take".equals(option))
		{
			emptyFrom = null;
			int id = eggId(target);
			if (id > 0)
			{
				lastTake = id;
				lastTakeTick = tick;
			}
		}
		else if ("Empty".equals(option) && target.toLowerCase().contains("bag"))
		{
			// A full inventory can reject or only partially empty the bag. Wait for transferred eggs.
			emptyFrom = new HashMap<>(inventory);
			emptyTick = tick;
		}
	}

	/** Confirm an Empty transfer by colour instead of erasing hidden eggs on the menu click. */
	public void inventory(Map<Integer, Integer> observed, int tick)
	{
		if (emptyFrom != null)
		{
			boolean moved = false;
			if (tick - emptyTick <= TAKE_WINDOW_TICKS)
			{
				for (int id : new int[]{BaIds.EGG_RED, BaIds.EGG_GREEN, BaIds.EGG_BLUE})
				{
					int gained = Math.max(0, observed.getOrDefault(id, 0) - emptyFrom.getOrDefault(id, 0));
					if (gained > 0) { eggs.put(id, Math.max(0, count(id) - gained)); moved = true; }
				}
			}
			if (moved || tick - emptyTick > TAKE_WINDOW_TICKS) emptyFrom = null;
		}
		inventory = new HashMap<>(observed);
	}

	/** A chat line, tags removed. {@code listen} is the collector's current call, the fallback colour. */
	public void message(String msg, BaCall listen, int tick)
	{
		if (PUT_IN_BAG.equals(msg))
		{
			long takeAge = (long) tick - lastTakeTick;
			int id = lastTake > 0 && takeAge >= 0 && takeAge <= TAKE_WINDOW_TICKS ? lastTake
				: listen != null && eggId(listen.getItemName()) > 0 ? listen.getItemId() : -1;
			if (id > 0)
			{
				eggs.merge(id, 1, Integer::sum);
			}
		}
		else if (LOADED.equals(msg))
		{
			unload(BaIds.EGG_GREEN, hopperGreen);
			unload(BaIds.EGG_RED, hopperRed);
			unload(BaIds.EGG_BLUE, hopperBlue);
		}
	}

	public int count(int itemId)
	{
		return eggs.getOrDefault(itemId, 0);
	}

	private void unload(int id, int loaded)
	{
		int have = count(id);
		int moved = Math.min(have, Math.max(0, HOPPER_CAP - loaded));
		if (moved > 0)
		{
			eggs.put(id, have - moved);
		}
	}

	/** "Red egg" / "Red eggs" / "Green egg" / "Blue egg" to the item id, else -1. */
	static int eggId(String name)
	{
		String n = name.toLowerCase();
		if (n.startsWith("red egg"))
		{
			return BaIds.EGG_RED;
		}
		if (n.startsWith("green egg"))
		{
			return BaIds.EGG_GREEN;
		}
		if (n.startsWith("blue egg"))
		{
			return BaIds.EGG_BLUE;
		}
		return -1;
	}
}
