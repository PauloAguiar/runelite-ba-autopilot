package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Turns client events into a {@link Snapshot} once per tick. Everything client-facing lives here;
 * the round's bookkeeping is in {@link RoundLogic} and the planner only sees the snapshot.
 */
@Singleton
public class BaTracker
{
	private static final class GroundEntry
	{
		final TileItem item;
		final WorldPoint tile;
		/** Where the game put it in the live scene, for drawing without converting back. */
		final LocalPoint live;
		final boolean good;
		final int spawnTick;

		GroundEntry(TileItem item, WorldPoint tile, LocalPoint live, boolean good, int spawnTick)
		{
			this.item = item;
			this.tile = tile;
			this.live = live;
			this.good = good;
			this.spawnTick = spawnTick;
		}
	}

	@Inject
	private Client client;

	private final RoundLogic round = new RoundLogic();
	private final RoleProgress progress = new RoleProgress();
	private final RoundLogic.Hud hud = new RoundLogic.Hud()
	{
		@Override
		public int model(int component)
		{
			Widget w = client.getWidget(component);
			return w == null || w.isHidden() ? -1 : w.getModelId();
		}

		@Override
		public boolean visible(int component)
		{
			Widget w = client.getWidget(component);
			return w != null && !w.isHidden();
		}

		@Override
		public String text(int component)
		{
			Widget w = client.getWidget(component);
			if (w == null || w.isHidden())
			{
				return null;
			}
			return Text.removeTags(w.getText());
		}
	};

	/** Live penance by NPC index, for the planner and for overlays to resolve targets. */
	private final Map<Integer, NPC> npcs = new LinkedHashMap<>();
	private final Map<TileObject, Integer> objects = new HashMap<>();
	private final List<GroundEntry> ground = new ArrayList<>();
	private static final Snapshot INACTIVE = Snapshot.builder().build();
	private boolean wasActive;
	/** Scene spawn events can precede the first arena GameTick. */
	private boolean arenaSeen;

	public Performance getPerformance()
	{
		return round.getPerformance();
	}

	public Map<Integer, NPC> getNpcs()
	{
		return npcs;
	}

	public void reset()
	{
		round.reset();
		progress.reset();
		npcs.clear();
		objects.clear();
		ground.clear();
		wasActive = false;
		arenaSeen = false;
	}

	/** Cached account data for the sidebar; does not inspect the arena, inventory or players. */
	public Snapshot progress()
	{
		return progress.read(client);
	}

	// ------------------------------------------------------------------ snapshot

