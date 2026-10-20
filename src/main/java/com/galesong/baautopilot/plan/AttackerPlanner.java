package com.galesong.baautopilot.plan;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Attacker: match the collector's call, never hit with the wrong style (2 HP and a team point
 * each), stop as the call flips, and kill fighters/rangers in the order that ends the wave
 * soonest.
 */
class AttackerPlanner
{
	/** Ticks before a call change during which a new attack would likely land on the wrong call. */
	static final int HOLD_TICKS = 2;

	/**
	 * The kill pipeline (arrows for the call, hold as it flips, attack) is time critical while a
	 * fighter or ranger is up; with none up, the launcher, wave 10's spikes and gear for the next
	 * spawn are upkeep, and waiting is idle.
	 */
	List<Candidate> candidates(Snapshot s, Settings c)
	{
		List<Candidate> out = new ArrayList<>();
		BaCall call = s.getListenCall();
		boolean anyArrows = BaIds.ARROWS.stream().anyMatch(s::has) || BaIds.ARROWS.contains(s.getAmmoId());
		// Out-of-view ones stay targets: they're alive (recorded: hundreds left view and came back)
		List<PenanceView> targets = new ArrayList<>(s.alive(PenanceType.FIGHTER));
		targets.addAll(s.alive(PenanceType.RANGER));
		if (c.getAttackMethod() == AttackMethod.RANGED && !anyArrows && (!targets.isEmpty() || fightersRemain(s)))
		{
			out.add(Candidate.task(Tier.TIME_CRITICAL, "att.arrows", takeArrows(s, "all four arrow types",
				"Get arrows first, then take the western route behind the cannon hill")));
			return out;
		}
		Instruction opening = opening(s);
		if (opening != null)
		{
			out.add(Candidate.of(Tier.TIME_CRITICAL, "att.opening", opening));
			return out;
		}

		// Recorded: 372 of 507 arrow prompts came with no fighter or ranger alive. Gear only matters
		// while there is something to shoot
		if (targets.isEmpty())
		{
			support(s, call, c, anyArrows, out);
			return out;
		}

		if (call == null)
		{
			boolean attacking = targets.stream().anyMatch(p -> p.getNpcIndex() == s.getInteractingNpc());
			if (c.getAttackMethod() == AttackMethod.RANGED && !anyArrows)
			{
				out.add(Candidate.task(Tier.MAINTENANCE, "att.arrows",
					takeArrows(s, "all four arrow types", "Grab them now so you can switch the moment the collector calls")));
			}
			out.add(Candidate.of(Tier.IDLE, "att.wait", Instruction.builder()
				.step(Step.ATT_WAIT_CALL)
				.headline(attacking ? "Stop attacking: wait for the collector's call" : "Wait for the collector's call")
				.detail(attacking ? "Click a safe tile to disengage, then wait for a fresh call"
					: "Head to the marked tile by the fighter and ranger caves meanwhile")
				.target(Target.tile(ArenaTiles.attackerWait(s.getWave()), "Wait here"))
				.urgency(attacking ? Urgency.HIGH : Urgency.INFO)
				.build()));
			return out;
		}

		out.add(kill(s, c, call, targets));
		return out;
	}

	private static boolean fightersRemain(Snapshot s)
	{
		return !(s.getCleared().contains(PenanceType.FIGHTER) && s.getCleared().contains(PenanceType.RANGER))
			&& s.getFighterRangerKills() < WaveInfo.fightersTotal(s.getWave()) + WaveInfo.rangersTotal(s.getWave());
	}

	/** Open from the western dispenser corridor, then leave later combat movement alone. */
	private static Instruction opening(Snapshot s)
	{
		if (s.isAttackerOpeningComplete() || s.getPlayerTile() == null || s.getWaveTick() < 0 || s.getWaveTick() >= WaveInfo.TICKS_PER_CALL
			|| s.getFighterRangerKills() > 0 || s.getInteractingNpc() >= 0 || (s.getPlayerTile().getY() & 63) >= 37
			|| (s.getPlayerTile().getX() & 63) > 33) return null;
		boolean west = (s.getPlayerTile().getX() & 63) <= 19
			&& (s.getPlayerTile().getY() & 63) >= (s.getWave() == 10 ? 30 : 28);
		return Instruction.builder().step(Step.ATT_POSITION)
			.headline(west ? "Move north behind the west cannon hill" : "Go west around the cannon hill")
			.detail("Use the western approach before fighting to reduce pulling Penance Healers to the left side")
			.target(Target.tile(west ? ArenaTiles.collectorBehindHill(s.getWave()) : ArenaTiles.collectorWestApproach(s.getWave()), "West route"))
			.build();
	}

