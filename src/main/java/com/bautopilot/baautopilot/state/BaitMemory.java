package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;

/** Reattaches a bait drop's classification when a scene update replaces its ground-item identity. */
final class BaitMemory
{
	private final List<GroundItemView> removed = new ArrayList<>();
	private int removedTick = -1;
	private int dropTick = -1;
	private int dropItem = -1;

	void reset()
	{
		removed.clear();
		removedTick = dropTick = dropItem = -1;
	}

	void menu(String option, String target, int tick)
	{
		BaCall bait = "Drop".equals(option) ? BaCall.parse(Role.DEFENDER, target) : null;
		if (bait != null)
		{
			dropItem = bait.getItemId();
			dropTick = tick;
		}
	}

	void despawn(GroundItemView item, int tick)
	{
		if (!BaIds.BAIT.contains(item.getItemId()) || item.getTile() == null) return;
		advance(tick);
		removed.add(item);
	}

	GroundItemView spawn(int id, WorldPoint tile, int quantity, int tick, WorldPoint player, BooleanSupplier landed)
	{
		advance(tick);
		boolean ownDrop = id == dropItem && tick >= dropTick && tick - dropTick <= 2
			&& player != null && tile != null && ArenaTiles.distance(player, tile) <= 2;
		for (int i = 0; i < removed.size(); i++)
		{
			GroundItemView old = removed.get(i);
			if (old.getItemId() == id && old.getTile().equals(tile) && old.getQuantity() == quantity)
			{
				removed.remove(i);
				if (!ownDrop) return new GroundItemView(id, tile, quantity, old.isGood(), old.getSpawnTick());
				break;
			}
		}
		return new GroundItemView(id, tile, quantity, landed.getAsBoolean(), tick);
	}

	private void advance(int tick)
	{
		if (removedTick != tick)
		{
			removed.clear();
			removedTick = tick;
		}
	}
}
