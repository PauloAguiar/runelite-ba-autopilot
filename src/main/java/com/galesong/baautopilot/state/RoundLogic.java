package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

/**
 * The round's bookkeeping behind a {@link Snapshot}: role, wave, call clock, heard calls, healer
 * food and kills. Free of client references: {@link BaTracker} feeds it live events and HUD
 * widgets, the replay harness feeds it a recorded round.
 */
@Slf4j
public class RoundLogic
{
	/** Reads the BA HUD: live widgets in the client, recorded texts in a replay. */
	public interface Hud
	{
		/** The widget exists and is not hidden. */
		boolean visible(int component);

		/** The widget's text without tags, or null when it is missing or hidden. */
		String text(int component);

		/** The widget's model id, or -1 (recordings don't carry models). */
		default int model(int component)
		{
			return -1;
		}
	}

	private static final Pattern WAVE_MESSAGE = Pattern.compile("---- Wave: (10|[1-9]) ----");
	private static final String WRONG_FOOD = "wrong type of poisoned food";
	/**
	 * "All of the Penance @mes_hl_blu@Runners</col> have been killed!" - the game's word that a type
	 * is cleared (seen in every recording; the BA Minigame plugin reads it too).
	 */
	private static final Pattern ALL_KILLED = Pattern.compile("All of the Penance .*?(Fighters|Rangers|Runners|Healers).* have been killed");
	/** A use-food click must show up as a missing item within this many ticks. */
	public static final int FEED_CONFIRM_TICKS = 20;
	/** A despawn this close is a death: NPCs leave your view 11+ tiles away (recorded). */
	static final int VIEW_EDGE = 10;
	/** Out of view this long counts as dead (recorded: 95% of returns within 60 ticks). */
	static final int UNSEEN_TICKS = 60;
	/**
	 * At a call change the HUD's call text can update a tick after our period flips (recorded
	 * 2026-10-16: "Call Aggressive!" at 439, HUD switched to Controlled at 440 - a wrong call). The
	 * new period's call is trusted once the text changed, or after this many ticks (same call twice).
	 * 4: the lag reached 3 ticks (recorded wave 7: period flip 3312, HUD 3315, stale prompt at 3314).
	 */
	static final int SHOUT_SETTLE_TICKS = 4;
	/** Launcher fire rate is measured over this many ticks (one call). */
	static final int SHOT_RATE_WINDOW = 50;
	/** Child of each role HUD group holding "Wave N". */
	private static final int HUD_WAVE_CHILD = 5;

	public static final int[] TEAMMATE_NAMES = {
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER1_NAME,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER2_NAME,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER3_NAME,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER4_NAME,
	};
	private static final int[] TEAMMATE_HPS = {
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER1_HP,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER2_HP,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER3_HP,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER4_HP,
	};

	/** Every HUD component this class reads, so a recorder can capture exactly these. */
	public static final List<Integer> HUD_COMPONENTS;

	static
	{
		ImmutableList.Builder<Integer> b = ImmutableList.builder();
		for (Role r : Role.values())
		{
			b.add(WidgetUtil.packComponentId(r.getHudGroup(), 0));
			b.add(WidgetUtil.packComponentId(r.getHudGroup(), HUD_WAVE_CHILD));
			if (r.getListenPrefixComponent() >= 0)
			{
				b.add(r.getListenPrefixComponent());
			}
			b.add(r.getListenComponent(), r.getShoutComponent(), r.getHornListenComponent());
		}
		for (int c : new int[]{
			InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N10, InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N1,
			InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N9, InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N6,
			InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N7})
		{
			b.add(c);
		}
		for (int i = 0; i < TEAMMATE_NAMES.length; i++)
		{
			b.add(TEAMMATE_NAMES[i], TEAMMATE_HPS[i]);
		}
		HUD_COMPONENTS = b.build();
	}

	private static final class PendingFeed
	{
		final int npcIndex;
		final int itemId;
		final int tick;

		PendingFeed(int npcIndex, int itemId, int tick)
		{
			this.npcIndex = npcIndex;
			this.itemId = itemId;
			this.tick = tick;
		}
	}

	private final CallClock clock = new CallClock();
	private final HealerLedger ledger = new HealerLedger();
	private final ListenResolver listen = new ListenResolver();
	private final EggBag bag = new EggBag();
	private final TeamRoster roster = new TeamRoster();
	@Getter
	private final Performance performance = new Performance();
	/** The resolved listen call as of the last tick, for event handlers. */
	private BaCall currentListen;
	/** It was heard this call period (not last period's, still shown until the partner calls again). */
	private boolean currentListenFresh;

	private final Map<Integer, Integer> spawnTicks = new HashMap<>();
	private final Set<Integer> deadCounted = new HashSet<>();
	/** The latest view of every penance NPC, for remembering one that walks out of view. */
	private final Map<Integer, PenanceView> lastViews = new HashMap<>();
	/** Out of view, not known dead: index -> tick it vanished. */
	private final Map<Integer, Integer> unseenSince = new HashMap<>();
	@Getter
	private final DamageLog damage = new DamageLog();
	/** Ticks of recent launcher shots (red/green loads going down), for the fire rate. */
	private final java.util.ArrayDeque<Integer> shotTicks = new java.util.ArrayDeque<>();
	private int lastLoaded = -1;
	/** Last health bar seen per NPC: the bar hides a few seconds after each hit. */
	private final Map<Integer, int[]> lastBars = new HashMap<>();
	/** Counted dead without being seen dying (vanished nearby, or out of view too long). */
	private final Set<Integer> vanishedCounted = new HashSet<>();
	/** Timeout guesses remain useful for accounting, but cannot establish runner completion. */
	private final Set<Integer> timedOutRunners = new HashSet<>();
	private final List<PendingFeed> pendingFeeds = new ArrayList<>();
	private Map<Integer, Integer> lastInventory = ImmutableMap.of();

