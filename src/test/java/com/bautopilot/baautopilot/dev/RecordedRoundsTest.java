package com.bautopilot.baautopilot.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.plan.Step;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Replays every real round in {@code src/test/resources/recordings} through the current code and
 * checks it against what the recording says happened (see {@link RoundInvariants}).
 */
public class RecordedRoundsTest
{
	static final Path RECORDINGS = Paths.get("src", "test", "resources", "recordings");
	private static final int MAX_LISTED = 25;

	private static List<RoundReplay.Frame> advancedDefender(RecordedRound round)
	{
		com.bautopilot.baautopilot.plan.Planner planner = new com.bautopilot.baautopilot.plan.Planner();
		return RoundReplay.run(round, (s, settings) -> planner.plan(s, settings.toBuilder().defenderMultiKill(true).build()));
	}

	@Test public void latestWaveTenUsesItsOwnBaitTilesAndLavaEggChain() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-19_22-53-07-wave10.jsonl")));
		RoundReplay.Frame opening = frames.stream().filter(f -> f.raw.t == 2084).findFirst().get();
		assertEquals(1, opening.raw.live.wave);
		assertEquals(10, opening.snapshot.getWave());
		assertEquals(ArenaTiles.safety(10), opening.instruction.getTarget().getTile());
		for (int tick : new int[]{2170, 2171, 2172, 2173, 2174})
		{
			RoundReplay.Frame release = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			assertEquals("tick " + tick, Step.DEF_STEP, release.instruction.getStep());
			assertEquals(ArenaTiles.trapStep(10), release.instruction.getTarget().getTile());
			assertEquals(1, release.snapshot.alive(PenanceType.RUNNER).stream().filter(p -> p.isUnseen()).count());
		}
		RoundReplay.Frame eggs = frames.stream().filter(f -> f.raw.t == 2482).findFirst().get();
		assertEquals("DEF_SUPPORT", eggs.raw.live.step);
		assertEquals(Step.QUEEN_LAVA, eggs.instruction.getStep());
		assertEquals(10, eggs.snapshot.getWave());
	}

	@Test public void latestWaveNineReleasesFiveGatheredRunnersDespiteAStaleUnseenRunner() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-19_22-49-19-wave9.jsonl")));
		for (int tick : new int[]{1684, 1685})
		{
			RoundReplay.Frame release = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			assertEquals(Step.DEF_STEP, release.instruction.getStep());
			assertEquals(ArenaTiles.trapStep(9), release.instruction.getTarget().getTile());
			assertEquals(1, release.snapshot.alive(PenanceType.RUNNER).stream().filter(p -> p.isUnseen()).count());
		}
	}

	@Test public void defenderMainstackSceneReplacementKeepsItsOriginalGoodFood() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_18-08-35-wave6.jsonl")));
		for (int tick : new int[]{4262, 4263, 4264})
		{
			RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			List<com.bautopilot.baautopilot.state.GroundItemView> bait = frame.snapshot.getGroundItems().stream()
				.filter(g -> g.getItemId() == com.bautopilot.baautopilot.model.BaCall.BAIT_WORMS.getItemId()
					&& g.getTile().equals(ArenaTiles.mainstack(6))).collect(Collectors.toList());
			assertEquals("tick " + tick, 2, bait.stream().mapToInt(g -> g.getQuantity()).sum());
			assertTrue("tick " + tick, bait.stream().allMatch(g -> g.isGood() && g.getSpawnTick() < 4200));
			assertEquals(Integer.valueOf(4), frame.snapshot.getGoodBaitLanded().get(ArenaTiles.mainstack(6)));
			assertFalse(frame.snapshot.getBadBaitLanded().containsKey(ArenaTiles.mainstack(6)));
		}
	}

	@Test public void defenderUsesWaveEightWaitingTimeToPrepareTheTrapFood() throws IOException
	{
		RoundReplay.Frame frame = advancedDefender(RecordedRound.read(RECORDINGS.resolve("2026-10-18_18-13-19-wave8.jsonl")))
			.stream().filter(f -> f.raw.t == 4669).findFirst().get();
		assertEquals("def.prepare", frame.instruction.getTask());
		assertEquals(Step.DEF_DROP, frame.instruction.getStep());
		assertEquals(ArenaTiles.trapFood(8), frame.instruction.getTarget().getTile());
		assertTrue(frame.instruction.getItems().contains(com.bautopilot.baautopilot.model.BaCall.BAIT_TOFU.getItemId()));
		assertTrue(frame.instruction.getHeadline(), frame.instruction.getHeadline().contains("drop 2 Tofu"));
	}

	@Test public void latestDefenderDoesNotRestockWhileHoldingOrReleaseAheadOfArrivals() throws IOException
	{
		RoundReplay.Frame restock = advancedDefender(RecordedRound.read(RECORDINGS.resolve("2026-10-18_18-02-54-wave2.jsonl")))
			.stream().filter(f -> f.raw.t == 3624).findFirst().get();
		assertEquals("DEF_TAKE_BAIT", restock.raw.live.step);
		assertEquals(Step.DEF_HOLD, restock.instruction.getStep());
		assertEquals(ArenaTiles.trapFood(2), restock.instruction.getTarget().getTile());
		RoundReplay.Frame early = advancedDefender(RecordedRound.read(RECORDINGS.resolve("2026-10-18_18-07-06-wave5.jsonl")))
			.stream().filter(f -> f.raw.t == 4054).findFirst().get();
		assertEquals("DEF_STEP", early.raw.live.step);
		assertEquals(Step.DEF_DROP, early.instruction.getStep());
		assertEquals("def.prepare", early.instruction.getTask());
		assertEquals(ArenaTiles.trapFood(5), early.instruction.getTarget().getTile());
	}

	@Test public void waveNineRunnerTimeoutCannotDeclareCompletionBeforeTheActualClear() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_18-15-53-wave9.jsonl")));
		for (int tick : new int[]{5069, 5081})
		{
			RoundReplay.Frame missing = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			assertEquals(6, missing.snapshot.getRunnersKilled());
			assertEquals(3, missing.snapshot.getUncertainRunners());
			assertEquals(Integer.valueOf(6), missing.snapshot.getKillsByType().get(PenanceType.RUNNER));
			assertNotEquals(Step.DEF_SUPPORT, missing.instruction.getStep());
		}
		RoundReplay.Frame returned = frames.stream().filter(f -> f.raw.t == 5082).findFirst().get();
		assertEquals(6, returned.snapshot.getRunnersKilled());
		assertEquals(2, returned.snapshot.getUncertainRunners());
		for (RoundReplay.Frame frame : frames)
			if (frame.raw.t < 5138) assertNotEquals("tick " + frame.raw.t, Step.DEF_SUPPORT, frame.instruction.getStep());
		RoundReplay.Frame clear = frames.stream().filter(f -> f.raw.t == 5138).findFirst().get();
		assertTrue(clear.snapshot.getCleared().contains(PenanceType.RUNNER));
		assertEquals(9, clear.snapshot.getRunnersKilled());
		assertEquals(0, clear.snapshot.getUncertainRunners());
		assertEquals(Step.DEF_SUPPORT, clear.instruction.getStep());
	}

	@Test public void latestAttackerRunGetsArrowsBeforeSpikesAndKeepsTheOmegaVolleyReady() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_17-51-27-wave10.jsonl")));
		RoundReplay.Frame opening = frames.stream().filter(f -> f.raw.t == 2422).findFirst().get();
		assertEquals("ATT_SPIKES", opening.raw.live.step);
		assertEquals(Step.ATT_GET_ARROWS, opening.instruction.getStep());
		RoundReplay.Frame passed = frames.stream().filter(f -> f.raw.t == 2918).findFirst().get();
		assertEquals(3, passed.snapshot.getQueenSpikedPassed());
		assertNotEquals(Step.ATT_SPIKES, passed.instruction.getStep());
		for (int tick : new int[]{2986, 2989})
		{
			RoundReplay.Frame loaded = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			assertEquals(Step.QUEEN_FIRE, loaded.instruction.getStep());
			assertEquals(net.runelite.api.gameval.InterfaceID.BarbassaultTurret.OMEGA_QUEEN,
				com.bautopilot.baautopilot.plan.TurretChoice.pick(loaded.snapshot));
		}
		List<RoundReplay.Frame> ordinary = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_17-27-52-wave1.jsonl")));
		assertTrue(ordinary.stream().anyMatch(f -> f.instruction.getStep() == Step.ATT_POSITION));
		assertTrue(ordinary.stream().filter(f -> f.snapshot.getFighterRangerKills() > 0)
			.noneMatch(f -> f.instruction.getStep() == Step.ATT_POSITION));
	}

	@Test public void syntheticDirectedHandoffReplaysFromTheNewPublicPlayerFields() throws IOException
	{
		RecordedRound round = RecordedRound.read(RECORDINGS.resolve("2026-10-18_17-51-27-wave10.jsonl"));
		Rec.Tick raw = round.ticks.stream().filter(t -> t.t == 2985).findFirst().get();
		// This old capture lacks roles/interaction. Supply explicit scenario observations, not inferred inventory.
		Rec.Plr defender = raw.players.stream().filter(p -> p.n.equals("Player11")).findFirst().get();
		Rec.Plr collector = raw.players.stream().filter(p -> p.n.equals("Player5")).findFirst().get();
		defender.role = com.bautopilot.baautopilot.model.Role.DEFENDER;
		defender.interacting = collector.n;
		collector.role = com.bautopilot.baautopilot.model.Role.COLLECTOR;
		RoundReplay.Frame handoff = RoundReplay.run(round).stream().filter(f -> f.raw.t == 2985).findFirst().get();
		assertEquals(0, handoff.snapshot.getEggsOmega());
		assertEquals(2985, handoff.snapshot.getQueenOmegaHandoffTick());
		assertEquals(Step.QUEEN_FIRE, handoff.instruction.getStep());
	}

	@Test
	public void latestQueenCollectorWaitsForItsBatchAndPositionsForOneAtATimeLoading() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_13-48-27-wave10.jsonl")));
		RoundReplay.Frame watch = frames.stream().filter(f -> f.raw.t == 3135).findFirst().get();
		assertEquals(Step.COL_LOAD, watch.instruction.getStep());
		assertTrue(watch.instruction.getHeadline().contains("Preload"));
		RoundReplay.Frame firstEgg = frames.stream().filter(f -> f.raw.t == 3159).findFirst().get();
		assertEquals("QUEEN_PASS", firstEgg.raw.live.step);
		assertEquals(1, firstEgg.snapshot.count(com.bautopilot.baautopilot.model.BaIds.EGG_YELLOW));
		assertNotEquals(Step.QUEEN_PASS, firstEgg.instruction.getStep());
		assertEquals(Step.COL_LOAD, firstEgg.instruction.getStep());
		assertTrue(firstEgg.instruction.getHeadline().contains("Preload"));
		RoundReplay.Frame load = frames.stream().filter(f -> f.raw.t == 3424).findFirst().get();
		assertEquals("QUEEN_LOAD", load.raw.live.step);
		assertEquals(Step.QUEEN_LOAD_POSITION, load.instruction.getStep());
		assertEquals(load.snapshot.nearestObject(com.bautopilot.baautopilot.model.BaIds.HOPPERS).getTile().dy(-2), load.instruction.getTarget().getTile());
		assertFalse(frames.stream().anyMatch(f -> f.snapshot.isQueenAlive() && f.instruction.getStep() == Step.QUEEN_LOAD));
	}

	@Test
	public void collectorNavigationRegressionsFromTheLatestRun() throws IOException
	{
		List<RoundReplay.Frame> wave1 = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_13-22-35-wave1.jsonl")));
		RoundReplay.Frame opening = wave1.stream().filter(f -> f.raw.t == 307).findFirst().get();
		assertEquals(Step.COL_POSITION, opening.instruction.getStep());
		assertEquals(ArenaTiles.collectorWestApproach(1), opening.instruction.getTarget().getTile());
		// A pile already underfoot may still fit at 351; the old one-tile trip no longer fits.
		RoundReplay.Frame lastSafe = wave1.stream().filter(f -> f.raw.t == 351).findFirst().get();
		if (lastSafe.instruction.getStep() == Step.COL_COLLECT)
			assertEquals(0, lastSafe.snapshot.walk(lastSafe.instruction.getTarget().getTile()));
		assertNotEquals(Step.COL_COLLECT, wave1.stream().filter(f -> f.raw.t == 353).findFirst().get().instruction.getStep());
		assertTrue(wave1.stream().filter(f -> f.raw.t == 365).findFirst().get().instruction.getHeadline().endsWith("×2"));
		RoundReplay.Frame nearEgg = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_13-28-25-wave3.jsonl")))
			.stream().filter(f -> f.raw.t == 917).findFirst().get();
		assertEquals(Step.COL_COLLECT, nearEgg.instruction.getStep());
		assertEquals(3, nearEgg.snapshot.walk(nearEgg.instruction.getTarget().getTile()));
		List<RoundReplay.Frame> wave5 = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_13-33-08-wave5.jsonl")));
		for (int tick : new int[]{1246, 1247, 1248})
		{
			RoundReplay.Frame f = wave5.stream().filter(frame -> frame.raw.t == tick).findFirst().get();
			assertEquals(Step.COL_LOAD, f.instruction.getStep());
			assertEquals(f.snapshot.nearestObject(com.bautopilot.baautopilot.model.BaIds.HOPPERS).getTile(), f.instruction.getTarget().getTile());
		}
	}

	@Test
	public void latestQueenDunkTripSurvivesTheCapturedHpDrops() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_12-33-39-wave10.jsonl")));
		for (int tick = 3576; tick <= 3585; tick++)
		{
			int at = tick;
			RoundReplay.Frame f = frames.stream().filter(frame -> frame.raw.t == at).findFirst().get();
			assertEquals("tick " + tick, Step.QUEEN_DUNK, f.instruction.getStep());
		}
		RoundReplay.Frame exact = frames.stream().filter(f -> f.raw.t == 3585).findFirst().get();
		assertEquals(47, exact.snapshot.getHp());
		RoundReplay.Frame filler = frames.stream().filter(f -> f.raw.t == 3575).findFirst().get();
		assertEquals(0, filler.instruction.getPriorities().stream().filter(p -> "queen.heal.Player25".equals(p.getTask()))
			.findFirst().get().getScoreTerms().get("continue current action"), .001);
		assertTrue(exact.snapshot.getSelfDamagePerTick() > 0);
		assertTrue(exact.instruction.getScoreTerms().get("continue current action") > 0);
		assertEquals(Step.QUEEN_PASS, frames.stream().filter(f -> f.raw.t == 3586).findFirst().get().instruction.getStep());
	}

	@Test
	public void latestQueenHandsOffThePoisonedEggBeforeHealingFortyEightHp() throws IOException
	{
		RoundReplay.Frame f = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_12-33-39-wave10.jsonl")))
			.stream().filter(frame -> frame.raw.t == 3639).findFirst().get();
		assertEquals("QUEEN_PASS", f.raw.live.step);
		assertEquals(48, f.snapshot.getTeammates().stream().filter(t -> t.getName().equals("Player22")).findFirst().get().getHp());
		assertEquals(Step.QUEEN_PASS, f.instruction.getStep());
	}

	@Test
	public void latestQueenHealerCaptureHealsBeforeDunkingAndProtectsOwnHp() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_12-33-39-wave10.jsonl")));
		for (int tick : new int[]{3741, 3764, 3767})
		{
			RoundReplay.Frame f = frames.stream().filter(frame -> frame.raw.t == tick).findFirst().get();
			assertEquals("QUEEN_DUNK", f.raw.live.step);
			assertEquals("tick " + tick, Step.HEAL_TEAMMATE, f.instruction.getStep());
		}
		for (int tick : new int[]{3786, 3790})
		{
			RoundReplay.Frame f = frames.stream().filter(frame -> frame.raw.t == tick).findFirst().get();
			assertEquals(29, f.snapshot.getHp());
			assertEquals(Step.RETREAT, f.instruction.getStep());
		}
	}

	@Test
	public void dyingHealerReturningToViewKeepsItsConfirmedFoodHistory() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_12-33-39-wave10.jsonl")));
		RoundInvariants.Report report = new RoundInvariants.Report();
		RoundInvariants.healerFoodCounts(frames, report);
		assertTrue(report.violations.toString(), report.violations.isEmpty());
		assertEquals(2, frames.stream().filter(f -> f.raw.t == 3484).findFirst().get().snapshot.getPenance().stream()
			.filter(p -> p.getNpcIndex() == 42598).findFirst().get().getFoods());
	}

	@Test
	public void scarceOpeningRedsStayLocalAndDoNotYieldToPrematureLoading() throws IOException
	{
		int originalCannonTicks = 0, safeDetours = 0;
		for (String name : List.of("2026-10-18_11-41-21-wave1.jsonl", "2026-10-18_11-46-01-wave2.jsonl"))
		{
			List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve(name)));
			for (RoundReplay.Frame frame : frames)
			{
				com.bautopilot.baautopilot.state.Snapshot s = frame.snapshot;
				if (s.getListenCall() != com.bautopilot.baautopilot.model.BaCall.RED_EGG || !s.isListenFresh()
					|| s.getFreeSlots() <= 0 || s.getCallPeriod() >= 2) continue;
				int held = s.count(com.bautopilot.baautopilot.model.BaIds.EGG_RED) + s.getBagRed();
				boolean local = s.getGroundItems().stream().anyMatch(g -> g.getItemId() == s.getListenCall().getItemId()
					&& s.walk(g.getTile()) <= 6 && Math.min(200 - (s.getTick() - g.getSpawnTick()), s.getTicksToCallChange()) > (s.walk(g.getTile()) + 1) / 2 + 3);
				if (held <= 4 && local)
					assertFalse(frame.raw.t + ": " + frame.instruction.getDetail(), frame.instruction.getStep() == Step.COL_LOAD || frame.instruction.getStep() == Step.COL_FIRE);
				if (frame.raw.live != null && ("COL_LOAD".equals(frame.raw.live.step) || "COL_FIRE".equals(frame.raw.live.step)))
				{
					originalCannonTicks++;
					if (held <= 4 && local)
					{
						safeDetours++;
						assertEquals("Captured safe egg at tick " + frame.raw.t, Step.COL_COLLECT, frame.instruction.getStep());
					}
				}
			}
			if (name.contains("wave2"))
			{
				RoundReplay.Frame exact = frames.stream().filter(f -> f.raw.t == 569).findFirst().get();
				assertEquals(24, exact.snapshot.getFreeSlots());
				assertEquals(4, exact.snapshot.count(com.bautopilot.baautopilot.model.BaIds.EGG_RED) + exact.snapshot.getBagRed());
				assertEquals(Step.COL_COLLECT, exact.instruction.getStep());
			}
		}
		assertEquals(35, originalCannonTicks);
		assertTrue("Opening detours with scarce nearby reds are checked", safeDetours > 0);
	}

	@Test public void stockedCollectorLoadsWithoutSeeingRunnersAndRejectsTheCapturedLongEggTrip() throws IOException
	{
		RoundReplay.Frame stocked = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_13-26-38-wave2.jsonl")))
			.stream().filter(f -> f.raw.t == 605).findFirst().get();
		assertEquals(10, stocked.snapshot.count(com.bautopilot.baautopilot.model.BaIds.EGG_RED) + stocked.snapshot.getBagRed());
		assertEquals(0, stocked.snapshot.getEggsRed());
		assertEquals(Step.COL_LOAD, stocked.instruction.getStep());
		assertEquals("col.load", stocked.instruction.getTask());
		RoundReplay.Frame distant = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-15_20-41-28-wave7.jsonl")))
			.stream().filter(f -> f.raw.t == 1602).findFirst().get();
		assertNotEquals(Step.COL_COLLECT, distant.instruction.getStep());
	}

	@Test
	public void labelledBlueWindowCollectsNearbyEggsTowardTheAttackersInsteadOfLoadingTwoGreens() throws IOException
	{
		RoundReplay.Frame frame = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_11-46-01-wave2.jsonl")))
			.stream().filter(f -> f.raw.t == 682).findFirst().get();
		assertEquals("COL_LOAD", frame.raw.live.step);
		assertEquals(Step.COL_COLLECT, frame.instruction.getStep());
		assertEquals(com.bautopilot.baautopilot.model.BaIds.EGG_BLUE, frame.instruction.getTarget().getId());
		net.runelite.api.coords.WorldPoint attackers = ArenaTiles.attackerWait(frame.snapshot.getWave());
		assertTrue(ArenaTiles.distance(frame.instruction.getTarget().getTile(), attackers)
			< ArenaTiles.distance(frame.snapshot.getPlayerTile(), attackers));
	}

	@Test
	public void latestWaveEightCoversTheUndamagedHealerInsteadOfTheWoundedRefresh() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_21-54-13-wave8.jsonl")));
		RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == 3158).findFirst().get();
		assertTrue(frame.raw.live.headline.contains("healer #5"));
		com.bautopilot.baautopilot.state.PenanceView fresh = frame.snapshot.alive(PenanceType.HEALER).stream()
			.filter(h -> h.getOrder() == 6).findFirst().get();
		assertEquals(0, fresh.getFoods());
		assertEquals(0, fresh.getDamageTaken());
		assertEquals(0, fresh.nextPoisonHit(frame.snapshot.getTick()));
		assertEquals(Step.HEAL_FEED, frame.instruction.getStep());
		assertEquals("heal.cover", frame.instruction.getTask());
		assertEquals(fresh.getNpcIndex(), frame.instruction.getTarget().getId());
	}

	@Test
	public void latestWaveTenIgnoresTheAnimationAndRecognizesActualPoisonDamage() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_22-01-44-wave10.jsonl")));
		for (int tick : new int[]{3835, 3853, 3854})
		{
			RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			com.bautopilot.baautopilot.state.PenanceView fresh = frame.snapshot.alive(PenanceType.HEALER).stream()
				.filter(h -> h.getOrder() == 3).findFirst().get();
			assertEquals(0, fresh.nextPoisonHit(tick));
			assertEquals(0, fresh.getDamageTaken());
			assertEquals(fresh.getNpcIndex(), frame.instruction.getTarget().getId());
			assertEquals(Step.HEAL_FEED, frame.instruction.getStep());
		}
		RoundReplay.Frame waited = frames.stream().filter(f -> f.raw.t == 3853).findFirst().get();
		assertEquals("HEAL_WAIT", waited.raw.live.step);
		RoundReplay.Frame landed = frames.stream().filter(f -> f.raw.t == 3862).findFirst().get();
		assertEquals(4, landed.snapshot.alive(PenanceType.HEALER).stream().filter(h -> h.getOrder() == 3).findFirst().get().nextPoisonHit(3862));
		assertEquals(1, landed.snapshot.alive(PenanceType.HEALER).stream()
			.filter(h -> h.getNpcIndex() == landed.instruction.getTarget().getId()).findFirst().get().getOrder());
	}

	@Test
	public void latestWaveNineSeeksExpiredPoisonBeforeStrongerVisiblePoison() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_21-58-22-wave9.jsonl")));
		RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == 3603).findFirst().get();
		assertTrue(frame.raw.live.headline.contains("healer #8"));
		com.bautopilot.baautopilot.state.PenanceView target = frame.snapshot.alive(PenanceType.HEALER).stream()
			.filter(h -> h.getNpcIndex() == frame.instruction.getTarget().getId()).findFirst().get();
		assertEquals(6, target.getOrder());
		assertTrue(target.isUnseen());
		assertEquals(0, target.nextPoisonHit(frame.snapshot.getTick()));
	}

	@Test
	public void passingSafetyKeepsTheChosenDestinationForBothDropdownVariants() throws IOException
	{
		RecordedRound round = RecordedRound.read(RECORDINGS.resolve("2026-10-17_20-42-18-wave8.jsonl"));
		com.bautopilot.baautopilot.plan.Planner planner = new com.bautopilot.baautopilot.plan.Planner();
		List<RoundReplay.Frame> frames = RoundReplay.run(round, (s, settings) -> planner.plan(s, settings.toBuilder()
			.defenderGuide(com.bautopilot.baautopilot.model.DefenderGuide.WIKI_NO_SAFETY).build()));
		List<RoundReplay.Frame> safetyFirst = RoundReplay.run(round);
		RoundReplay.Frame misleading = frames.stream().filter(f -> f.raw.t == 6325).findFirst().get();
		assertEquals("Drop 1 bad at Safety", misleading.raw.live.headline);
		for (int tick : new int[]{6324, 6325, 6326})
		{
			RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == tick).findFirst().get();
			assertEquals(Step.DEF_DROP, frame.instruction.getStep());
			assertEquals(ArenaTiles.trapFood(8), frame.instruction.getTarget().getTile());
			assertEquals(ArenaTiles.safety(8), safetyFirst.stream().filter(f -> f.raw.t == tick).findFirst().get().instruction.getTarget().getTile());
		}
	}

	@Test
	public void walkingBetweenCapturedToolsDoesNotChangeTheSelectedItem() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_20-47-14-wave9.jsonl")));
		RoundReplay.Frame first = frames.stream().filter(f -> f.raw.t == 6851).findFirst().get();
		RoundReplay.Frame moved = frames.stream().filter(f -> f.raw.t == 6852).findFirst().get();
		assertEquals("DEF_HAMMER", first.raw.live.step);
		assertEquals("DEF_LOGS", moved.raw.live.step);
		assertEquals(Step.DEF_HAMMER, first.instruction.getStep());
		assertEquals(Step.DEF_HAMMER, moved.instruction.getStep());
		assertEquals(first.instruction.getTarget(), moved.instruction.getTarget());
	}

	@Test
	public void waveTenUsefulAmmoCanBeLoadedWithoutAnActiveShooter() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_20-10-05-wave10.jsonl")));
		RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == 3226).findFirst().get();
		assertEquals("COL_LOAD", frame.raw.live.step);
		assertTrue(frame.raw.live.detail.contains("available Attacker or Healer"));
		assertEquals(-1, frame.snapshot.getLastLauncherShotTick());
		assertTrue(frame.snapshot.getFreeSlots() > 3);
		assertEquals(0, frame.snapshot.getEggsRed() + frame.snapshot.getEggsGreen() + frame.snapshot.getEggsBlue());
		assertEquals(Step.COL_LOAD, frame.instruction.getStep());
		assertTrue(frame.instruction.getScoreTerms().get("useful wave-10 ammunition") > 0);
	}

	@Test
	public void latestQueenCaptureKeepsTheUnusedReserveAfterOnlyTwoOmegas() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-18_16-01-00-wave10.jsonl")));
		RoundReplay.Frame firstRed = frames.stream().filter(f -> f.raw.t == 2969).findFirst().get();
		assertEquals("Early red collection must beat a premature green preload trip", Step.COL_COLLECT, firstRed.instruction.getStep());
		assertEquals(5, frames.stream().mapToInt(f -> f.snapshot.getQueenYellowPassed()).max().getAsInt());
		// A third egg stays loaded through the final arena tick; the lobby reset is not a shot.
		assertEquals(2, frames.stream().mapToInt(f -> f.snapshot.getQueenOmegaFired()).max().getAsInt());
		for (RoundReplay.Frame frame : frames)
		{
			if (frame.snapshot.getQueenYellowPassed() >= 5)
				assertNotEquals("Reserve before the initial volley, tick " + frame.raw.t, Step.QUEEN_PASS, frame.instruction.getStep());
			if (frame.snapshot.isQueenDead())
			{
				assertNotEquals(Step.QUEEN_PASS, frame.instruction.getStep());
				assertNotEquals(Step.QUEEN_COLLECT, frame.instruction.getStep());
			}
		}
	}

	@Test
	public void fullPoisonFoodNeedsAReserveReasonRatherThanACodeQuantity() throws IOException
	{
		List<RoundReplay.Frame> frames = RoundReplay.run(RecordedRound.read(RECORDINGS.resolve("2026-10-17_18-24-13-wave8.jsonl")));
		RoundReplay.Frame frame = frames.stream().filter(f -> f.raw.t == 2033).findFirst().get();
		assertEquals("HEAL_FEED", frame.raw.live.step);
		assertTrue(frame.raw.live.detail.contains("1 more"));
		assertTrue(frame.snapshot.alive(PenanceType.HEALER).stream()
			.anyMatch(h -> h.getOrder() == 1 && h.poisoned() && h.nextPoisonHit(frame.snapshot.getTick()) == 4));
		assertEquals("heal.reserve", frame.instruction.getTask());
		assertTrue(frame.instruction.getDetail().contains("2 reserves still need kills"));
		assertEquals(0, frame.snapshot.getHealersKilled());
		assertEquals(7, frame.snapshot.count(frame.snapshot.getListenCall().getItemId()));
		assertTrue(frame.instruction.getDetail().contains("Keep 6 food for coverage"));
		assertFalse(frame.instruction.getDetail().contains("1 more"));
	}

	@Test
	public void latestHealerCapturePromptsTheFirstFeedBeforeTheCodeDelay() throws IOException
	{
		RecordedRound round = RecordedRound.read(RECORDINGS.resolve("2026-10-17_17-52-14-wave10.jsonl"));
		List<RoundReplay.Frame> frames = RoundReplay.run(round);
		RoundReplay.Frame first = frames.stream().filter(f -> f.raw.t == 3502).findFirst().get();
		assertEquals("The live capture waited until 21s at this tick", "HEAL_WAIT", first.raw.live.step);
		assertTrue(first.snapshot.isListenFresh());
		assertEquals(Step.HEAL_FEED, first.instruction.getStep());
		assertEquals("heal.cover", first.instruction.getTask());
		assertTrue(first.snapshot.alive(PenanceType.HEALER).stream().anyMatch(h -> h.getOrder() == 1
			&& !h.poisoned() && h.getNpcIndex() == first.instruction.getTarget().getId()));
		// Replaying another policy or round cannot contaminate task history.
		RoundReplay.Frame repeated = RoundReplay.run(round).stream().filter(f -> f.raw.t == 3502).findFirst().get();
		assertEquals(first.instruction, repeated.instruction);
		assertFalse(first.instruction.getTasks().getEntries().isEmpty());
	}

	@Test
	public void everyRecordingHoldsItsInvariants() throws IOException
	{
		List<Path> files;
		try (Stream<Path> s = Files.isDirectory(RECORDINGS) ? Files.list(RECORDINGS) : Stream.empty())
		{
			files = s.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
		Assume.assumeFalse("no recordings in " + RECORDINGS.toAbsolutePath(), files.isEmpty());

		StringBuilder failures = new StringBuilder();
		for (Path file : files)
		{
			RecordedRound round = RecordedRound.read(file);
			List<RoundReplay.Frame> frames = RoundReplay.run(round);
			RoundInvariants.Report report = RoundInvariants.check(round, frames);
			System.out.println(round.name() + ": " + frames.size() + " ticks, " + report.violations.size() + " violations");
			report.notes.forEach(n -> System.out.println("  " + n));
			if (!report.violations.isEmpty())
			{
				failures.append('\n').append(round.name()).append(':');
				report.violations.stream().limit(MAX_LISTED).forEach(v -> failures.append("\n  ").append(v));
				if (report.violations.size() > MAX_LISTED)
				{
					failures.append("\n  ... and ").append(report.violations.size() - MAX_LISTED).append(" more");
				}
			}
		}
		if (failures.length() > 0)
		{
			fail("Recorded rounds broke invariants (gradlew replay -Pfile=<file> shows the timeline):" + failures);
		}
	}
}
