package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.CallClock;
import com.galesong.baautopilot.state.ListenResolver;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.RoundLogic;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.plan.RunnerState;
import com.galesong.baautopilot.plan.Tier;
import com.galesong.baautopilot.plan.TurretChoice;
import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.WaveStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * Checks a replayed round against what the raw recording says happened. Each check works from the
 * raw fields independently of the plugin's logic, then compares with the replayed snapshots.
 */
public final class RoundInvariants
{
	/** The role must be known this many ticks after its HUD first shows. */
	static final int ROLE_TICKS = 2;
	/** A shout-text flip must land this close to a call boundary of the wave-message clock. */
	static final int FLIP_TOLERANCE = 2;

	private static final Pattern WAVE_MESSAGE = Pattern.compile("---- Wave: (10|[1-9]) ----");
	private static final String WRONG_FOOD = "wrong type of poisoned food";

	/** What one check found. */
	public static final class Report
	{
		public final List<String> violations = new ArrayList<>();
		/** Facts worth printing (e.g. measured flip offsets) that are not failures. */
		public final List<String> notes = new ArrayList<>();
	}

	private RoundInvariants()
	{
	}

	public static Report check(RecordedRound round, List<RoundReplay.Frame> frames)
	{
		Report r = new Report();
		roleDetected(frames, r);
		listenMatchesHorn(frames, r);
		flipsOnBoundaries(frames, r);
		healerFoodCounts(frames, r);
		initialPoisonNotDelayed(frames, r);
		fullPoisonNotRefed(frames, r);
		weakestPoisonFirst(frames, r);
		cannonInterruptsNeedEvidence(frames, r);

		itemsMatchFreshCall(frames, r);
		staleCallsNotUsed(frames, r);
		eggBagMatchesLoads(frames, r);
		wrongCallsNotDone(frames, r);
		performanceNotes(frames, r);
		attackSwitches(frames, r);
		liveAgreement(frames, r);
		r.notes.add("metrics: " + ReplayMetrics.measure(frames).line("").trim());
		return r;
	}

	/** Primary cannon interrupts require the observed runner threat or recent low-red firing. */
	static void cannonInterruptsNeedEvidence(List<RoundReplay.Frame> frames, Report r)
	{
		for (RoundReplay.Frame frame : frames)
		{
			Step step = frame.instruction.getStep();
			if (frame.instruction.getTier() != Tier.TEAM_BLOCKING || (step != Step.COL_LOAD && step != Step.COL_FIRE)) continue;
			Snapshot s = frame.snapshot;
			int shotAge = s.getLastLauncherShotTick() < 0 ? -1 : s.getTick() - s.getLastLauncherShotTick();
			boolean lowRedActive = s.getEggsRed() <= 2 && shotAge >= 0 && shotAge <= 8;
			boolean escapeInRange = s.getPenance().stream().anyMatch(p -> p.getType() == PenanceType.RUNNER
				&& !p.isDying() && !p.isUnseen() && RunnerState.of(s, p) == RunnerState.ESCAPING
				&& s.getObjects().stream().anyMatch(o -> BaIds.contains(BaIds.LAUNCHERS, o.getId())
					&& ArenaTiles.distance(o.getTile(), p.getTile()) <= TurretChoice.RANGE));
			if (!lowRedActive && !escapeInRange)
			{
				r.violations.add("cannon interrupt: tick " + frame.raw.t + ": " + frame.instruction.getDetail());
			}
		}
	}

