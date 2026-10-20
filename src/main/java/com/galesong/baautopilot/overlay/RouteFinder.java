package com.galesong.baautopilot.overlay;

import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.state.ArenaPath;
import com.galesong.baautopilot.state.BaTracker;
import com.galesong.baautopilot.state.DefenderArea;
import com.galesong.baautopilot.state.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Walking route from you to the current instruction's target, on the scene collision map. Run on
 * the client thread once per tick; the overlay draws the latest route.
 */
@Singleton
public class RouteFinder
{
	/** Longer routes aren't drawn: the arena is about 60 tiles across. */
	static final int MAX_STEPS = 80;

	private final Client client;
	private final BaTracker tracker;
	private volatile List<LocalPoint> route = Collections.emptyList();

	@Inject
	RouteFinder(Client client, BaTracker tracker)
	{
		this.client = client;
		this.tracker = tracker;
	}

	public List<LocalPoint> route()
	{
		return route;
	}

	public void clear()
	{
		route = Collections.emptyList();
	}

	public void update(Instruction ins, Snapshot snapshot)
	{
		route = compute(ins, snapshot);
	}

	/** The same protected map is used for cost ranking, route search, and line smoothing. */
	public int[][] navigationFlags(Snapshot snapshot, boolean approachPlayer)
	{
		WorldView wv = client.getTopLevelWorldView();
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return null;
		}
		int[][] flags = maps[plane].getFlags();
		Player me = client.getLocalPlayer();
		if (snapshot == null || me == null || me.getLocalLocation() == null)
		{
			return flags;
		}
		List<WorldPoint> others = new ArrayList<>();
		for (Player player : wv.players())
		{
			if (player != null && player != me && player.getLocalLocation() != null)
			{
				others.add(WorldPoint.fromLocalInstance(client, player.getLocalLocation()));
			}
		}
		WorldPoint held = DefenderArea.heldTile(snapshot, others);
		LocalPoint holding = held == null ? null : Scenes.toLocal(client, held);
		if (holding == null)
		{
			return flags;
		}
		LocalPoint start = me.getLocalLocation();
		return DefenderArea.avoid(flags, holding.getSceneX(), holding.getSceneY(),
			start.getSceneX(), start.getSceneY(), approachPlayer);
	}

	private List<LocalPoint> compute(Instruction ins, Snapshot snapshot)
	{
		Player me = client.getLocalPlayer();
		if (ins == null || me == null || me.getLocalLocation() == null)
		{
			return Collections.emptyList();
		}
		WorldView wv = client.getTopLevelWorldView();
		int[][] flags = navigationFlags(snapshot, ins.getTarget().getKind() == Target.Kind.PLAYER);
		if (flags == null)
		{
			return Collections.emptyList();
		}
		int[] area = goalArea(ins.getTarget(), wv);
		if (area == null)
		{
			return Collections.emptyList();
		}
		int minX = area[0];
		int minY = area[1];
		int maxX = area[2];
		int maxY = area[3];
		boolean adjacent = area[4] == 1;
		ArenaPath.Goal goal = (x, y) -> adjacent
			? x >= minX - 1 && x <= maxX + 1 && y >= minY - 1 && y <= maxY + 1
			&& !(x >= minX && x <= maxX && y >= minY && y <= maxY)
			: x >= minX && x <= maxX && y >= minY && y <= maxY;
		LocalPoint start = me.getLocalLocation();
		if (goal.reached(start.getSceneX(), start.getSceneY()))
		{
			return Collections.emptyList();
		}
		List<int[]> steps = ArenaPath.waypoints(flags,
			ArenaPath.find(flags, start.getSceneX(), start.getSceneY(), goal, MAX_STEPS));
		List<LocalPoint> out = new ArrayList<>(steps.size());
		for (int[] p : steps)
		{
			out.add(LocalPoint.fromScene(p[0], p[1], wv));
		}
		return out;
	}

	/** {minX, minY, maxX, maxY, adjacent?1:0} in scene tiles, or null. */
	private int[] goalArea(Target t, WorldView wv)
	{
		switch (t.getKind())
		{
			case NPC:
			{
				NPC npc = tracker.getNpcs().get(t.getId());
				if (npc == null || npc.getLocalLocation() == null)
				{
					// Out of view: head for where it was last seen
					LocalPoint lp = t.getTile() == null ? null : Scenes.toLocal(client, t.getTile());
					return lp == null ? null : new int[]{lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY(), 0};
				}
				int size = npc.getComposition() == null ? 1 : Math.max(1, npc.getComposition().getSize());
				int x = npc.getLocalLocation().getSceneX() - (size - 1) / 2;
				int y = npc.getLocalLocation().getSceneY() - (size - 1) / 2;
				return new int[]{x, y, x + size - 1, y + size - 1, 1};
			}
			case OBJECT:
			{
				TileObject obj = t.getTile() == null ? null : tracker.findObject(t.getId(), t.getTile());
				if (obj instanceof GameObject)
				{
					GameObject go = (GameObject) obj;
					int x = go.getSceneMinLocation().getX();
					int y = go.getSceneMinLocation().getY();
					return new int[]{x, y, x + go.sizeX() - 1, y + go.sizeY() - 1, 1};
				}
				LocalPoint lp = obj != null ? obj.getLocalLocation() : Scenes.toLocal(client, t.getTile());
				return lp == null ? null : new int[]{lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY(), 1};
			}
			case TILE:
			case GROUND_ITEM:
			{
				LocalPoint live = t.getKind() == Target.Kind.GROUND_ITEM ? tracker.groundItemLocation(t.getId(), t.getTile()) : null;
				LocalPoint lp = live != null ? live : Scenes.toLocal(client, t.getTile());
				return lp == null ? null : new int[]{lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY(), 0};
			}
			case PLAYER:
				for (Player p : wv.players())
				{
					if (p != null && t.getLabel() != null && t.getLabel().equalsIgnoreCase(p.getName()) && p.getLocalLocation() != null)
					{
						int x = p.getLocalLocation().getSceneX();
						int y = p.getLocalLocation().getSceneY();
						return new int[]{x, y, x, y, 1};
					}
				}
				return null;
			default:
				return null;
		}
	}
}
