package com.bautopilot.baautopilot.tutorial;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Tutorial props: client-side models (RuneLite objects) only you see, placed on lobby room 1's
 * tiles as tutorial-layout.json says. Client thread only.
 */
@Slf4j
@Singleton
public class TutorialScene
{
	/** A placed model, by the key tutorial steps highlight it with. */
	@Value
	public static class Prop
	{
		String key;
		RuneLiteObject object;
		/** Label height above the floor; -1: just above the model. */
		int labelHeight;
		/** Looping idle animation; -1 for none. */
		int idle;
		/** Where it was placed; a scene that moves it (an egg being passed) puts it back here. */
		LocalPoint home;
	}

	private final Client client;
	private final Gson gson;
	private final List<Prop> props = new ArrayList<>();
	/** Where each lobby room's own object stands; the lobby isn't instanced, so found once. */
	private final java.util.Map<Integer, WorldPoint> roomObjects = new java.util.HashMap<>();
	/** Real objects pages point at, by id, found with the room objects. */
	private final java.util.Map<Integer, List<TileObject>> sceneObjects = new java.util.HashMap<>();
	private static final java.util.Set<Integer> WANTED_OBJECTS = new java.util.HashSet<>(java.util.Arrays.asList(
		net.runelite.api.gameval.ObjectID.BARBASSAULT_SCROLL_TABLE, net.runelite.api.gameval.ObjectID.BARBASSAULT_DOOR_01));
	private TutorialLayout layout = new TutorialLayout();
	/** The layout's version when placed, to place the props again once a development tool's copy changes. */
	private long loadedAt;

	@Inject
	TutorialScene(Client client, Gson gson)
	{
		this.client = client;
		this.gson = gson;
	}

	public boolean isShown()
	{
		return !props.isEmpty();
	}

	/** A development tool's layout changed since the scene was placed. */
	public boolean isStale()
	{
		return isShown() && TutorialLayout.version() != loadedAt;
	}

	/** Places the props; false when a model isn't loaded yet (try again next tick). */
	public boolean show()
	{
		clear();
		layout = TutorialLayout.load(gson);
		roomObjects.clear();
		sceneObjects.clear();
		findRoomObjects();
		loadedAt = TutorialLayout.version();
		List<Prop> placed = new ArrayList<>();
		for (TutorialLayout.Npc n : layout.npcs)
		{
			Model model = npcModel(n.npcId, n.scale, n.attach);
			if (model == null)
			{
				placed.forEach(p -> p.getObject().setActive(false));
				return false;
			}
			placed.add(spawn(n.key, model, n.room, n.x, n.y, n.orientation, n.animation, n.labelHeight, true));
		}
		for (TutorialLayout.Item i : layout.items)
		{
			Model model = itemModel(i.itemId);
			if (model != null)
			{
				placed.add(spawn(i.key, model, i.room, i.x, i.y, i.orientation, -1, i.labelHeight, true));
			}
		}
		for (TutorialLayout.Shape m : layout.models)
		{
			Model model = m.recolorFrom != null && m.recolorTo != null
				? client.loadModel(m.modelId, m.recolorFrom, m.recolorTo)
				: client.loadModel(m.modelId);
			if (model != null)
			{
				// Scenery loops (the hopper) or animates on cue (the trap springing)
				Prop prop = spawn(m.key, model, m.room, m.x, m.y, m.orientation, m.animation, m.labelHeight, m.loop);
				raise(prop.getObject(), model, m.lift, m.onFloor);
				placed.add(prop);
			}
		}
		props.addAll(placed);
		return true;
	}

	public void clear()
	{
		for (Prop p : props)
		{
			p.getObject().setActive(false);
		}
		props.clear();
	}

	public List<Prop> getProps()
	{
		return Collections.unmodifiableList(props);
	}

	/** A named tile from the layout; null when unknown or not in the scene. */
	public LocalPoint tile(String key)
	{
		TutorialLayout.Tile t = layout.tile(key);
		return t == null ? null : local(t.room, t.x, t.y);
	}

	/** Only these props are drawn (the page's own); the rest stay placed but hidden. */
	public void showOnly(java.util.Collection<String> keys)
	{
		for (Prop p : props)
		{
			boolean want = keys.contains(p.getKey());
			if (p.getObject().isActive() != want && p.getObject().getLocation() != null)
			{
				p.getObject().setActive(want);
			}
		}
	}

	public Prop prop(String key)
	{
		for (Prop p : props)
		{
			if (p.getKey().equals(key))
			{
				return p;
			}
		}
		return null;
	}

