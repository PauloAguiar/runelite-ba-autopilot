package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.TaskMemory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Everything the planner needs for one tick. Immutable and free of client references so the
 * planner can be unit tested with hand-built snapshots.
 */
@Value
@Builder(toBuilder = true)
public class Snapshot
{
	int tick;
	boolean inArena;
	boolean inLobby;
	/** Once the Collector has passed the hill this wave, later lulls must not restart the opening. */
	boolean collectorOpeningComplete;
	/** The Attacker's opening western route is finished for this wave. */
	boolean attackerOpeningComplete;
	/** 1-10, 0 when unknown. */
	int wave;
	Role role;

	/** Ticks since the wave began, -1 when unknown. */
	@Builder.Default
	int waveTick = -1;
	/** 0-based index of the current 30-second call. */
	int callPeriod;
	@Builder.Default
	int ticksToCallChange = -1;

	/** The call you received (what you act on); null before your partner calls. */
	BaCall listenCall;
	/** True when the listen call was (re)heard during the current call period. */
	boolean listenFresh;
	/** The call you must give your partner; null when the HUD shows none. */
	BaCall shoutCall;
	/** True when you already used the horn for the current call. */
	boolean shoutDone;
	/** The wrong call you made this period (not yet corrected), or null. */
	BaCall shoutWrong;
	/** A teammate of your role who already made your call this period, or null. */
	String shoutCoveredBy;
	/** Raw HUD texts behind the two calls, for the diagnostics panel. */
	String listenText;
	String shoutText;
	/** Which source the listen call came from (HUD, HORN, CHAT), for diagnostics. */
	String listenSource;

	@Builder.Default
	Map<Integer, Integer> inventory = ImmutableMap.of();
	int freeSlots;
	@Builder.Default
	int ammoId = -1;
	/** A hammer in the inventory, or an Imcando hammer worn or carried. */
	boolean hasHammer;
	int hp;
	int maxHp;
	WorldPoint playerTile;
	/** Tile the previous instruction pointed at, so targets don't hop between equal choices. */
	WorldPoint lastTargetTile;
	/** The task the previous instruction belonged to, so a committed task is kept until done. */
	String lastTask;
	/** Previous action identity; unlike lastTask, distinguishes dunking from passing the same egg. */
	com.bautopilot.baautopilot.plan.Step lastStep;
	com.bautopilot.baautopilot.plan.Target lastActionTarget;
	/** Unfinished work survives a call, survival interruption, or temporary prerequisite failure. */
	@Builder.Default
	TaskMemory tasks = TaskMemory.empty();
	/** Walking distance on the collision map; null (replays, tests) falls back to straight-line tiles. */
	WalkCost walkCost;
	/** NPC index the previous instruction pointed at, -1 for none. */
	@Builder.Default
	int lastTargetNpc = -1;
	/** NPC index you are attacking or interacting with, -1 for none. */
	@Builder.Default
	int interactingNpc = -1;

	@Builder.Default
	List<PenanceView> penance = ImmutableList.of();
	/** Penance that walked out of view and is presumed alive, as last seen (not in {@link #penance}). */
	@Builder.Default
	List<PenanceView> unseenPenance = ImmutableList.of();
	/** Last observations of timed-out runners; diagnostic only, not additional live targets. */
	@Builder.Default
	List<PenanceView> uncertainPenance = ImmutableList.of();
	@Builder.Default
	List<GroundItemView> groundItems = ImmutableList.of();
	@Builder.Default
	List<ObjectView> objects = ImmutableList.of();
	@Builder.Default
	List<TeammateView> teammates = ImmutableList.of();
	/** Observed player positions in template coordinates, for care/travel tradeoffs. */
	@Builder.Default
	Map<String, WorldPoint> playerTiles = ImmutableMap.of();
	/** Mean observed net HP loss per tick over the last 20 ticks; no history means zero. */
	double selfDamagePerTick;
	@Builder.Default
	Map<String, Double> teammateDamagePerTick = ImmutableMap.of();

	/** Your own player name, for matching yourself in the team list. */
	String selfName;

	/** Teammates' roles by name, from the lobby team list (may be empty). */
	@Builder.Default
	Map<String, Role> team = ImmutableMap.of();
	/** Your Tell- click this call never became a call (a click right after the horn cancels it). */
	boolean shoutLost;

	/** This wave's performance so far (null outside a wave). */
	WaveStats waveStats;

	/** Eggs in the collector's collection bag (not in the inventory), tracked from game messages. */
	int bagRed;
	int bagGreen;
	int bagBlue;