	/** Follow-up food must not bypass a known untagged healer, including one last seen out of view. */
	static void initialPoisonNotDelayed(List<RoundReplay.Frame> frames, Report r)
	{
		int opportunities = 0;
		for (RoundReplay.Frame frame : frames)
		{
			Snapshot s = frame.snapshot;
			Step step = frame.instruction.getStep();
			if (!s.isInArena() || s.getRole() != Role.HEALER || !s.isListenFresh() || s.getListenCall() == null
				|| !s.has(s.getListenCall().getItemId())
				|| s.alive(PenanceType.HEALER).stream().noneMatch(h -> h.nextPoisonHit(s.getTick()) == 0))
			{
				continue;
			}
			// Calls, healing and Queen work can interrupt poisoning. Ordinary food work cannot.
			if (step == Step.HEAL_FEED || step == Step.HEAL_WAIT || step == Step.HEAL_SUPPORT || step == Step.HEAL_TAKE_FOOD)
			{
				opportunities++;
				boolean initial = step == Step.HEAL_FEED && s.alive(PenanceType.HEALER).stream()
					.anyMatch(h -> h.nextPoisonHit(s.getTick()) == 0 && h.getNpcIndex() == frame.instruction.getTarget().getId());
				if (!initial) r.violations.add("initial poison: tick " + frame.raw.t + ": " + frame.instruction.getHeadline());
			}
		}
		if (opportunities > 0) r.notes.add("initial poison: " + opportunities + " available decision ticks checked");
	}

	/** Full poison feeds need a real reserve-opening tactic, fresh food and protected future tags. */
	static void fullPoisonNotRefed(List<RoundReplay.Frame> frames, Report r)
	{
		for (RoundReplay.Frame frame : frames)
		{
			if (frame.instruction.getStep() != Step.HEAL_FEED) continue;
			for (PenanceView h : frame.snapshot.alive(PenanceType.HEALER))
			{
				if (h.getNpcIndex() == frame.instruction.getTarget().getId() && h.poisoned()
					&& h.nextPoisonHit(frame.snapshot.getTick()) >= WaveInfo.FOOD_DAMAGE)
				{
					Snapshot s = frame.snapshot;
					int futureTags = Math.max(0, WaveInfo.healersTotal(s.getWave()) - s.getHealersKilled() - s.alive(PenanceType.HEALER).size());
					boolean justified = "heal.reserve".equals(frame.instruction.getTask()) && s.isListenFresh()
						&& s.getListenCall() != null && s.count(s.getListenCall().getItemId()) > futureTags
						&& s.getHealersKilled() < WaveInfo.healersTotal(s.getWave()) - WaveInfo.healersInitial(s.getWave())
						&& s.alive(PenanceType.HEALER).stream().mapToInt(PenanceView::getOrder).max().orElse(0) < WaveInfo.healersTotal(s.getWave())
						&& !h.isUnseen() && s.walk(h.getTile()) <= 8;
					if (!justified) r.violations.add("full poison: tick " + frame.raw.t + ": " + frame.instruction.getHeadline());
				}
			}
		}
	}

	/** Code timing and previous targets cannot bypass a healer with weaker active poison. */
	static void weakestPoisonFirst(List<RoundReplay.Frame> frames, Report r)
	{
		for (RoundReplay.Frame frame : frames)
		{
			Snapshot s = frame.snapshot;
			Step step = frame.instruction.getStep();
			if (s.getRole() != Role.HEALER || !s.isListenFresh() || s.getListenCall() == null
				|| !s.has(s.getListenCall().getItemId()) || !(step == Step.HEAL_FEED || step == Step.HEAL_WAIT
				|| step == Step.HEAL_SUPPORT || step == Step.HEAL_TAKE_FOOD)) continue;
			int lowest = s.alive(PenanceType.HEALER).stream().mapToInt(h -> h.nextPoisonHit(s.getTick())).min().orElse(4);
			if (lowest >= 4) continue;
			boolean correct = step == Step.HEAL_FEED && s.alive(PenanceType.HEALER).stream()
				.anyMatch(h -> h.getNpcIndex() == frame.instruction.getTarget().getId() && h.nextPoisonHit(s.getTick()) == lowest);
			if (!correct) r.violations.add("weakest poison: tick " + frame.raw.t + ": " + frame.instruction.getHeadline());
		}
	}

	// ------------------------------------------------------------------ checks