	/** Plays an animation once, then goes back to the prop's idle loop (or stands still). */
	public void play(Prop p, int animation)
	{
		Animation anim = client.loadAnimation(animation);
		if (anim == null)
		{
			return;
		}
		p.getObject().setAnimationController(new AnimationController(client, anim)
			.setOnFinished(done -> idle(p)));
	}

	/** Moves a prop (an egg being passed); {@link #home} puts it back. */
	public void move(Prop p, LocalPoint to)
	{
		move(p, to, 0);
	}

	/** Moves a prop and holds it {@code lift} above the floor (an egg passed overhead). */
	public void move(Prop p, LocalPoint to, int lift)
	{
		if (to != null)
		{
			p.getObject().setLocation(to, 0);
			if (lift != 0)
			{
				p.getObject().setZ(p.getObject().getZ() - lift);
			}
		}
	}

	public void home(Prop p)
	{
		if (p.getHome() != null && !p.getHome().equals(p.getObject().getLocation()))
		{
			p.getObject().setLocation(p.getHome(), 0);
		}
	}

	/** Loops an animation (walking) until the next play or idle. */
	public void loop(Prop p, int animation)
	{
		Animation anim = client.loadAnimation(animation);
		if (anim != null)
		{
			p.getObject().setAnimationController(new AnimationController(client, anim).setOnFinished(AnimationController::loop));
		}
	}

	/** Back to the idle loop. */
	public void idle(Prop p)
	{
		Animation anim = p.getIdle() == -1 ? null : client.loadAnimation(p.getIdle());
		p.getObject().setAnimationController(anim == null ? null
			: new AnimationController(client, anim).setOnFinished(AnimationController::loop));
	}

	/** Where a prop stands, or a layout tile; null when neither. */
	public LocalPoint focus(String key)
	{
		if (key.startsWith(OBJECT_KEY))
		{
			TileObject o = object(Integer.parseInt(key.substring(OBJECT_KEY.length())));
			return o == null ? null : o.getLocalLocation();
		}
		for (Prop p : props)
		{
			if (p.getKey().equals(key))
			{
				return p.getObject().getLocation();
			}
		}
		return tile(key);
	}

	private LocalPoint local(int room, int x, int y)
	{
		WorldPoint corner = corner(room);
		return corner == null ? null
			: LocalPoint.fromWorld(client.getTopLevelWorldView(), corner.dx(x).dy(y));
	}

	/**
	 * A room's south-west tile: as listed in the layout, else where the game's own object for that
	 * room (BARBASSAULT_ROOM_01..10) puts it - the rooms are built alike, so room N's object sits
	 * where room 1's does in room 1. Null when neither is known.
	 */
	private WorldPoint corner(int room)
	{
		TutorialLayout.Corner c = layout.corner(room);
		if (c != null)
		{
			return new WorldPoint(c.x, c.y, 0);
		}
		WorldPoint one = roomObjects.get(1);
		WorldPoint mine = roomObjects.get(room);
		TutorialLayout.Corner first = layout.corner(1);
		if (one == null || mine == null || first == null)
		{
			return null;
		}
		return new WorldPoint(first.x + mine.getX() - one.getX(), first.y + mine.getY() - one.getY(), 0);
	}

