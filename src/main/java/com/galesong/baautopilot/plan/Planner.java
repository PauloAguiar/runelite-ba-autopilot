package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableSet;

/**
 * Pure decision engine: one snapshot in, one instruction out. Order of precedence:
 * <ol>
 * <li>outside the arena: lobby / nothing</li>
 * <li>critically low HP (not the healer): get to a launcher hill and wait for the medic</li>
 * <li>the call you owe your partner (it is the single most valuable click in the minigame)</li>
 * <li>the Penance Queen egg chain on wave 10, weighed against health and supplies for the Healer</li>
 * <li>the role's own step: its planner lists candidates by {@link Tier}, the {@link Chooser} picks one,
 * keeping a committed task until it is done or a higher tier comes up</li>
 * </ol>
 * The owed call (recorded: "Collector not calling" 8 ticks in; "Healer you need to be on top of
 * calls or it ruins def aggro") leads from the first tick, keeping the role step's target.
 */
public class Planner
{
	/** During the Queen (up to 13 every 2.4 s to everyone near her) retreat earlier than usual. */
	static final double QUEEN_RETREAT = 0.45;
	private static final java.util.Set<Step> WAIT_STEPS = java.util.EnumSet.of(
		Step.ATT_WAIT_CALL, Step.COL_WAIT_CALL, Step.DEF_WAIT_CALL, Step.HEAL_WAIT_CALL);

	private final AttackerPlanner attacker = new AttackerPlanner();
	private final CollectorPlanner collector = new CollectorPlanner();
	private final DefenderPlanner defender = new DefenderPlanner();
	private final HealerPlanner healer = new HealerPlanner();
	private final QueenPlanner queen = new QueenPlanner();

	public Instruction plan(Snapshot s, Settings c)
	{
		TaskMemory tasks = s.getTasks().observe(s, c);
		Snapshot observed = s.toBuilder().tasks(tasks).build();
		Instruction chosen = decide(observed, c);
		return chosen.toBuilder().tasks(tasks.advance(observed, c, chosen)).build();
	}

	private Instruction decide(Snapshot s, Settings c)
	{
		if (!s.isInArena())
		{
			if (s.isInLobby())
			{
				return Instruction.builder()
					.step(Step.LOBBY)
					.headline(lobbyHeadline(s))
					.detail(s.getRole() == null || s.getWave() == 0 ? "Wait in a room for a leader's offer, or take a scroll and lead"
						: lobbyTip(s.getRole()))
					.urgency(Urgency.INFO)
					.build();
			}
			return Instruction.builder().step(Step.NOT_IN_GAME).headline("").urgency(Urgency.INFO).build();
		}

		if (s.getRole() == null)
		{
			return Instruction.builder().step(Step.WAIT_ROLE).headline("Waiting for your role")
				.detail("The role HUD appears a moment after the wave loads").urgency(Urgency.INFO).build();
		}

		// Low HP: any death ends the game for the whole team ("The game ended early because one of
		// your teammates died"). It's shown with the step, not instead of it - a step about to finish
		// (dipping an egg) mustn't be pulled away (reported 2026-10-17); it takes over only when
		// you're idle or close to dying
		double retreatAt = s.isQueenAlive() ? Math.max(c.getRetreatBelow(), QUEEN_RETREAT) : c.getRetreatBelow();
		Instruction danger = null;
		if (s.getRole() != Role.HEALER && s.getMaxHp() > 0 && s.hpFraction() < retreatAt)
		{
			danger = retreat(s).toBuilder().decidedBy("Survival: HP under " + Math.round(retreatAt * 100) + "%").build();
		}
		if (s.getRole() == Role.HEALER && !QueenHealerPlanner.applies(s))
		{
			Instruction drink = HealerPlanner.selfDrink(s, c);
			if (drink != null && s.hpFraction() < c.getRetreatBelow() / 2)
			{
				// Close to dying yourself: nobody else can heal you
				return drink.toBuilder().decidedBy("Survival: your own HP is critical").build();
			}
			Instruction care = HealerPlanner.teammateDanger(s, c);
			if (care != null)
			{
				return care.toBuilder().decidedBy("Survival: a teammate in danger (before everything)").build();
			}
			danger = drink == null ? null : drink.toBuilder().decidedBy("Survival: your own HP").build();
		}
		Instruction main = act(s, c);
		if (danger == null)
		{
			return main;
		}
		boolean idle = main.getTier() == Tier.IDLE || main.getStep().getKind() == ActionKind.WAIT;
		if (idle || s.hpFraction() < retreatAt / 2)
		{
			return danger;
		}
		return main.toBuilder().advice(danger.getHeadline() + " when you can (" + Math.round(s.hpFraction() * 100) + "% HP)").build();
	}