	@Getter
	private int wave;
	@Getter
	private Role role;
	private int fighterRangerKills;
	/** Kills per penance type this wave (for the wave info: out, dead, reserves). */
	private final java.util.EnumMap<PenanceType, Integer> killsByType = new java.util.EnumMap<>(PenanceType.class);
	private int runnersKilled;
	private int healersKilled;
	/** Bait that landed on each tile this wave, good and bad, eaten or not (pattern progress). */
	private final Map<WorldPoint, Integer> goodLanded = new HashMap<>();
	private final Map<WorldPoint, Integer> badLanded = new HashMap<>();
	private final BaitMemory baitMemory = new BaitMemory();
	/** Penance types the game has announced as all killed this wave. */
	private final java.util.Set<PenanceType> cleared = java.util.EnumSet.noneOf(PenanceType.class);
	/** Logs picked up this wave (repairs use them up; the wave's log target counts pickups). */
	private int logsTaken;
	private String lastShoutText;
	/** The call you made that counted as this period's (see the late HUD flip check in tick). */
	private BaCall shoutDoneCall;
	/** Tick the HUD's call-to-make text last changed. */
	private int shoutChangedTick = -1;
	private int shoutDonePeriod = -1;
	/**
	 * A Tell- click waiting for the game to make the call (your own call line over your head, 0-1
	 * ticks later in 136 of 139 recorded clicks, at most 5). A click right after the horn cancels it
	 * (recorded 2026-10-17: Tell-worms then Walk here the same tick, no call, but counted as done).
	 */
	private BaCall pendingTell;
	private int pendingTellTick = -1;
	/** The period a Tell- click went unconfirmed: the call is owed again, say so. */
	private int tellLostPeriod = -1;
	public static final int TELL_CONFIRM_TICKS = 6;
	/** A call you made this period that didn't match the HUD, until you make the right one. */
	private BaCall wrongShout;
	private int wrongShoutPeriod = -1;
	/** Another player of your role made your call this period (two attackers, two healers). */
	private int coveredPeriod = -1;
	private String coveredBy;
	/** The Queen has appeared this wave (she leaves the scene whenever you're far from her). */
	private boolean queenSeen;
	private final QueenProgress queenProgress = new QueenProgress();
	private boolean collectorOpeningComplete;
	private boolean attackerOpeningComplete;
	private boolean wasInArena;

	// Results of the latest tick
	private int tick;
	private boolean inArena;
	private BaCall shout;
	private String listenText;
	private String shoutText;
	private ListenResolver.Result heard = new ListenResolver.Result(null, false, ListenResolver.Source.NONE, -1);
	private List<TeammateView> teammates = ImmutableList.of();
	private final PlayerHealthHistory playerHealth = new PlayerHealthHistory();
	/** Your name, to find your row on the lobby's team list. */
	@lombok.Setter
	private String selfName;

	public void reset()
	{
		performance.finish(tick);
		performance.reset();
		roster.reset();
		wave = 0;
		role = null;
		resetWave();
	}

	private void resetWave()
	{
		collectorOpeningComplete = false;
		attackerOpeningComplete = false;
		playerHealth.reset();
		clock.reset();
		ledger.reset();
		spawnTicks.clear();
		deadCounted.clear();
		lastViews.clear();
		lastBars.clear();
		damage.reset();
		shotTicks.clear();
		lastLoaded = -1;
		unseenSince.clear();
		vanishedCounted.clear();
		timedOutRunners.clear();
		pendingFeeds.clear();
		fighterRangerKills = 0;
		killsByType.clear();
		runnersKilled = 0;
		healersKilled = 0;
		logsTaken = 0;
		cleared.clear();
		goodLanded.clear();
		badLanded.clear();
		baitMemory.reset();
		lastShoutText = null;
		shoutChangedTick = -1;
		shoutDonePeriod = -1;
		pendingTell = null;
		tellLostPeriod = -1;
		wrongShout = null;
		wrongShoutPeriod = -1;
		coveredPeriod = -1;
		coveredBy = null;
		queenSeen = false;
		queenProgress.reset();
		listen.reset();
		currentListen = null;
		currentListenFresh = false;
		bag.reset();
	}

	// ------------------------------------------------------------------ tick