	static void roleDetected(List<RoundReplay.Frame> frames, Report r)
	{
		for (int i = 0; i < frames.size(); i++)
		{
			Role hud = hudRole(frames.get(i).raw);
			if (hud == null)
			{
				continue;
			}
			int first = frames.get(i).raw.t;
			for (int j = i; j < frames.size() && frames.get(j).raw.t - first <= ROLE_TICKS; j++)
			{
				if (frames.get(j).snapshot.getRole() == hud)
				{
					return;
				}
			}
			r.violations.add("role: " + hud + " HUD showed at tick " + first + " but the role was not detected within "
				+ ROLE_TICKS + " ticks");
			return;
		}
	}

	/** Every horn chat line addressed to our role becomes the listen call on the tick it arrives. */
	static void listenMatchesHorn(List<RoundReplay.Frame> frames, Report r)
	{
		for (RoundReplay.Frame f : frames)
		{
			Role role = f.snapshot.getRole();
			if (role == null || !f.snapshot.isInArena())
			{
				continue;
			}
			ListenResolver.Addressed last = null;
			int lastTick = -1;
			for (Rec.Event e : events(f.raw, "chat"))
			{
				ListenResolver.Addressed line = ListenResolver.addressed(Text.removeTags(e.text));
				if (line == null || line.receiver != role)
				{
					continue;
				}
				if (line.call == null)
				{
					r.violations.add("listen: tick " + e.t + ": horn line to " + role + " not understood: '" + Text.removeTags(e.text) + "'");
					continue;
				}
				last = line;
				lastTick = e.t;
			}
			if (last != null && f.snapshot.getListenCall() != last.call)
			{
				r.violations.add("listen: tick " + f.raw.t + ": heard '" + last.call.name() + "' (chat at tick " + lastTick
					+ ") but the listen call is " + f.snapshot.getListenCall() + " via " + f.snapshot.getListenSource());
			}
		}
	}

	/**
	 * The call to give flips on the HUD at call boundaries; boundaries are the wave message tick +
	 * START_OFFSET + 50k. This is what validates the offset (8 recorded waves 2026-10-15: waves 2-8 all
	 * flipped wave message + 1 + 50k; wave 1's message arrived 6 ticks late, which the clock's
	 * re-anchor on the first flip absorbs, so wave 1 is reported but not failed).
	 */
	static void flipsOnBoundaries(List<RoundReplay.Frame> frames, Report r)
	{
		int waveTick = -1;
		int wave = 0;
		Integer firstOff = null;
		String lastShout = null;
		List<String> offsets = new ArrayList<>();
		for (RoundReplay.Frame f : frames)
		{
			for (Rec.Event e : events(f.raw, "chat"))
			{
				if (ChatMessageType.GAMEMESSAGE.name().equals(e.type) && WAVE_MESSAGE.matcher(Text.removeTags(e.text)).find())
				{
					waveTick = e.t;
					firstOff = null;
					java.util.regex.Matcher m = WAVE_MESSAGE.matcher(Text.removeTags(e.text));
					wave = m.find() && m.groupCount() >= 1 && m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
				}
			}
			Role role = hudRole(f.raw);
			if (role == null || waveTick < 0 || !f.snapshot.isInArena())
			{
				continue;
			}
			String shout = RoundReplay.hud(f.raw.hud).text(role.getShoutComponent());
			boolean flipped = shout != null && !shout.equals(lastShout) && BaCall.parse(role.partner(), shout) != null;
			if (flipped && f.raw.t > waveTick)
			{
				int since = f.raw.t - waveTick - CallClock.START_OFFSET;
				int k = Math.round(since / (float) WaveInfo.TICKS_PER_CALL);
				int off = since - k * WaveInfo.TICKS_PER_CALL;
				offsets.add("call " + (k + 1) + ": " + (off >= 0 ? "+" : "") + off);
				// The wave message can arrive a few ticks late, and a leader stall can shift the whole call
				// cycle (recorded: one 47-tick call in attacker wave 8). The clock re-anchors on every flip
				// within RESYNC_WINDOW of a boundary, so a shift up to that is a note; only a bigger jump,
				// which the clock would ignore, is a failure
				if (firstOff != null)
				{
					int shift = off - firstOff;
					if (Math.abs(shift) > CallClock.RESYNC_WINDOW)
					{
						r.violations.add("clock: tick " + f.raw.t + ": shout flipped to '" + shout + "' " + shift
							+ " ticks off the rhythm, too far for the clock to re-anchor");
					}
					else if (Math.abs(shift) > FLIP_TOLERANCE)
					{
						r.notes.add("clock: call cycle shifted " + shift + " ticks at tick " + f.raw.t + " (re-anchored)");
					}
				}
				firstOff = off;
			}
			if (shout != null)
			{
				lastShout = shout;
			}
		}
		if (!offsets.isEmpty())
		{
			r.notes.add("shout flips vs wave message + " + CallClock.START_OFFSET + " + 50k: " + String.join(", ", offsets));
		}
	}

