package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** Small, immutable record of unfinished role work; the planner still emits one action. */
@Value
public class TaskMemory
{
	public enum Status { READY, RUNNING, SUSPENDED, BLOCKED }

	@Value
	public static class Progress
	{
		Target target;
		Status status;
		String reason;
		int startedTick;
		int lastActiveTick;
		int lastProgressTick;
		int evidence;
		WorldPoint position;
	}

	private static final Set<String> TRACKED = Set.of("def.tools", "def.repair", "def.fix", "def.pattern",
		"col.load", "col.support", "col.scan", "queen.col.pass", "heal.stock", "heal.food", "heal.topup", "heal.cover", "heal.reserve", "call");
	Role role;
	int wave;
	Map<String, Progress> entries;

	public static TaskMemory empty()
	{
		return new TaskMemory(null, 0, ImmutableMap.of());
	}

	public Target target(String task)
	{
		Progress p = entries.get(task);
		return p == null ? null : p.getTarget();
	}

	public int lastActive(String task)
	{
		Progress p = entries.get(task);
		return p == null ? -1 : p.getLastActiveTick();
	}

	/** Reconcile completion and prerequisites from observations, even while another job is selected. */
	TaskMemory observe(Snapshot s, Settings c)
	{
		if (!s.isInArena() || s.getRole() == null)
		{
			return empty();
		}
		Map<String, Progress> next = new LinkedHashMap<>();
		if (role == s.getRole() && wave == s.getWave())
		{
			entries.forEach((id, p) ->
			{
				if (needed(id, s, c)) next.put(id, refresh(id, p, s));
			});
		}
		// These independent obligations remain visible when calls, supplies, or survival block them.
		if (s.getRole() == Role.DEFENDER)
		{
			seed(next, "def.pattern", s, c);
			seed(next, "def.tools", s, c);
			seed(next, "def.repair", s, c);
		}
		else if (s.getRole() == Role.HEALER)
		{
			seed(next, "heal.stock", s, c);
			seed(next, "heal.food", s, c);
			seed(next, "heal.cover", s, c);
		}
		else if (s.getRole() == Role.COLLECTOR)
		{
			seed(next, "col.load", s, c);
		}
		seed(next, "call", s, c);
		return new TaskMemory(s.getRole(), s.getWave(), ImmutableMap.copyOf(next));
	}

	TaskMemory advance(Snapshot s, Settings c, Instruction chosen)
	{
		if (!s.isInArena() || s.getRole() == null)
		{
			return empty();
		}
		Map<String, Progress> next = new LinkedHashMap<>(entries);
		for (PriorityLine option : chosen.getPriorities())
		{
			if (TRACKED.contains(option.getTask() == null ? "" : option.getTask()) && !CollectorPlanner.CHECK_TASK.equals(option.getTask()))
			{
				seed(next, option.getTask(), s, c);
			}
		}
		String selected = chosen.getStep() == Step.CALL ? "call" : chosen.getTask();
		if (selected != null && TRACKED.contains(selected) && needed(selected, s, c))
		{
			Progress old = next.get(selected);
			boolean restartCheck = selected.equals(CollectorPlanner.CHECK_TASK) && old != null
				&& (CollectorPlanner.CHECK_DONE.equals(old.getReason()) || s.getTick() - old.getLastActiveTick() >= CollectorPlanner.CENTRE_CHECK_COOLDOWN);
			int started = old == null || old.getStartedTick() < 0 || restartCheck ? s.getTick() : old.getStartedTick();
			int progress = old == null ? s.getTick() : old.getLastProgressTick();
			if (old != null && !sameTarget(old.getTarget(), chosen.getTarget())) progress = s.getTick();
			next.put(selected, new Progress(chosen.getTarget(), Status.RUNNING, "", started,
				s.getTick(), progress, evidence(selected, s), s.getPlayerTile()));
			if (selected.equals("queen.col.pass") && chosen.getTarget().getKind() == Target.Kind.PLAYER)
				next.put("queen.col.recipient", new Progress(chosen.getTarget(), Status.READY, "", started,
					s.getTick(), progress, 0, s.getPlayerTile()));
		}
		return new TaskMemory(role, wave, ImmutableMap.copyOf(next));
	}

	private static boolean sameTarget(Target previous, Target current)
	{
		return previous.getKind() == Target.Kind.NPC && current.getKind() == Target.Kind.NPC
			? previous.getId() == current.getId() : previous.equals(current);
	}

	private static void seed(Map<String, Progress> entries, String id, Snapshot s, Settings c)
	{
		if (!entries.containsKey(id) && needed(id, s, c))
		{
			Progress p = new Progress(Target.NONE, Status.READY, "", -1, -1, s.getTick(), evidence(id, s), s.getPlayerTile());
			entries.put(id, refresh(id, p, s));
		}
	}

	private static Progress refresh(String id, Progress p, Snapshot s)
	{
		if (id.equals(CollectorPlanner.CHECK_TASK) && (CollectorPlanner.CHECK_DONE.equals(p.getReason())
			|| p.getTarget().getTile() != null && s.walk(p.getTarget().getTile()) <= 1))
			return new Progress(p.getTarget(), Status.READY, CollectorPlanner.CHECK_DONE, p.getStartedTick(),
				p.getLastActiveTick(), CollectorPlanner.CHECK_DONE.equals(p.getReason()) ? p.getLastProgressTick() : s.getTick(),
				0, s.getPlayerTile());
		String blocked = blocked(id, s);
		boolean moved = p.getTarget().getTile() != null && ArenaTiles.distance(s.getPlayerTile(), p.getTarget().getTile())
			< ArenaTiles.distance(p.getPosition(), p.getTarget().getTile());
		int value = evidence(id, s);
		Status status = !blocked.isEmpty() ? Status.BLOCKED : p.getStartedTick() < 0 ? Status.READY : Status.SUSPENDED;
		return new Progress(p.getTarget(), status,
			blocked, p.getStartedTick(), p.getLastActiveTick(), moved || value != p.getEvidence() ? s.getTick() : p.getLastProgressTick(),
			value, s.getPlayerTile());
	}