	private Instruction act(Snapshot s, Settings c)
	{
		Instruction call = call(s, c);
		Snapshot acting = gate(s);
		boolean queenPhase = queen.applies(acting) || QueenHealerPlanner.applies(acting);
		Instruction work = queenPhase ? queen.plan(acting, c).toBuilder().decidedBy("Queen phase: the egg chain").build() : roleStep(acting, c);
		if (queenPhase && s.getRole() == Role.HEALER)
		{
			work = QueenHealerPlanner.plan(acting, work);
		}
		work = explainGate(s, acting, work);
		// With the Queen out, the egg chain beats calls: a call only goes first while you're waiting
		if (call != null && queenPhase && work.getStep() != Step.QUEEN_WAIT)
		{
			return work;
		}
		if (call != null)
		{
			// The call leads from the first tick (partners wait on it). Only the wave's first call is
			// made on the move, keeping the work step's target and marks (nothing is urgent yet and
			// the walk to the machine is long); later calls are made on the spot, nothing to walk to
			String next = work.getHeadline() == null || work.getHeadline().isEmpty() ? "" : "Then: " + work.getHeadline();
			Instruction.InstructionBuilder led = call.toBuilder()
				.detail(call.getDetail() + (next.isEmpty() ? "" : "\n" + next)).task("call");
			if (s.getCallPeriod() == 0)
			{
				led.target(work.getTarget()).marks(work.getMarks()).then(work.getStep()).leftClick(work.getLeftClick())
					.pickupCount(work.getPickupCount()).badBait(work.isBadBait()).rightClick(work.isRightClick());
			}
			return led.priorities(work.getPriorities())
				.decidedBy("Owed call leads (partner waits); underneath: " + work.getDecidedBy()).build();
		}
		return work;
	}

	/**
	 * Last period's call is not this period's: right after the call changes, until the partner calls
	 * again, the role acts as if it has no call (food, arrows, eggs and bait from the old call all
	 * cost points). Elapsed time cannot make an old instruction valid again. Work that does not
	 * require a new call, such as repairing or using known ground bait, remains available.
	 */
	static Snapshot gate(Snapshot s)
	{
		if (s.getListenCall() == null || s.isListenFresh())
		{
			return s;
		}
		return s.toBuilder().listenCall(null).build();
	}

	private static Instruction explainGate(Snapshot s, Snapshot acting, Instruction work)
	{
		BaCall last = s.getListenCall();
		if (last == null || s.isListenFresh())
		{
			return work;
		}
		String partner = s.getRole().partner().getLabel().toLowerCase();
		if (acting.getListenCall() == null && WAIT_STEPS.contains(work.getStep()))
		{
			boolean disengage = work.getStep() == Step.ATT_WAIT_CALL && s.getInteractingNpc() >= 0;
			return work.toBuilder()
				.headline((disengage ? "Stop attacking: wait for the " : "Wait for the ") + partner + "'s new call")
				.detail((disengage ? "Click a safe tile to disengage. " : "")
					+ "No fresh call this period. Last call was " + last.getItemName()
					+ "; using it now could be a penalty")
				.build();
		}
		return work;
	}

	private Instruction roleStep(Snapshot s, Settings c)
	{
		switch (s.getRole())
		{
			case ATTACKER:
				return Chooser.choose(s, attacker.candidates(s, c));
			case COLLECTOR:
				return Chooser.choose(s, collector.candidates(s, c));
			case DEFENDER:
				return Chooser.choose(s, defender.candidates(s, c));
			default:
				return Chooser.choose(s, healer.candidates(s, c));
		}
	}