	/**
	 * Advances the round to {@code tick}. Call once per game tick, before {@link #penance} and
	 * {@link #snapshot}.
	 *
	 * @param inventory item id -> count of the inventory this tick
	 * @param hopperGreen egg launcher loads this tick (varbits 3257 / 3258 / 3259)
	 * @return true when the player just left the arena (the caller drops its object and ground-item
	 * tracking)
	 */
	public boolean tick(int tick, boolean inArena, Hud hud, Map<Integer, Integer> inventory,
		int hopperGreen, int hopperRed, int hopperBlue)
	{
		bag.hoppers(hopperGreen, hopperRed, hopperBlue);
		this.tick = tick;
		expireUnseen();
		countShots(hopperRed + hopperGreen);
		this.inArena = inArena;
		boolean left = wasInArena && !inArena;
		if (left)
		{
			performance.finish(tick);
			resetWave();
		}
		wasInArena = inArena;

		Role seen = readRole(hud);
		if (seen != null)
		{
			role = seen;
		}
		if (inArena && wave == 0)
		{
			wave = readHudWave(hud);
		}
		if (inArena && seen != null && !clock.running())
		{
			clock.start(tick);
		}

		clock.tick(tick);
		ledger.endTick();
		if (pendingTell != null && tick - pendingTellTick > TELL_CONFIRM_TICKS)
		{
			// The game never made the call: it's owed again
			pendingTell = null;
			tellLostPeriod = clock.period();
		}

		shout = null;
		listenText = null;
		shoutText = null;
		if (inArena && role != null)
		{
			listenText = readListen(hud, role);
			shoutText = hud.text(role.getShoutComponent());
			shout = BaCall.parse(role.partner(), shoutText);
			listen.hud(role, listenText, tick);
			listen.horn(role, hud.text(role.getHornListenComponent()), tick);

			if (shoutText != null && lastShoutText != null && !shoutText.equals(lastShoutText)
				&& BaCall.parse(role.partner(), lastShoutText) != null && shout != null)
			{
				clock.callFlipped(tick);
			}
			if (shoutText != null && !shoutText.equals(lastShoutText))
			{
				shoutChangedTick = tick;
				// The HUD switched to the new call after you had already called the old one this
				// period (it can lag our period flip by a tick): that call was wrong, call again.
				// Recorded 2026-10-16: Tell-aggressive at 440, HUD went Controlled the same tick
				if (shoutDonePeriod == clock.period() && shoutDoneCall != null && shout != null && shoutDoneCall != shout)
				{
					shoutDonePeriod = -1;
					if (wrongShoutPeriod != clock.period() || wrongShout != shoutDoneCall)
					{
						performance.wrongCall();
					}
					wrongShout = shoutDoneCall;
					wrongShoutPeriod = clock.period();
				}
			}
			lastShoutText = shoutText;
		}

		int period = clock.period();
		int into = clock.ticksInto(tick);
		heard = inArena && role != null ? listen.resolve(clock.periodStartTick())
			: new ListenResolver.Result(null, false, ListenResolver.Source.NONE, -1);
		if (heard.getCall() != currentListen)
		{
			log.debug("BA listen: {} via {} at tick {} (hud text '{}', period {})",
				heard.getCall(), heard.getSource(), heard.getHeardTick(), listenText, period);
		}
		currentListen = heard.getCall();
		currentListenFresh = heard.isFresh();

		if (inArena && role != null)
		{
			performance.inventory(lastInventory, inventory);
			// Calls stop mattering once wave 10's penance are all dead: not owed, so not missed
			boolean callsOver = (wave == 10 && (queenSeen || waveCleared())) || partnerTargetsCleared();
			performance.tick(tick, wave, role, period, clock.periodStartTick(), heard.isFresh(),
				shout != null && shoutDonePeriod != period && !callsOver, shoutDonePeriod == period, coveredPeriod == period);
		}
		readTeamStats(hud);
		roster.read(hud);
		// In the lobby the role HUD is gone: the team list (top left once you're on a team) says
		// which role you'll play next, so the panel and lobby tips follow it before the wave starts
		if (!inArena && seen == null)
		{
			Role mine = roster.roleOf(selfName);
			if (mine != null)
			{
				role = mine;
			}
		}
		int logsGained = inventory.getOrDefault(BaIds.LOGS, 0) - lastInventory.getOrDefault(BaIds.LOGS, 0);
		if (inArena && logsGained > 0)
		{
			logsTaken += logsGained;
		}
		confirmFeeds(tick, period, inventory);
		teammates = role == Role.HEALER ? readTeammates(hud) : ImmutableList.of();
		bag.inventory(inventory, tick);
		if (inArena) playerHealth.observeTeam(tick, teammates);
		return left;
	}

	/**
	 * The planner's view of one live penance NPC this tick. A dying NPC is counted as a kill here,
	 * so build every view before {@link #snapshot}.
	 */
	public PenanceView penance(int npcIndex, PenanceType type, WorldPoint tile, int ratio, int scale, boolean dead)
	{
		return penance(npcIndex, type, tile, ratio, scale, dead, -1, null);
	}

	public PenanceView penance(int npcIndex, PenanceType type, WorldPoint tile, int ratio, int scale, boolean dead,
		int animation, String overhead)
	{
		boolean dying = dead || (scale > 0 && ratio == 0);
		if (type == PenanceType.QUEEN && dying) queenProgress.died();
		if (dying)
		{
			vanishedCounted.remove(npcIndex);
			timedOutRunners.remove(npcIndex);
			countDeath(npcIndex, type);
		}
		if (scale > 0 && ratio >= 0)
		{
			lastBars.put(npcIndex, new int[]{ratio, scale});
		}
		int[] bar = lastBars.get(npcIndex);
		PenanceView previous = lastViews.get(npcIndex);
		PenanceView.PenanceViewBuilder b = PenanceView.builder()
			.npcIndex(npcIndex)
			.type(type)
			.tile(tile)
			.previousTile(previous != null && previous.getObservedTick() == tick - 1 && !previous.isDying() ? previous.getTile() : null)
			.observedTick(tick).animation(animation).overhead(overhead)
			.healthRatio(bar == null ? ratio : bar[0])
			.healthScale(bar == null ? scale : bar[1])
			.spawnTick(spawnTicks.getOrDefault(npcIndex, tick))
			.dying(dying)
			.damageTaken(damage.total(npcIndex))
			.damageMine(damage.mine(npcIndex))
			.damagePoison(damage.poison(npcIndex))
			.dps(damage.dps(npcIndex, tick));
		unseenSince.remove(npcIndex);
		if (type == PenanceType.HEALER)
		{
			HealerLedger.Entry e = ledger.get(npcIndex);
			if (e != null)
			{
				int period = clock.period();
				b.order(e.getOrder()).foods(e.getFoods()).foodsThisPeriod(e.foodsIn(period)).lastFoodTick(e.getLastFoodTick())
					.lastPoisonDamage(e.getLastPoisonDamage()).lastPoisonTick(e.getLastPoisonTick());
			}
		}
		PenanceView view = b.build();
		lastViews.put(npcIndex, view);
		return view;
	}

