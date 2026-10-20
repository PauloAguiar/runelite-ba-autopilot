package com.galesong.baautopilot.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.galesong.baautopilot.model.BaIds;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class TutorialLayoutTest
{
	private final TutorialLayout layout = TutorialLayout.bundled(new Gson());

	@Test
	public void bundledLayoutPlacesEverythingInRoomOne()
	{
		layout.npcs.forEach(n -> assertInRoom(n.key, n.room, n.x, n.y));
		layout.items.forEach(i -> assertInRoom(i.key, i.room, i.x, i.y));
		layout.tiles.forEach(t -> assertInRoom(t.key, t.room, t.x, t.y));
		layout.models.forEach(m -> assertInRoom(m.key, m.room, m.x, m.y));
		assertEquals(TutorialLayout.ORIGIN_X, layout.corner(1).x);
		assertEquals(TutorialLayout.ORIGIN_Y, layout.corner(1).y);
	}

	/** Room tiles run 0-7; room 1's are inside its measured bounds. */
	private static void assertInRoom(String key, int room, int x, int y)
	{
		assertTrue(key, room >= 1 && room <= 10 && x >= 0 && x <= 7 && y >= 0 && y <= 7);
		if (room == 1)
		{
			assertTrue(key, LobbyRooms.inRoomOne(TutorialLayout.world(x, y)));
		}
	}

	private static java.util.List<TutorialSteps.Page> allPages()
	{
		java.util.List<TutorialSteps.Page> pages = new java.util.ArrayList<>();
		for (Tutorial t : Tutorial.values())
		{
			pages.addAll(t.getPages());
		}
		return pages;
	}

	/** A layout file edited before a prop existed still gets it, and keeps its own positions. */
	@Test
	public void olderLayoutFilesGetNewProps()
	{
		TutorialLayout old = TutorialLayout.parse(new Gson(), new java.io.StringReader(
			"{\"npcs\": [{\"key\": \"queen\", \"npcId\": 5775, \"x\": 0, \"y\": 0}]}"));
		old.withMissing(layout);
		assertEquals(layout.npcs.size(), old.npcs.size());
		assertEquals(0, old.npcs.get(0).x);
		assertNotNull(old.tile("team"));
	}

	/** Every key a tutorial page points at exists in the layout. */
	@Test
	public void pagesOnlyNameKnownKeys()
	{
		Set<String> props = new HashSet<>();
		layout.npcs.forEach(n -> props.add(n.key));
		layout.items.forEach(i -> props.add(i.key));
		layout.models.forEach(m -> props.add(m.key));
		for (TutorialSteps.Page page : allPages())
		{
			page.getProps().forEach(k -> assertTrue(k, props.contains(k)));
			// A mark on a prop the page hides would never show
			page.getMarks().forEach(m -> assertTrue(m.getKey(), page.getProps().contains(m.getKey())));
			page.getTiles().forEach(t -> assertNotNull(t.getKey(), layout.tile(t.getKey())));
			page.getTour().forEach(k -> assertNotNull(k, layout.tile(k)));
			page.getSpeech().keySet().forEach(k -> assertTrue(k, page.getProps().contains(k)));
			String camera = page.getCamera();
			assertTrue(String.valueOf(camera), camera == null || props.contains(camera) || layout.tile(camera) != null
				|| camera.startsWith(TutorialScene.OBJECT_KEY));
		}
	}
}