	/**
	 * Calls no longer do anything: the Queen is out, wave 10's penance are all dead, or the game
	 * announced every penance your call is for as killed - the defender's call picks the healer's
	 * poison food, the healer's the defender's runner bait, the collector's the attacker's style.
	 */
	public static boolean callsOver(Snapshot s)
	{
		if (s.isQueenAlive() || (s.getWave() == 10 && CollectorPlanner.nothingLeftToShoot(s)))
		{
			return true;
		}
		if (s.getRole() == null)
		{
			return false;
		}
		switch (s.getRole())
		{
			case DEFENDER:
				return s.getCleared().contains(PenanceType.HEALER);
			case HEALER:
				return s.getCleared().contains(PenanceType.RUNNER);
			case COLLECTOR:
				return s.getCleared().contains(PenanceType.FIGHTER) && s.getCleared().contains(PenanceType.RANGER);
			default:
				return false;
		}
	}

	static Instruction call(Snapshot s, Settings c)
	{
		BaCall shout = s.getShoutCall();
		// Once wave 10's penance are dead (and through the Queen), egg-colour and style calls no longer
		// do anything; a teammate of your role who already made the right call covers it
		if (shout == null || s.isShoutDone() || !s.isShoutSettled() || callsOver(s)
			|| (s.getShoutCoveredBy() != null && c.isSkipCoveredCalls()))
		{
			return null;
		}
		BaCall wrong = s.getShoutWrong();
		return Instruction.builder()
			.step(Step.CALL)
			.headline(wrong != null ? "Wrong call! Call " + shout.getKeyword() + "!"
				: s.isShoutLost() ? "Call didn't go through: call " + shout.getKeyword() + " again!" : "Call " + shout.getKeyword() + "!")
			.detail((wrong != null ? "You said " + wrong.getKeyword() + ". "
				: s.isShoutLost() ? "A click right after the horn cancels it. " : "")
				+ "Horn: " + shout.getHornOption() + ", from where you are")
			.items(BaIds.HORNS)
			.urgency(Urgency.HIGH)
			.build();
	}

	private static Instruction retreat(Snapshot s)
	{
		if (s.isQueenAlive())
		{
			// The Queen's ranged attack reaches the hills; the healer heals by the spring
			ObjectView spring = s.nearestObject(BaIds.SPRING);
			return Instruction.builder()
				.step(Step.RETREAT)
				.headline("Low HP: go to the healer spring")
				.detail("Ask the healer for the vial. Only the healer can drink from the spring")
				.target(spring == null ? Target.NONE : Target.object(spring.getId(), spring.getTile()))
				.urgency(Urgency.HIGH)
				.build();
		}
		ObjectView hill = s.nearestObject(BaIds.LAUNCHERS);
		return Instruction.builder()
			.step(Step.RETREAT)
			.headline("Low HP: get onto a launcher hill")
			.detail("Penance can't follow you up there. Call for the healer's vial, then return")
			.target(hill == null ? Target.NONE : Target.object(hill.getId(), hill.getTile()))
			.urgency(Urgency.HIGH)
			.build();
	}

	private static String lobbyHeadline(Snapshot s)
	{
		if (s.getRole() == null || s.getWave() == 0)
		{
			return "Join a team";
		}
		return s.getWave() >= 10 ? "Round done" : "Wave " + (s.getWave() + 1) + " next";
	}

	private static String lobbyTip(Role role)
	{
		switch (role)
		{
			case ATTACKER:
				return "Attacker: turn auto-retaliate off; bring a bow and your best ranged gear";
			case COLLECTOR:
				return "Collector: keep inventory space free for eggs";
			case DEFENDER:
				return "Defender: empty inventory for bait, logs and a hammer";
			default:
				return "Healer: empty inventory for poisoned food and the vial";
		}
	}

	static ImmutableSet<Integer> items(int... ids)
	{
		ImmutableSet.Builder<Integer> b = ImmutableSet.builder();
		for (int id : ids)
		{
			b.add(id);
		}
		return b.build();
	}
}