	/**
	 * Healer only: the foods the ledger counted equal our own feeds (use-on-healer click followed by
	 * the food leaving the inventory, no wrong-food message, matching the heard call), plus at most
	 * one per poison graphic that was not our own.
	 */
	static void healerFoodCounts(List<RoundReplay.Frame> frames, Report r)
	{
		if (frames.stream().noneMatch(f -> f.snapshot.getRole() == Role.HEALER && f.snapshot.isInArena()))
		{
			return;
		}
		List<int[]> clicks = new ArrayList<>(); // {npcIndex, itemId, tick}
		Map<Integer, Integer> maxFoods = new HashMap<>();
		Map<Integer, Integer> confirmed = new HashMap<>();
		java.util.Set<Integer> died = new java.util.HashSet<>();
		Map<Integer, Integer> lastInv = new HashMap<>();
		int own = 0;
		int rejected = 0;
		for (RoundReplay.Frame f : frames)
		{
			for (Rec.Event e : events(f.raw, null))
			{
				if ("death".equals(e.k) && e.npc != null) died.add(e.npc);
				if ("npcSpawn".equals(e.k) && died.contains(e.npc))
				{
					// A dying NPC can re-enter view: its death event precedes its spawn event in this
					// tick (wave 10, 3484). Only a living replacement starts a fresh food count.
					boolean dying = f.raw.npcs != null && f.raw.npcs.stream().anyMatch(n -> n.i == e.npc && n.dead);
					if (!dying)
					{
						died.remove(e.npc);
						confirmed.remove(e.npc);
					}
				}
				if ("menu".equals(e.k) && MenuAction.WIDGET_TARGET_ON_NPC.name().equals(e.type) && e.npc != null
					&& e.selected != null && BaIds.POISON_FOOD.contains(e.selected) && PenanceType.forNpcId(e.id) == PenanceType.HEALER)
				{
					clicks.add(new int[]{e.npc, e.selected, e.t});
				}
				else if ("chat".equals(e.k) && Text.removeTags(e.text).toLowerCase().contains(WRONG_FOOD))
				{
					rejected += clicks.size();
					clicks.clear();
				}
			}
			int tick = f.raw.t;
			clicks.removeIf(c -> tick - c[2] > RoundLogic.FEED_CONFIRM_TICKS);
			Map<Integer, Integer> inv = f.raw.inv == null ? new HashMap<>() : f.raw.inv;
			BaCall call = f.snapshot.getListenCall();
			for (int food : BaIds.POISON_FOOD)
			{
				int dropped = lastInv.getOrDefault(food, 0) - inv.getOrDefault(food, 0);
				for (int i = 0; i < dropped; i++)
				{
					int[] match = clicks.stream().filter(c -> c[1] == food).findFirst().orElse(null);
					if (match == null)
					{
						break;
					}
					clicks.remove(match);
					if (call != null && call.getItemId() == food)
					{
						own++;
						confirmed.merge(match[0], 1, Integer::sum);
					}
				}
			}
			lastInv = inv;
			for (PenanceView p : f.snapshot.getPenance())
			{
				if (p.getType() == PenanceType.HEALER)
				{
					maxFoods.merge(p.getNpcIndex(), p.getFoods(), Math::max);
					int expected = confirmed.getOrDefault(p.getNpcIndex(), 0);
					if (p.getFoods() != expected) r.violations.add("healer food: tick " + tick + " NPC " + p.getNpcIndex()
						+ " counted " + p.getFoods() + " but " + expected + " own foods were confirmed");
				}
			}
		}
		int counted = maxFoods.values().stream().mapToInt(Integer::intValue).sum();
		r.notes.add("healer food: " + own + " own feeds from inventory, " + rejected + " rejected, ledger counted " + counted);
		// A final food can land on an NPC after it left the scene, so no final visible view is
		// guaranteed. Validate every observed NPC above rather than requiring its last view here.
		if (counted > own)
		{
			r.violations.add("healer food: ledger counted " + counted + ", more than " + own + " confirmed own feeds");
		}
	}