	public Snapshot snapshot()
	{
		WorldView world = client.getTopLevelWorldView();
		if (client.getGameState() != GameState.LOGGED_IN || world == null) return inactive(false);
		int tick = client.getTickCount();
		Player me = client.getLocalPlayer();
		WorldPoint here = me == null ? null : world.isInstance() ? template(me.getLocalLocation()) : me.getWorldLocation();
		int region = here == null ? -1 : here.getRegionID();
		boolean inArena = world.isInstance() && ArenaTiles.isArenaRegion(region);
		if (!inArena) return inactive(!world.isInstance() && ArenaTiles.isLobby(here));
		wasActive = true;
		arenaSeen = true;

		Map<Integer, Integer> inventory = inventoryCounts();
		round.setSelfName(me == null ? null : me.getName());
		if (round.tick(tick, inArena, hud, inventory,
			client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_POISON),
			client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_EXPLOSIVE),
			client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_STUN)))
		{
			npcs.clear();
			objects.clear();
			ground.clear();
		}

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		round.observeSelfHp(hp, maxHp);
		round.observePosition(here);
		round.observeQueenAmmo(client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_OMEGA));
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		int ammo = -1;
		boolean imcando = false;
		if (worn != null)
		{
			Item a = worn.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
			ammo = a == null ? -1 : a.getId();
			imcando = worn.contains(ItemID.IMCANDO_HAMMER) || worn.contains(ItemID.IMCANDO_HAMMER_OFFHAND);
		}

		List<PenanceView> penance = penanceViews();
		List<ObjectView> views = objectViews();
		boolean queenAlive = round.queenPhase(penance.stream().anyMatch(n -> n.getType() == PenanceType.QUEEN));
		Map<String, WorldPoint> playerTiles = new java.util.LinkedHashMap<>();
		List<PlayerView> players = new ArrayList<>();
		for (Player player : client.getTopLevelWorldView().players())
		{
			WorldPoint tile = template(player.getLocalLocation());
			if (player.getName() != null && tile != null) playerTiles.put(player.getName(), tile);
			players.add(new PlayerView(player.getName(), round.roleOf(player.getName()), tile, player.getAnimation(),
				player.getInteracting() instanceof Player ? player.getInteracting().getName() : null));
		}
		round.observeQueenPlayers(players);
		int interacting = me != null && me.getInteracting() instanceof NPC ? ((NPC) me.getInteracting()).getIndex() : -1;
		return round.snapshot()
			.selfName(me == null ? null : me.getName())
			.interactingNpc(interacting)
			.inventory(inventory)
			.freeSlots(freeSlots())
			.ammoId(ammo)
			.hasHammer(hasHammer(inventory, imcando))
			.hp(hp)
			.maxHp(maxHp)
			.playerTile(here)
			.playerTiles(com.google.common.collect.ImmutableMap.copyOf(playerTiles))
			.penance(penance)
			.groundItems(groundViews())
			.objects(views)
			.eggsRed(client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_EXPLOSIVE))
			.eggsGreen(client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_POISON))
			.eggsBlue(client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_STUN))
			.eggsOmega(client.getVarbitValue(VarbitID.BARBASSAULT_EGGCOUNT_OMEGA))
			.trapCharges(RoundLogic.trapCharges(views, round.getWave()))
			.roleLevels(progress().getRoleLevels())
			.rolePoints(progress().getRolePoints())
			.queenAlive(queenAlive)
			.build();
	}

	/** No inventory, player/scene scan, combat history, varbit maps or snapshot allocation while idle. */
	private Snapshot inactive(boolean lobby)
	{
		if (wasActive)
		{
			round.tick(client.getTickCount(), false, hud, ImmutableMap.of(), 0, 0, 0);
			npcs.clear();
			objects.clear();
			ground.clear();
			wasActive = false;
		}
		arenaSeen = false;
		// The lobby team list supplies named Queen hand-offs. Only this small read stays awake here.
		if (lobby) round.rememberTeam(hud);
		return INACTIVE;
	}

	private boolean arenaTile(LocalPoint local)
	{
		WorldView world = client.getTopLevelWorldView();
		if (world == null || !world.isInstance() || local == null) return false;
		WorldPoint point = template(local);
		return point != null && ArenaTiles.isArenaRegion(point.getRegionID());
	}

	private boolean arenaEvents()
	{
		WorldView world = client.getTopLevelWorldView();
		if (world == null || !world.isInstance()) return false;
		if (arenaSeen) return true;
		Player me = client.getLocalPlayer();
		return me != null && arenaTile(me.getLocalLocation());
	}

	/** A hammer in the inventory, or an Imcando hammer worn or carried. */
	public static boolean hasHammer(Map<Integer, Integer> inventory, boolean imcandoWorn)
	{
		return imcandoWorn || inventory.containsKey(ItemID.HAMMER) || inventory.containsKey(ItemID.IMCANDO_HAMMER)
			|| inventory.containsKey(ItemID.IMCANDO_HAMMER_OFFHAND);
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			progress.invalidate();
		}
		else if (e.getGameState() == GameState.LOADING)
		{
			arenaSeen = false;
			objects.clear();
			ground.clear();
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		progress.changed(e);
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		// Like RuneLite's BA plugin, accept the wave line before checking the scene: it arrives
		// before the role HUD/instance finishes loading. Other events still sleep outside BA.
		if (e != null && e.getType() == ChatMessageType.GAMEMESSAGE && e.getMessage() != null
			&& e.getMessage().contains("---- Wave:"))
		{
			String message = Text.removeTags(e.getMessage());
			if (RoundLogic.isWaveStartMessage(message))
			{
				round.chat(true, message, false, client.getTickCount());
				npcs.clear();
				return;
			}
		}
		if (!arenaEvents()) return;
		boolean game = e.getType() == ChatMessageType.GAMEMESSAGE || e.getType() == ChatMessageType.WELCOME
			|| e.getType() == ChatMessageType.MESBOX || e.getType() == ChatMessageType.SPAM;
		String message = Text.removeTags(e.getMessage());
		Player me = client.getLocalPlayer();
		boolean fromMe = me != null && e.getName() != null
			&& Text.standardize(e.getName()).equals(Text.standardize(me.getName()));
		// The arena's own lines ("All of the Penance Runners have been killed!") come as WELCOME
		if (round.chat(game, message, fromMe, client.getTickCount()))
		{
			npcs.clear();
		}
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged e)
	{
		if (!arenaEvents()) return;
		if (!(e.getActor() instanceof Player) || e.getOverheadText() == null)
		{
			return;
		}
		Player p = (Player) e.getActor();
		Player me = client.getLocalPlayer();
		round.overhead(p.getName(), Text.removeTags(e.getOverheadText()), p == me, client.getTickCount());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (!arenaEvents()) return;
		String option = Text.removeTags(e.getMenuOption());
		round.menuClicked(option, Text.removeTags(e.getMenuTarget()), client.getTickCount());
		if (round.getRole() != null && option.startsWith("Tell-"))
		{
			round.tellClicked(option, hud);
			return;
		}
		if (e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_NPC && e.getMenuEntry().getNpc() != null)
		{
			NPC npc = e.getMenuEntry().getNpc();
			Widget selected = client.getSelectedWidget();
			if (selected != null)
			{
				round.usedOnNpc(npc.getIndex(), PenanceType.forNpcId(npc.getId()), selected.getItemId(), client.getTickCount());
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		WorldView world = client.getTopLevelWorldView();
		if (world == null || !world.isInstance()) return;
		NPC npc = e.getNpc();
		PenanceType type = PenanceType.forNpcId(npc.getId());
		if (type == null || !arenaTile(npc.getLocalLocation()))
		{
			return;
		}
		arenaSeen = true;
		npcs.put(npc.getIndex(), npc);
		round.npcSpawned(npc.getIndex(), type, client.getTickCount());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		if (!arenaEvents()) return;
		NPC npc = e.getNpc();
		if (npcs.remove(npc.getIndex()) != null)
		{
			Player me = client.getLocalPlayer();
			int distance = me == null || me.getWorldLocation() == null || npc.getWorldLocation() == null
				? 0 : me.getWorldLocation().distanceTo2D(npc.getWorldLocation());
			round.npcDespawned(npc.getIndex(), PenanceType.forNpcId(npc.getId()), distance);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (!arenaEvents()) return;
		if (e.getActor() instanceof NPC && npcs.containsKey(((NPC) e.getActor()).getIndex()))
		{
			Hitsplat h = e.getHitsplat();
			int type = h.getHitsplatType();
			round.hit(((NPC) e.getActor()).getIndex(), h.getAmount(), h.isMine(),
				type == HitsplatID.POISON || type == HitsplatID.VENOM, client.getTickCount());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath e)
	{
		if (!arenaEvents()) return;
		if (e.getActor() instanceof NPC)
		{
			NPC npc = (NPC) e.getActor();
			round.npcDied(npc.getIndex(), PenanceType.forNpcId(npc.getId()));
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		track(e.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		if (!arenaEvents()) return;
		objects.remove(e.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		track(e.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		if (!arenaEvents()) return;
		objects.remove(e.getGroundObject());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned e)
	{
		WorldView world = client.getTopLevelWorldView();
		if (world == null || !world.isInstance()) return;
		TileItem item = e.getItem();
		if (!RoundLogic.tracksGroundItem(item.getId()))
		{
			return;
		}
		Tile tile = e.getTile();
		if (!arenaTile(tile.getLocalLocation())) return;
		arenaSeen = true;
		WorldPoint p = template(tile.getLocalLocation());
		Player me = client.getLocalPlayer();
		GroundItemView view = round.groundItemSpawned(item.getId(), p, item.getQuantity(), client.getTickCount(),
			me == null ? null : template(me.getLocalLocation()));
		ground.add(new GroundEntry(item, p, tile.getLocalLocation(), view.isGood(), view.getSpawnTick()));
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned e)
	{
		if (!arenaEvents()) return;
		TileItem item = e.getItem();
		for (GroundEntry g : ground)
		{
			if (g.item == item) round.groundItemDespawned(new GroundItemView(item.getId(), g.tile,
				item.getQuantity(), g.good, g.spawnTick), client.getTickCount());
		}
		ground.removeIf(g -> g.item == item);
	}

	/** The live scene tile of a tracked ground item on this template tile, or null. */
	public LocalPoint groundItemLocation(int itemId, WorldPoint templateTile)
	{
		for (GroundEntry g : ground)
		{
			if (g.item.getId() == itemId && g.tile != null && g.tile.equals(templateTile))
			{
				return g.live;
			}
		}
		return null;
	}

	/**
	 * The call texts on the HUD right now differ from the snapshot's (taken at the last game tick):
	 * a call is changing and the snapshot hasn't caught up yet. Overlays render every frame, the
	 * snapshot only once a tick, so for a moment the old call showed (reported 2026-10-16).
	 */
	public boolean callTextsChanging(Snapshot s)
	{
		Role r = s == null ? null : s.getRole();
		if (r == null)
		{
			return false;
		}
		return !java.util.Objects.equals(hud.text(r.getShoutComponent()), s.getShoutText())
			|| !java.util.Objects.equals(RoundLogic.readListen(hud, r), s.getListenText());
	}

	/** The tracked object with this id on this template tile, for overlays. */
	public TileObject findObject(int id, WorldPoint templateTile)
	{
		for (Map.Entry<TileObject, Integer> e : objects.entrySet())
		{
			if (e.getValue() == id && templateTile.equals(template(e.getKey().getLocalLocation())))
			{
				return e.getKey();
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ helpers

	private List<PenanceView> penanceViews()
	{
		ImmutableList.Builder<PenanceView> out = ImmutableList.builder();
		for (NPC npc : npcs.values())
		{
			PenanceType type = PenanceType.forNpcId(npc.getId());
			if (type != null)
			{
				out.add(round.penance(npc.getIndex(), type, template(npc.getLocalLocation()),
					npc.getHealthRatio(), npc.getHealthScale(), npc.isDead(), npc.getAnimation(), npc.getOverheadText()));
			}
		}
		return out.build();
	}

	private List<GroundItemView> groundViews()
	{
		List<GroundItemView> raw = new ArrayList<>();
		for (GroundEntry g : ground)
		{
			raw.add(new GroundItemView(g.item.getId(), g.tile, g.item.getQuantity(), g.good, g.spawnTick, underneath(g)));
		}
		return RoundLogic.mergeGround(raw);
	}

	/**
	 * Another item is drawn on top of this item's pile, so a left-click on the tile takes that one
	 * (the client's item layer keeps the top item of each pile). Unknown counts as on top.
	 */
	private boolean underneath(GroundEntry g)
	{
		if (g.live == null)
		{
			return false;
		}
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		Tile[][][] tiles = wv.getScene().getTiles();
		int plane = wv.getPlane();
		int x = g.live.getSceneX();
		int y = g.live.getSceneY();
		if (plane < 0 || plane >= tiles.length || x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length)
		{
			return false;
		}
		Tile tile = tiles[plane][x][y];
		net.runelite.api.ItemLayer layer = tile == null ? null : tile.getItemLayer();
		net.runelite.api.Renderable top = layer == null ? null : layer.getTop();
		return top instanceof TileItem && ((TileItem) top).getId() != g.item.getId();
	}

	private List<ObjectView> objectViews()
	{
		ImmutableList.Builder<ObjectView> out = ImmutableList.builder();
		objects.forEach((obj, id) ->
		{
			WorldPoint p = template(obj.getLocalLocation());
			if (p != null)
			{
				out.add(new ObjectView(id, p));
			}
		});
		return out.build();
	}

	private void track(TileObject obj)
	{
		WorldView world = client.getTopLevelWorldView();
		if (world == null || !world.isInstance()) return;
		if (obj != null && BaIds.TRACKED_OBJECTS.contains(obj.getId()) && arenaTile(obj.getLocalLocation()))
		{
			arenaSeen = true;
			objects.put(obj, obj.getId());
		}
	}

	private Map<Integer, Integer> inventoryCounts()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return ImmutableMap.of();
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() > 0)
			{
				counts.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}
		return ImmutableMap.copyOf(counts);
	}

	private int freeSlots()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return 28;
		}
		int used = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() > 0)
			{
				used++;
			}
		}
		return 28 - used;
	}

	private WorldPoint template(LocalPoint lp)
	{
		if (lp == null)
		{
			return null;
		}
		return WorldPoint.fromLocalInstance(client, lp);
	}
}