	/** Own HP comes from the client (or capture), after tick() establishes the current wave. */
	public void observeSelfHp(int hp, int maxHp)
	{
		if (inArena) playerHealth.observeSelf(tick, hp, maxHp);
	}

	public void observePosition(WorldPoint tile)
	{
		if (inArena && role == Role.COLLECTOR && tile != null && (tile.getY() & 63) >= 37)
			collectorOpeningComplete = true;
		if (inArena && role == Role.ATTACKER && tile != null && (tile.getY() & 63) >= 37)
			attackerOpeningComplete = true;
	}

	public Role roleOf(String player) { return roster.roleOf(player); }

	public void observeQueenPlayers(List<PlayerView> players)
	{
		if (inArena && wave == 10 && queenSeen && !queenProgress.isDead()) queenProgress.players(tick, players);
	}

	/** Called after tick establishes the wave, from live tracking and replay. */
	public void observeQueenAmmo(int omega)
	{
		if (inArena && wave == 10) queenProgress.ammo(tick, omega);
	}

	/** The round's part of this tick's snapshot; the caller adds what it reads from the game. */
	public Snapshot.SnapshotBuilder snapshot()
	{
		return Snapshot.builder()
			.tick(tick)
			.inArena(inArena)
			.collectorOpeningComplete(collectorOpeningComplete)
			.attackerOpeningComplete(attackerOpeningComplete)
			.queenYellowPassed(queenProgress.getYellowPassed())
			.queenSpikedPassed(queenProgress.getSpikedPassed())
			.queenOmegaHandoffTick(queenProgress.getOmegaHandoffTick())
			.queenOmegaFired(queenProgress.getOmegaFired())
			.lastQueenOmegaShotTick(queenProgress.getLastShotTick())
			.queenDead(queenProgress.isDead())
			.wave(wave)
			.role(role)
			.waveTick(clock.running() ? Math.max(0, tick - waveStartTick()) : -1)
			.callPeriod(clock.period())
			.ticksToCallChange(clock.ticksToChange(tick))
			.listenCall(heard.getCall())
			.listenFresh(heard.isFresh())
			.listenSource(heard.getSource().name())
			.shoutCall(shout)
			// A call just clicked counts as made while the game catches up, so it isn't asked for twice
			.shoutDone(shoutDonePeriod == clock.period() || (pendingTell != null && pendingTell == shout))
			.shoutLost(tellLostPeriod == clock.period() && shoutDonePeriod != clock.period())
			// A call that showed up a tick before the period counts: at a wave start the HUD shows the
			// first call one tick before our clock starts (recorded: first prompt only at +3/+4)
			.shoutSettled(shoutChangedTick >= clock.periodStartTick() - 1 || tick - clock.periodStartTick() >= SHOUT_SETTLE_TICKS)
			.shoutWrong(wrongShoutPeriod == clock.period() && shoutDonePeriod != clock.period() ? wrongShout : null)
			.shoutCoveredBy(coveredPeriod == clock.period() && shoutDonePeriod != clock.period() ? coveredBy : null)
			.listenText(listenText)
			.shoutText(shoutText)
			.teammates(teammates)
			.selfDamagePerTick(playerHealth.selfRate(tick))
			.teammateDamagePerTick(playerHealth.teamRates(tick))
			.fighterRangerKills(fighterRangerKills)
			.killsByType(confirmedKillsByType())
			.runnersKilled(confirmedRunnerKills())
			.uncertainRunners(uncertainRunners())
			.healersKilled(healersKilled)
			.logsTaken(logsTaken)
			.cleared(ImmutableSet.copyOf(cleared))
			.goodBaitLanded(ImmutableMap.copyOf(goodLanded))
			.badBaitLanded(ImmutableMap.copyOf(badLanded))
			.unseenPenance(ImmutableList.copyOf(unseen()))
			.uncertainPenance(uncertainPenance())
			.launcherShotsPerTick(shotTicks.size() / (double) SHOT_RATE_WINDOW)
			.lastLauncherShotTick(shotTicks.isEmpty() ? -1 : shotTicks.peekLast())
			.myDps(damage.myDps(tick))
			.teamDps(damage.teamDps(tick))
			.myDamage(damage.myTotal())
			.waveStats(performance.current())
			.team(roster.roles())
			.bagRed(bag.count(BaIds.EGG_RED))
			.bagGreen(bag.count(BaIds.EGG_GREEN))
			.bagBlue(bag.count(BaIds.EGG_BLUE))
;
	}

	private int waveStartTick()
	{
		return tick - (clock.period() * WaveInfo.TICKS_PER_CALL + Math.max(0, clock.ticksInto(tick)));
	}

	// ------------------------------------------------------------------ events