	/**
	 * Steps whose items are put away rather than used on the call: loading eggs (any colour goes in
	 * the hopper), destroying blue eggs, the defender's bad bait, and the queen's egg chain.
	 */
	private static final java.util.Set<Step> NOT_CALL_USE = java.util.EnumSet.of(
		Step.COL_LOAD, Step.COL_BLUE, Step.COL_DISCARD, Step.DEF_DROP, Step.DEF_RELURE,
		Step.QUEEN_COLLECT, Step.QUEEN_PASS, Step.QUEEN_DUNK, Step.QUEEN_LAVA, Step.QUEEN_SPIKE, Step.QUEEN_LOAD, Step.QUEEN_LOAD_ONE);

	/** A fresh call means the instruction must never point at a different item of the same kind. */
	static void itemsMatchFreshCall(List<RoundReplay.Frame> frames, Report r)
	{
		for (RoundReplay.Frame f : frames)
		{
			Snapshot s = f.snapshot;
			BaCall call = s.getListenCall();
			if (call == null || !s.isListenFresh() || s.getRole() == null || NOT_CALL_USE.contains(f.instruction.getStep()))
			{
				continue;
			}
			for (int item : f.instruction.getItems())
			{
				if (BaCall.isItemOf(s.getRole(), item) && item != call.getItemId())
				{
					r.violations.add("items: tick " + f.raw.t + ": call is " + call.name() + " but '" + f.instruction.getHeadline()
						+ "' outlines " + BaCall.forItem(s.getRole(), item).name());
				}
			}
		}
	}

	/** Missing or stale calls never authorize a new call-dependent action. */
	static void staleCallsNotUsed(List<RoundReplay.Frame> frames, Report r)
	{
		java.util.Set<Step> dependent = java.util.EnumSet.of(Step.ATT_ATTACK, Step.ATT_EQUIP,
			Step.HEAL_FEED, Step.COL_COLLECT, Step.DEF_DROP, Step.DEF_RELURE);
		for (RoundReplay.Frame frame : frames)
		{
			Step step = frame.instruction.getStep();
			if (frame.snapshot.isInArena() && !frame.snapshot.isListenFresh() && dependent.contains(step)
				&& (step != Step.DEF_RELURE || !frame.instruction.getItems().isEmpty()))
			{
				r.violations.add("stale call: tick " + frame.raw.t + ": " + frame.instruction.getHeadline());
			}
		}
	}

