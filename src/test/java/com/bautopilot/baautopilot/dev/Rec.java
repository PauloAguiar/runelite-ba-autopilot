package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.model.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Map;

/**
 * The JSONL recording format: one {@link Header} line, then one {@link Tick} line per game tick.
 * Raw game inputs only (texts keep their tags), plus the live plugin's result under {@code live}.
 * Absent fields are null and left out of the line.
 */
final class Rec
{
	static final int VERSION = 1;

	/** Compact (one object per line); maps with int keys round-trip through string keys. */
	static final Gson GSON = new GsonBuilder().create();

	private Rec()
	{
	}

	static final class Header
	{
		String type = "header";
		int version = VERSION;
		/** Local wall time the segment started. */
		String started;
		String pluginVersion;
		/** Identifies the recorder/decision changes used for the next-run reliability validation. */
		String recorderBuild;
		/** Old recordings: the strategy mode they were made with (no longer used). */
		String strategy;
		Settings settings;
	}

	static final class Tick
	{
		String type = "tick";
		/** client.getTickCount() */
		int t;
		/** Milliseconds since the segment started. */
		long ms;
		boolean instance;
		/** Player template tile (instance template coordinates, as the tracker sees it). */
		Pos tile;
		/** RoundLogic.HUD_COMPONENTS that are visible -> raw widget text. */
		Map<Integer, String> hud;
		/** Inventory item id -> count. */
		Map<Integer, Integer> inv;
		int free;
		int ammo = -1;
		boolean imcandoWorn;
		int hp;
		int maxHp;
		/** NPC index the player is interacting with (attacking), null for none. */
		Integer interacting;
		/** Varbit id -> value: egg loads 3257/3258/3259/3266, AREAEXIT 3923. */
		Map<Integer, Integer> vb;
		/** Every penance NPC in the scene. */
		List<Npc> npcs;
		/** Tracked ground items (bait, eggs, logs, hammer). */
		List<Ground> ground;
		/** Tracked objects (machines, trap, hoppers, ...); only written when the set changed. */
		List<Obj> objects;
		/** Other players in the scene (names anonymised) with their spot-animations. */
		List<Plr> players;
		/** Graphics objects in the scene (id + tile), to find role rings drawn as effects. */
		List<Obj> gfx;
		/** Events since the previous tick line, in arrival order. */
		List<Event> ev;
		/** What the live plugin made of this tick. */
		Live live;
	}

	static final class Pos
	{
		int x;
		int y;
		int p;

		Pos(int x, int y, int p)
		{
			this.x = x;
			this.y = y;
			this.p = p;
		}
	}

	static final class Npc
	{
		int i;
		int id;
		Pos tile;
		int hr;
		int hs;
		boolean dead;
		int anim;
		/** NPC speech, e.g. a runner's good/bad-food reaction; absent in older captures. */
		String overhead;
		int[] spot;
	}

	static final class Plr
	{
		String n;
		Role role;
		Pos tile;
		int anim;
		/** Directed public interaction, anonymised; absent in old captures. */
		String interacting;
		int[] spot;
	}

	static final class Ground
	{
		/** Recorder-assigned identity of the TileItem (stable while it lies there). */
		int uid;
		int id;
		int qty;
		Pos tile;
	}

	static final class Obj
	{
		int id;
		Pos tile;

		Obj(int id, Pos tile)
		{
			this.id = id;
			this.tile = tile;
		}
	}

	/**
	 * A discrete event. {@code k} is one of chat, menu, npcSpawn, npcDespawn, death, graphic,
	 * itemSpawn, itemDespawn, gameState.
	 */
	static final class Event
	{
		int t;
		String k;
		/** chat: ChatMessageType name; gameState: GameState name; menu: MenuAction name. */
		String type;
		/** chat sender / dead player, anonymised ("Me", "Player2", ...). */
		String name;
		/** chat message (raw, with tags). */
		String text;
		String option;
		String target;
		/** NPC index (menu target, spawn, death, graphic). */
		Integer npc;
		/** NPC or item id. */
		Integer id;
		/** menu: the selected (used) widget's item id; the clicked item id. */
		Integer selected;
		Integer item;
		/** menu: the clicked interface component (packed group << 16 | child), for widget clicks. */
		Integer widget;
		Integer uid;
		Integer qty;
		Pos tile;
		int[] spot;
	}

	static final class Live
	{
		String role;
		int wave;
		int period;
		int toChange;
		int waveTick;
		String listen;
		boolean fresh;
		String source;
		String shout;
		boolean shoutDone;
		/** Null in older recordings; confirmed red/green ammunition consumption tick, -1 when unknown. */
		Integer lastLauncherShotTick;
		boolean coordinated;
		String step;
		String task;
		/** Unfinished obligation id -> current status and blocking reason, when any. */
		Map<String, String> tasks;
		String headline;
		String detail;
		List<Integer> items;
		/** Healer NPC index -> foods counted. */
		Map<Integer, Integer> foods;
	}
}