	/**
	 * A chat line, tags removed. Not filtered by chat type: whichever type the horn line arrives as,
	 * it is matched by text. A horn call puts a line in chat addressed to the receiver: "Healer:
	 * Poison worms!" is the defender's call to the healer. Lines to us are our new call; lines to
	 * our partner from us confirm that we called.
	 *
	 * @param gameMessage the line is a GAMEMESSAGE (wave start, wrong food)
	 * @param fromMe      the local player sent it
	 * @return true when it started a new wave (the caller drops its penance tracking)
	 */
	public boolean chat(boolean gameMessage, String msg, boolean fromMe, int tick)
	{
		if (gameMessage && wave == 10)
		{
			queenProgress.message(msg, role == Role.COLLECTOR && lastInventory.getOrDefault(BaIds.EGG_YELLOW, 0) > 0, tick);
			if (role == Role.ATTACKER && lastInventory.getOrDefault(BaIds.EGG_SPIKED, 0) > 0
				&& "You passed the egg to the player.".equals(msg)) queenProgress.passedSpiked();
		}
		bag.message(msg, role == Role.COLLECTOR ? currentListen : null, tick);
		performance.chat(msg);
		if (gameMessage)
		{
			Matcher cleared = ALL_KILLED.matcher(msg);
			if (cleared.find())
			{
				allKilled(cleared.group(1));
			}
			Matcher m = WAVE_MESSAGE.matcher(msg);
			if (m.find())
			{
				performance.finish(tick);
				int w = Integer.parseInt(m.group(1));
				resetWave();
				wave = w;
				clock.start(tick);
				return true;
			}
			if (msg.toLowerCase().contains(WRONG_FOOD))
			{
				pendingFeeds.clear();
				return false;
			}
		}
		if (role == null || listen.chat(role, msg, tick))
		{
			return false;
		}
		ListenResolver.Addressed line = ListenResolver.addressed(msg);
		if (line != null && line.receiver == role.partner() && fromMe)
		{
			shouted(line.call);
		}
		return false;
	}

	/**
	 * You used the horn. Only the call the HUD asks for counts as done; anything else is a wrong
	 * call to fix (recorded: Tell-red on a green call was taken as done from its chat line).
	 */
	private void shouted(BaCall said)
	{
		pendingTell = null;
		BaCall want = shout;
		if (want == null || said == null)
		{
			return;
		}
		int period = clock.period();
		if (said == want)
		{
			shoutDonePeriod = period;
			shoutDoneCall = said;
			return;
		}
		if (wrongShoutPeriod != period || wrongShout != said)
		{
			performance.wrongCall();
		}
		wrongShout = said;
		wrongShoutPeriod = period;
	}

	/**
	 * Text over a player's head, tags removed. Horn calls show here for other players (recorded: in
	 * 29 waves no teammate's horn line ever arrived as chat, only your own). A line to you is your new
	 * call; a teammate of your role making your call means you needn't repeat it.
	 */
	public void overhead(String name, String text, boolean fromMe, int tick)
	{
		if (role == null || text == null || listen.chat(role, text, tick))
		{
			return;
		}
		ListenResolver.Addressed line = ListenResolver.addressed(text);
		if (line == null || line.receiver != role.partner())
		{
			return;
		}
		if (fromMe)
		{
			shouted(line.call);
		}
		else if (line.call != null && line.call == shout)
		{
			coveredPeriod = clock.period();
			coveredBy = name;
		}
	}

	/**
	 * Whether the Queen phase is on, given whether her NPC is in the scene this tick. Recorded wave
	 * 10 (healer at the spring): she was in the scene only 148 of ~295 ticks, so once she has
	 * appeared the phase lasts until the wave ends.
	 */
	public boolean queenPhase(boolean visibleNow)
	{
		if (visibleNow && wave == 10)
		{
			queenSeen = true;
		}
		return !queenProgress.isDead() && (visibleNow || queenSeen);
	}

	/** Any menu click, tags removed (egg pick-ups and emptying the collection bag). */
	public void menuClicked(String option, String target, int tick)
	{
		bag.menu(option, target, tick);
		if (role == Role.DEFENDER) baitMemory.menu(option, target, tick);
		performance.menu(option, target);
	}

	/** A "Tell-..." horn option was clicked (tags removed). */
	public void tellClicked(String option, Hud hud)
	{
		if (role == null)
		{
			return;
		}
		BaCall said = null;
		for (BaCall c : BaCall.values())
		{
			if (c.getReceiver() == role.partner() && c.getHornOption().equalsIgnoreCase(option))
			{
				said = c;
			}
		}
		if (shout == null)
		{
			shout = BaCall.parse(role.partner(), hud.text(role.getShoutComponent()));
		}
		// Counted once your call line appears (overhead / chat), not on the click
		if (said != null)
		{
			pendingTell = said;
			pendingTellTick = tick;
		}
	}

	/** An item was used on an NPC; a poisoned food on a penance healer becomes a pending feed. */
	public void usedOnNpc(int npcIndex, PenanceType type, int itemId, int tick)
	{
		if (type == PenanceType.HEALER && BaIds.POISON_FOOD.contains(itemId))
		{
			pendingFeeds.add(new PendingFeed(npcIndex, itemId, tick));
		}
	}

	public void npcSpawned(int npcIndex, PenanceType type, int tick)
	{
		if (unseenSince.remove(npcIndex) != null)
		{
			// Back in view: same NPC, same spawn tick and ledger
			return;
		}
		if (vanishedCounted.remove(npcIndex))
		{
			// Counted dead while out of view, but it was alive all along
			timedOutRunners.remove(npcIndex);
			uncountDeath(npcIndex, type);
			return;
		}
		if (deadCounted.remove(npcIndex))
		{
			// A real death, and the game reused the index for a new NPC
			ledger.forget(npcIndex);
			damage.forget(npcIndex);
			lastBars.remove(npcIndex);
		}
		spawnTicks.put(npcIndex, tick);
		if (type == PenanceType.HEALER)
		{
			ledger.spawned(npcIndex);
		}
	}