	/**
	 * Collector only: at every "You load the hopper." the hopper may grow by at most what the
	 * tracked bag plus the inventory held of each colour (capped at 5 loaded), read two ticks later.
	 * A hopper that grows by more means the bag counter missed eggs.
	 */
	static void eggBagMatchesLoads(List<RoundReplay.Frame> frames, Report r)
	{
		int checked = 0;
		int exact = 0;
		for (int i = 1; i + 2 < frames.size(); i++)
		{
			RoundReplay.Frame f = frames.get(i);
			if (events(f.raw, "chat").stream().noneMatch(e -> "You load the hopper.".equals(Text.removeTags(e.text))))
			{
				continue;
			}
			Snapshot before = frames.get(i - 1).snapshot;
			Snapshot after = frames.get(i + 2).snapshot;
			int[][] colours = {
				{BaIds.EGG_GREEN, before.getBagGreen(), before.getEggsGreen(), after.getEggsGreen()},
				{BaIds.EGG_RED, before.getBagRed(), before.getEggsRed(), after.getEggsRed()},
				{BaIds.EGG_BLUE, before.getBagBlue(), before.getEggsBlue(), after.getEggsBlue()},
			};
			boolean ok = true;
			boolean same = true;
			StringBuilder why = new StringBuilder();
			for (int[] c : colours)
			{
				int expected = Math.min(before.count(c[0]) + c[1], Math.max(0, 5 - c[2]));
				int actual = c[3] - c[2];
				same &= actual == expected;
				// Firing between the two reads can only make the hopper look emptier
				if (actual > expected)
				{
					ok = false;
					why.append(BaCall.forItem(Role.COLLECTOR, c[0])).append(" +").append(actual)
						.append(" (bag ").append(c[1]).append(", inventory ").append(before.count(c[0])).append(") ");
				}
			}
			checked++;
			if (same)
			{
				exact++;
			}
			if (!ok)
			{
				r.violations.add("bag: tick " + f.raw.t + ": hopper grew by more than bag + inventory: " + why.toString().trim());
			}
		}
		if (checked > 0)
		{
			r.notes.add("egg bag: " + exact + "/" + checked + " hopper loads exactly matched bag + inventory");
		}
	}

	/** A horn call that doesn't match the HUD's call-to-give must not count as the call made. */
	static void wrongCallsNotDone(List<RoundReplay.Frame> frames, Report r)
	{
		int wrong = 0;
		for (int i = 0; i + 2 < frames.size(); i++)
		{
			RoundReplay.Frame f = frames.get(i);
			Role role = f.snapshot.getRole();
			BaCall want = f.snapshot.getShoutCall();
			// Extra clicks after the call was already made (e.g. calling the next one early, recorded
			// in wave 10) and calls during the Queen don't count
			boolean alreadyDone = i > 0 && frames.get(i - 1).snapshot.isShoutDone();
			if (role == null || want == null || alreadyDone || f.snapshot.isQueenAlive())
			{
				continue;
			}
			for (Rec.Event e : events(f.raw, "menu"))
			{
				String option = e.option == null ? "" : e.option;
				if (option.startsWith("Tell-") && !option.equalsIgnoreCase(want.getHornOption()))
				{
					wrong++;
					Snapshot after = frames.get(i + 2).snapshot;
					boolean fixed = false;
					for (int j = i; j <= i + 2; j++)
					{
						fixed |= events(frames.get(j).raw, "menu").stream()
							.anyMatch(x -> want.getHornOption().equalsIgnoreCase(x.option));
					}
					if (after.isShoutDone() && !fixed && after.getShoutCall() == want)
					{
						r.violations.add("call: tick " + e.t + ": " + option + " on a " + want.getKeyword() + " call counted as done");
					}
					if (!fixed && after.getShoutWrong() == null && after.getShoutCall() == want)
					{
						r.violations.add("call: tick " + e.t + ": " + option + " on a " + want.getKeyword() + " call not flagged as wrong");
					}
				}
			}
		}
		if (wrong > 0)
		{
			r.notes.add("wrong calls: " + wrong);
		}
	}

	/** Not a failure: how often the attack target changed while the previous one was still alive. */
	static void attackSwitches(List<RoundReplay.Frame> frames, Report r)
	{
		switches(frames, r, Step.ATT_ATTACK, "attack");
		switches(frames, r, Step.HEAL_FEED, "feed");
	}