	/** Egg launcher loads (shared by both launchers). */
	int eggsRed;
	int eggsGreen;
	int eggsBlue;
	int eggsOmega;
	/** Bait dropped per tile this wave, eaten or not: a pattern spot once filled stays done. */
	@Builder.Default
	java.util.Map<WorldPoint, Integer> goodBaitLanded = com.google.common.collect.ImmutableMap.of();
	@Builder.Default
	java.util.Map<WorldPoint, Integer> badBaitLanded = com.google.common.collect.ImmutableMap.of();
	/** Types the game announced as all killed this wave ("All of the Penance X have been killed!"). */
	@Builder.Default
	java.util.Set<PenanceType> cleared = com.google.common.collect.ImmutableSet.of();
	/** Your Barbarian Assault level per role (1-5; varbits 3251/3253/3254/3255), 0 when unknown. */
	@Builder.Default
	java.util.Map<com.bautopilot.baautopilot.model.Role, Integer> roleLevels = com.google.common.collect.ImmutableMap.of();
	/** Your Honour points per role (varbit base + 512 x extra, as the BA Minigame plugin reads them). */
	@Builder.Default
	java.util.Map<com.bautopilot.baautopilot.model.Role, Integer> rolePoints = com.google.common.collect.ImmutableMap.of();
	/** The HUD's call text is this period's (changed since the period began, or 2 ticks in). */
	@Builder.Default
	boolean shoutSettled = true;
	/** Logs picked up this wave, used or not. */
	int logsTaken;
	/** Red/green launcher shots per tick over the last call (anyone's), 0 when nobody fires. */
	double launcherShotsPerTick;
	/** Last confirmed red/green ammunition decrease, -1 when no recent shot is known. */
	@Builder.Default
	int lastLauncherShotTick = -1;
	/** Damage per second on penance over the last few seconds: yours, and everyone's. */
	double myDps;
	double teamDps;
	/** Your damage on penance this wave. */
	int myDamage;

	/** Fighters + rangers killed this wave (drives attacker alternation). */
	int fighterRangerKills;
	/** Kills per penance type this wave. */
	@Builder.Default
	Map<PenanceType, Integer> killsByType = ImmutableMap.of();
	/** Counted runner kills, excluding unconfirmed out-of-view timeouts. */
	int runnersKilled;
	/** Runner absences that timed out without kill evidence. */
	int uncertainRunners;
	int healersKilled;
	/** Runner trap charges (0-2), -1 when the trap is not in view. */
	@Builder.Default
	int trapCharges = -1;
	boolean queenAlive;
	/** Confirmed own yellow hand-offs this wave. */
	int queenYellowPassed;
	/** Confirmed own spiked hand-offs; consuming spikes is progress, not a restock obligation. */
	int queenSpikedPassed;
	/** Visible Defender-to-Collector hand-off cue; -1 when not observed. */
	@Builder.Default
	int queenOmegaHandoffTick = -1;
	/** Lower bound on omega eggs fired, not individual simultaneous player hits. */
	int queenOmegaFired;
	@Builder.Default
	int lastQueenOmegaShotTick = -1;
	/** Actual death event/zero health evidence, never inferred from leaving view. */
	boolean queenDead;


	public int count(int itemId)
	{
		return inventory.getOrDefault(itemId, 0);
	}

	public boolean has(int itemId)
	{
		return count(itemId) > 0;
	}

	/** Tiles to walk from you to a tile: the collision map when known, else straight-line distance. */
	public int walk(WorldPoint tile)
	{
		if (playerTile == null || tile == null)
		{
			return 0;
		}
		int path = walkCost == null ? -1 : walkCost.tilesTo(tile);
		return path >= 0 ? path : ArenaTiles.distance(playerTile, tile);
	}

	public double hpFraction()
	{
		return maxHp <= 0 ? 1.0 : (double) hp / maxHp;
	}

	/** Ticks since the current call began, -1 when unknown. */
	public int ticksIntoCall()
	{
		return ticksToCallChange < 0 ? -1 : WaveInfo.TICKS_PER_CALL - ticksToCallChange;
	}

	public int waveSeconds()
	{
		return waveTick < 0 ? -1 : waveTick * 3 / 5;
	}

	public List<PenanceView> penance(PenanceType type)
	{
		if (cleared.contains(type))
		{
			return new java.util.ArrayList<>();
		}
		return penance.stream().filter(p -> p.getType() == type && !p.isDying()).collect(Collectors.toList());
	}

	/**
	 * Alive penance of a type: in view, then out of view (flagged {@link PenanceView#isUnseen}, last
	 * seen tile). Use for "is any left" and for keeping a target; use {@link #penance} for anything
	 * that needs a live position, like launcher range.
	 */
	/**
	 * Tiles from a bait within which a runner notices it: 4 at defender level 1, 5 at levels 2-5
	 * (OSRS Wiki, Advanced strategies).
	 */
	public int baitRange()
	{
		return roleLevels.getOrDefault(com.bautopilot.baautopilot.model.Role.DEFENDER, 1) >= 2 ? 5 : 4;
	}

	public List<PenanceView> alive(PenanceType type)
	{
		if (cleared.contains(type))
		{
			return new java.util.ArrayList<>();
		}
		List<PenanceView> out = penance(type);
		unseenPenance.stream().filter(p -> p.getType() == type).forEach(out::add);
		return out;
	}

	public List<GroundItemView> ground(Predicate<GroundItemView> filter)
	{
		return groundItems.stream().filter(filter).collect(Collectors.toList());
	}

	public ObjectView nearestObject(Predicate<ObjectView> filter)
	{
		ObjectView best = null;
		int bestD = Integer.MAX_VALUE;
		for (ObjectView o : objects)
		{
			if (!filter.test(o))
			{
				continue;
			}
			int d = playerTile == null ? 0 : ArenaTiles.distance(playerTile, o.getTile());
			if (best == null || d < bestD)
			{
				best = o;
				bestD = d;
			}
		}
		return best;
	}

	public ObjectView nearestObject(int... ids)
	{
		return nearestObject(o ->
		{
			for (int id : ids)
			{
				if (o.getId() == id)
				{
					return true;
				}
			}
			return false;
		});
	}
}