	/** One step of the kill pipeline: the called arrows, then hold as the call flips, then attack. */
	private Candidate kill(Snapshot s, Settings c, BaCall call, List<PenanceView> targets)
	{
		Instruction gear = gear(s, c, call);
		if (gear != null)
		{
			return Candidate.task(Tier.TIME_CRITICAL, gear.getStep() == Step.ATT_GET_ARROWS ? "att.arrows" : "att.equip", gear);
		}

		if (s.getTicksToCallChange() >= 0 && s.getTicksToCallChange() <= HOLD_TICKS)
		{
			return Candidate.of(Tier.TIME_CRITICAL, "att.hold", Instruction.builder()
				.step(Step.ATT_HOLD)
				.headline("Hold: the call changes now")
				.detail("Don't start a new attack until the collector's next call")
				.urgency(Urgency.HIGH)
				.build()).withDeadline(s.getTicksToCallChange());
		}

		PenanceType want = WaveInfo.attackerNext(s.getWave(), s.getFighterRangerKills());
		// With other attackers on the team, take your share (same answer on every client); help with
		// theirs only when none of yours is up
		TargetSplit.Slot slot = c.isSplitTargets() ? TargetSplit.slot(s) : TargetSplit.NONE;
		List<PenanceView> mine = targets;
		if (slot.of > 1)
		{
			mine = new ArrayList<>();
			for (PenanceView p : targets)
			{
				if (TargetSplit.owner(p.getNpcIndex(), slot.of) == slot.index)
				{
					mine.add(p);
				}
			}
		}
		boolean helping = mine.isEmpty();
		PenanceView pick = pick(s, helping ? targets : mine, want);
		String name = pick.getType() == PenanceType.FIGHTER ? "Penance Fighter" : "Penance Ranger";
		String why = slot.of > 1
			? (helping ? "Helping the other attacker: none of yours is up" : "Your share of " + slot.of + " attackers")
			: "Kill order this wave: " + order(s);
		return Candidate.of(Tier.TIME_CRITICAL, "att.attack", Instruction.builder()
			.step(Step.ATT_ATTACK)
			.headline(pick.isUnseen() ? "Head to the " + name + " (out of view)" : "Attack the " + name)
			.detail(styleHint(c, call) + " · " + why + (pick.isUnseen() ? ". Walk to where it was last seen" : ""))
			.target(Target.npc(pick.getNpcIndex(), pick.getTile()))
			.build());
	}

	private Instruction gear(Snapshot s, Settings c, BaCall call)
	{
		switch (c.getAttackMethod())
		{
			case RANGED:
				if (s.getAmmoId() == call.getItemId())
				{
					return null;
				}
				if (s.has(call.getItemId()))
				{
					return Instruction.builder()
						.step(Step.ATT_EQUIP)
						.headline("Equip " + call.getItemName())
						.detail("The call is " + call.getKeyword())
						.items(Planner.items(call.getItemId()))
						.urgency(Urgency.HIGH)
						.build();
				}
				return takeArrows(s, call.getItemName(), "The call is " + call.getKeyword());
			default:
				// Melee style and autocast can't be read reliably across weapons: the attack step names them
				return null;
		}
	}

	private static String styleHint(Settings c, BaCall call)
	{
		switch (c.getAttackMethod())
		{
			case MELEE:
				return call.getKeyword() + " combat style";
			case MAGIC:
				return call.getExtra() + " spells";
			default:
				return call.getItemName();
		}
	}

	private static Instruction takeArrows(Snapshot s, String what, String detail)
	{
		ObjectView machine = s.nearestObject(BaIds.ATTACKER_MACHINE);
		return Instruction.builder()
			.step(Step.ATT_GET_ARROWS)
			.headline("Take " + what + " from the attacker machine")
			.detail(detail)
			.target(machine == null ? Target.NONE : Target.object(machine.getId(), machine.getTile()))
			.build();
	}

	/**
	 * Stick with a target until it dies (recorded: the suggestion jumped 160 times while the previous
	 * target was still alive): the one you're attacking first, then the one already suggested, and
	 * only then a fresh pick by kill order, lowest HP, distance.
	 */
	private static PenanceView pick(Snapshot s, List<PenanceView> targets, PenanceType want)
	{
		for (int keep : new int[]{s.getInteractingNpc(), s.getLastTargetNpc()})
		{
			for (PenanceView p : targets)
			{
				if (keep >= 0 && p.getNpcIndex() == keep)
				{
					return p;
				}
			}
		}
		// A fresh pick prefers one you can see and hit now
		Comparator<PenanceView> byPreference = Comparator
			.comparing(PenanceView::isUnseen)
			.thenComparing((PenanceView p) -> p.getType() == want ? 0 : 1)
			.thenComparingDouble(PenanceView::hpFraction)
			.thenComparingInt(p -> s.walk(p.getTile()));
		return targets.stream().min(byPreference).orElse(targets.get(0));
	}

