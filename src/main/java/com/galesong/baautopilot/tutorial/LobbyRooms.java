package com.galesong.baautopilot.tutorial;

import net.runelite.api.coords.WorldPoint;

/**
 * Lobby waiting rooms by their floor corners (the lobby isn't instanced, so these are fixed world
 * tiles). Room 1, measured in game 2026-10-18: top left (2576, 5298), bottom right (2583, 5291).
 */
public final class LobbyRooms
{
	private static final int ROOM_1_WEST = 2576;
	private static final int ROOM_1_EAST = 2583;
	private static final int ROOM_1_SOUTH = 5291;
	private static final int ROOM_1_NORTH = 5298;

	private LobbyRooms()
	{
	}

	public static boolean inRoomOne(WorldPoint p)
	{
		return p != null && p.getPlane() == 0
			&& p.getX() >= ROOM_1_WEST && p.getX() <= ROOM_1_EAST
			&& p.getY() >= ROOM_1_SOUTH && p.getY() <= ROOM_1_NORTH;
	}
}
