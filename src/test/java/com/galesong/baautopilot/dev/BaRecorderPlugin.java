package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.BaAutopilotConfig;
import com.galesong.baautopilot.BaAutopilotPlugin;
import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Settings;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.RoundLogic;
import com.galesong.baautopilot.state.Snapshot;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

/**
 * Development only (test sources, never shipped): records the raw game inputs of every tick in the
 * Barbarian Assault lobby and arena, plus what BA Autopilot made of them, to
 * {@code ~/.runelite/ba-autopilot/recordings/<timestamp>-wave<N>.jsonl}. One file per wave; it is
 * closed and flushed when you leave the arena. Player names are replaced by "Me" / "PlayerN".
 * Replay a file with {@code gradlew replay -Pfile=...}; drop it into
 * {@code src/test/resources/recordings} to keep it as a regression test.
 */
@Slf4j
@PluginDescriptor(
	name = "BA Autopilot Recorder (Debug)",
	description = "Development only: records Barbarian Assault rounds for BA Autopilot replay tests",
	tags = {"ba", "debug"}
)
public class BaRecorderPlugin extends Plugin
{
	/** Ticks outside lobby and arena before an open segment is closed (covers loading screens). */
	private static final int OUTSIDE_GRACE_TICKS = 50;
	private static final int FLUSH_EVERY_TICKS = 100;
	private static final int[] VARBITS = {
		VarbitID.BARBASSAULT_EGGCOUNT_POISON, VarbitID.BARBASSAULT_EGGCOUNT_EXPLOSIVE,
		VarbitID.BARBASSAULT_EGGCOUNT_STUN, VarbitID.BARBASSAULT_EGGCOUNT_OMEGA,
		VarbitID.BARBASSAULT_AREAEXIT_PENDING,
		// Not used by the plugin yet; recorded to learn what they track (bag contents, picks, role level)
		VarbitID.BARBASSAULT_COLL_EGGINBAG_02, VarbitID.BARBASSAULT_COLL_EGGINBAG_03,
		VarbitID.BARBASSAULT_COLL_PICK_GOOD, VarbitID.BARBASSAULT_COLL_PICK_BAD,
		VarbitID.BARBASSULT_ROLELEVEL_COL, VarbitID.BARBASSULT_ROLELEVEL_ATT, VarbitID.BARBASSULT_ROLELEVEL_DEF,
		VarbitID.BARBASSULT_ROLELEVEL_HEAL, VarbitID.BARBASSAULT_USINGHORN,
		VarbitID.BARBASSAULT_HEALER_PNT_REST, VarbitID.BARBASSAULT_HEALER_FAIL,
	};
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	/** Horn calls as chat and overhead text show them; recordings keep no other player's words. */
	private static final Pattern HORN_CALL = Pattern.compile("^(Attacker|Defender|Collector|Healer): .+!$");
	/** Chat channels whose lines are other people's words or names (clan, friends, private, trade). */
	private static final Pattern PRIVATE_CHANNEL = Pattern.compile("^(CLAN_|FRIENDSCHAT|PRIVATECHAT|MODPRIVATECHAT|TRADE|CHALREQ|BROADCAST|FRIENDNOTIFICATION|IGNORENOTIFICATION|LOGINLOGOUTNOTIFICATION)");
	private static final Pattern SIGN_UP_NAME = Pattern.compile("(sign up ).+?( because)");

	@Inject
	private Client client;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ConfigManager configManager;

	/** Rounds are recorded only in a developer-mode client (--developer-mode). */
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	private BaAutopilotPlugin autopilot;
	private BaAutopilotConfig config;

	private final List<Rec.Event> pending = new ArrayList<>();
	private final Map<TileItem, Rec.Ground> ground = new IdentityHashMap<>();
	private final Map<TileObject, Integer> objects = new HashMap<>();
	private final Map<String, String> aliases = new HashMap<>();
	private int nextUid = 1;
	private List<Rec.Obj> lastObjects;

	private BufferedWriter writer;
	private Path partFile;
	private String stamp;
	private long startedMs;
	private int linesSinceFlush;
	private int outsideTicks;
	private boolean wasInArena;
	private boolean sawArena;
	private int maxWave;

	@Override
	protected void startUp()
	{
		autopilot = pluginManager.getPlugins().stream()
			.filter(BaAutopilotPlugin.class::isInstance)
			.map(BaAutopilotPlugin.class::cast)
			.findFirst()
			.orElse(null);
		config = configManager.getConfig(BaAutopilotConfig.class);
	}