	private static String order(Snapshot s)
	{
		switch (s.getWave())
		{
			case 1:
				return "alternate, fighter first";
			case 2:
				return "two fighters, then ranger/fighter";
			case 3:
				return "alternate, ranger first";
			default:
				return "alternate, fighter first";
		}
	}

	/**
	 * Nothing to attack. Recorded: fighters and rangers are often all dead by mid-wave; the rest of
	 * the wave is runners and healers, which only the launcher lets an attacker hurt.
	 */
	private static void support(Snapshot s, BaCall call, Settings c, boolean anyArrows, List<Candidate> out)
	{
		boolean queenNext = s.getWave() == 10 && !s.isQueenAlive();
		boolean more = fightersRemain(s);
		if (queenNext && !more && s.count(BaIds.SPIKES) < QueenPlanner.SPIKES_WANTED)
		{
			ObjectView shroom = s.nearestObject(BaIds.MUSHROOMS);
			out.add(Candidate.task(Tier.MAINTENANCE, "att.spikes", Instruction.builder()
				.step(Step.ATT_SPIKES)
				.headline("Collect spikes from a petrified mushroom")
				.detail("Up to 8 before the queen spawns (" + s.count(BaIds.SPIKES) + " so far)")
				.target(shroom == null ? Target.NONE : Target.object(shroom.getId(), shroom.getTile()))
				.build()));
		}
		boolean loaded = s.getEggsRed() + s.getEggsGreen() > 0;
		boolean softTargets = !s.alive(PenanceType.RUNNER).isEmpty() || !s.alive(PenanceType.HEALER).isEmpty();
		ObjectView launcher = s.nearestObject(BaIds.LAUNCHERS);
		ObjectView inRange = TurretChoice.bestLauncher(s);
		if (loaded && inRange != null)
		{
			Instruction fire = CollectorPlanner.fire(s, inRange);
			out.add(Candidate.of(Tier.MAINTENANCE, "att.fire",
				fire.toBuilder().step(Step.ATT_FIRE).detail("Nothing to attack. " + fire.getDetail()).build()));
		}
		if (queenNext && !more)
		{
			// Wiki: gather around the healing pool before the Queen spawns, so the healer reaches everyone
			ObjectView spring = s.nearestObject(BaIds.SPRING);
			out.add(Candidate.of(Tier.IDLE, "att.gather", Instruction.builder()
				.step(Step.ATT_SUPPORT)
				.headline("Gather at the healer spring")
				.detail("Spikes ready. The Queen comes next: stay by the healer, not on the same tile")
				.target(spring == null ? Target.NONE : Target.object(spring.getId(), spring.getTile()))
				.urgency(Urgency.INFO)
				.build()));
			return;
		}
		if (more && c.getAttackMethod() == AttackMethod.RANGED && !anyArrows)
		{
			// Recorded 2026-10-16: a new attacker got no arrow prompt until the first spawn. Holding
			// nothing at all is the one gear gap worth fixing before anything is alive
			out.add(Candidate.task(Tier.MAINTENANCE, "att.arrows",
				takeArrows(s, "all four arrow types", "Before the first fighter or ranger spawns, so you can switch on every call")));
		}
		if (more)
		{
			boolean ready = call == null || c.getAttackMethod() != AttackMethod.RANGED || s.getAmmoId() == call.getItemId();
			out.add(Candidate.of(Tier.IDLE, "att.wait", Instruction.builder()
				.step(Step.ATT_SUPPORT)
				.headline("Next fighter or ranger is coming")
				.detail("Wait on the marked tile by their caves" + (ready ? "" : ". Have " + call.getItemName() + " on when it spawns"))
				.target(Target.tile(ArenaTiles.attackerWait(s.getWave()), "Wait here"))
				.urgency(Urgency.INFO)
				.build()));
			return;
		}
		out.add(Candidate.of(Tier.IDLE, "att.done", Instruction.builder()
			.step(Step.ATT_SUPPORT)
			.headline(softTargets ? "Kills done: stand by the launcher" : "Kills done")
			.detail(softTargets ? "Fire when the collector loads it" : "Wait for the wave to end")
			.target(softTargets && launcher != null ? Target.object(launcher.getId(), launcher.getTile()) : Target.NONE)
			.urgency(Urgency.INFO)
			.build()));
	}
}