	private static void switches(List<RoundReplay.Frame> frames, Report r, Step step, String what)
	{
		int attacks = 0;
		int switches = 0;
		int last = -1;
		for (RoundReplay.Frame f : frames)
		{
			if (f.instruction.getStep() != step)
			{
				continue;
			}
			attacks++;
			int now = f.instruction.getTarget().getId();
			final int prev = last;
			boolean prevAlive = prev >= 0 && f.snapshot.getPenance().stream()
				.anyMatch(p -> p.getNpcIndex() == prev && !p.isDying());
			if (now != prev && prevAlive)
			{
				switches++;
			}
			last = now;
		}
		if (attacks > 0)
		{
			r.notes.add(what + " target switches while the previous target lived: " + switches + " in " + attacks + " " + what + " ticks");
		}
	}

	/** Not a failure: the performance numbers the plugin would show for this wave. */
	static void performanceNotes(List<RoundReplay.Frame> frames, Report r)
	{
		WaveStats w = null;
		int now = 0;
		for (RoundReplay.Frame f : frames)
		{
			if (f.snapshot.getWaveStats() != null)
			{
				w = f.snapshot.getWaveStats();
				now = f.raw.t;
			}
		}
		if (w == null)
		{
			return;
		}
		r.notes.add(String.format("performance: wave %d %s %s | your calls %d, avg %.1fs, worst %.1fs, missed %d"
				+ " | partner calls avg %.1fs, missed %d | eggs %d (+%d wrong), loads %d, shots %d, foods %d, kills F/R %d R %d",
			w.getWave(), w.getRole(), WaveStats.clock(w.durationTicks(now)),
			w.getCallReactions().size(), WaveStats.meanSeconds(w.getCallReactions()), WaveStats.worstSeconds(w.getCallReactions()),
			w.getCallsMissed(), WaveStats.meanSeconds(w.getPartnerDelays()), w.getPartnerMissed(),
			w.getEggsPicked(), w.getEggsWrong(), w.getLoads(), w.getShots(), w.getFoods(), w.getFighterRangerKills(), w.getRunnerKills()));
	}

	/** Not a failure: where the replay (current code) and the live plugin disagreed. */
	static void liveAgreement(List<RoundReplay.Frame> frames, Report r)
	{
		int compared = 0;
		int steps = 0;
		int listens = 0;
		String firstStep = null;
		for (RoundReplay.Frame f : frames)
		{
			Rec.Live live = f.raw.live;
			if (live == null)
			{
				continue;
			}
			compared++;
			String step = f.instruction.getStep() == null ? null : f.instruction.getStep().name();
			if (!Objects.equals(step, live.step))
			{
				steps++;
				if (firstStep == null)
				{
					firstStep = "tick " + f.raw.t + " live " + live.step + " / replay " + step;
				}
			}
			String listen = f.snapshot.getListenCall() == null ? null : f.snapshot.getListenCall().name();
			if (!Objects.equals(listen, live.listen))
			{
				listens++;
			}
		}
		if (compared > 0)
		{
			r.notes.add("live vs replay over " + compared + " ticks: " + steps + " step and " + listens + " listen-call differences"
				+ (firstStep == null ? "" : " (first: " + firstStep + ")"));
		}
	}

	// ------------------------------------------------------------------ helpers

	static Role hudRole(Rec.Tick t)
	{
		if (t.hud == null)
		{
			return null;
		}
		for (Role role : Role.values())
		{
			if (t.hud.containsKey(WidgetUtil.packComponentId(role.getHudGroup(), 0)))
			{
				return role;
			}
		}
		return null;
	}

	static List<Rec.Event> events(Rec.Tick t, String kind)
	{
		List<Rec.Event> out = new ArrayList<>();
		if (t.ev != null)
		{
			for (Rec.Event e : t.ev)
			{
				if (kind == null || kind.equals(e.k))
				{
					out.add(e);
				}
			}
		}
		return out;
	}
}