	@Override
	protected void shutDown()
	{
		closeSegment();
		pending.clear();
		ground.clear();
		objects.clear();
	}

	// ------------------------------------------------------------------ tick

	/** Runs after BA Autopilot's own tick handler so its snapshot for this tick is ready. */
	@Subscribe(priority = -10)
	public void onGameTick(GameTick e)
	{
		Player me = client.getLocalPlayer();
		WorldPoint here = me == null ? null : template(me.getLocalLocation());
		int region = here == null ? -1 : here.getRegionID();
		boolean instance = client.getTopLevelWorldView().isInstance();
		boolean inArena = instance && ArenaTiles.isArenaRegion(region);
		boolean inLobby = !instance && region == ArenaTiles.REGION_LOBBY;

		if (inArena || inLobby)
		{
			outsideTicks = 0;
			if (writer == null && developerMode)
			{
				openSegment();
			}
		}
		else
		{
			outsideTicks++;
		}

		if (writer != null)
		{
			sawArena |= inArena;
			write(tickLine(instance, here));
			boolean leftArena = wasInArena && !inArena;
			if (leftArena || outsideTicks > OUTSIDE_GRACE_TICKS)
			{
				closeSegment();
			}
		}
		pending.clear();
		wasInArena = inArena;
	}

	private Rec.Tick tickLine(boolean instance, WorldPoint here)
	{
		Rec.Tick t = new Rec.Tick();
		t.t = client.getTickCount();
		t.ms = System.currentTimeMillis() - startedMs;
		t.instance = instance;
		t.tile = pos(here);
		t.hud = hud();
		t.inv = inventory();
		t.free = freeSlots();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item a = worn.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
			t.ammo = a == null ? -1 : a.getId();
			t.imcandoWorn = worn.contains(ItemID.IMCANDO_HAMMER) || worn.contains(ItemID.IMCANDO_HAMMER_OFFHAND);
		}
		t.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		Player self = client.getLocalPlayer();
		t.interacting = self != null && self.getInteracting() instanceof NPC ? ((NPC) self.getInteracting()).getIndex() : null;
		t.maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		t.vb = new TreeMap<>();
		for (int v : VARBITS)
		{
			t.vb.put(v, client.getVarbitValue(v));
		}
		t.npcs = penance();
		t.players = players();
		t.gfx = graphicsObjects();
		t.ground = new ArrayList<>(ground.values());
		List<Rec.Obj> objs = objectList();
		if (!sameObjects(objs, lastObjects))
		{
			t.objects = objs;
			lastObjects = objs;
		}
		t.ev = pending.isEmpty() ? null : new ArrayList<>(pending);
		t.live = live();
		return t;
	}

	private Map<Integer, String> hud()
	{
		Map<Integer, String> out = new TreeMap<>();
		for (int c : RoundLogic.HUD_COMPONENTS)
		{
			Widget w = client.getWidget(c);
			if (w == null || w.isHidden())
			{
				continue;
			}
			String text = w.getText() == null ? "" : w.getText();
			for (int name : RoundLogic.TEAMMATE_NAMES)
			{
				if (c == name && !text.trim().isEmpty() && !text.trim().equals("-"))
				{
					text = alias(Text.removeTags(text));
				}
			}
			out.put(c, text);
		}
		return out;
	}

	private List<Rec.Npc> penance()
	{
		List<Rec.Npc> out = new ArrayList<>();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null || PenanceType.forNpcId(npc.getId()) == null)
			{
				continue;
			}
			Rec.Npc n = new Rec.Npc();
			n.i = npc.getIndex();
			n.id = npc.getId();
			n.tile = pos(template(npc.getLocalLocation()));
			n.hr = npc.getHealthRatio();
			n.hs = npc.getHealthScale();
			n.dead = npc.isDead();
			n.anim = npc.getAnimation();
			n.overhead = npc.getOverheadText();
			n.spot = spotAnims(npc);
			out.add(n);
		}
		return out;
	}

	private Rec.Live live()
	{
		if (autopilot == null)
		{
			return null;
		}
		Snapshot s = autopilot.getSnapshot();
		Instruction in = autopilot.getInstruction();
		if (s == null || in == null)
		{
			return null;
		}
		Rec.Live l = new Rec.Live();
		l.role = s.getRole() == null ? null : s.getRole().name();
		l.wave = s.getWave();
		l.period = s.getCallPeriod();
		l.toChange = s.getTicksToCallChange();
		l.waveTick = s.getWaveTick();
		l.listen = s.getListenCall() == null ? null : s.getListenCall().name();
		l.fresh = s.isListenFresh();
		l.source = s.getListenSource();
		l.shout = s.getShoutCall() == null ? null : s.getShoutCall().name();
		l.shoutDone = s.isShoutDone();
		l.lastLauncherShotTick = s.getLastLauncherShotTick();
		l.step = in.getStep() == null ? null : in.getStep().name();
		l.task = in.getTask();
		Map<String, String> tasks = new LinkedHashMap<>();
		in.getTasks().getEntries().forEach((id, progress) -> tasks.put(id, progress.getStatus().name()
			+ (progress.getReason().isEmpty() ? "" : ": " + progress.getReason())));
		l.tasks = tasks.isEmpty() ? null : tasks;
		l.headline = in.getHeadline();
		l.detail = in.getDetail();
		l.items = in.getItems().isEmpty() ? null : new ArrayList<>(in.getItems());
		Map<Integer, Integer> foods = new LinkedHashMap<>();
		for (PenanceView p : s.penance(PenanceType.HEALER))
		{
			foods.put(p.getNpcIndex(), p.getFoods());
		}
		l.foods = foods.isEmpty() ? null : foods;
		maxWave = Math.max(maxWave, s.getWave());
		return l;
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (!recordable(e.getType(), e.getMessage()))
		{
			return;
		}
		Rec.Event ev = event("chat");
		ev.type = e.getType().name();
		ev.name = e.getName() == null || e.getName().isEmpty() ? null : alias(e.getName());
		ev.text = SIGN_UP_NAME.matcher(e.getMessage()).replaceAll("$1Player$2");
		pending.add(ev);
	}

	/** Game messages and horn calls only: public chat that isn't a call, clan, friends and private chat stay out. */
	static boolean recordable(ChatMessageType type, String text)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case AUTOTYPER:
			case MODCHAT:
			case MODAUTOTYPER:
				return HORN_CALL.matcher(Text.removeTags(text)).matches();
			default:
				return !PRIVATE_CHANNEL.matcher(type.name()).find();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		Rec.Event ev = event("menu");
		ev.type = e.getMenuAction().name();
		ev.option = Text.removeTags(e.getMenuOption());
		ev.target = Text.removeTags(e.getMenuTarget());
		ev.id = e.getId();
		ev.item = e.getItemId() > 0 ? e.getItemId() : null;
		ev.widget = e.getWidget() != null ? e.getWidget().getId() : null;
		NPC npc = e.getMenuEntry().getNpc();
		if (npc != null)
		{
			ev.npc = npc.getIndex();
			ev.id = npc.getId();
		}
		Player player = e.getMenuEntry().getPlayer();
		if (player != null && player.getName() != null)
		{
			ev.target = alias(player.getName());
		}
		Widget selected = client.getSelectedWidget();
		if (selected != null && selected.getItemId() > 0)
		{
			ev.selected = selected.getItemId();
		}
		pending.add(ev);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		pending.add(npcEvent("npcSpawn", e.getNpc()));
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		pending.add(npcEvent("npcDespawn", e.getNpc()));
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (e.getActor() instanceof NPC && PenanceType.forNpcId(((NPC) e.getActor()).getId()) != null)
		{
			Rec.Event ev = npcEvent("hit", (NPC) e.getActor());
			ev.qty = e.getHitsplat().getAmount();
			ev.type = String.valueOf(e.getHitsplat().getHitsplatType());
			ev.name = e.getHitsplat().isMine() ? "Me" : null;
			pending.add(ev);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath e)
	{
		Actor a = e.getActor();
		if (a instanceof NPC)
		{
			pending.add(npcEvent("death", (NPC) a));
		}
		else if (a instanceof Player && a.getName() != null)
		{
			Rec.Event ev = event("death");
			ev.name = alias(a.getName());
			pending.add(ev);
		}
	}

	/** Overhead text of players: horn calls show here for other players (not as chat). */
	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged e)
	{
		if (writer == null || !(e.getActor() instanceof Player) || e.getOverheadText() == null
			|| !HORN_CALL.matcher(Text.removeTags(e.getOverheadText())).matches())
		{
			return;
		}
		Rec.Event ev = event("overhead");
		ev.name = alias(((Player) e.getActor()).getName());
		ev.text = e.getOverheadText();
		pending.add(ev);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged e)
	{
		if (!(e.getActor() instanceof NPC) || PenanceType.forNpcId(((NPC) e.getActor()).getId()) != PenanceType.HEALER)
		{
			return;
		}
		NPC npc = (NPC) e.getActor();
		Rec.Event ev = npcEvent("graphic", npc);
		ev.spot = spotAnims(npc);
		pending.add(ev);
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned e)
	{
		TileItem item = e.getItem();
		if (!RoundLogic.tracksGroundItem(item.getId()))
		{
			return;
		}
		Rec.Ground g = new Rec.Ground();
		g.uid = nextUid++;
		g.id = item.getId();
		g.qty = item.getQuantity();
		g.tile = pos(template(e.getTile().getLocalLocation()));
		ground.put(item, g);
		Rec.Event ev = event("itemSpawn");
		ev.uid = g.uid;
		ev.id = g.id;
		ev.qty = g.qty;
		ev.tile = g.tile;
		pending.add(ev);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned e)
	{
		Rec.Ground g = ground.remove(e.getItem());
		if (g != null)
		{
			Rec.Event ev = event("itemDespawn");
			ev.uid = g.uid;
			ev.id = g.id;
			pending.add(ev);
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
		objects.remove(e.getGroundObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		Rec.Event ev = event("gameState");
		ev.type = e.getGameState().name();
		pending.add(ev);
		switch (e.getGameState())
		{
			case LOADING:
				// The tracker forgets objects and ground items on a load; mirror it
				objects.clear();
				ground.clear();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				closeSegment();
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ file

	private void openSegment()
	{
		try
		{
			Path dir = RuneLite.RUNELITE_DIR.toPath().resolve("ba-autopilot").resolve("recordings");
			Files.createDirectories(dir);
			stamp = LocalDateTime.now().format(STAMP);
			partFile = dir.resolve(stamp + ".jsonl.part");
			writer = Files.newBufferedWriter(partFile, StandardCharsets.UTF_8);
			startedMs = System.currentTimeMillis();
			aliases.clear();
			lastObjects = null;
			sawArena = false;
			maxWave = 0;
			linesSinceFlush = 0;

			Rec.Header h = new Rec.Header();
			h.started = stamp;
			h.pluginVersion = "0.1-beta";
			h.recorderBuild = "healer-reserve-pressure-2026-10-18";
			h.settings = Settings.builder()
				.attackMethod(config.attackMethod())
				.retreatBelow(config.retreatBelow() / 100.0)
				.healTeammateBelow(config.healTeammateBelow() / 100.0)
				.collectorLoadAt(config.collectorLoadAt())
				.healCodeSet(config.healCodeSet())
				.defenderGuide(config.defenderGuide())
				.safetyBait(config.safetyBait())
				.defenderPattern(config.defenderPattern())
				.defenderMultiKill(config.defenderMultiKill())
				.skipCoveredCalls(config.skipCoveredCalls())
				.splitTargets(config.splitTargets())
				.queenStack(config.queenStack())
				.queenEggBatch(config.queenEggBatch())
				.queenEggBuffer(config.queenEggBuffer())
				.queenSingleLoads(config.queenSingleLoads())
				.build();
			write(h);
			log.info("BA recorder: recording to {}", partFile);
		}
		catch (IOException ex)
		{
			log.warn("BA recorder: cannot open a recording", ex);
			writer = null;
		}
	}

	private void closeSegment()
	{
		if (writer == null)
		{
			return;
		}
		try
		{
			writer.close();
			if (sawArena)
			{
				Path done = partFile.resolveSibling(stamp + "-wave" + maxWave + ".jsonl");
				Files.move(partFile, done, StandardCopyOption.REPLACE_EXISTING);
				log.info("BA recorder: saved {}", done);
			}
			else
			{
				// Lobby only, no wave played: nothing worth replaying
				Files.deleteIfExists(partFile);
			}
		}
		catch (IOException ex)
		{
			log.warn("BA recorder: cannot finish {}", partFile, ex);
		}
		writer = null;
		partFile = null;
	}

	private void write(Object line)
	{
		try
		{
			writer.write(Rec.GSON.toJson(line));
			writer.newLine();
			if (++linesSinceFlush >= FLUSH_EVERY_TICKS)
			{
				writer.flush();
				linesSinceFlush = 0;
			}
		}
		catch (IOException ex)
		{
			log.warn("BA recorder: write failed, stopping this recording", ex);
			closeSegment();
		}
	}

	// ------------------------------------------------------------------ helpers

	private Rec.Event event(String kind)
	{
		Rec.Event ev = new Rec.Event();
		ev.t = client.getTickCount();
		ev.k = kind;
		return ev;
	}

	private Rec.Event npcEvent(String kind, NPC npc)
	{
		Rec.Event ev = event(kind);
		ev.npc = npc.getIndex();
		ev.id = npc.getId();
		ev.tile = pos(template(npc.getLocalLocation()));
		return ev;
	}

	/** Stable per-recording stand-in for a player name. */
	private String alias(String name)
	{
		if (name == null)
		{
			return null;
		}
		String key = Text.standardize(Text.removeTags(name));
		Player me = client.getLocalPlayer();
		if (me != null && me.getName() != null && key.equals(Text.standardize(me.getName())))
		{
			return "Me";
		}
		return aliases.computeIfAbsent(key, k -> "Player" + (aliases.size() + 2));
	}

	/** Every other player: anonymised name, tile, animation and spot-animations (role rings, if they are spotanims). */
	private List<Rec.Plr> players()
	{
		List<Rec.Plr> out = new ArrayList<>();
		Player me = client.getLocalPlayer();
		for (Player p : client.getTopLevelWorldView().players())
		{
			if (p == null || p == me || p.getName() == null)
			{
				continue;
			}
			Rec.Plr r = new Rec.Plr();
			r.n = alias(p.getName());
			r.role = autopilot == null || autopilot.getSnapshot() == null ? null : autopilot.getSnapshot().getTeam().get(p.getName());
			r.tile = pos(template(p.getLocalLocation()));
			r.anim = p.getAnimation();
			r.interacting = p.getInteracting() instanceof Player ? alias(p.getInteracting().getName()) : null;
			r.spot = spotAnims(p);
			out.add(r);
		}
		return out.isEmpty() ? null : out;
	}

	private List<Rec.Obj> graphicsObjects()
	{
		List<Rec.Obj> out = new ArrayList<>();
		for (GraphicsObject g : client.getTopLevelWorldView().getGraphicsObjects())
		{
			Rec.Pos p = pos(template(g.getLocation()));
			if (p != null)
			{
				out.add(new Rec.Obj(g.getId(), p));
			}
		}
		return out.isEmpty() ? null : out;
	}

	private static int[] spotAnims(Actor npc)
	{
		List<Integer> ids = new ArrayList<>();
		for (ActorSpotAnim a : npc.getSpotAnims())
		{
			ids.add(a.getId());
		}
		return ids.isEmpty() ? null : ids.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Role glow scenery (20212 healer, 20213 defender, 20214 collector, 20215 attacker): possibly the rings under players. */
	private static boolean glow(int id)
	{
		return id >= ObjectID.BARBASSAULT_GLOW_HEALER && id <= ObjectID.BARBASSAULT_GLOW_ATTACKER;
	}

	private void track(TileObject obj)
	{
		if (obj != null && (BaIds.TRACKED_OBJECTS.contains(obj.getId()) || glow(obj.getId())))
		{
			objects.put(obj, obj.getId());
		}
	}

	private List<Rec.Obj> objectList()
	{
		List<Rec.Obj> out = new ArrayList<>();
		objects.forEach((obj, id) ->
		{
			Rec.Pos p = pos(template(obj.getLocalLocation()));
			if (p != null)
			{
				out.add(new Rec.Obj(id, p));
			}
		});
		out.sort((a, b) -> a.id != b.id ? Integer.compare(a.id, b.id)
			: a.tile.x != b.tile.x ? Integer.compare(a.tile.x, b.tile.x) : Integer.compare(a.tile.y, b.tile.y));
		return out;
	}

	private static boolean sameObjects(List<Rec.Obj> a, List<Rec.Obj> b)
	{
		if (b == null || a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			Rec.Obj x = a.get(i);
			Rec.Obj y = b.get(i);
			if (x.id != y.id || x.tile.x != y.tile.x || x.tile.y != y.tile.y || x.tile.p != y.tile.p)
			{
				return false;
			}
		}
		return true;
	}

	private Map<Integer, Integer> inventory()
	{
		Map<Integer, Integer> counts = new TreeMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item item : inv.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					counts.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
				}
			}
		}
		return counts;
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
		return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
	}

	private static Rec.Pos pos(WorldPoint p)
	{
		return p == null ? null : new Rec.Pos(p.getX(), p.getY(), p.getPlane());
	}
}
