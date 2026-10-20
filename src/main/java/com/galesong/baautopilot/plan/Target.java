package com.galesong.baautopilot.plan;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** What the scene overlay should point at. Tiles are instance-template coordinates. */
@Value
public class Target
{
	public enum Kind
	{
		NONE,
		NPC,
		OBJECT,
		TILE,
		GROUND_ITEM,
		PLAYER
	}

	public static final Target NONE = new Target(Kind.NONE, -1, null, null);

	Kind kind;
	/** NPC index, object id or item id. */
	int id;
	WorldPoint tile;
	/** Player name for PLAYER targets, otherwise a short label. */
	String label;

	public static Target npc(int npcIndex, WorldPoint tile)
	{
		return new Target(Kind.NPC, npcIndex, tile, null);
	}

	public static Target object(int objectId, WorldPoint tile)
	{
		return new Target(Kind.OBJECT, objectId, tile, null);
	}

	public static Target tile(WorldPoint tile, String label)
	{
		return new Target(Kind.TILE, -1, tile, label);
	}

	public static Target groundItem(int itemId, WorldPoint tile)
	{
		return new Target(Kind.GROUND_ITEM, itemId, tile, null);
	}

	public static Target player(String name)
	{
		return new Target(Kind.PLAYER, -1, null, name);
	}
}