	/**
	 * A tracked penance NPC despawned, {@code distance} tiles from you. Recorded 2026-10-16: 854 of
	 * the despawns over 33 waves were NPCs walking out of view (11-17 tiles away) that came back,
	 * most within 30 ticks. Only one nearby, or one not back within {@link #UNSEEN_TICKS}, counts
	 * as dead; one seen dying already has.
	 */
	public void npcDespawned(int npcIndex, PenanceType type, int distance)
	{
		if (!wasInArena || deadCounted.contains(npcIndex))
		{
			return;
		}
		PenanceView last = lastViews.get(npcIndex);
		if (type == PenanceType.RUNNER && escaped(last))
		{
			// Gone through the south exit: not a kill, and a new runner takes its place (wiki)
			lastViews.remove(npcIndex);
			return;
		}
		if (distance <= VIEW_EDGE)
		{
			countDeath(npcIndex, type);
			vanishedCounted.add(npcIndex);
		}
		else
		{
			unseenSince.put(npcIndex, tick);
		}
	}

	/** The game announced every penance your call is for as killed (see Planner.callsOver). */
	private boolean partnerTargetsCleared()
	{
		if (role == null)
		{
			return false;
		}
		switch (role)
		{
			case DEFENDER:
				return cleared.contains(PenanceType.HEALER);
			case HEALER:
				return cleared.contains(PenanceType.RUNNER);
			case COLLECTOR:
				return cleared.contains(PenanceType.FIGHTER) && cleared.contains(PenanceType.RANGER);
			default:
				return false;
		}
	}

	/** Every fighter, ranger, runner and healer of this wave has been counted dead. */
	private boolean waveCleared()
	{
		return fighterRangerKills >= WaveInfo.fightersTotal(wave) + WaveInfo.rangersTotal(wave)
			&& confirmedRunnerKills() >= WaveInfo.runnersTotal(wave)
			&& healersKilled >= WaveInfo.healersTotal(wave);
	}

	/**
	 * The game says every penance of a type is dead: counts go to the wave's total and none of that
	 * type is remembered out of view. Reported 2026-10-16: the defender was told to fix the trap
	 * after "All of the Penance Runners have been killed!".
	 */
	private void allKilled(String which)
	{
		PenanceType type = which.startsWith("Runner") ? PenanceType.RUNNER
			: which.startsWith("Healer") ? PenanceType.HEALER
			: which.startsWith("Fighter") ? PenanceType.FIGHTER : PenanceType.RANGER;
		cleared.add(type);
		switch (type)
		{
			case RUNNER:
				runnersKilled = Math.max(runnersKilled, WaveInfo.runnersTotal(wave));
				killsByType.put(type, WaveInfo.total(type, wave));
				break;
			case HEALER:
				healersKilled = Math.max(healersKilled, WaveInfo.healersTotal(wave));
				killsByType.put(type, WaveInfo.total(type, wave));
				break;
			default:
				killsByType.put(type, WaveInfo.total(type, wave));
				if (cleared.contains(PenanceType.FIGHTER) && cleared.contains(PenanceType.RANGER))
				{
					fighterRangerKills = Math.max(fighterRangerKills, WaveInfo.fightersTotal(wave) + WaveInfo.rangersTotal(wave));
				}
				break;
		}
		unseenSince.keySet().removeIf(i -> lastViews.get(i) != null && lastViews.get(i).getType() == type);
	}

	/** Red/green loads going down are shots (loads only ever add). */
	private void countShots(int loaded)
	{
		if (lastLoaded >= 0 && loaded < lastLoaded)
		{
			for (int i = loaded; i < lastLoaded; i++)
			{
				shotTicks.addLast(tick);
			}
		}
		lastLoaded = loaded;
		while (!shotTicks.isEmpty() && shotTicks.peekFirst() <= tick - SHOT_RATE_WINDOW)
		{
			shotTicks.removeFirst();
		}
	}

	/** A hitsplat landed on a tracked penance NPC. */
	public void hit(int npcIndex, int amount, boolean mine, boolean poison)
	{
		hit(npcIndex, amount, mine, poison, tick);
	}

	public void hit(int npcIndex, int amount, boolean mine, boolean poison, int hitTick)
	{
		damage.hit(npcIndex, hitTick, amount, mine, poison);
		if (poison) ledger.poisonHit(npcIndex, amount, hitTick);
	}

	/**
	 * Runners last seen this far south of the safety bait (the bad food below the trap) left
	 * through the exit. Recorded 2026-10-16: runners vanished 8-13 tiles south of the trap and a
	 * new one spawned.
	 */
	static final int ESCAPE_SOUTH_OF_SAFETY = 5;

	private boolean escaped(PenanceView last)
	{
		return last != null && last.getTile() != null && !last.isDying() && wave > 0
			&& last.getTile().getY() <= ArenaTiles.safety(wave).getY() - ESCAPE_SOUTH_OF_SAFETY;
	}

	/** Penance out of view but presumed alive, as last seen. */
	public List<PenanceView> unseen()
	{
		List<PenanceView> out = new ArrayList<>();
		for (int index : unseenSince.keySet())
		{
			PenanceView v = lastViews.get(index);
			if (v != null)
			{
				out.add(v.toBuilder().unseen(true).unseenTicks(tick - unseenSince.get(index)).build());
			}
		}
		return out;
	}

