package com.bautopilot.baautopilot.tutorial;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Reported 2026-10-18: the nearest-room-object guess dropped out of room 1 at times. */
public class LobbyRoomsTest
{
	@Test
	public void cornersAreInside()
	{
		assertTrue(LobbyRooms.inRoomOne(new WorldPoint(2576, 5298, 0)));
		assertTrue(LobbyRooms.inRoomOne(new WorldPoint(2583, 5291, 0)));
		assertTrue(LobbyRooms.inRoomOne(new WorldPoint(2580, 5294, 0)));
	}

	@Test
	public void oneTileOutIsOutside()
	{
		assertFalse(LobbyRooms.inRoomOne(new WorldPoint(2575, 5295, 0)));
		assertFalse(LobbyRooms.inRoomOne(new WorldPoint(2584, 5295, 0)));
		assertFalse(LobbyRooms.inRoomOne(new WorldPoint(2580, 5290, 0)));
		assertFalse(LobbyRooms.inRoomOne(new WorldPoint(2580, 5299, 0)));
		assertFalse(LobbyRooms.inRoomOne(new WorldPoint(2580, 5294, 1)));
	}
}
