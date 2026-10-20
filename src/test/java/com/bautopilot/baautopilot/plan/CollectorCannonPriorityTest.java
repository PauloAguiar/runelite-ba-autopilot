package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.model.PenanceType;
import com.bautopilot.baautopilot.model.Role;
import com.bautopilot.baautopilot.state.ObjectView;
import com.bautopilot.baautopilot.state.PenanceView;
import com.bautopilot.baautopilot.state.RoundLogic;
import com.bautopilot.baautopilot.state.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorCannonPriorityTest
{
	private static final int R = ArenaTiles.REGION_WAVES_1_9;
	private static final WorldPoint ME = ArenaTiles.p(R, 40, 45);
	private static final Settings SETTINGS = Settings.builder().collectorLoadAt(15).build();
	private final Planner planner = new Planner();

	private static Snapshot base()
	{
		return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(3).waveTick(70)
			.listenCall(BaCall.GREEN_EGG).listenFresh(true).shoutDone(true).freeSlots(10).hp(99).maxHp(99).playerTile(ME)
			.inventory(Map.of(BaIds.EGG_RED, 15)).eggsRed(2).eggsGreen(3)
			.objects(List.of(new ObjectView(BaIds.HOPPERS[0], ME.dy(-10)), new ObjectView(BaIds.LAUNCHERS[0], ME.dy(-9))))
			.groundItems(List.of(new com.bautopilot.baautopilot.state.GroundItemView(BaIds.EGG_GREEN, ME.dx(1), 1, false, 990)))
			.penance(List.of(npc(PenanceType.HEALER, ME.dy(-1)))).build();
	}

	private static PenanceView npc(PenanceType type, WorldPoint tile)
	{
		return PenanceView.builder().npcIndex(9).type(type).tile(tile).build();
	}

	@Test
	public void lowRedsRequireAConfirmedRecentShotRatherThanAnOldAverage()
	{
		Snapshot s = base().toBuilder().launcherShotsPerTick(0.8).build();
		assertFalse(CollectorPlanner.supportReady(s));
		double prior = Double.POSITIVE_INFINITY;
		for (int age : new int[]{0, 4, 8})
		{
			Instruction urgent = planner.plan(s.toBuilder().lastLauncherShotTick(1000 - age).build(), SETTINGS);
			double value = urgent.getPriorities().stream().filter(p -> p.getHeadline().startsWith("Load"))
				.findFirst().get().getScoreTerms().get("active shooter");
			assertTrue(value > 0 && value < prior);
			prior = value;
		}
		for (int shot : new int[]{-1, 991, 1001})
		{
			Snapshot inactive = s.toBuilder().lastLauncherShotTick(shot).build();
			assertFalse(CollectorPlanner.supportReady(inactive));
			Instruction supply = planner.plan(inactive, SETTINGS);
			assertEquals(Step.COL_LOAD, supply.getStep());
			assertEquals("col.load", supply.getTask());
			assertNotEquals(Tier.TEAM_BLOCKING, supply.getTier());
		}
	}

	@Test
	public void healthyRedsOrGreenOnlyInventoryDoNotJustifyARedRefill()
	{
		Snapshot active = base().toBuilder().lastLauncherShotTick(1000).build();
		assertFalse(CollectorPlanner.supportReady(active.toBuilder().eggsRed(3).build()));
		assertFalse(CollectorPlanner.supportReady(active.toBuilder().eggsRed(5).eggsGreen(0).build()));
		assertFalse(CollectorPlanner.supportReady(active.toBuilder().inventory(Map.of(BaIds.EGG_GREEN, 20)).build()));
		assertFalse(CollectorPlanner.supportReady(active.toBuilder().eggsRed(0).lastLauncherShotTick(-1).build()));
	}

	@Test
	public void onlyEscapingRunnersCreateTheRunnerException()
	{
		Snapshot empty = base().toBuilder().eggsRed(0).eggsGreen(0).build();
		WorldPoint trap = ArenaTiles.trapFood(3);
		for (WorldPoint tile : List.of(trap, trap.dy(5)))
		{
			assertFalse(CollectorPlanner.supportReady(empty.toBuilder().penance(List.of(npc(PenanceType.RUNNER, tile))).build()));
		}
		Snapshot escaping = empty.toBuilder().penance(List.of(npc(PenanceType.RUNNER, trap.dy(-4)))).build();
		assertTrue(CollectorPlanner.supportReady(escaping));
		assertEquals(Step.COL_LOAD, planner.plan(escaping, SETTINGS).getStep());
		assertFalse(CollectorPlanner.supportReady(escaping.toBuilder()
			.penance(List.of(npc(PenanceType.RUNNER, trap.dy(-40)))).build()));
	}

	@Test
	public void loadedAmmoDoesNotPullTheCollectorIntoOrdinaryShooting()
	{
		Snapshot s = base().toBuilder().eggsRed(5).inventory(Map.of(BaIds.EGG_GREEN, 20))
			.penance(List.of(npc(PenanceType.RUNNER, ArenaTiles.trapFood(3).dy(-4)))).build();
		assertFalse(CollectorPlanner.supportReady(s));
		Instruction fire = planner.plan(s, SETTINGS);
		assertEquals(Step.COL_COLLECT, fire.getStep());
		assertTrue(fire.getPriorities().stream().filter(p -> "col.escape.fire".equals(p.getTask()))
			.allMatch(p -> p.getScore() < fire.getScore()));
	}

	@Test
	public void anExpiredSupportTripCannotBecomeABlueBatchCommitment()
	{
		Snapshot s = base().toBuilder().listenCall(BaCall.BLUE_EGG).inventory(Map.of(BaIds.EGG_RED, 3))
			.eggsRed(1).eggsGreen(0).lastLauncherShotTick(1000).build();
		Instruction urgent = planner.plan(s, SETTINGS);
		assertEquals("col.support", urgent.getTask());
		Snapshot stale = s.toBuilder().tick(1009).eggsRed(0).tasks(urgent.getTasks()).lastTask(urgent.getTask())
			.lastTargetTile(urgent.getTarget().getTile()).groundItems(List.of(new com.bautopilot.baautopilot.state.GroundItemView(
				BaIds.EGG_BLUE, ME.dx(1), 1, false, 1000))).penance(List.of()).build();
		Instruction expired = planner.plan(stale, SETTINGS);
		assertNotEquals(Step.COL_LOAD, expired.getStep());
		assertNotEquals(Tier.TEAM_BLOCKING, expired.getTier());
		assertFalse(expired.getTasks().getEntries().containsKey("col.support"));
		assertEquals(-1, expired.getTasks().lastActive("col.load"));
	}

	@Test
	public void observedAmmoDropsRecordShotsButLoadingAndResetDoNot()
	{
		RoundLogic logic = new RoundLogic();
		RoundLogic.Hud hud = new RoundLogic.Hud()
		{
			public boolean visible(int component) { return false; }
			public String text(int component) { return null; }
		};
		logic.tick(1000, true, hud, Map.of(), 0, 5, 0);
		assertEquals(-1, logic.snapshot().build().getLastLauncherShotTick());
		logic.tick(1001, true, hud, Map.of(), 0, 4, 0);
		assertEquals(1001, logic.snapshot().build().getLastLauncherShotTick());
		logic.tick(1002, true, hud, Map.of(), 0, 5, 0);
		assertEquals(1001, logic.snapshot().build().getLastLauncherShotTick());
		logic.tick(1010, true, hud, Map.of(), 0, 5, 0);
		assertFalse(CollectorPlanner.activelyFiring(logic.snapshot().build()));
		logic.reset();
		assertEquals(-1, logic.snapshot().build().getLastLauncherShotTick());
	}
}