	private void expireUnseen()
	{
		unseenSince.entrySet().removeIf(e ->
		{
			if (tick - e.getValue() < UNSEEN_TICKS)
			{
				return false;
			}
			PenanceView v = lastViews.get(e.getKey());
			if (v != null)
			{
				countDeath(e.getKey(), v.getType());
				vanishedCounted.add(e.getKey());
				if (v.getType() == PenanceType.RUNNER) timedOutRunners.add(e.getKey());
			}
			return true;
		});
	}

	public void npcDied(int npcIndex, PenanceType type)
	{
		if (type == PenanceType.QUEEN) queenProgress.died();
		vanishedCounted.remove(npcIndex);
		timedOutRunners.remove(npcIndex);
		countDeath(npcIndex, type);
	}

	private int uncertainRunners()
	{
		if (cleared.contains(PenanceType.RUNNER)) return 0;
		return timedOutRunners.size();
	}

	private List<PenanceView> uncertainPenance()
	{
		if (cleared.contains(PenanceType.RUNNER)) return ImmutableList.of();
		return timedOutRunners.stream().sorted().map(lastViews::get).filter(java.util.Objects::nonNull)
			.map(v -> v.toBuilder().unseen(true).unseenTicks(Math.max(0, tick - v.getObservedTick())).build())
			.collect(ImmutableList.toImmutableList());
	}

	private int confirmedRunnerKills()
	{
		return cleared.contains(PenanceType.RUNNER) ? Math.max(runnersKilled, WaveInfo.runnersTotal(wave))
			: Math.max(0, runnersKilled - uncertainRunners());
	}

	private Map<PenanceType, Integer> confirmedKillsByType()
	{
		Map<PenanceType, Integer> confirmed = new java.util.EnumMap<>(PenanceType.class);
		confirmed.putAll(killsByType);
		if (confirmed.containsKey(PenanceType.RUNNER)) confirmed.put(PenanceType.RUNNER, confirmedRunnerKills());
		return ImmutableMap.copyOf(confirmed);
	}

	public GroundItemView groundItemSpawned(int itemId, WorldPoint tile, int quantity, int tick, WorldPoint player)
	{
		return baitMemory.spawn(itemId, tile, quantity, tick, player, () -> baitLanded(itemId, tile));
	}

	public void groundItemDespawned(GroundItemView item, int tick)
	{
		baitMemory.despawn(item, tick);
	}

	/** A genuinely new bait item landed on {@code tile}; returns whether it is good bait. */
	public boolean baitLanded(int itemId, WorldPoint tile)
	{
		boolean good = baitGood(itemId);
		if (BaIds.BAIT.contains(itemId) && tile != null && role == Role.DEFENDER)
		{
			(good ? goodLanded : badLanded).merge(tile, 1, Integer::sum);
		}
		return good;
	}

	/**
	 * Bait landing on the ground now is good bait: it matches the defender's call heard this period.
	 * Between a call change and the healer's next call there is no call (the HUD shows "- - -"), so
	 * last period's call doesn't make bait good.
	 */
	public boolean baitGood(int itemId)
	{
		return BaIds.BAIT.contains(itemId) && role == Role.DEFENDER
			&& currentListen != null && currentListenFresh && currentListen.getItemId() == itemId;
	}

	// ------------------------------------------------------------------ helpers

	public static boolean tracksGroundItem(int id)
	{
		return BaIds.BAIT.contains(id) || id == BaIds.LOGS || id == BaIds.HAMMER
			|| id == BaIds.EGG_RED || id == BaIds.EGG_GREEN || id == BaIds.EGG_BLUE || id == BaIds.EGG_YELLOW;
	}

	/** Within this many tiles of the trap food, a trap object is the defender's trap. */
	static final int TRAP_NEAR_FOOD = 3;

	/**
	 * Charges (0-2) of the defender's trap, the one by the trap food; -1 when it is not in view.
	 * Recorded 2026-10-16: the arena has a second trap on the west side that nobody uses and that
	 * stays full; reading every trap together hid a broken east trap ("not telling me to fix it").
	 */
	public static int trapCharges(List<ObjectView> objects, int wave)
	{
		WorldPoint food = ArenaTiles.trapFood(wave);
		int charges = -1;
		for (ObjectView o : objects)
		{
			if (o.getTile() == null || ArenaTiles.distance(o.getTile(), food) > TRAP_NEAR_FOOD)
			{
				continue;
			}
			if (o.getId() == BaIds.TRAP_FULL)
			{
				charges = charges < 0 ? 2 : Math.min(charges, 2);
			}
			if (o.getId() == BaIds.TRAP_ONE)
			{
				charges = charges < 0 ? 1 : Math.min(charges, 1);
			}
			if (o.getId() == BaIds.TRAP_BROKEN)
			{
				charges = 0;
			}
		}
		// If old/new states overlap during a scene update, never infer a full trap from list order.
		return charges;
	}

