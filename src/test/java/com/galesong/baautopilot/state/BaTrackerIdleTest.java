package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.ArenaTiles;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.lang.reflect.Method;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaTrackerIdleTest
{
	private static final class Harness
	{
		boolean instance;
		boolean activeReads;
		int region = ArenaTiles.REGION_WAVES_1_9;
		GameState state = GameState.LOGGED_IN;
		WorldPoint outside = new WorldPoint(3200, 3200, 0);
		int tick = 100;
		int playerIterations;
		final Map<String, Integer> calls = new HashMap<>();
		final WorldView world;
		final Player player;
		final Client client;
		final BaTracker tracker = new BaTracker();

		Harness() throws Exception
		{
			IndexedObjectSet<?> players = proxy(IndexedObjectSet.class, (method, args) ->
			{
				if (method.getName().equals("iterator")) { playerIterations++; return Collections.emptyIterator(); }
				return primitive(method.getReturnType());
			});
			world = proxy(WorldView.class, (method, args) ->
			{
				switch (method.getName())
				{
					case "isInstance": return instance;
					case "getInstanceTemplateChunks":
						int[][][] chunks = new int[4][13][13];
						WorldPoint template = WorldPoint.fromRegion(region, 50, 50, 0);
						chunks[0][6][6] = (template.getX() / 8 << 14) | (template.getY() / 8 << 3);
						return chunks;
					case "players": return players;
					default: return primitive(method.getReturnType());
				}
			});
			LocalPoint local = new LocalPoint(50 * 128 + 64, 50 * 128 + 64, world);
			player = proxy(Player.class, (method, args) ->
			{
				switch (method.getName())
				{
					case "getWorldLocation": return outside;
					case "getLocalLocation": return local;
					case "getName": return "Me";
					default: return primitive(method.getReturnType());
				}
			});
			client = proxy(Client.class, (method, args) ->
			{
				String name = method.getName();
				calls.merge(name, 1, Integer::sum);
				switch (name)
				{
					case "getGameState": return state;
					case "getTopLevelWorldView": case "getWorldView": return world;
					case "getLocalPlayer": return player;
					case "getTickCount": return tick;
					case "getWidget":
						assertTrue("HUD read outside arena/lobby", activeReads || ArenaTiles.isLobby(outside)); return null;
					case "getItemContainer": case "getVarbitValue": case "getBoostedSkillLevel": case "getRealSkillLevel":
						assertTrue("Active client read while idle: " + name, activeReads); return primitive(method.getReturnType());
					default: return primitive(method.getReturnType());
				}
			});
			Field field = BaTracker.class.getDeclaredField("client");
			field.setAccessible(true);
			field.set(tracker, client);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> call)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) ->
		{
			if (method.getDeclaringClass() == Object.class)
			{
				if (method.getName().equals("hashCode")) return System.identityHashCode(self);
				if (method.getName().equals("equals")) return self == args[0];
				return type.getSimpleName();
			}
			return call.apply(method, args);
		});
	}

	private static Object primitive(Class<?> type)
	{
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		return null;
	}

	@Test public void aThousandIdleTicksReuseOneSnapshotWithoutScanningActiveInputs() throws Exception
	{
		Harness h = new Harness();
		Snapshot first = h.tracker.snapshot();
		for (int i = 0; i < 1000; i++)
		{
			h.tick++;
			assertSame(first, h.tracker.snapshot());
			h.tracker.onChatMessage(null);
			h.tracker.onMenuOptionClicked(null);
			h.tracker.onOverheadTextChanged(null);
			h.tracker.onHitsplatApplied(null);
			h.tracker.onActorDeath(null);
			h.tracker.onNpcSpawned(null);
			h.tracker.onNpcDespawned(null);
			h.tracker.onItemSpawned(null);
			h.tracker.onItemDespawned(null);
		}
		assertFalse(first.isInArena());
		assertEquals(0, h.playerIterations);
		assertFalse(h.calls.containsKey("getItemContainer"));
		assertFalse(h.calls.containsKey("getVarbitValue"));
		assertFalse(h.calls.containsKey("getWidget"));
	}

	@Test public void unrelatedInstancesAlsoStayIdle() throws Exception
	{
		Harness h = new Harness();
		h.instance = true;
		h.region = 12850;
		assertFalse(h.tracker.snapshot().isInArena());
		assertEquals(0, h.playerIterations);
		assertFalse(h.calls.containsKey("getItemContainer"));
	}

	@Test public void lobbyOnlyReadsTheRecruitmentWidget() throws Exception
	{
		Harness h = new Harness();
		h.outside = WorldPoint.fromRegion(ArenaTiles.REGION_LOBBY, 20, 20, 0);
		for (int i = 0; i < 10; i++) h.tracker.snapshot();
		assertEquals(Integer.valueOf(10), h.calls.get("getWidget"));
		assertEquals(0, h.playerIterations);
		assertFalse(h.calls.containsKey("getItemContainer"));
	}

	@Test public void arenaEventsBeforeTheFirstTickAreRetainedAndExitOnlyFinalisesOnce() throws Exception
	{
		Harness h = new Harness();
		h.instance = true;
		h.activeReads = true;
		NPC npc = proxy(NPC.class, (method, args) ->
		{
			switch (method.getName())
			{
				case "getId": return NpcID.BARBASSAULT_PEN_HEALER_LV1;
				case "getIndex": return 7;
				case "getLocalLocation": return h.player.getLocalLocation();
				case "getHealthRatio": case "getHealthScale": return -1;
				default: return primitive(method.getReturnType());
			}
		});
		h.tracker.onNpcSpawned(new NpcSpawned(npc));
		assertTrue(h.tracker.getNpcs().containsKey(7));
		Snapshot active = h.tracker.snapshot();
		assertTrue(active.isInArena());
		assertEquals(1, active.getPenance().size());
		assertEquals(1, h.playerIterations);
		h.instance = false;
		assertFalse(h.tracker.snapshot().isInArena());
		assertTrue(h.tracker.getNpcs().isEmpty());
		int reads = h.calls.get("getWidget");
		h.activeReads = false;
		for (int i = 0; i < 100; i++) h.tracker.snapshot();
		assertEquals(Integer.valueOf(reads), h.calls.get("getWidget"));
		assertEquals(1, h.playerIterations);
	}
}
