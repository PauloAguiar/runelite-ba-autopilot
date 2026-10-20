package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.plan.Target;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.state.BaTracker;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.PlayerView;
import com.bautopilot.baautopilot.state.RoundLogic;
import com.bautopilot.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;

/**
 * Re-runs a recorded round through the plugin's own logic: recorded events and HUD texts go into
 * {@link RoundLogic} exactly as {@link BaTracker} would pass them, and each rebuilt
 * {@link Snapshot} goes through the {@link Planner}. Each recording starts from a fresh round.
 */
public final class RoundReplay
{
	/** One replayed tick: the raw recorded line and what the current code makes of it. */
	public static final class Frame
	{
		public final Rec.Tick raw;
		public final Snapshot snapshot;
		public final Instruction instruction;

		Frame(Rec.Tick raw, Snapshot snapshot, Instruction instruction)
		{
			this.raw = raw;
			this.snapshot = snapshot;
			this.instruction = instruction;
		}
	}

	private RoundReplay()
	{
	}

	public static List<Frame> run(RecordedRound round)
	{
		return run(round, new Planner()::plan);
	}

	/** Same recorded inputs, with independent instruction history for the supplied decision policy. */
	public static List<Frame> run(RecordedRound round, BiFunction<Snapshot, Settings, Instruction> policy)
	{
		Settings settings = round.header.settings == null ? Settings.builder().build() : round.header.settings;
		RoundLogic logic = new RoundLogic();

		// Mirrors of the tracker's client-side state
		Set<Integer> tracked = new LinkedHashSet<>();
		Map<Integer, GroundItemView> groundMemory = new HashMap<>();
		List<Rec.Obj> objects = Collections.emptyList();
		RoundLogic.Hud hud = hud(null);

		List<Frame> frames = new ArrayList<>();
		Instruction previous = null;
		int lastNpc = -1;
		for (Rec.Tick t : round.ticks)
		{
			if (t.objects != null)
			{
				objects = t.objects;
			}
			if (t.ev != null)
			{
				for (Rec.Event e : t.ev)
				{
					switch (e.k)
					{
						case "chat":
							boolean game = ChatMessageType.GAMEMESSAGE.name().equals(e.type) || ChatMessageType.WELCOME.name().equals(e.type)
								|| ChatMessageType.MESBOX.name().equals(e.type) || ChatMessageType.SPAM.name().equals(e.type);
							if (logic.chat(game, Text.removeTags(e.text), "Me".equals(e.name), e.t))
							{
								tracked.clear();
							}
							break;
						case "menu":
							String option = e.option == null ? "" : e.option;
							logic.menuClicked(option, e.target == null ? null : Text.removeTags(e.target), e.t);
							if (logic.getRole() != null && option.startsWith("Tell-"))
							{
								// The shout text as of the previous tick line
								logic.tellClicked(option, hud);
							}
							else if (MenuAction.WIDGET_TARGET_ON_NPC.name().equals(e.type) && e.npc != null && e.selected != null)
							{
								logic.usedOnNpc(e.npc, PenanceType.forNpcId(e.id), e.selected, e.t);
							}
							break;
						case "npcSpawn":
						{
							PenanceType type = PenanceType.forNpcId(e.id);
							if (type != null)
							{
								tracked.add(e.npc);
								logic.npcSpawned(e.npc, type, e.t);
							}
							break;
						}
						case "npcDespawn":
							if (tracked.remove(e.npc))
							{
								int distance = e.tile == null || t.tile == null ? 0
									: Math.max(Math.abs(e.tile.x - t.tile.x), Math.abs(e.tile.y - t.tile.y));
								logic.npcDespawned(e.npc, PenanceType.forNpcId(e.id), distance);
							}
							break;
						case "death":
							if (e.npc != null)
							{
								logic.npcDied(e.npc, PenanceType.forNpcId(e.id));
							}
							break;
						case "hit":
							if (e.npc != null && e.qty != null && tracked.contains(e.npc))
							{
								int type = e.type == null ? -1 : Integer.parseInt(e.type);
								logic.hit(e.npc, e.qty, "Me".equals(e.name),
									type == net.runelite.api.HitsplatID.POISON || type == net.runelite.api.HitsplatID.VENOM, e.t);
							}
							break;
						case "overhead":
							logic.overhead(e.name, Text.removeTags(e.text), "Me".equals(e.name), e.t);
							break;
						case "itemSpawn":
							groundMemory.put(e.uid, logic.groundItemSpawned(e.id, point(e.tile), e.qty == null ? 1 : e.qty, e.t, point(t.tile)));
							break;
						case "itemDespawn":
							GroundItemView gone = groundMemory.remove(e.uid);
							if (gone != null) logic.groundItemDespawned(gone, e.t);
							break;
						case "gameState":
							if ("LOGIN_SCREEN".equals(e.type) || "HOPPING".equals(e.type))
							{
								logic.reset();
								tracked.clear();
							}
							break;
						default:
							break;
					}
				}
			}

			hud = hud(t.hud);
			WorldPoint here = point(t.tile);
			int region = here == null ? -1 : here.getRegionID();
			boolean inArena = t.instance && ArenaTiles.isArenaRegion(region);
			boolean inLobby = !t.instance && region == ArenaTiles.REGION_LOBBY;
			Map<Integer, Integer> inventory = t.inv == null ? ImmutableMap.of() : ImmutableMap.copyOf(t.inv);
			if (logic.tick(t.t, inArena, hud, inventory, varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_POISON),
				varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_EXPLOSIVE), varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_STUN)))
			{
				tracked.clear();
			}

			logic.observeSelfHp(t.hp, t.maxHp);
			logic.observePosition(here);
			logic.observeQueenAmmo(varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_OMEGA));
			Map<Integer, Rec.Npc> scene = new HashMap<>();
			if (t.npcs != null)
			{
				t.npcs.forEach(n -> scene.put(n.i, n));
			}
			ImmutableList.Builder<PenanceView> penance = ImmutableList.builder();
			boolean queen = false;
			for (int idx : tracked)
			{
				Rec.Npc n = scene.get(idx);
				PenanceType type = n == null ? null : PenanceType.forNpcId(n.id);
				if (type != null)
				{
					penance.add(logic.penance(n.i, type, point(n.tile), n.hr, n.hs, n.dead, n.anim, n.overhead));
					queen |= type == PenanceType.QUEEN;
				}
			}

			List<GroundItemView> ground = new ArrayList<>();
			if (t.ground != null)
			{
				for (Rec.Ground g : t.ground)
				{
					GroundItemView remembered = groundMemory.get(g.uid);
					ground.add(new GroundItemView(g.id, point(g.tile), g.qty, remembered != null && remembered.isGood(),
						remembered == null ? t.t : remembered.getSpawnTick()));
				}
			}
			ImmutableList.Builder<ObjectView> objectViews = ImmutableList.builder();
			List<Integer> objectIds = new ArrayList<>();
			for (Rec.Obj o : objects)
			{
				objectViews.add(new ObjectView(o.id, point(o.tile)));
				objectIds.add(o.id);
			}

			Map<String, WorldPoint> playerTiles = new java.util.LinkedHashMap<>();
			List<PlayerView> players = new ArrayList<>();
			Map<String, Role> playerRoles = new java.util.LinkedHashMap<>();
			if (t.players != null) for (Rec.Plr player : t.players)
			{
				if (player.n != null && player.tile != null) playerTiles.put(player.n, point(player.tile));
				players.add(new PlayerView(player.n, player.role, point(player.tile), player.anim, player.interacting));
				if (player.n != null && player.role != null) playerRoles.put(player.n, player.role);
			}
			players.add(new PlayerView("Me", logic.getRole(), here, -1, null));
			boolean queenAlive = logic.queenPhase(queen);
			logic.observeQueenPlayers(players);
			if (!playerRoles.isEmpty() && logic.getRole() != null) playerRoles.put("Me", logic.getRole());
			Snapshot s = logic.snapshot()
				.inLobby(inLobby)
				.inventory(inventory)
				.freeSlots(t.free)
				.ammoId(t.ammo)
				.hasHammer(BaTracker.hasHammer(inventory, t.imcandoWorn))
				.hp(t.hp)
				.maxHp(t.maxHp)
				.playerTile(here)
				.playerTiles(com.google.common.collect.ImmutableMap.copyOf(playerTiles))
				.team(com.google.common.collect.ImmutableMap.copyOf(playerRoles))
				.penance(penance.build())
				.groundItems(RoundLogic.mergeGround(ground))
				.objects(objectViews.build())
				.eggsRed(varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_EXPLOSIVE))
				.eggsGreen(varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_POISON))
				.eggsBlue(varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_STUN))
				.eggsOmega(varbit(t, VarbitID.BARBASSAULT_EGGCOUNT_OMEGA))
				.trapCharges(RoundLogic.trapCharges(objectViews.build(), logic.getWave()))
				.roleLevels(roleLevels(t))
				.queenAlive(queenAlive)
				.lastTargetTile(previous == null ? null : previous.getTarget().getTile())
				.lastTargetNpc(lastNpc)
				.lastTask(previous == null ? null : previous.getTask())
				.lastStep(previous == null ? null : previous.getStep())
				.lastActionTarget(previous == null ? null : previous.getTarget())
				.tasks(previous == null ? com.bautopilot.baautopilot.plan.TaskMemory.empty() : previous.getTasks())
				.interactingNpc(t.interacting == null ? -1 : t.interacting)
				.build();
			Instruction ins = policy.apply(s, settings);
			frames.add(new Frame(t, s, ins));
			previous = ins;
			if (ins.getTarget().getKind() == Target.Kind.NPC)
			{
				lastNpc = ins.getTarget().getId();
			}
		}
		return frames;
	}

	/** Recorded levels belong to the recording player, matching the live tracker's varbits. */
	static Map<Role, Integer> roleLevels(Rec.Tick tick)
	{
		Map<Role, Integer> levels = new java.util.EnumMap<>(Role.class);
		for (Role role : Role.values())
		{
			int level = varbit(tick, roleLevelVarbit(role));
			if (level >= 1 && level <= 5) levels.put(role, level);
		}
		return levels;
	}

	static int roleLevelVarbit(Role role)
	{
		switch (role)
		{
			case ATTACKER: return VarbitID.BARBASSULT_ROLELEVEL_ATT;
			case DEFENDER: return VarbitID.BARBASSULT_ROLELEVEL_DEF;
			case COLLECTOR: return VarbitID.BARBASSULT_ROLELEVEL_COL;
			default: return VarbitID.BARBASSULT_ROLELEVEL_HEAL;
		}
	}

	/** The recorded HUD of one tick, read the way the live tracker reads widgets. */
	static RoundLogic.Hud hud(Map<Integer, String> recorded)
	{
		Map<Integer, String> texts = recorded == null ? Collections.emptyMap() : recorded;
		return new RoundLogic.Hud()
		{
			@Override
			public boolean visible(int component)
			{
				return texts.containsKey(component);
			}

			@Override
			public String text(int component)
			{
				String raw = texts.get(component);
				return raw == null ? null : Text.removeTags(raw);
			}
		};
	}

	static WorldPoint point(Rec.Pos p)
	{
		return p == null ? null : new WorldPoint(p.x, p.y, p.p);
	}

	private static int varbit(Rec.Tick t, int id)
	{
		return t.vb == null ? 0 : t.vb.getOrDefault(id, 0);
	}

	static boolean contains(int[] ids, int id)
	{
		if (ids != null)
		{
			for (int i : ids)
			{
				if (i == id)
				{
					return true;
				}
			}
		}
		return false;
	}
}