	/** Merges single ground items into one view per item, tile and goodness, first-seen order. */
	public static List<GroundItemView> mergeGround(List<GroundItemView> items)
	{
		Map<String, int[]> merged = new LinkedHashMap<>();
		Map<String, GroundItemView> first = new HashMap<>();
		// One on top makes the merged pile left-clickable
		Map<String, Boolean> onTop = new HashMap<>();
		for (GroundItemView g : items)
		{
			if (g.getTile() == null)
			{
				continue;
			}
			WorldPoint t = g.getTile();
			String key = g.getItemId() + "@" + t.getX() + "," + t.getY() + "," + t.getPlane() + ":" + g.isGood();
			merged.computeIfAbsent(key, k -> new int[1])[0] += Math.max(1, g.getQuantity());
			first.merge(key, g, (a, b) -> a.getSpawnTick() <= b.getSpawnTick() ? a : b);
			onTop.merge(key, !g.isUnderneath(), Boolean::logicalOr);
		}
		ImmutableList.Builder<GroundItemView> out = ImmutableList.builder();
		merged.forEach((k, q) ->
		{
			GroundItemView g = first.get(k);
			out.add(new GroundItemView(g.getItemId(), g.getTile(), q[0], g.isGood(), g.getSpawnTick(), !onTop.get(k)));
		});
		return out.build();
	}

	private void uncountDeath(int npcIndex, PenanceType type)
	{
		if (!deadCounted.remove(npcIndex))
		{
			return;
		}
		if (type != null)
		{
			killsByType.merge(type, -1, Integer::sum);
		}
		performance.undoDeath(type);
		if (type == PenanceType.FIGHTER || type == PenanceType.RANGER)
		{
			fighterRangerKills--;
		}
		else if (type == PenanceType.RUNNER)
		{
			runnersKilled--;
		}
		else if (type == PenanceType.HEALER)
		{
			healersKilled--;
		}
	}

	private void countDeath(int npcIndex, PenanceType type)
	{
		if (!deadCounted.add(npcIndex))
		{
			return;
		}
		if (type != null)
		{
			killsByType.merge(type, 1, Integer::sum);
		}
		Integer born = spawnTicks.get(npcIndex);
		performance.death(type, born == null ? -1 : tick - born);
		if (type == PenanceType.FIGHTER || type == PenanceType.RANGER)
		{
			fighterRangerKills++;
		}
		else if (type == PenanceType.RUNNER)
		{
			runnersKilled++;
		}
		else if (type == PenanceType.HEALER)
		{
			healersKilled++;
		}
	}

	private void confirmFeeds(int tick, int period, Map<Integer, Integer> now)
	{
		pendingFeeds.removeIf(p -> tick - p.tick > FEED_CONFIRM_TICKS);
		for (int food : BaIds.POISON_FOOD)
		{
			int dropped = lastInventory.getOrDefault(food, 0) - now.getOrDefault(food, 0);
			for (int i = 0; i < dropped; i++)
			{
				PendingFeed match = pendingFeeds.stream().filter(p -> p.itemId == food).findFirst().orElse(null);
				if (match == null)
				{
					break;
				}
				pendingFeeds.remove(match);
				if (role == Role.HEALER && currentListen != null && currentListen.getItemId() == food)
				{
					ledger.fed(match.npcIndex, tick, period);
					performance.ownFeed();
				}
			}
		}
		lastInventory = now;
	}

	/** Wave-complete screen counts (BA Minigame's mapping): wrong attacks, runners passed, eggs, HP healed, wrong poison. */
	static final int[] TEAM_STAT_COMPONENTS = {
		InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N10,
		InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N1,
		InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N9,
		InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N6,
		InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_N7,
	};

	private void readTeamStats(Hud hud)
	{
		int[] v = new int[TEAM_STAT_COMPONENTS.length];
		for (int i = 0; i < v.length; i++)
		{
			String text = hud.text(TEAM_STAT_COMPONENTS[i]);
			String digits = text == null ? "" : text.replaceAll("[^0-9]", "");
			if (digits.isEmpty())
			{
				return;
			}
			v[i] = Integer.parseInt(digits);
		}
		performance.teamStats(v[0], v[1], v[2], v[3], v[4]);
	}

	private static Role readRole(Hud hud)
	{
		for (Role r : Role.values())
		{
			if (hud.visible(WidgetUtil.packComponentId(r.getHudGroup(), 0)))
			{
				return r;
			}
		}
		return null;
	}

	private int readHudWave(Hud hud)
	{
		if (role == null)
		{
			return 0;
		}
		String text = hud.text(WidgetUtil.packComponentId(role.getHudGroup(), HUD_WAVE_CHILD));
		if (text == null)
		{
			return 0;
		}
		String digits = text.replaceAll("\\D", "");
		try
		{
			int n = digits.isEmpty() ? 0 : Integer.parseInt(digits);
			return WaveInfo.valid(n) ? n : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	static String readListen(Hud hud, Role r)
	{
		String detail = hud.text(r.getListenComponent());
		if (r.getListenPrefixComponent() < 0)
		{
			return detail;
		}
		String prefix = hud.text(r.getListenPrefixComponent());
		if (prefix == null)
		{
			return detail;
		}
		return prefix + (detail == null ? "" : detail);
	}

	private static List<TeammateView> readTeammates(Hud hud)
	{
		ImmutableList.Builder<TeammateView> out = ImmutableList.builder();
		for (int i = 0; i < TEAMMATE_NAMES.length; i++)
		{
			String name = hud.text(TEAMMATE_NAMES[i]);
			String hp = hud.text(TEAMMATE_HPS[i]);
			if (name == null || hp == null)
			{
				continue;
			}
			String[] parts = hp.split("/");
			if (parts.length != 2)
			{
				continue;
			}
			try
			{
				out.add(new TeammateView(name.trim(), Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
			}
			catch (NumberFormatException ignored)
			{
				// the HUD shows "-" for empty slots
			}
		}
		return out.build();
	}
}
