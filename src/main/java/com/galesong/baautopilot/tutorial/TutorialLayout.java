package com.galesong.baautopilot.tutorial;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

/**
 * Where the tutorial places things, in lobby room 1's own tiles: x 0-7 from the west wall, y 0-7
 * from the south wall. A developer-mode client reads tutorial-layout.json in the RuneLite folder
 * (ba-autopilot/), created from the bundled copy the first time, so it can be tweaked without
 * rebuilding; every other client uses the bundled copy.
 */
@Slf4j
public class TutorialLayout
{
	/** Room 1's south-west tile: room (0, 0). */
	public static final int ORIGIN_X = 2576;
	public static final int ORIGIN_Y = 5291;
	private static final String RESOURCE = "tutorial-layout.json";
	public static final File FILE = new File(new File(RuneLite.RUNELITE_DIR, "ba-autopilot"), RESOURCE);

	/** Item models' own height is unreliable (inventory models are posed for the icon): a fixed one. */
	public static final int ITEM_LABEL_HEIGHT = 60;

	/** A lobby room's south-west tile: its room (0, 0). */
	public static class Corner
	{
		public int x;
		public int y;
	}

	public static class Npc
	{
		public String key;
		/** Lobby room (1-10) the x / y are in. */
		public int room = 1;
		public int npcId;
		public int animation = -1;
		public int x;
		public int y;
		public int orientation;
		/** Label height above the floor; -1: just above the model. */
		public int labelHeight = -1;
		/** Size in percent of the real thing. */
		public int scale = 100;
		/** Worn models merged in (a bow in the hands): rigged like a player's, so they move with it. */
		public List<Attach> attach = new ArrayList<>();
	}

	/** A worn item's model (an item's male model in the game cache), with its recolours. */
	public static class Attach
	{
		public int modelId;
		public short[] recolorFrom;
		public short[] recolorTo;
	}

	/** A bare model from the game cache (scenery such as the runner trap), with its recolours. */
	public static class Shape
	{
		public String key;
		public int room = 1;
		public int modelId;
		public short[] recolorFrom;
		public short[] recolorTo;
		public int animation = -1;
		/** Play the animation all the time; otherwise only on cue (the trap springing). */
		public boolean loop;
		/** Raise the model this much (128 is a tile's width). */
		public int lift;
		/**
		 * Raise it until its lowest point rests on the floor: for scenery built to sit in a dip in
		 * the arena's ground (the Healer spring), which would otherwise sink below the lobby floor.
		 */
		public boolean onFloor;
		public int x;
		public int y;
		public int orientation;
		public int labelHeight = ITEM_LABEL_HEIGHT;
	}

	public static class Item
	{
		public String key;
		public int room = 1;
		public int itemId;
		public int x;
		public int y;
		public int orientation;
		/** Label height above the floor. */
		public int labelHeight = ITEM_LABEL_HEIGHT;
	}

	public static class Tile
	{
		public String key;
		public int room = 1;
		public int x;
		public int y;
	}

	/**
	 * Room corners by room number. Rooms not listed are found from the game's room objects
	 * (see TutorialScene); listing one here pins it.
	 */
	public Map<String, Corner> rooms = new HashMap<>();
	public List<Npc> npcs = new ArrayList<>();
	public List<Item> items = new ArrayList<>();
	public List<Shape> models = new ArrayList<>();
	public List<Tile> tiles = new ArrayList<>();

	/** A room 1 tile as a world tile. */
	public static WorldPoint world(int x, int y)
	{
		return new WorldPoint(ORIGIN_X + x, ORIGIN_Y + y, 0);
	}

	/** The listed corner of a room; null when it isn't listed. */
	public Corner corner(int room)
	{
		return rooms.get(String.valueOf(room));
	}

	public Tile tile(String key)
	{
		return tiles.stream().filter(t -> key.equals(t.key)).findFirst().orElse(null);
	}

	/**
	 * Entries the bundled layout has and this one doesn't (by key) are added, so props new to the
	 * tutorial still show up in a layout file edited before they existed.
	 */
	public TutorialLayout withMissing(TutorialLayout from)
	{
		from.npcs.stream().filter(n -> npcs.stream().noneMatch(m -> m.key.equals(n.key))).forEach(npcs::add);
		from.items.stream().filter(i -> items.stream().noneMatch(m -> m.key.equals(i.key))).forEach(items::add);
		from.models.stream().filter(i -> models.stream().noneMatch(m -> m.key.equals(i.key))).forEach(models::add);
		from.tiles.stream().filter(t -> tile(t.key) == null).forEach(tiles::add);
		from.rooms.forEach(rooms::putIfAbsent);
		return this;
	}

	public static TutorialLayout parse(Gson gson, Reader json)
	{
		TutorialLayout layout = gson.fromJson(json, TutorialLayout.class);
		return layout == null ? new TutorialLayout() : layout;
	}

	/** The bundled layout. */
	public static TutorialLayout bundled(Gson gson)
	{
		try (InputStream in = TutorialLayout.class.getResourceAsStream(RESOURCE))
		{
			return parse(gson, new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Bundled tutorial layout unreadable", e);
			return new TutorialLayout();
		}
	}

	/** The editable copy (written from the bundled one if missing); the bundled one if it won't parse. */
	public static TutorialLayout load(Gson gson)
	{
		try
		{
			if (!FILE.exists())
			{
				FILE.getParentFile().mkdirs();
				try (InputStream in = TutorialLayout.class.getResourceAsStream(RESOURCE))
				{
					Files.copy(in, FILE.toPath());
				}
			}
			try (Reader r = Files.newBufferedReader(FILE.toPath(), StandardCharsets.UTF_8))
			{
				return parse(gson, r).withMissing(bundled(gson));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Tutorial layout {} unreadable, using the bundled one", FILE, e);
			return bundled(gson);
		}
	}
}
