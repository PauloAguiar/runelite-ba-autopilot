package com.bautopilot.baautopilot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.model.WaveInfo;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Planner;
import com.bautopilot.baautopilot.plan.Settings;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.plan.RunnerState;
import com.bautopilot.baautopilot.plan.Target;
import com.bautopilot.baautopilot.plan.Urgency;
import com.bautopilot.baautopilot.plan.TargetSplit;
import com.bautopilot.baautopilot.plan.Tier;
import com.bautopilot.baautopilot.plan.TurretChoice;
import net.runelite.api.gameval.InterfaceID;
import com.bautopilot.baautopilot.state.GroundItemView;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.Snapshot;
import com.bautopilot.baautopilot.state.WaveStats;
import com.bautopilot.baautopilot.state.TeammateView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PlannerTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 40, 35);

	private final Planner planner = new Planner();
	// Existing stack-guide scenarios exercise the opt-in strategy; continuous mode has its own suite.
	private final Settings settings = Settings.builder().defenderMultiKill(true).build();

	private static Snapshot.SnapshotBuilder base(Role role, int wave)
	{
		return Snapshot.builder()
			.tick(1000)
			.inArena(true)
			.wave(wave)
			.role(role)
			.waveTick(10)
			.collectorOpeningComplete(true)
			.callPeriod(0)
			.ticksToCallChange(40)
			.listenFresh(true)
			.shoutDone(true)
			.hp(90)
			.maxHp(99)
			.freeSlots(20)
			.playerTile(ME)
			.objects(ImmutableList.of(
				new ObjectView(BaIds.ATTACKER_MACHINE, ArenaTiles.p(R, 50, 20)),
				new ObjectView(BaIds.DEFENDER_MACHINE, ArenaTiles.p(R, 51, 20)),
				new ObjectView(BaIds.HEALER_MACHINE, ArenaTiles.p(R, 52, 20)),
				new ObjectView(BaIds.HOPPERS[0], ArenaTiles.p(R, 40, 30)),
				new ObjectView(BaIds.LAUNCHERS[0], ArenaTiles.p(R, 41, 30)),
				new ObjectView(BaIds.POISON_CRATERS[0], ArenaTiles.p(R, 30, 30))));
	}

	private static PenanceView npc(int index, PenanceType type, int x, int y)
	{
		return PenanceView.builder().npcIndex(index).type(type).tile(ArenaTiles.p(R, x, y)).build();
	}

	private Instruction plan(Snapshot s)
	{
		return planner.plan(s, settings);
	}

	/** Healer feeding by priority (heal code Off). */
	private Instruction planPriority(Snapshot s)
	{
		return planner.plan(s, settings.toBuilder().healCodeSet(com.bautopilot.baautopilot.model.HealCodeSet.OFF).build());
	}

	// ------------------------------------------------------------------ common

	@Test
	public void owedCallComesFirstAndNamesTheNextStep()
	{
		Snapshot s = base(Role.ATTACKER, 3)
			.shoutCall(BaCall.RED_EGG)
			.shoutDone(false)
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.CALL, ins.getStep());
		assertEquals("Call Red!", ins.getHeadline());
		assertTrue(ins.getDetail().contains("Tell-red"));
		assertTrue(ins.getDetail().contains("Then:"));
		assertTrue(ins.getItems().containsAll(BaIds.HORNS));
		// The target belongs to the next step: the floor marks name that step, not "CALL"
		Instruction work = plan(s.toBuilder().shoutDone(true).build());
		assertEquals(work.getStep(), ins.getThen());
		assertEquals(work.getTarget(), ins.getTarget());
		// Later calls are made on the spot: no target to walk to
		Instruction later = plan(s.toBuilder().callPeriod(2).build());
		assertEquals(Step.CALL, later.getStep());
		assertEquals(Target.NONE, later.getTarget());
		assertTrue(later.getMarks().isEmpty());
	}

	@Test
	public void aCallATeammateAlreadyMadeIsSkipped()
	{
		Snapshot s = base(Role.ATTACKER, 3)
			.shoutCall(BaCall.GREEN_EGG)
			.shoutDone(false)
			.shoutCoveredBy("Player2")
			.listenCall(BaCall.ACCURATE)
			.build();
		assertTrue(plan(s).getStep() != Step.CALL);
		Instruction still = planner.plan(s, Settings.builder().skipCoveredCalls(false).build());
		assertEquals(Step.CALL, still.getStep());
	}

	@Test
	public void aWrongCallAsksForTheRightOne()
	{
		// Recorded: Tell-red on a green call was taken as done
		Snapshot s = base(Role.ATTACKER, 3)
			.shoutCall(BaCall.GREEN_EGG)
			.shoutDone(false)
			.shoutWrong(BaCall.RED_EGG)
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.CALL, ins.getStep());
		assertEquals("Wrong call! Call Green!", ins.getHeadline());
		assertTrue(ins.getDetail().contains("You said Red"));
		assertTrue(ins.getDetail().contains("Tell-green"));
	}

	@Test
	public void lowHpRetreatsExceptHealer()
	{
		// Low HP is shown with the step, not instead of it (reported 2026-10-17: retreat pulled the
		// player off an egg dip about to finish)
		Snapshot s = base(Role.ATTACKER, 5).hp(20).build();
		Instruction low = plan(s);
		assertTrue(low.getStep() != Step.RETREAT);
		assertTrue(low.getAdvice(), low.getAdvice().startsWith("Low HP: get onto a launcher hill"));
		// Close to dying (under half the threshold): retreat takes over
		Instruction critical = plan(s.toBuilder().hp(10).build());
		assertEquals(Step.RETREAT, critical.getStep());
		assertEquals(Target.Kind.OBJECT, critical.getTarget().getKind());

		Snapshot healer = base(Role.HEALER, 5).hp(20).build();
		assertTrue(plan(healer).getStep() != Step.RETREAT);
	}

	@Test
	public void outsideTheArenaIsSilent()
	{
		Snapshot s = Snapshot.builder().inArena(false).build();
		assertEquals(Step.NOT_IN_GAME, plan(s).getStep());
		Snapshot lobby = Snapshot.builder().inLobby(true).wave(3).role(Role.DEFENDER).build();
		Instruction ins = plan(lobby);
		assertEquals(Step.LOBBY, ins.getStep());
		assertEquals("Wave 4 next", ins.getHeadline());
		assertEquals("Join a team", plan(Snapshot.builder().inLobby(true).build()).getHeadline());
	}

	// ------------------------------------------------------------------ attacker

	@Test
	public void attackerEquipsTheCalledArrow()
	{
		Snapshot s = base(Role.ATTACKER, 4)
			.listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.CONTROLLED.getItemId())
			.inventory(ImmutableMap.of(BaCall.ACCURATE.getItemId(), 50))
			.penance(ImmutableList.of(npc(1, PenanceType.FIGHTER, 30, 40)))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.ATT_EQUIP, ins.getStep());
		assertTrue(ins.getItems().contains(BaCall.ACCURATE.getItemId()));
	}

	@Test
	public void attackerFetchesMissingArrows()
	{
		Snapshot s = base(Role.ATTACKER, 4).listenCall(BaCall.ACCURATE)
			.penance(ImmutableList.of(npc(1, PenanceType.FIGHTER, 30, 40))).build();
		Instruction ins = plan(s);
		assertEquals(Step.ATT_GET_ARROWS, ins.getStep());
		assertEquals(BaIds.ATTACKER_MACHINE, ins.getTarget().getId());
	}

	@Test
	public void attackerHoldsAsTheCallFlips()
	{
		Snapshot s = base(Role.ATTACKER, 4)
			.listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.ACCURATE.getItemId())
			.ticksToCallChange(1)
			.penance(ImmutableList.of(npc(1, PenanceType.FIGHTER, 30, 40)))
			.build();
		assertEquals(Step.ATT_HOLD, plan(s).getStep());
	}

	@Test
	public void attackerFollowsTheWaveKillOrder()
	{
		Snapshot.SnapshotBuilder b = base(Role.ATTACKER, 4)
			.listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.ACCURATE.getItemId())
			.penance(ImmutableList.of(npc(1, PenanceType.RANGER, 30, 40), npc(2, PenanceType.FIGHTER, 31, 40)));
		Instruction ins = plan(b.build());
		assertEquals(Step.ATT_ATTACK, ins.getStep());
		assertEquals(2, ins.getTarget().getId());
	}

	@Test
	public void attackerFillsDowntimeWithTheLauncher()
	{
		// Recorded: fighters and rangers were all dead by mid-wave; the attacker idled for the rest
		Snapshot.SnapshotBuilder s = base(Role.ATTACKER, 2)
			.listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.ACCURATE.getItemId())
			.eggsRed(2)
			.fighterRangerKills(4)
			.penance(ImmutableList.of(npc(7, PenanceType.RUNNER, 45, 40)));
		Instruction fire = plan(s.build());
		assertEquals(Step.ATT_FIRE, fire.getStep());
		assertEquals(BaIds.LAUNCHERS[0], fire.getTarget().getId());

		// Launcher empty, more fighters/rangers to come: wait for them
		Instruction next = plan(s.eggsRed(0).build());
		assertEquals("Next fighter or ranger is coming", next.getHeadline());

		// All 9 of wave 2's fighters and rangers dead: stand by the launcher
		Instruction done = plan(s.fighterRangerKills(9).build());
		assertEquals("Kills done: stand by the launcher", done.getHeadline());
		assertEquals(Target.Kind.OBJECT, done.getTarget().getKind());
	}

	@Test
	public void turretButtonPrefersRunnersThenHealersRedBeforeGreen()
	{
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 4).eggsRed(2).eggsGreen(3)
			.penance(ImmutableList.of(npc(1, PenanceType.RUNNER, 40, 40), npc(2, PenanceType.HEALER, 48, 40)));
		assertEquals(InterfaceID.BarbassaultTurret.EXPLOSIVE_RUNNER, TurretChoice.pick(s.build()));
		// Green poison ticks every 3 s on runners, every 18 s on healers
		assertEquals(InterfaceID.BarbassaultTurret.POISON_RUNNER, TurretChoice.pick(s.eggsRed(0).build()));
		assertEquals(InterfaceID.BarbassaultTurret.POISON_HEALER, TurretChoice.pick(s.eggsRed(0)
			.penance(ImmutableList.of(npc(2, PenanceType.HEALER, 48, 40))).build()));
		s.penance(ImmutableList.of(npc(1, PenanceType.RUNNER, 40, 40), npc(2, PenanceType.HEALER, 48, 40)));
		assertEquals(TurretChoice.NONE, TurretChoice.pick(s.eggsGreen(0).build()));
		assertEquals(InterfaceID.BarbassaultTurret.OMEGA_QUEEN,
			TurretChoice.pick(base(Role.DEFENDER, 10).queenAlive(true).eggsOmega(1).build()));
	}

	@Test
	public void attackerKnowsWhatToWaitForDuringTheQueen()
	{
		// Recorded wave 10: the attacker waited 3 minutes with 11 spikes, tried spikes on a yellow egg,
		// attacked the Queen and her spawns, then died; the chain had stalled at the healer
		WorldPoint spring = ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 20, 20);
		PenanceView queen = PenanceView.builder().npcIndex(99).type(PenanceType.QUEEN)
			.tile(ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 30, 30)).spawnTick(1000 - 60).build();
		Snapshot.SnapshotBuilder s = base(Role.ATTACKER, 10)
			.queenAlive(true)
			.inventory(ImmutableMap.of(BaIds.SPIKES, 11))
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, spring)))
			.penance(ImmutableList.of(queen));
		Instruction wait = plan(s.build());
		assertEquals("Wait for the healer's poisoned egg", wait.getHeadline());
		assertTrue(wait.getDetail().contains("POISONED egg, not a yellow one"));
		assertTrue(wait.getDetail().contains("Don't attack the Queen"));
		assertTrue(wait.getDetail().contains("No omega yet"));
		assertEquals(BaIds.SPRING, wait.getTarget().getId());

		// Low HP with the Queen out: the spring, not a launcher hill
		Instruction low = plan(s.hp(15).build());
		assertEquals(Step.RETREAT, low.getStep());
		assertEquals(BaIds.SPRING, low.getTarget().getId());

		// No calls while the Queen is out
		s.hp(90);
		assertTrue(plan(s.shoutCall(BaCall.RED_EGG).shoutDone(false).build()).getStep() != Step.CALL);

		// Short of spikes while waiting: get more
		Instruction more = plan(s.inventory(ImmutableMap.of(BaIds.SPIKES, 3)).build());
		assertEquals("Get spikes while you wait (3/8)", more.getHeadline());

		// Holding the poisoned egg: spike it, even with a call owed
		Instruction spike = plan(s.hp(90).shoutCall(BaCall.RED_EGG).shoutDone(false)
			.inventory(ImmutableMap.of(BaIds.SPIKES, 11, BaIds.EGG_POISONED, 1)).build());
		assertEquals(Step.QUEEN_SPIKE, spike.getStep());
	}

	@Test
	public void spikedEggsGoToTheDefenderByName()
	{
		// Recorded: a spiked egg used on a non-defender
		Snapshot s = base(Role.ATTACKER, 10)
			.queenAlive(true)
			.inventory(ImmutableMap.of(BaIds.EGG_SPIKED, 2))
			.team(ImmutableMap.of("Zezima", Role.COLLECTOR, "Lynx Titan", Role.DEFENDER))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.QUEEN_PASS, ins.getStep());
		assertEquals("Pass 2 spiked eggs to Lynx Titan", ins.getHeadline());
		assertEquals(Target.Kind.PLAYER, ins.getTarget().getKind());
		assertEquals("Lynx Titan", ins.getTarget().getLabel());
	}

	@Test
	public void queenEggStacksAreCountedAndOmegasGoOneAtATime()
	{
		// Recorded second Queen fight: up to 4 poisoned and 5 spiked eggs at once, one-egg method
		Snapshot.SnapshotBuilder att = base(Role.ATTACKER, 10).queenAlive(true);
		Instruction spike = plan(att.inventory(ImmutableMap.of(BaIds.EGG_POISONED, 4, BaIds.SPIKES, 2)).build());
		assertEquals("Spike your 4 poisoned eggs", spike.getHeadline());
		assertTrue(spike.getDetail().contains("get 2 more"));
		Instruction none = plan(att.inventory(ImmutableMap.of(BaIds.EGG_POISONED, 2)).build());
		assertEquals("Get spikes (need 2)", none.getHeadline());
		Instruction pass = plan(att.inventory(ImmutableMap.of(BaIds.EGG_SPIKED, 5)).build());
		assertEquals("Pass 5 spiked eggs to the defender", pass.getHeadline());

		Snapshot.SnapshotBuilder col = base(Role.COLLECTOR, 10).queenAlive(true)
			.playerTile(ArenaTiles.p(R, 40, 28))
			.inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 3));
		// Stacking at the cannon: one omega at a time while the team spams Fire
		Settings stack = settings.toBuilder().queenStack(true).build();
		Instruction one = planner.plan(col.build(), stack);
		assertEquals("Load ONE omega egg", one.getHeadline());
		assertTrue(one.getDetail().contains("3 left"));
		Instruction hold = planner.plan(col.eggsOmega(1).build(), stack);
		assertEquals("Hold: an omega is in the launcher", hold.getHeadline());
		// Explicit bulk-loading opt-out: load them all and fire yourself.
		Instruction all = planner.plan(col.eggsOmega(0).build(), settings.toBuilder().queenSingleLoads(false).build());
		assertEquals("Load your 3 omega eggs", all.getHeadline());
	}

	@Test
	public void spikesComeFromTheClickableMushroomOnly()
	{
		// Recorded: Get-Spikes is on 20250; the 20252 group next to each is decoration
		WorldPoint real = ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 30, 30);
		WorldPoint decoration = ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 39, 35);
		Snapshot s = base(Role.ATTACKER, 10)
			.fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
			.listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.ACCURATE.getItemId())
			.objects(ImmutableList.of(new ObjectView(20252, decoration), new ObjectView(20250, real)))
			.playerTile(ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 40, 35))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.ATT_SPIKES, ins.getStep());
		assertEquals(20250, ins.getTarget().getId());
	}

	@Test
	public void noGearFoodOrRepairOnceTheirTargetsAreGone()
	{
		// Recorded: 372 of 507 arrow prompts came with no fighter or ranger alive
		Snapshot.SnapshotBuilder att = base(Role.ATTACKER, 2).listenCall(BaCall.ACCURATE).ammoId(BaCall.CONTROLLED.getItemId());
		Instruction between = plan(att.fighterRangerKills(3).build());
		assertEquals("Next fighter or ranger is coming", between.getHeadline());
		assertTrue(between.getDetail().contains("Have Field arrows on"));
		// A real tile to wait on, not "north-west"
		assertEquals(ArenaTiles.attackerWait(2), between.getTarget().getTile());
		Instruction done = plan(att.fighterRangerKills(9).build());
		assertTrue(done.getStep() != Step.ATT_EQUIP && done.getStep() != Step.ATT_GET_ARROWS);
		assertTrue(!done.getDetail().contains("arrows"));

		// No arrows at all with spawns still to come (recorded: healer -> attacker, no prompt)
		Snapshot empty = base(Role.ATTACKER, 2).listenCall(BaCall.ACCURATE).build();
		assertEquals(Step.ATT_GET_ARROWS, plan(empty).getStep());
		assertTrue(plan(empty.toBuilder().fighterRangerKills(9).build()).getStep() != Step.ATT_GET_ARROWS);

		Snapshot healer = base(Role.HEALER, 2).listenCall(BaCall.POISON_WORMS).healersKilled(3).build();
		assertTrue(plan(healer).getStep() != Step.HEAL_TAKE_FOOD);

		Snapshot defender = base(Role.DEFENDER, 2).listenCall(BaCall.BAIT_TOFU).waveTick(200)
			.trapCharges(0).runnersKilled(3).build();
		assertTrue(plan(defender).getStep() != Step.DEF_REPAIR);
	}

	@Test
	public void launcherChoiceRespectsEachLaunchersRange()
	{
		// Recorded: "no targetable Penance Runners within firing range" at 16 tiles, hits up to 15
		WorldPoint west = ArenaTiles.p(R, 29, 42);
		WorldPoint east = ArenaTiles.p(R, 48, 42);
		Snapshot.SnapshotBuilder s = base(Role.ATTACKER, 3)
			.listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId())
			.fighterRangerKills(11)
			.eggsRed(3)
			.playerTile(ArenaTiles.p(R, 30, 42))
			.objects(ImmutableList.of(new ObjectView(20133, west), new ObjectView(20133, east)))
			// Runner 17 tiles east of the west launcher, 2 from the east one
			.penance(ImmutableList.of(npc(7, PenanceType.RUNNER, 46, 40)));
		Instruction fire = plan(s.build());
		assertEquals(Step.ATT_FIRE, fire.getStep());
		assertEquals(east, fire.getTarget().getTile());
		assertTrue(fire.getDetail(), fire.getDetail().contains("east"));

		// In the west launcher's aiming window that runner is out of range: nothing to press
		assertEquals(TurretChoice.NONE, TurretChoice.pick(s.build(), west));
		assertEquals(InterfaceID.BarbassaultTurret.EXPLOSIVE_RUNNER, TurretChoice.pick(s.build(), east));

		// A dying runner can't be targeted
		Snapshot dying = s.penance(ImmutableList.of(PenanceView.builder().npcIndex(7).type(PenanceType.RUNNER)
			.tile(ArenaTiles.p(R, 46, 40)).dying(true).build())).build();
		assertEquals(TurretChoice.NONE, TurretChoice.pick(dying, east));
	}

	@Test
	public void attackTargetSticksUntilItDies()
	{
		Snapshot.SnapshotBuilder s = base(Role.ATTACKER, 4)
			.listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId())
			.penance(ImmutableList.of(
				PenanceView.builder().npcIndex(1).type(PenanceType.FIGHTER).tile(ArenaTiles.p(R, 30, 40)).healthRatio(20).healthScale(30).build(),
				PenanceView.builder().npcIndex(2).type(PenanceType.FIGHTER).tile(ArenaTiles.p(R, 31, 40)).healthRatio(5).healthScale(30).build()));
		// Fresh pick: the weaker fighter
		assertEquals(2, plan(s.build()).getTarget().getId());
		// Already suggested #1: keep it even though #2 is weaker now
		assertEquals(1, plan(s.lastTargetNpc(1).build()).getTarget().getId());
		// You're attacking #2 yourself: follow you
		assertEquals(2, plan(s.lastTargetNpc(1).interactingNpc(2).build()).getTarget().getId());
		// The kept target is gone: pick again
		assertEquals(2, plan(s.lastTargetNpc(9).interactingNpc(-1).build()).getTarget().getId());
	}

	@Test
	public void twoPluginAttackersSplitTargetsTheSameWayOnBothClients()
	{
		java.util.List<PenanceView> fr = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			fr.add(npc(100 + i, i % 2 == 0 ? PenanceType.FIGHTER : PenanceType.RANGER, 30 + i, 40));
		}
		ImmutableMap<String, Role> team = ImmutableMap.of("Zezima", Role.ATTACKER, "Lynx Titan", Role.ATTACKER,
			"Durial321", Role.HEALER);
		Snapshot.SnapshotBuilder base = base(Role.ATTACKER, 5).listenCall(BaCall.ACCURATE)
			.ammoId(BaCall.ACCURATE.getItemId()).team(team).penance(ImmutableList.copyOf(fr));

		// Each client: who owns what, as seen by Zezima and by Lynx Titan
		Snapshot zez = base.selfName("Zezima").build();
		Snapshot lynx = base.selfName("Lynx Titan").build();
		TargetSplit.Slot a = TargetSplit.slot(zez);
		TargetSplit.Slot b = TargetSplit.slot(lynx);
		assertEquals(2, a.of);
		assertTrue(a.index != b.index);
		assertEquals(a.attackers, b.attackers);

		int zTarget = plan(zez).getTarget().getId();
		int lTarget = plan(lynx).getTarget().getId();
		assertTrue(zTarget != lTarget);
		assertEquals(a.index, TargetSplit.owner(zTarget, 2));
		assertEquals(b.index, TargetSplit.owner(lTarget, 2));

		// Both shares get used: the hash spreads consecutive NPC indexes
		long zezimaShare = fr.stream().filter(p -> TargetSplit.owner(p.getNpcIndex(), 2) == a.index).count();
		assertTrue(zezimaShare >= 2 && zezimaShare <= 6);

		// Only the other attacker's targets left: help with them
		java.util.List<PenanceView> theirs = new java.util.ArrayList<>();
		for (PenanceView p : fr)
		{
			if (TargetSplit.owner(p.getNpcIndex(), 2) == b.index)
			{
				theirs.add(p);
			}
		}
		Instruction help = plan(base.selfName("Zezima").penance(ImmutableList.copyOf(theirs)).build());
		assertEquals(Step.ATT_ATTACK, help.getStep());
		assertTrue(help.getDetail().contains("Helping"));

		// One attacker on the team, or no team list: no split
		assertEquals(1, TargetSplit.slot(base.team(ImmutableMap.of("Zezima", Role.ATTACKER)).build()).of);
		assertEquals(1, TargetSplit.slot(base.team(ImmutableMap.of()).build()).of);
	}

	@Test
	public void healerKeepsTheTeamUpThroughTheQueen()
	{
		WorldPoint springTile = ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 20, 20);
		Snapshot.SnapshotBuilder s = base(Role.HEALER, 10).queenAlive(true)
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, springTile), new ObjectView(BaIds.HEALER_MACHINE, ArenaTiles.p(R, 52, 20))))
			.inventory(ImmutableMap.of(BaIds.VIAL_FULL, 1))
			.teammates(ImmutableList.of(new TeammateView("Zezima", 80, 99), new TeammateView("Lynx", 60, 99)));

		// Someone below 70%: heal the lowest, with the vial's doses shown
		Instruction heal = plan(s.build());
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		assertEquals("Heal Lynx (61%)", heal.getHeadline());
		assertTrue(heal.getDetail().contains("Vial 4/4"));

		// 60 HP is safe enough for egg progress; a dangerous HP level still interrupts it.
		Snapshot.SnapshotBuilder egg = s.inventory(ImmutableMap.of(BaIds.VIAL_FULL, 1, BaIds.EGG_YELLOW, 1));
		assertEquals(Step.QUEEN_DUNK, plan(egg.build()).getStep());
		Instruction critical = plan(egg.teammates(ImmutableList.of(new TeammateView("Lynx", 20, 99))).build());
		assertEquals(Step.HEAL_TEAMMATE, critical.getStep());
		assertEquals(Urgency.HIGH, critical.getUrgency());

		// Everyone fine and the vial half used: top it up at the spring
		Instruction topUp = plan(base(Role.HEALER, 10).queenAlive(true)
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, springTile)))
			.inventory(ImmutableMap.of(net.runelite.api.gameval.ItemID.BARBASSAULT_VIAL_04_HALF, 1))
			.teammates(ImmutableList.of(new TeammateView("Lynx", 95, 99))).build());
		assertEquals("Top up the vial (2/4)", topUp.getHeadline());

		// Your own HP low: drink when you can, alongside the step; first when close to dying
		Instruction self = plan(base(Role.HEALER, 5).hp(20).listenCall(BaCall.POISON_TOFU)
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, springTile)))
			.inventory(ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 5))
			.penance(ImmutableList.of(healer(1, 1, 0, -1))).build());
		assertEquals(Step.HEAL_FEED, self.getStep());
		assertTrue(self.getAdvice(), self.getAdvice() != null && self.getAdvice().startsWith("Drink from the spring"));
		Instruction dying = plan(s.hp(10).build());
		assertEquals(Step.RETREAT, dying.getStep());
		assertTrue(dying.getHeadline().startsWith("Drink from the spring"));
		s.hp(90);

		// Dry vial and someone hurt: refill first
		Instruction refill = plan(s.inventory(ImmutableMap.of(BaIds.VIAL_EMPTY, 1))
			.teammates(ImmutableList.of(new TeammateView("Lynx", 50, 99))).build());
		assertEquals(Step.HEAL_FILL_VIAL, refill.getStep());
		assertEquals(springTile, refill.getTarget().getTile());
		assertTrue(refill.getItems().contains(BaIds.VIAL_EMPTY));
	}

	@Test
	public void survivalComesBeforeCallsForEveryRole()
	{
		// Any death ends the game: a dying teammate beats an owed call for the healer
		Snapshot healer = base(Role.HEALER, 4)
			.shoutCall(BaCall.BAIT_TOFU).shoutDone(false)
			.listenCall(BaCall.POISON_WORMS)
			.inventory(ImmutableMap.of(BaIds.VIAL_FULL, 1))
			.teammates(ImmutableList.of(new TeammateView("Lynx", 10, 99)))
			.build();
		Instruction heal = plan(healer);
		assertEquals(Step.HEAL_TEAMMATE, heal.getStep());
		assertEquals(Urgency.HIGH, heal.getUrgency());

		// The healer's own HP counts too, outside the Queen as well
		Snapshot lowHealer = healer.toBuilder().teammates(ImmutableList.of()).hp(20)
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, ArenaTiles.p(R, 20, 20)))).build();
		assertTrue(plan(lowHealer).getAdvice().startsWith("Drink from the spring"));
		assertTrue(plan(lowHealer.toBuilder().hp(10).build()).getHeadline().startsWith("Drink from the spring"));

		// Non-healers are warned earlier during the Queen: 40% is fine normally, not with her out
		Snapshot att = base(Role.ATTACKER, 10).hp(40).build();
		assertTrue(plan(att).getAdvice() == null);
		assertTrue(plan(att.toBuilder().queenAlive(true).build()).getAdvice() != null);
		assertEquals(Step.RETREAT, plan(att.toBuilder().queenAlive(true).hp(20).build()).getStep());
	}

	@Test
	public void healerFeedTargetYieldsToWeakerPoisonAndStopsWhenFed()
	{
		Snapshot.SnapshotBuilder b = base(Role.HEALER, 6)
			.listenCall(BaCall.POISON_TOFU)
			.inventory(ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 5));
		// Both at half HP with decayed poison; #2 is weaker and preempts a remembered #1.
		ImmutableList<PenanceView> decayed = ImmutableList.of(half(healer(1, 1, 1, 970)), half(healer(2, 2, 1, 940)));
		assertEquals(2, planPriority(b.penance(decayed).build()).getTarget().getId());
		assertEquals(2, planPriority(b.penance(decayed).lastTargetNpc(1).build()).getTarget().getId());
		// #1 just got its food: move on to #2
		ImmutableList<PenanceView> fed = ImmutableList.of(half(healer(1, 1, 2, 999)), half(healer(2, 2, 1, 940)));
		assertEquals(2, planPriority(b.penance(fed).lastTargetNpc(1).build()).getTarget().getId());
		// Both just fed (poison at 4): nobody is fed again, not even the last one (reported
		// 2026-10-17: "feed the same healer over and over even after I already fed them")
		ImmutableList<PenanceView> fresh = ImmutableList.of(healer(1, 1, 3, 997), healer(2, 2, 3, 997));
		assertTrue(planPriority(b.penance(fresh).lastTargetNpc(2).build()).getStep() != Step.HEAL_FEED);
	}

	@Test
	public void theLauncherIsTheTurretBottomObject()
	{
		// Recorded Shoot clicks all targeted object 20133
		assertTrue(BaIds.contains(BaIds.LAUNCHERS, 20133));
	}

	// ------------------------------------------------------------------ collector

	@Test
	public void collectorDestroysBlueEggsOutsideABlueCall()
	{
		Snapshot s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.GREEN_EGG)
			.inventory(ImmutableMap.of(BaIds.EGG_BLUE, 2))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.COL_BLUE, ins.getStep());
		assertEquals("Destroy your blue eggs", ins.getHeadline());
		assertTrue(ins.getDetail().contains("Shift-click"));
		assertTrue(!ins.getDetail().contains("Converter"));
		assertTrue(ins.getItems().contains(BaIds.EGG_BLUE));
	}

	@Test
	public void collectorKeepsCollectingBlueDuringABlueCall()
	{
		// Reported in game: the plugin told the collector to convert blue eggs while blue was the call
		Snapshot s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.BLUE_EGG)
			.inventory(ImmutableMap.of(BaIds.EGG_BLUE, 2))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_BLUE, ArenaTiles.p(R, 36, 35), 1, false, 990)))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.COL_COLLECT, ins.getStep());
		assertEquals(BaIds.EGG_BLUE, ins.getTarget().getId());

		// Nothing left to pick up: now get rid of them, they already counted
		Instruction done = plan(s.toBuilder().groundItems(ImmutableList.of()).build());
		assertEquals(Step.COL_BLUE, done.getStep());
		assertTrue(done.getDetail().contains("already counted"));
	}

	@Test
	public void collectorSkipsEggsThatWillVanishFirst()
	{
		// Recorded: untouched eggs disappear about 200 ticks after they drop
		WorldPoint far = ArenaTiles.p(R, 10, 35);
		WorldPoint near = ArenaTiles.p(R, 38, 35);
		Snapshot s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.RED_EGG)
			.groundItems(ImmutableList.of(
				new GroundItemView(BaIds.EGG_RED, far, 1, false, 1000 - 190),
				new GroundItemView(BaIds.EGG_RED, near, 1, false, 1000 - 120)))
			.build();
		assertEquals(near, plan(s).getTarget().getTile());

		Snapshot onlyDying = s.toBuilder()
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, far, 1, false, 1000 - 195)))
			.build();
		assertEquals(Step.COL_FOLLOW, plan(onlyDying).getStep());
	}

	@Test
	public void collectorIsToldWhenTheHopperIsFull()
	{
		// Recorded: 41 "You can't load any more ... eggs" with that colour already at 5/5
		Snapshot s = base(Role.COLLECTOR, 5)
			.listenCall(BaCall.RED_EGG)
			.eggsRed(5)
			.inventory(ImmutableMap.of(BaIds.EGG_RED, 6))
			.penance(ImmutableList.of(npc(9, PenanceType.RUNNER, 45, 40)))
			.build();
		Instruction fire = plan(s);
		assertEquals(Step.COL_FOLLOW, fire.getStep());
		assertEquals("Wait near the attackers for Red eggs", fire.getHeadline());
		assertEquals(Target.Kind.NONE, fire.getTarget().getKind());
		assertTrue(fire.getDetail().contains("Red hopper is full (5/5)"));

		// With red eggs still on the ground: keep collecting, and say why loading won't work
		Instruction collect = plan(s.toBuilder()
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 38, 35), 1, false, 990)))
			.build());
		assertEquals(Step.COL_COLLECT, collect.getStep());
		assertTrue(collect.getDetail().contains("Keep collecting: they still count"));
	}

	@Test
	public void collectorCountsTheBagWhenDecidingToLoad()
	{
		// Recorded: the first 2 eggs go in the collection bag, which the inventory doesn't show
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 3)
			.listenCall(BaCall.BLUE_EGG)
			.inventory(ImmutableMap.of(BaIds.EGG_RED, 3))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_BLUE, ArenaTiles.p(R, 38, 35), 1, false, 990)));
		assertEquals(Step.COL_COLLECT, plan(s.build()).getStep());
		Instruction load = plan(s.bagRed(2).build());
		assertEquals(Step.COL_LOAD, load.getStep());
		assertTrue(load.getDetail(), load.getDetail().contains("Bag: 2 red"));
	}

	@Test
	public void collectorDoesNotInterruptRedCollectionForHopperLevelsOrShotRate()
	{
		// Hopper at (40, 30); you're 10 tiles off with an egg 4 tiles away
		WorldPoint field = ArenaTiles.p(R, 34, 40);
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 3)
			.listenCall(BaCall.RED_EGG)
			.playerTile(field)
			.bagRed(1)
			.penance(ImmutableList.of(npc(4, PenanceType.RUNNER, 45, 45)))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 30, 40), 1, false, 990)));

		// Launcher dry, but one egg isn't worth the walk: keep collecting
		assertEquals(Step.COL_COLLECT, plan(s.build()).getStep());
		// An empty hopper alone still does not establish an available shooter.
		Instruction dry = plan(s.bagRed(3).build());
		assertEquals(Step.COL_COLLECT, dry.getStep());

		// Loaded, but fired fast enough to run dry before you're back
		Snapshot.SnapshotBuilder firing = s.eggsRed(2).launcherShotsPerTick(0.2);
		Instruction soon = plan(firing.build());
		assertEquals(Step.COL_COLLECT, soon.getStep());
		// Nobody firing: keep collecting
		assertEquals(Step.COL_COLLECT, plan(firing.launcherShotsPerTick(0).build()).getStep());

		// A remembered loading trip yields to this red collecting window.
		WorldPoint hopper = ArenaTiles.p(R, 40, 30);
		assertEquals(Step.COL_COLLECT, plan(firing.lastTargetTile(hopper).build()).getStep());

		// Even passing the hopper does not cut the red window short.
		Snapshot passing = base(Role.COLLECTOR, 3).listenCall(BaCall.RED_EGG)
			.playerTile(ArenaTiles.p(R, 40, 33)).bagRed(1)
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 30, 40), 1, false, 990)))
			.build();
		Instruction top = plan(passing);
		assertEquals(Step.COL_COLLECT, top.getStep());

		// No eggs on the ground after the first kill: more will drop, so wait for them
		Snapshot.SnapshotBuilder empty = base(Role.COLLECTOR, 3).listenCall(BaCall.RED_EGG).playerTile(field).bagRed(2)
			.fighterRangerKills(1);
		assertTrue(plan(empty.build()).getStep() != Step.COL_LOAD);
		// No more attackers' eggs and no remaining cannon target: no pointless supply trip.
		Instruction last = plan(empty.fighterRangerKills(WaveInfo.fightersTotal(3) + WaveInfo.rangersTotal(3)).build());
		assertTrue(last.getStep() != Step.COL_LOAD);

		// Only eggs that fit count: a full red hopper leaves nothing to load
		assertEquals(Step.COL_COLLECT, plan(s.bagRed(5).eggsRed(5).lastTargetTile(null).build()).getStep());
	}

	@Test
	public void collectorGetsReadyForTheQueenOnceWaveTenIsCleared()
	{
		// Recorded 2026-10-16: "pick up", "load", "hopper full" for 64 ticks with nothing left alive
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 10).listenCall(BaCall.RED_EGG)
			.objects(ImmutableList.of(new ObjectView(BaIds.SPRING, ArenaTiles.p(R, 45, 45)),
				new ObjectView(BaIds.HOPPERS[0], ArenaTiles.p(R, 40, 30))))
			.bagRed(2)
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 41, 36), 1, false, 990)))
			.fighterRangerKills(WaveInfo.fightersTotal(10) + WaveInfo.rangersTotal(10))
			.runnersKilled(WaveInfo.runnersTotal(10))
			.healersKilled(WaveInfo.healersTotal(10));
		Instruction ready = plan(s.build());
		assertEquals(Step.COL_LOAD, ready.getStep());
		ready = plan(s.eggsRed(1).build());
		assertEquals(Step.COL_QUEEN_READY, ready.getStep());
		assertEquals(ArenaTiles.queenCollectorWatch(), ready.getTarget().getTile());

		// A runner still out: the launcher still matters
		assertTrue(plan(s.penance(ImmutableList.of(npc(4, PenanceType.RUNNER, 45, 45))).build()).getStep()
			!= Step.COL_QUEEN_READY);
	}

	@Test
	public void collectorSeesItsEggsTowardTheSixtyEggPointCap()
	{
		com.bautopilot.baautopilot.state.Performance perf = new com.bautopilot.baautopilot.state.Performance();
		perf.tick(10, 3, Role.COLLECTOR, 0, 10, true, false, false);
		perf.chat("You put the egg in the bag.");
		WaveStats w = perf.current();
		Snapshot s = base(Role.COLLECTOR, 3).listenCall(BaCall.RED_EGG).playerTile(ArenaTiles.p(R, 30, 45))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 30, 40), 1, false, 990)))
			.waveStats(w).build();
		assertTrue(plan(s).getDetail(), plan(s).getDetail().contains("1/60 for points"));
	}

	@Test
	public void collectorLeavesOrdinaryShootingToTeammates()
	{
		WorldPoint atLauncher = ArenaTiles.p(R, 41, 31);
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 3)
			.listenCall(BaCall.BLUE_EGG)
			.playerTile(atLauncher)
			.eggsRed(3)
			.penance(ImmutableList.of(npc(4, PenanceType.RUNNER, 45, 45), npc(5, PenanceType.HEALER, 48, 40)))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_BLUE, ArenaTiles.p(R, 25, 40), 1, false, 990)));
		Instruction fire = plan(s.build());
		assertEquals(Step.COL_FOLLOW, fire.getStep());
		assertTrue(fire.getPriorities().stream().filter(p -> "col.fire".equals(p.getTask())).allMatch(p -> p.getScore() < fire.getScore()));

		// A green egg close by: collect it first
		Instruction collect = plan(s.listenCall(BaCall.GREEN_EGG).groundItems(ImmutableList.of(
			new GroundItemView(BaIds.EGG_GREEN, ArenaTiles.p(R, 43, 33), 1, false, 990))).build());
		assertEquals(Step.COL_COLLECT, collect.getStep());

		// A lull during green is not a reason to leave for a non-escaping target.
		Instruction idle = plan(s.groundItems(ImmutableList.of()).playerTile(ME).build());
		assertEquals(Step.COL_FOLLOW, idle.getStep());
	}

	@Test
	public void collectorTargetStaysPutAndRiskyEggsStillShow()
	{
		WorldPoint a = ArenaTiles.p(R, 36, 35);
		WorldPoint b = ArenaTiles.p(R, 38, 35);
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.RED_EGG)
			.groundItems(ImmutableList.of(
				new GroundItemView(BaIds.EGG_RED, a, 1, false, 990),
				new GroundItemView(BaIds.EGG_RED, b, 1, false, 990)));
		// b is nearer, but we were already heading to a and it's only 2 tiles further
		assertEquals(a, plan(s.lastTargetTile(a).lastTask("col.collect").lastStep(Step.COL_COLLECT)
			.lastActionTarget(Target.groundItem(BaIds.EGG_RED, a)).build()).getTarget().getTile());
		assertEquals(b, plan(s.lastTargetTile(null).lastTask(null).lastActionTarget(null).build()).getTarget().getTile());

		// The only egg has a runner next to it: still shown, with a warning
		Instruction risky = plan(base(Role.COLLECTOR, 2)
			.listenCall(BaCall.RED_EGG)
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, a, 1, false, 990)))
			.penance(ImmutableList.of(npc(7, PenanceType.RUNNER, 37, 35)))
			.build());
		assertEquals(Step.COL_COLLECT, risky.getStep());
		assertTrue(risky.getDetail().contains("runner or healer"));
	}

	@Test
	public void collectorPicksTheNearestSafeEgg()
	{
		WorldPoint nearRunner = ArenaTiles.p(R, 41, 35);
		WorldPoint far = ArenaTiles.p(R, 34, 35);
		Snapshot s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.RED_EGG)
			.groundItems(ImmutableList.of(
				new GroundItemView(BaIds.EGG_RED, nearRunner, 1, false, 990),
				new GroundItemView(BaIds.EGG_RED, far, 1, false, 990),
				new GroundItemView(BaIds.EGG_GREEN, ME, 1, false, 990)))
			.penance(ImmutableList.of(npc(7, PenanceType.RUNNER, 42, 35)))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.COL_COLLECT, ins.getStep());
		assertEquals(far, ins.getTarget().getTile());
		// A safe egg ten tiles away no longer justifies passing a nearby, collectable egg.
		Instruction local = plan(s.toBuilder().groundItems(ImmutableList.of(
			new GroundItemView(BaIds.EGG_RED, nearRunner, 1, false, 990),
			new GroundItemView(BaIds.EGG_RED, ArenaTiles.p(R, 30, 35), 1, false, 990))).build());
		assertEquals(nearRunner, local.getTarget().getTile());
		assertTrue(local.getDetail().contains("runner or healer"));
	}

	@Test
	public void collectorTiersExpiringEggsAndSupportForAnAvailableShooter()
	{
		// Expiring reds remain time critical during the collecting window.
		WorldPoint egg = ArenaTiles.p(R, 36, 35);
		Snapshot.SnapshotBuilder s = base(Role.COLLECTOR, 3).listenCall(BaCall.RED_EGG).bagRed(5)
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, egg, 1, false, 1000 - 190)));
		Instruction rescue = plan(s.build());
		assertEquals(Step.COL_COLLECT, rescue.getStep());
		assertEquals(Tier.TIME_CRITICAL, rescue.getTier());
		assertEquals(Step.COL_COLLECT, plan(s.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, egg, 1, false, 990))).build()).getStep());

		// Outside red, an escaping runner warrants supply; red time remains for gathering.
		Instruction dry = plan(base(Role.COLLECTOR, 3).listenCall(BaCall.GREEN_EGG).bagRed(3)
			.inventory(ImmutableMap.of(BaIds.EGG_BLUE, 1))
			.penance(ImmutableList.of(npc(4, PenanceType.RUNNER, 45, 31))).build());
		assertEquals(Step.COL_LOAD, dry.getStep());
		assertEquals(Tier.TEAM_BLOCKING, dry.getTier());
	}

	@Test
	public void collectorSavesTheBatchForBlueRatherThanInterruptingRed()
	{
		Snapshot s = base(Role.COLLECTOR, 2)
			.listenCall(BaCall.RED_EGG)
			.inventory(ImmutableMap.of(BaIds.EGG_RED, 5))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_RED, ME, 1, false, 990)))
			.build();
		assertEquals(Step.COL_COLLECT, plan(s).getStep());
		s = s.toBuilder().listenCall(BaCall.BLUE_EGG)
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.EGG_BLUE, ME, 1, false, 990))).build();
		Instruction ins = plan(s);
		assertEquals(Step.COL_LOAD, ins.getStep());
		assertEquals(Target.Kind.OBJECT, ins.getTarget().getKind());

		Snapshot full = s.toBuilder().eggsRed(5).build();
		assertEquals(Step.COL_COLLECT, plan(full).getStep());
	}

	// ------------------------------------------------------------------ defender

	@Test
	public void defenderStocksForThePatternThenDropsInOrder()
	{
		// Nothing held: one Stock-Up click, even before the call
		Snapshot empty = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).build();
		Instruction stock = plan(empty);
		assertEquals(Step.DEF_TAKE_BAIT, stock.getStep());
		assertTrue(stock.getHeadline(), stock.getHeadline().contains("Stock-Up"));
		assertTrue(plan(empty.toBuilder().listenCall(null).build()).getHeadline().contains("Stock-Up"));

		// Stocked: safety bait south of the trap first, with every spot marked.
		Snapshot stocked = empty.toBuilder()
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9))
			.build();
		Instruction first = plan(stocked);
		assertEquals(Step.DEF_DROP, first.getStep());
		assertEquals(ArenaTiles.safety(3), first.getTarget().getTile());
		assertTrue(first.getItems().contains(BaCall.BAIT_TOFU.getItemId()));
		assertEquals(5, first.getMarks().size());
		stocked = stocked.toBuilder().badBaitLanded(ImmutableMap.of(ArenaTiles.safety(3), 1)).build();
		Instruction trapFirst = plan(stocked);
		assertEquals(ArenaTiles.trapFood(3), trapFirst.getTarget().getTile());
		assertTrue(trapFirst.getItems().contains(BaCall.BAIT_WORMS.getItemId()));

		// Trap done: the middle (trail) bait next, bad bait
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot afterTrap = stocked.toBuilder()
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5)))
			.build();
		Instruction second = plan(afterTrap);
		assertTrue(second.getHeadline(), second.getHeadline().contains("Trail"));
		assertTrue(second.getItems().contains(BaCall.BAIT_TOFU.getItemId()));

		// Little time left in the call: skip bad bait, the mainstack's good bait first
		Instruction rushed = plan(afterTrap.toBuilder().ticksToCallChange(5).build());
		assertTrue(rushed.getHeadline(), rushed.getHeadline().contains("Mainstack"));
	}

	@Test
	public void defenderGetsToolsOnTheWayAndLeavesTheTrapOnlyWhenItCopes()
	{
		WorldPoint main = ArenaTiles.mainstack(3);
		WorldPoint hammerTile = main.dx(-3).dy(-1);
		ImmutableMap<Integer, Integer> bait = ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9);
		// At the mainstack with its bait not down yet: drop it before the hammer 3 tiles away
		WorldPoint trapTile = ArenaTiles.trapFood(3);
		Snapshot pattern = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(30).playerTile(main)
			.inventory(bait)
			.badBaitLanded(ImmutableMap.of(ArenaTiles.safety(3), 1))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.HAMMER, hammerTile, 1, false, 0),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trapTile, 4, true, 5)))
			.build();
		Instruction first = plan(pattern);
		assertEquals(Step.DEF_DROP, first.getStep());
		assertTrue(first.getHeadline(), first.getHeadline().contains("Mainstack"));
		// Mainstack down: now the hammer, on the way past
		Snapshot after = pattern.toBuilder().groundItems(ImmutableList.of(new GroundItemView(BaIds.HAMMER, hammerTile, 1, false, 0),
			new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trapTile, 4, true, 5),
			new GroundItemView(BaCall.BAIT_WORMS.getItemId(), main, 4, true, 5))).build();
		assertEquals(Step.DEF_HAMMER, plan(after).getStep());
		// Walked off toward the trap (recorded wave 7: 7 tiles away it flipped back to the relure):
		// still the tools, they lie by the mainstack
		assertEquals(Step.DEF_HAMMER, plan(after.toBuilder().playerTile(ArenaTiles.p(R, 42, 40)).build()).getStep());

		// With the logs on the ground: the player's route - log on the West relure tile first
		WorldPoint relure = ArenaTiles.p(R, 29, 46);
		ImmutableList<GroundItemView> ground = ImmutableList.of(new GroundItemView(BaIds.HAMMER, hammerTile, 1, false, 0),
			new GroundItemView(BaIds.LOGS, relure, 1, false, 0), new GroundItemView(BaIds.LOGS, ArenaTiles.p(R, 28, 47), 1, false, 0),
			new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trapTile, 4, true, 5),
			new GroundItemView(BaCall.BAIT_WORMS.getItemId(), main, 4, true, 5));
		Instruction firstLog = plan(after.toBuilder().groundItems(ground).build());
		assertEquals(Step.DEF_LOGS, firstLog.getStep());
		assertEquals(relure, firstLog.getTarget().getTile());
		// Wave 9 needs 4 logs: with 2 in hand and the hammer, wait at the spawn for the respawn
		Instruction wait = plan(after.toBuilder().wave(9).hasHammer(true).logsTaken(2)
			.inventory(ImmutableMap.of(BaIds.LOGS, 2, BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.trapFood(9), 4, true, 5),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(9), 4, true, 5),
				new GroundItemView(BaCall.BAIT_TOFU.getItemId(), ArenaTiles.defenderPattern(9).get(2).getTile(), 1, false, 5),
				new GroundItemView(BaCall.BAIT_TOFU.getItemId(), ArenaTiles.logSpawn(9), 1, false, 5)))
			.goodBaitLanded(ImmutableMap.of(ArenaTiles.trapFood(9), 4, ArenaTiles.mainstack(9), 4))
			.badBaitLanded(ImmutableMap.of(ArenaTiles.defenderPattern(9).get(0).getTile(), 1, ArenaTiles.defenderPattern(9).get(2).getTile(), 1,
				ArenaTiles.logSpawn(9), 1))
			.build());
		assertTrue(wait.getHeadline(), wait.getHeadline().startsWith("Wait for the logs to respawn (2/4)"));
		// Standing on it: drop the West relure's bad bait there in the same stop
		Instruction drop = plan(after.toBuilder().groundItems(ground).playerTile(relure).build());
		assertEquals(Step.DEF_DROP, drop.getStep());
		assertTrue(drop.getHeadline(), drop.getHeadline().contains("West relure"));

		// After the pattern, on the trap food with 2 good bait and 2 charges
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot.SnapshotBuilder onTrap = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(60).playerTile(trap)
			.trapCharges(2).inventory(bait)
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 2, true, 5),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), main, 4, true, 5),
				new GroundItemView(BaIds.HAMMER, hammerTile, 1, false, 0)));
		// Already holding at the trap: routine supply trips wait for approaching runners.
		Snapshot three = onTrap.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 30, 60),
			npc(4, PenanceType.RUNNER, 31, 60), npc(5, PenanceType.RUNNER, 32, 60))).build();
		assertEquals("def.prepare", plan(three).getTask());
		assertEquals(Step.DEF_DROP, plan(three).getStep());
		assertEquals(trap, plan(three).getTarget().getTile());
		// One runner is still approaching: keep holding, even if the trap could kill it alone.
		Snapshot one = onTrap.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 30, 60))).build();
		assertEquals(Step.DEF_HOLD, plan(one).getStep());
		assertEquals("def.prepare", plan(three.toBuilder().waveTick(100).build()).getTask());
		// Fetching tools is still useful before taking up the holding position.
		assertEquals(Step.DEF_HAMMER, plan(three.toBuilder().playerTile(trap.dy(-3)).build()).getStep());
	}

	@Test
	public void runnerStateFollowsTheFoodTrail()
	{
		WorldPoint trap = ArenaTiles.trapFood(3);
		WorldPoint main = ArenaTiles.mainstack(3);
		Snapshot s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS)
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), main, 4, true, 5),
				new GroundItemView(BaCall.BAIT_TOFU.getItemId(), ArenaTiles.p(R, 39, 39), 1, false, 5)))
			.build();
		assertEquals(RunnerState.STACKED, RunnerState.of(s, npc(1, PenanceType.RUNNER, trap.getRegionX() - 1, trap.getRegionY())));
		assertEquals(RunnerState.LURED, RunnerState.of(s, npc(2, PenanceType.RUNNER, main.getRegionX(), main.getRegionY() + 3)));
		assertEquals(RunnerState.BAD_ONLY, RunnerState.of(s, npc(3, PenanceType.RUNNER, 42, 38)));
		assertEquals(RunnerState.LOOSE, RunnerState.of(s, npc(4, PenanceType.RUNNER, 20, 40)));
		assertEquals(RunnerState.ESCAPING, RunnerState.of(s, npc(5, PenanceType.RUNNER, trap.getRegionX(), trap.getRegionY() - 4)));
	}

	@Test
	public void singleKindRestockIsALeftClick()
	{
		// Trap food short, only the called kind needed: Take-Worms becomes the machine's left-click
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(120).playerTile(trap)
			.trapCharges(2).hasHammer(true).logsTaken(WaveInfo.defenderLogs(3))
			.inventory(ImmutableMap.of(BaCall.BAIT_TOFU.getItemId(), 3, BaCall.BAIT_CRACKERS.getItemId(), 3))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5)))
			.build();
		Instruction take = plan(s);
		assertEquals(Step.DEF_TAKE_BAIT, take.getStep());
		assertEquals("Take-Worms", take.getLeftClick());
		// The healer's food is one kind by nature
		Snapshot heal = base(Role.HEALER, 3).listenCall(BaCall.POISON_MEAT).waveTick(60).build();
		Instruction food = plan(heal);
		assertEquals(Step.HEAL_TAKE_FOOD, food.getStep());
		assertEquals("Take-Meat", food.getLeftClick());
	}

	@Test
	public void defenderLogTargetCountsPickupsForTheWholeWave()
	{
		// Reported 2026-10-16: both logs used on repairs sent the defender back for more
		WorldPoint trap = ArenaTiles.trapFood(3);
		// 3 of 4 runners dead, trap full: the last one needs no log
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(120).playerTile(trap)
			.trapCharges(2).hasHammer(true).logsTaken(2).runnersKilled(3)
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 5))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5),
				new GroundItemView(BaIds.LOGS, ArenaTiles.p(R, 29, 46), 1, false, 0)))
			.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 30, 60)));
		assertTrue(plan(s.build()).getStep() != Step.DEF_LOGS);
		// The trap breaks with no log left: one more, for the repair
		Instruction broken = plan(s.trapCharges(0).build());
		assertEquals(Step.DEF_LOGS, broken.getStep());
	}

	@Test
	public void defenderFinishesTheToolTripUnlessRunnersNeedTheTrap()
	{
		// Reported 2026-10-16: trips flip-flopped as small things changed
		WorldPoint trap = ArenaTiles.trapFood(3);
		WorldPoint hammerTile = ArenaTiles.mainstack(3).dx(-3).dy(-1);
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(60).trapCharges(2)
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9, BaCall.BAIT_CRACKERS.getItemId(), 9))
			.groundItems(ImmutableList.of(new GroundItemView(BaIds.HAMMER, hammerTile, 1, false, 0),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5)))
			.playerTile(trap.dx(-2));
		// Fresh, nothing out: the nearer job first, stocking the trap
		assertEquals(Step.DEF_DROP, plan(s.build()).getStep());
		// Already on the way to the hammer: finish the trip
		Instruction trip = plan(s.lastTask("def.tools").build());
		assertEquals(Step.DEF_HAMMER, trip.getStep());
		assertEquals("def.tools", trip.getTask());
		// A runner out with no food on the trap outranks it
		assertEquals(Step.DEF_DROP, plan(s.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 30, 60))).build()).getStep());
	}

	@Test
	public void aPatternSpotStaysDoneAfterRunnersEatIt()
	{
		// Recorded 2026-10-16: trap bait eaten early -> "Drop 4 good at Trap" again, mainstack never
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(30)
			.playerTile(ArenaTiles.p(R, 38, 41))
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 5, BaCall.BAIT_TOFU.getItemId(), 9))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 1, true, 5)))
			.goodBaitLanded(ImmutableMap.of(trap, 4))
			.badBaitLanded(ImmutableMap.of(ArenaTiles.safety(3), 1, ArenaTiles.defenderPattern(3).get(2).getTile(), 1))
			.build();
		Instruction next = plan(s);
		assertTrue(next.getHeadline(), next.getHeadline().contains("Mainstack"));
	}

	@Test
	public void callsStopOnceThePartnersTargetsAreAllKilled()
	{
		// Defender's call picks the healer's poison food: pointless once all healers are dead
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 4).shoutCall(BaCall.POISON_MEAT).shoutDone(false);
		assertEquals(Step.CALL, plan(s.build()).getStep());
		Snapshot done = s.cleared(com.google.common.collect.ImmutableSet.of(PenanceType.HEALER)).build();
		assertTrue(plan(done).getStep() != Step.CALL);
		assertTrue(plan(done).getAlsoCall() == null);
		// Runners being dead doesn't stop the defender's call
		assertEquals(Step.CALL, plan(s.cleared(com.google.common.collect.ImmutableSet.of(PenanceType.RUNNER)).build()).getStep());
	}

	@Test
	public void relureGetsAheadOfTheRunnerDropsInRangeThenWaits()
	{
		WorldPoint trap = ArenaTiles.trapFood(3);
		WorldPoint runner = trap.dy(-6);
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(120)
			.trapCharges(2).hasHammer(true).logsTaken(4).runnersKilled(3)
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 5, BaCall.BAIT_TOFU.getItemId(), 3))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5)))
			.penance(ImmutableList.of(PenanceView.builder().npcIndex(9).type(PenanceType.RUNNER).tile(runner).build()));

		// Far away: get to the tile 2 north of it
		Instruction ahead = plan(s.playerTile(trap.dx(-10)).build());
		assertEquals(Step.DEF_RELURE, ahead.getStep());
		assertEquals(runner.dy(2), ahead.getTarget().getTile());

		// Within 3 tiles and north of it: drop now, bad bait
		Instruction drop = plan(s.playerTile(runner.dy(2)).build());
		assertTrue(drop.getHeadline(), drop.getHeadline().startsWith("Drop bad bait now"));
		assertTrue(drop.getItems().contains(BaCall.BAIT_TOFU.getItemId()));

		// Bad bait already lying in front of it: wait for it to eat
		Instruction wait = plan(s.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5),
			new GroundItemView(BaCall.BAIT_TOFU.getItemId(), runner.dy(2), 1, false, 100))).build());
		assertTrue(wait.getHeadline(), wait.getHeadline().contains("wait"));

		// One tile south of the trap food, stacked on you: not an escape
		Snapshot stacked = s.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5)))
			.playerTile(trap)
			.penance(ImmutableList.of(PenanceView.builder().npcIndex(9).type(PenanceType.RUNNER).tile(trap.dy(-1)).build())).build();
		assertTrue(plan(stacked).getStep() != Step.DEF_RELURE);
	}

	@Test
	public void safetyBaitIsDroppedWhenPassingAndReDroppedWhenGone()
	{
		WorldPoint safety = ArenaTiles.safety(3);
		WorldPoint trap = ArenaTiles.trapFood(3);
		ImmutableMap<Integer, Integer> bait = ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9,
			BaCall.BAIT_CRACKERS.getItemId(), 9);
		// Pattern, walking to the trap 2 tiles from the safety spot: drop it on the way
		Snapshot passing = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(15).playerTile(safety.dx(-2))
			.inventory(bait).build();
		Instruction drop = plan(passing);
		assertTrue(drop.getHeadline(), drop.getHeadline().contains("Safety"));

		// After the pattern, on the trap food, safety bait gone, trap copes: re-drop it
		Snapshot.SnapshotBuilder later = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(120).playerTile(trap)
			.trapCharges(2).hasHammer(true).logsTaken(4).inventory(bait)
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5)));
		Instruction redrop = plan(later.build());
		assertEquals("Re-drop the safety bait", redrop.getHeadline());
		assertEquals(safety, redrop.getTarget().getTile());
		// Still lying there: nothing to do
		Snapshot there = later.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5),
			new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5),
			new GroundItemView(BaCall.BAIT_TOFU.getItemId(), safety, 1, false, 5))).build();
		assertTrue(plan(there).getHeadline(), !plan(there).getHeadline().contains("safety"));
	}

	@Test
	public void playOptionsChangeTheDefenderAndHealerPlans()
	{
		// Gau Cho: no log-spawn relure, his log counts
		assertEquals(4, com.bautopilot.baautopilot.model.ArenaTiles.defenderPattern(3, com.bautopilot.baautopilot.model.DefenderGuide.GAU_CHO, true).size());
		assertEquals(3, com.bautopilot.baautopilot.model.ArenaTiles.defenderPattern(3, com.bautopilot.baautopilot.model.DefenderGuide.GAU_CHO, false).size());
		assertEquals(0, WaveInfo.defenderLogs(1, com.bautopilot.baautopilot.model.DefenderGuide.GAU_CHO));
		assertEquals(2, WaveInfo.defenderLogs(1, com.bautopilot.baautopilot.model.DefenderGuide.WIKI));

		// Safety off: not in the pattern marks, no re-drop
		Settings noSafety = Settings.builder().safetyBait(false).build();
		Snapshot stocked = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS)
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 9, BaCall.BAIT_TOFU.getItemId(), 9)).build();
		Instruction pattern = new Planner().plan(stocked, noSafety);
		assertEquals(4, pattern.getMarks().size());
		assertTrue(pattern.getMarks().stream().noneMatch(m -> m.getLabel().startsWith("Safety")));

		// Heal code sets: a variant where the wave has one, Regular where it doesn't, none when off
		assertEquals("W6 2x OS", com.bautopilot.baautopilot.model.HealCode.of(com.bautopilot.baautopilot.model.HealCodeSet.TWO_OS, 6).getName());
		assertEquals(com.bautopilot.baautopilot.model.HealCode.regular(5).getName(),
			com.bautopilot.baautopilot.model.HealCode.of(com.bautopilot.baautopilot.model.HealCodeSet.TWO_OS, 5).getName());
		assertTrue(com.bautopilot.baautopilot.model.HealCode.of(com.bautopilot.baautopilot.model.HealCodeSet.OFF, 6) == null);
	}

	@Test
	public void defenderTopsUpALowBaitKindWhenTheTrapCopes()
	{
		// Recorded waves 5-9: up to 118 ticks without the called bait
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_WORMS).waveTick(120).playerTile(trap)
			.trapCharges(2).hasHammer(true).logsTaken(4)
			.inventory(ImmutableMap.of(BaCall.BAIT_WORMS.getItemId(), 5, BaCall.BAIT_TOFU.getItemId(), 4, BaCall.BAIT_CRACKERS.getItemId(), 1))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_WORMS.getItemId(), trap, 4, true, 5),
				new GroundItemView(BaCall.BAIT_WORMS.getItemId(), ArenaTiles.mainstack(3), 4, true, 5)));
		Instruction top = plan(s.build());
		assertEquals(Step.DEF_TAKE_BAIT, top.getStep());
		assertTrue(top.getHeadline(), top.getHeadline().contains("Take-Crackers"));
		// Three runners out and only 4 bait on the trap... with 2 charges it can't take them all: stay
		Snapshot busy = s.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 30, 60), npc(4, PenanceType.RUNNER, 31, 60),
			npc(5, PenanceType.RUNNER, 32, 60))).build();
		assertTrue(plan(busy).getStep() != Step.DEF_TAKE_BAIT);
	}

	@Test
	public void theCallLeadsFromTheFirstTickButKeepsTheWalkTarget()
	{
		// Owed call + a stock-up walk: the call is the headline, the machine stays the target
		Snapshot s = base(Role.DEFENDER, 3).waveTick(1).ticksToCallChange(49).shoutCall(BaCall.POISON_WORMS).shoutDone(false).build();
		Instruction ins = plan(s);
		assertEquals(Step.CALL, ins.getStep());
		assertEquals(BaIds.DEFENDER_MACHINE, ins.getTarget().getId());
		// The HUD still shows last period's call at the flip: no prompt for it
		Snapshot stale = s.toBuilder().shoutSettled(false).build();
		assertTrue(plan(stale).getStep() != Step.CALL);
		assertTrue(plan(stale).getAlsoCall() == null);
	}

	@Test
	public void defenderRepairsABrokenTrap()
	{
		Snapshot s = base(Role.DEFENDER, 3)
			.listenCall(BaCall.BAIT_WORMS)
			.waveTick(200)
			.trapCharges(0)
			.hasHammer(true)
			.inventory(ImmutableMap.of(BaIds.LOGS, 2))
			.build();
		assertEquals(Step.DEF_REPAIR_POSITION, plan(s).getStep());
		assertEquals(ArenaTiles.trapFood(3), plan(s).getTarget().getTile());
	}

	@Test
	public void defenderKeepsBaitOnTheTrapWithAnyTeam()
	{
		Snapshot s = base(Role.DEFENDER, 3)
			
			.waveTick(60)
			.listenCall(BaCall.BAIT_CRACKERS)
			.trapCharges(2)
			.hasHammer(true)
			.inventory(ImmutableMap.of(BaCall.BAIT_CRACKERS.getItemId(), 5, BaIds.LOGS, 2))
			.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 45, 50)))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.DEF_DROP, ins.getStep());
		assertEquals(ArenaTiles.trapFood(3), ins.getTarget().getTile());
		assertEquals(ins, plan(s.toBuilder().build()));

		// The first call is for the pattern, team or no team
		Snapshot early = s.toBuilder().waveTick(10).build();
		assertEquals(plan(early), plan(early.toBuilder().build()));
		assertTrue(plan(early).getHeadline(), !plan(early).getMarks().isEmpty());
	}

	@Test
	public void defenderFollowsTheWikiProcedure()
	{
		// Wave 10 pattern (recorded: east trap (45, 34), logs at (30, 46)): trap food west of the
		// trap, last bad food at the log spawn
		assertEquals(ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 44, 34), ArenaTiles.trapFood(10));
		assertEquals(ArenaTiles.p(ArenaTiles.REGION_WAVE_10, 30, 46), ArenaTiles.defenderPattern(10).get(3).getTile());

		WorldPoint trap = ArenaTiles.trapFood(3);
		WorldPoint main = ArenaTiles.mainstack(3);
		Snapshot.SnapshotBuilder s = base(Role.DEFENDER, 3).listenCall(BaCall.BAIT_CRACKERS).waveTick(200)
			.trapCharges(2).hasHammer(true).playerTile(trap)
			.inventory(ImmutableMap.of(BaIds.LOGS, 2, BaCall.BAIT_CRACKERS.getItemId(), 5, BaCall.BAIT_TOFU.getItemId(), 2))
			.groundItems(ImmutableList.of(
				new GroundItemView(BaCall.BAIT_CRACKERS.getItemId(), trap, 2, true, 5),
				new GroundItemView(BaCall.BAIT_TOFU.getItemId(), main, 2, true, 5)));

		// Three runners stacked on 2 good bait: top it up to 3 before stepping off
		Snapshot three = s.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 46, 36), npc(4, PenanceType.RUNNER, 44, 36),
			npc(5, PenanceType.RUNNER, 45, 37))).build();
		Instruction more = plan(three);
		assertEquals(Step.DEF_DROP, more.getStep());
		assertTrue(more.getDetail(), more.getDetail().contains("3 runners stacked"));

		// Trap at 1 charge, logs only for the runners left: let it break, fix it then
		Snapshot tight = s.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 40, 50))).trapCharges(1).build();
		assertTrue(plan(tight).getStep() != Step.DEF_REPAIR);
		// With a log to spare (2 of 4 runners dead): repair to full
		Snapshot worn = tight.toBuilder().runnersKilled(2).build();
		assertEquals(Step.DEF_REPAIR, plan(worn).getStep());

		// Missing spawn food can wait while the defender is holding an incoming runner.
		Snapshot bare = worn.toBuilder().trapCharges(2)
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_CRACKERS.getItemId(), trap, 2, true, 5))).build();
		Instruction restock = plan(bare);
		assertEquals(Step.DEF_HOLD, restock.getStep());
		assertEquals(trap, restock.getTarget().getTile());
		// Away from the holding tile, routine spawn-bait maintenance remains available.
		Instruction away = plan(bare.toBuilder().playerTile(main).build());
		assertEquals(Step.DEF_DROP, away.getStep());
		assertEquals(main, away.getTarget().getTile());

		// A runner past the safety bait, heading for the exit: relure it with bad bait, first
		WorldPoint south = ArenaTiles.safety(3).dy(-3);
		Snapshot escaping = bare.toBuilder().runnersKilled(3).penance(ImmutableList.of(PenanceView.builder().npcIndex(9)
			.type(PenanceType.RUNNER).tile(south).build())).build();
		Instruction relure = plan(escaping);
		assertEquals(Step.DEF_RELURE, relure.getStep());
		assertTrue(relure.getItems().contains(BaCall.BAIT_TOFU.getItemId()));
	}

	@Test
	public void defenderStepsSouthOnAStack()
	{
		WorldPoint trap = ArenaTiles.trapFood(3);
		Snapshot s = base(Role.DEFENDER, 3)
			.listenCall(BaCall.BAIT_CRACKERS)
			.waveTick(200)
			.trapCharges(2)
			.hasHammer(true)
			.playerTile(trap)
			.inventory(ImmutableMap.of(BaIds.LOGS, 2))
			.groundItems(ImmutableList.of(new GroundItemView(BaCall.BAIT_CRACKERS.getItemId(), trap, 4, true, 5)))
			.penance(ImmutableList.of(npc(3, PenanceType.RUNNER, 46, 36), npc(4, PenanceType.RUNNER, 44, 36)))
			.ticksToCallChange(45) // 5 ticks in: the 27 s mark
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.DEF_STEP, ins.getStep());
		assertEquals(ArenaTiles.trapStep(3), ins.getTarget().getTile());

		// With the call timer known, wait for the slow multi-kill mark: 27, 21, 15, 9 or 3 seconds.
		// 2 ticks into the call (28.8 s): 3 ticks to the 27 s mark
		Instruction hold = plan(s.toBuilder().ticksToCallChange(48).build());
		assertEquals(Step.DEF_HOLD, hold.getStep());
		assertTrue(hold.getHeadline(), hold.getHeadline().contains("27s"));
		// Trap down to 1 charge under the stack, tools in hand: fix it now
		Snapshot worn = s.toBuilder().trapCharges(1).ticksToCallChange(48).build();
		Instruction fix = plan(worn);
		assertEquals(Step.DEF_REPAIR, fix.getStep());
		// On the mark (35 ticks in = 9 s): step
		assertEquals(Step.DEF_STEP, plan(s.toBuilder().ticksToCallChange(15).build()).getStep());
		// 47 ticks in: the next mark is the next call's 27 s, not -3 s
		assertTrue(plan(s.toBuilder().ticksToCallChange(3).build()).getHeadline().contains("27s"));
		// Just past it (36 ticks in): hold for the 3 s mark
		assertTrue(plan(s.toBuilder().ticksToCallChange(14).build()).getHeadline().contains("3s"));
	}

	// ------------------------------------------------------------------ healer

	@Test
	public void otherRolesKeepPenanceThatWalkedOutOfView()
	{
		// Attacker: the fighter you were on stays the target, at its last seen tile
		PenanceView away = npc(4, PenanceType.FIGHTER, 30, 60).toBuilder().unseen(true).build();
		Snapshot att = base(Role.ATTACKER, 2).listenCall(BaCall.ACCURATE).ammoId(BaCall.ACCURATE.getItemId())
			.unseenPenance(ImmutableList.of(away)).lastTargetNpc(4).build();
		Instruction chase = plan(att);
		assertEquals(Step.ATT_ATTACK, chase.getStep());
		assertEquals(4, chase.getTarget().getId());
		assertTrue(chase.getHeadline(), chase.getHeadline().contains("out of view"));
		// A fresh pick prefers one in view
		Snapshot both = att.toBuilder().lastTargetNpc(-1)
			.penance(ImmutableList.of(npc(5, PenanceType.RANGER, 41, 36))).build();
		assertEquals(5, plan(both).getTarget().getId());

		// Defender: a runner out of view is still a runner out, so no "all runners are dead"
		Snapshot def = base(Role.DEFENDER, 2).listenCall(BaCall.BAIT_TOFU).waveTick(200).trapCharges(2)
			.hasHammer(true).inventory(ImmutableMap.of(BaIds.LOGS, 2)).runnersKilled(WaveInfo.runnersTotal(2) - 1)
			.unseenPenance(ImmutableList.of(npc(9, PenanceType.RUNNER, 30, 60).toBuilder().unseen(true).build()))
			.build();
		assertTrue(plan(def).getStep() != Step.DEF_SUPPORT);
		assertEquals(Step.DEF_SUPPORT, plan(def.toBuilder().unseenPenance(ImmutableList.of())
			.runnersKilled(WaveInfo.runnersTotal(2)).build()).getStep());
	}

	@Test
	public void healerCoversVisibleNewcomersBeforeSeekingAnUnseenUntaggedTarget()
	{
		// Reported 2026-10-16: the feed target jumped when its healer left view
		PenanceView away = healer(5, 1, 0, -1).toBuilder().unseen(true).build();
		Snapshot s = base(Role.HEALER, 3).listenCall(BaCall.POISON_MEAT)
			.inventory(ImmutableMap.of(BaCall.POISON_MEAT.getItemId(), 5))
			.penance(ImmutableList.of(healer(6, 2, 0, -1)))
			.unseenPenance(ImmutableList.of(away))
			.lastTargetNpc(5)
			.build();
		Instruction ins = planPriority(s);
		assertEquals(6, ins.getTarget().getId());
		// The unseen healer is still owed its first poison, even when the visible one is decaying.
		Instruction seek = planPriority(s.toBuilder().penance(ImmutableList.of(healer(6, 2, 1, 970))).build());
		assertEquals(5, seek.getTarget().getId());
		assertTrue(seek.getHeadline(), seek.getHeadline().contains("out of view"));
	}

	private static PenanceView healer(int index, int order, int foodsThisPeriod, int lastFoodTick)
	{
		return PenanceView.builder().npcIndex(index).type(PenanceType.HEALER).order(order)
			.tile(ArenaTiles.p(R, 50, 50)).foods(foodsThisPeriod).foodsThisPeriod(foodsThisPeriod)
			.lastFoodTick(lastFoodTick).build();
	}

	@Test
	public void healerUsesTheCodeOnlyForNeededPoisonRefreshes()
	{
		// W4 Reg: 2-5-1(24) // 0-0-1-7
		Snapshot.SnapshotBuilder b = base(Role.HEALER, 4)
			.healersKilled(1) // The reserve slot is already open; code counts cannot force spam.
			.listenCall(BaCall.POISON_MEAT)
			.inventory(ImmutableMap.of(BaCall.POISON_MEAT.getItemId(), 10))
			.waveTick(20);
		List<PenanceView> healers = new ArrayList<>();
		healers.add(healer(1, 1, 2, 990));
		healers.add(healer(2, 2, 1, 990));
		healers.add(healer(3, 3, 0, -1));
		Instruction ins = plan(b.penance(ImmutableList.copyOf(healers)).build());
		assertEquals(Step.HEAL_FEED, ins.getStep());
		assertEquals(3, ins.getTarget().getId());
		assertTrue(ins.getDetail().contains("Initial poison"));

		// Fixed quantities do not force extra food while every healer is at full poison.
		healers.set(2, healer(3, 3, 1, 995));
		ins = plan(b.penance(ImmutableList.copyOf(healers)).build());
		assertTrue(ins.getStep() != Step.HEAL_FEED);
		// #2 now needs a refresh: its code order can guide one food, then stop on confirmation.
		healers.set(1, healer(2, 2, 1, 970));
		ins = plan(b.penance(ImmutableList.copyOf(healers)).build());
		assertEquals(Step.HEAL_FEED, ins.getStep());
		assertEquals(2, ins.getTarget().getId());
		assertTrue(ins.getDetail().contains("refresh once to 4"));

		healers.set(1, healer(2, 2, 2, 995));
		Instruction done = plan(b.penance(ImmutableList.copyOf(healers)).waveTick(41).build());
		assertTrue("Do not add a second food to #3's one-food code entry", done.getStep() != Step.HEAL_FEED);
	}

	@Test
	public void pickupHealerPoisonsCleanThenDecayed()
	{
		Snapshot.SnapshotBuilder b = base(Role.HEALER, 6)
			
			.listenCall(BaCall.POISON_TOFU)
			.inventory(ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 5));
		Instruction clean = planPriority(b.penance(ImmutableList.of(healer(1, 1, 1, 990), healer(2, 2, 0, -1))).build());
		assertEquals(2, clean.getTarget().getId());

		// healer #1 fed 30 ticks ago: its poison is down to 3 a hit, below the full 4 - top it up
		// (player, 2026-10-17: keep every healer at full poison)
		Instruction top = planPriority(b.penance(ImmutableList.of(healer(1, 1, 1, 970), healer(2, 2, 1, 998))).build());
		assertEquals(Step.HEAL_FEED, top.getStep());
		assertEquals(1, top.getTarget().getId());
		assertTrue(top.getDetail(), top.getDetail().contains("refresh once to 4"));
		// #2 fed 2 ticks ago is at full poison: not asked for
		Instruction none = planPriority(b.penance(ImmutableList.of(healer(1, 1, 1, 998), healer(2, 2, 1, 998))).build());
		assertTrue(none.getStep() != Step.HEAL_FEED);
	}

	@Test
	public void duoHealersDoNotLeaveCoverageToAnAssumedPartner()
	{
		// Team-list order does not establish an agreed tagging/spamming assignment.
		ImmutableMap<String, Role> team = ImmutableMap.of("Zezima", Role.HEALER, "Me", Role.HEALER, "Lynx", Role.DEFENDER);
		Snapshot.SnapshotBuilder b = base(Role.HEALER, 6).team(team)
			.listenCall(BaCall.POISON_TOFU)
			.inventory(ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 9));
		// Listed second: still tag a fresh healer before touching one at full poison.
		Snapshot spam = b.selfName("Me")
			.penance(ImmutableList.of(healer(1, 1, 1, 990), healer(2, 2, 0, -1).toBuilder().spawnTick(995).build())).build();
		Instruction s1 = planPriority(spam);
		assertEquals(Step.HEAL_FEED, s1.getStep());
		assertEquals(2, s1.getTarget().getId());
		assertTrue(s1.getDetail(), s1.getDetail().contains("Initial poison"));
		// The tagger is late to the fresh one (nothing poisoned to spam): tag it
		Snapshot late = b.selfName("Me")
			.penance(ImmutableList.of(healer(2, 2, 0, -1).toBuilder().spawnTick(980).build())).build();
		assertEquals(2, planPriority(late).getTarget().getId());
		// Listed first: the same coverage rule applies.
		Snapshot tag = b.selfName("Zezima")
			.penance(ImmutableList.of(healer(1, 1, 1, 990), healer(2, 2, 0, -1))).build();
		assertEquals(2, planPriority(tag).getTarget().getId());
		Snapshot allTagged = b.selfName("Zezima").healersKilled(WaveInfo.healersTotal(6) - 2)
			.penance(ImmutableList.of(healer(1, 1, 1, 998), healer(2, 2, 1, 998))).build();
		assertTrue(planPriority(allTagged).getStep() != Step.HEAL_FEED);
	}

	/** Its health bar at half. */
	private static PenanceView half(PenanceView h)
	{
		return h.toBuilder().healthRatio(15).healthScale(30).build();
	}

	@Test
	public void healerSavesATeammateInDanger()
	{
		Snapshot s = base(Role.HEALER, 7)
			.listenCall(BaCall.POISON_TOFU)
			.inventory(ImmutableMap.of(BaCall.POISON_TOFU.getItemId(), 5, BaIds.VIALS_WITH_WATER.iterator().next(), 1))
			.teammates(ImmutableList.of(new TeammateView("Zezima", 15, 99), new TeammateView("Lynx", 90, 99)))
			.penance(ImmutableList.of(healer(1, 1, 0, -1)))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.HEAL_TEAMMATE, ins.getStep());
		assertEquals("Zezima", ins.getTarget().getLabel());
	}

	@Test
	public void healerTakesFoodAndFlagsStaleFood()
	{
		Snapshot s = base(Role.HEALER, 2)
			.listenCall(BaCall.POISON_WORMS)
			.inventory(ImmutableMap.of(BaCall.POISON_MEAT.getItemId(), 3))
			.build();
		Instruction ins = plan(s);
		assertEquals(Step.HEAL_TAKE_FOOD, ins.getStep());
		assertTrue(ins.getDetail().contains("Take-Worms"));
		assertTrue(ins.getDetail().contains("Don't use your other 3 food"));
	}

	@Test
	public void healerWaitsForTheNewCallInsteadOfCallingNewFoodWrong()
	{
		// Reported in game: the call changed to worms, the healer took worms, but the plugin still
		// only knew last call's meat and told them their worms were the wrong food.
		Snapshot stale = base(Role.HEALER, 3)
			.callPeriod(1)
			.ticksToCallChange(45)
			.listenCall(BaCall.POISON_MEAT)
			.listenFresh(false)
			.inventory(ImmutableMap.of(BaCall.POISON_WORMS.getItemId(), 5))
			.penance(ImmutableList.of(healer(1, 1, 0, -1)))
			.build();
		Instruction ins = plan(stale);
		assertEquals(Step.HEAL_WAIT_CALL, ins.getStep());
		assertEquals("Wait for the defender's new call", ins.getHeadline());
		assertTrue(ins.getDetail().contains("Poisoned meat"));
		assertTrue(!ins.getDetail().contains("Don't use"));
		assertTrue(ins.getItems().isEmpty());

		// Once the new call is heard it is used straight away
		Instruction fresh = plan(stale.toBuilder().listenCall(BaCall.POISON_WORMS).listenFresh(true).build());
		assertEquals(Step.HEAL_FEED, fresh.getStep());
		assertTrue(fresh.getItems().contains(BaCall.POISON_WORMS.getItemId()));

		// A delayed call must never make the previous instruction valid again.
		Instruction late = plan(stale.toBuilder().ticksToCallChange(20)
			.inventory(ImmutableMap.of(BaCall.POISON_MEAT.getItemId(), 5)).build());
		assertEquals(Step.HEAL_WAIT_CALL, late.getStep());
		assertTrue(late.getItems().isEmpty());
	}

	@Test
	public void staleCallGatesEveryRole()
	{
		Snapshot attacker = base(Role.ATTACKER, 4)
			.callPeriod(2).ticksToCallChange(48)
			.listenCall(BaCall.ACCURATE).listenFresh(false)
			.ammoId(BaCall.ACCURATE.getItemId())
			.penance(ImmutableList.of(npc(1, PenanceType.FIGHTER, 30, 40)))
			.build();
		Instruction ins = plan(attacker);
		assertEquals(Step.ATT_WAIT_CALL, ins.getStep());
		assertEquals("Wait for the collector's new call", ins.getHeadline());
	}

	// ------------------------------------------------------------------ queen

	@Test
	public void queenChainPassesEggsAlong()
	{
		Snapshot healer = base(Role.HEALER, 10).queenAlive(true)
			.inventory(ImmutableMap.of(BaIds.EGG_YELLOW, 1, BaIds.VIAL_FULL, 1)).build();
		Instruction dunk = plan(healer);
		assertEquals(Step.QUEEN_DUNK, dunk.getStep());
		assertEquals(BaIds.POISON_CRATERS[0], dunk.getTarget().getId());

		Snapshot attacker = base(Role.ATTACKER, 10).queenAlive(true)
			.inventory(ImmutableMap.of(BaIds.EGG_POISONED, 1, BaIds.SPIKES, 3)).build();
		assertEquals(Step.QUEEN_SPIKE, plan(attacker).getStep());

		Snapshot collector = base(Role.COLLECTOR, 10).queenAlive(true)
			.playerTile(ArenaTiles.p(R, 40, 28))
			.inventory(ImmutableMap.of(BaIds.EGG_OMEGA, 1)).build();
		Settings stack = settings.toBuilder().queenStack(true).build();
		assertEquals(Step.QUEEN_LOAD_ONE, planner.plan(collector, stack).getStep());
		assertEquals(Step.QUEEN_LOAD_ONE, plan(collector).getStep());

		// Eggs done: stacking spams Fire at the cannon, even before one is loaded; not stacking waits
		// by the spring and leaves the firing to the collector
		Snapshot idle = base(Role.DEFENDER, 10).queenAlive(true).eggsOmega(1).build();
		assertEquals(Step.QUEEN_FIRE, planner.plan(idle, stack).getStep());
		assertEquals(Step.QUEEN_FIRE, planner.plan(idle.toBuilder().eggsOmega(0).build(), stack).getStep());
		assertEquals(Step.QUEEN_WAIT, plan(idle).getStep());
	}
}