	private void findRoomObjects()
	{
		for (Tile[] column : client.getTopLevelWorldView().getScene().getTiles()[0])
		{
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				for (GameObject o : tile.getGameObjects())
				{
					noteRoomObject(o);
				}
				noteRoomObject(tile.getWallObject());
				noteRoomObject(tile.getDecorativeObject());
				noteRoomObject(tile.getGroundObject());
			}
		}
		log.debug("Lobby room objects: {}; scroll tables: {}", roomObjects,
			sceneObjects.getOrDefault(net.runelite.api.gameval.ObjectID.BARBASSAULT_SCROLL_TABLE, Collections.emptyList()).size());
	}

	/** Camera keys naming a real object: "object:20149". */
	public static final String OBJECT_KEY = "object:";
	/**
	 * Room 1's ladder, which the leader climbs to start a wave. It has no BA id of its own, so it
	 * is found by name inside room 1 and filed under this made-up id.
	 */
	public static final int LADDER = -1;

	private boolean isLadder(int id)
	{
		net.runelite.api.ObjectComposition def = client.getObjectDefinition(id);
		return def != null && "Ladder".equals(def.getName());
	}

	/** The real object with this id nearest room 1's middle; null when it isn't in the scene. */
	public TileObject object(int id)
	{
		TileObject best = null;
		int bestDistance = Integer.MAX_VALUE;
		WorldPoint middle = TutorialLayout.world(4, 4);
		for (TileObject o : sceneObjects.getOrDefault(id, Collections.emptyList()))
		{
			int d = o.getWorldLocation().distanceTo(middle);
			if (d < bestDistance)
			{
				best = o;
				bestDistance = d;
			}
		}
		return best;
	}

	private void noteRoomObject(TileObject o)
	{
		if (o != null && WANTED_OBJECTS.contains(o.getId()))
		{
			sceneObjects.computeIfAbsent(o.getId(), k -> new ArrayList<>()).add(o);
		}
		if (o != null && LobbyRooms.inRoomOne(o.getWorldLocation()) && isLadder(o.getId()))
		{
			sceneObjects.computeIfAbsent(LADDER, k -> new ArrayList<>()).add(o);
		}
		int room = o == null ? 0 : o.getId() - ObjectID.BARBASSAULT_ROOM_01 + 1;
		if (room >= 1 && room <= 10)
		{
			roomObjects.putIfAbsent(room, o.getWorldLocation());
		}
	}

	/** Lifts a placed model: by a set amount, plus whatever puts its lowest point on the floor. */
	private static void raise(RuneLiteObject o, Model model, int lift, boolean onFloor)
	{
		int up = lift;
		if (onFloor)
		{
			model.calculateBoundsCylinder();
			// Model y grows downwards: a positive bottom is below the floor
			up += Math.max(0, model.getBottomY());
		}
		if (up != 0)
		{
			o.setZ(o.getZ() - up);
		}
	}

	private Prop spawn(String key, Model model, int room, int x, int y, int orientation, int animation, int labelHeight,
		boolean loops)
	{
		RuneLiteObject o = client.createRuneLiteObject();
		o.setModel(model);
		Prop prop = new Prop(key, o, labelHeight, loops ? animation : -1, local(room, x, y));
		idle(prop);
		o.setOrientation(orientation);
		LocalPoint at = local(room, x, y);
		if (at != null)
		{
			// Shown by showOnly, when a page wants it
			o.setLocation(at, 0);
		}
		return prop;
	}

	/** The NPC as the game builds it: its model parts merged, recoloured, scaled and lit. */
	private Model npcModel(int npcId, int scalePercent, List<TutorialLayout.Attach> attach)
	{
		NPCComposition npc = client.getNpcDefinition(npcId);
		int[] ids = npc == null ? null : npc.getModels();
		if (ids == null || ids.length == 0)
		{
			return null;
		}
		ModelData[] parts = new ModelData[ids.length + attach.size()];
		for (int i = 0; i < ids.length; i++)
		{
			parts[i] = client.loadModelData(ids[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}
		for (int i = 0; i < attach.size(); i++)
		{
			TutorialLayout.Attach a = attach.get(i);
			ModelData part = client.loadModelData(a.modelId);
			if (part == null)
			{
				return null;
			}
			// The worn item's own colours first: the NPC's recolours below are for its body
			if (a.recolorFrom != null && a.recolorTo != null)
			{
				part = part.cloneColors();
				for (int c = 0; c < Math.min(a.recolorFrom.length, a.recolorTo.length); c++)
				{
					part.recolor(a.recolorFrom[c], a.recolorTo[c]);
				}
			}
			parts[ids.length + i] = part;
		}
		ModelData merged = client.mergeModels(parts).cloneColors();
		short[] from = npc.getColorToReplace();
		short[] to = npc.getColorToReplaceWith();
		if (from != null && to != null)
		{
			for (int i = 0; i < Math.min(from.length, to.length); i++)
			{
				merged.recolor(from[i], to[i]);
			}
		}
		int width = npc.getWidthScale() * scalePercent / 100;
		int height = npc.getHeightScale() * scalePercent / 100;
		if (width != 128 || height != 128)
		{
			merged = merged.cloneVertices().scale(width, height, width);
		}
		return merged.light();
	}

	private Model itemModel(int itemId)
	{
		ItemComposition item = client.getItemDefinition(itemId);
		if (item == null)
		{
			return null;
		}
		short[] from = item.getColorToReplace();
		short[] to = item.getColorToReplaceWith();
		return from != null && to != null
			? client.loadModel(item.getInventoryModel(), from, to)
			: client.loadModel(item.getInventoryModel());
	}
}