	private static String blocked(String id, Snapshot s)
	{
		if (id.equals("call")) return s.isShoutSettled() ? "" : "Waiting for the outgoing call to settle";
		if (id.equals("def.pattern") || (id.startsWith("heal.") && !id.equals("heal.stock")))
		{
			if (!s.isListenFresh() || s.getListenCall() == null) return "Waiting for a fresh incoming call";
			if ((id.equals("heal.cover") || id.equals("heal.reserve")) && !s.has(s.getListenCall().getItemId())) return "Waiting for correct food";
		}
		if ((id.equals("col.load") || id.equals("col.support")) && loadRoom(s) == 0) return "Waiting for hopper room";
		if ((id.equals("def.repair") || id.equals("def.fix")) && s.getTrapCharges() < 0) return "Trap is out of view";
		return "";
	}

	private static boolean needed(String id, Snapshot s, Settings c)
	{
		switch (id)
		{
			case "call": return s.getShoutCall() != null && !s.isShoutDone() && !Planner.callsOver(s)
				&& (!c.isSkipCoveredCalls() || s.getShoutCoveredBy() == null);
			case "def.pattern": return c.isDefenderPattern() && s.getWaveTick() >= 0 && s.getWaveTick() < WaveInfo.TICKS_PER_CALL
				&& ArenaTiles.defenderPattern(s.getWave(), c.getDefenderGuide(), c.isSafetyBait()).stream()
				.anyMatch(f -> DefenderPlanner.placed(s, f) < f.getCount());
			case "def.tools": return !s.getCleared().contains(PenanceType.RUNNER) && !DefenderPlanner.toolsReady(s);
			case "def.repair": return !s.getCleared().contains(PenanceType.RUNNER) && s.getTrapCharges() <= 0;
			case "def.fix": return !s.getCleared().contains(PenanceType.RUNNER) && s.getTrapCharges() < 2;
			case "col.load": return s.count(BaIds.EGG_RED) + s.count(BaIds.EGG_GREEN) + s.getBagRed() + s.getBagGreen() > 0;
			case "col.support": return CollectorPlanner.supportReady(s);
			case "col.scan": return s.getRole() == Role.COLLECTOR;
			case "queen.col.pass": return s.getRole() == Role.COLLECTOR && s.getWave() == 10 && !s.isQueenDead() && s.has(BaIds.EGG_YELLOW)
				&& (s.getQueenYellowPassed() < Math.max(1, c.getQueenEggBatch()) || QueenPlanner.reserveReady(s, c));
			case "queen.col.recipient": return s.getRole() == Role.COLLECTOR && s.getWave() == 10 && s.isQueenAlive();
			case "heal.stock": return healersRemain(s) && s.getWaveTick() >= 0 && s.getWaveTick() < WaveInfo.TICKS_PER_CALL
				&& BaIds.POISON_FOOD.stream().noneMatch(s::has);
			case "heal.food": return healersRemain(s) && (!s.isListenFresh() || s.getListenCall() == null
				|| !s.has(s.getListenCall().getItemId()));
			case "heal.topup": return healersRemain(s) && (!s.isListenFresh() || s.getListenCall() == null
				|| s.count(s.getListenCall().getItemId()) < HealerPlanner.foodToKeep(s, s.alive(PenanceType.HEALER)) - HealerPlanner.TOPUP_SLACK);
			case "heal.cover": return s.alive(PenanceType.HEALER).stream().anyMatch(h -> h.nextPoisonHit(s.getTick()) == 0);
			case "heal.reserve": return HealerPlanner.reservesWaiting(s) > 0 && (s.getListenCall() == null || !s.isListenFresh()
				|| s.count(s.getListenCall().getItemId()) > HealerPlanner.foodToKeep(s, s.alive(PenanceType.HEALER)));
			default: return false;
		}
	}

	private static boolean healersRemain(Snapshot s)
	{
		return !s.alive(PenanceType.HEALER).isEmpty()
			|| s.getHealersKilled() < WaveInfo.healersTotal(s.getWave());
	}

	private static int loadRoom(Snapshot s)
	{
		return Math.min(s.count(BaIds.EGG_RED) + s.getBagRed(), Math.max(0, 5 - s.getEggsRed()))
			+ Math.min(s.count(BaIds.EGG_GREEN) + s.getBagGreen(), Math.max(0, 5 - s.getEggsGreen()));
	}

	private static int evidence(String id, Snapshot s)
	{
		if (id.equals("call")) return s.getCallPeriod();
		if (id.equals("def.tools")) return s.count(BaIds.LOGS) + (s.isHasHammer() ? 100 : 0);
		if (id.equals("def.pattern")) return s.getGoodBaitLanded().values().stream().mapToInt(Integer::intValue).sum()
			+ s.getBadBaitLanded().values().stream().mapToInt(Integer::intValue).sum();
		if (id.startsWith("def.")) return s.getTrapCharges();
		if (id.equals("col.load") || id.equals("col.support")) return s.count(BaIds.EGG_RED) + s.count(BaIds.EGG_GREEN) + s.getBagRed() + s.getBagGreen();
		if (id.equals("queen.col.pass")) return s.count(BaIds.EGG_YELLOW);
		if (id.equals("heal.cover")) return s.alive(PenanceType.HEALER).stream().mapToInt(PenanceView::getFoods).sum();
		return BaIds.POISON_FOOD.stream().mapToInt(s::count).sum();
	}
}
