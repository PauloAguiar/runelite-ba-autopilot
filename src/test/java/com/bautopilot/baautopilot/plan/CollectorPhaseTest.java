package com.bautopilot.baautopilot.plan;

import com.bautopilot.baautopilot.model.*;
import com.bautopilot.baautopilot.state.*;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectorPhaseTest
{
 private static final int R = ArenaTiles.REGION_WAVES_1_9;
 private static final WorldPoint ME = ArenaTiles.p(R, 40, 45);
 private static final Settings SETTINGS = Settings.builder().collectorLoadAt(15).build();
 private final Planner planner = new Planner();
 static Snapshot phase(BaCall call, int reds) {
  return Snapshot.builder().tick(1000).inArena(true).role(Role.COLLECTOR).wave(3).callPeriod(1).waveTick(70)
   .listenCall(call).listenFresh(true).shoutDone(true).freeSlots(10).hp(99).maxHp(99).playerTile(ME)
   .inventory(Map.of(BaIds.EGG_RED,reds)).eggsRed(0).eggsGreen(0)
   .objects(List.of(new ObjectView(BaIds.HOPPERS[0],ME.dy(-10)),new ObjectView(BaIds.LAUNCHERS[0],ME.dy(-9))))
   .groundItems(List.of(new GroundItemView(call.getItemId(),ME.dx(1),1,false,990)))
   .penance(List.of(PenanceView.builder().npcIndex(9).type(PenanceType.RUNNER).tile(ArenaTiles.trapFood(3).dy(-6)).build())).build();
 }
 private Instruction plan(Snapshot s) { return planner.plan(s,SETTINGS); }
 private static double loadScore(Instruction ins) { return ins.getPriorities().stream().filter(p->p.getTask().equals("col.load")||p.getTask().equals("col.support")).mapToDouble(PriorityLine::getScore).max().getAsDouble(); }

 @Test public void redValueFallsSmoothlyWithReserveAndTime() {
  double prior=Double.POSITIVE_INFINITY;
  for(int reds=0;reds<28;reds++) { double value=CollectorScoring.redValue(phase(BaCall.RED_EGG,reds));assertTrue(value<prior);prior=value; }
  Snapshot s=phase(BaCall.RED_EGG,2);
  assertTrue(CollectorScoring.redValue(s.toBuilder().callPeriod(0).build())>CollectorScoring.redValue(s));
  assertTrue(CollectorScoring.redValue(s)>CollectorScoring.redValue(s.toBuilder().callPeriod(4).build()));
 }
 @Test public void theSecondRedBeatsLoadingButTheTwelfthCanYieldToAnEscaper() {
  assertEquals(Step.COL_COLLECT,plan(phase(BaCall.RED_EGG,1)).getStep());
  assertEquals(Step.COL_COLLECT,plan(phase(BaCall.RED_EGG,4)).getStep());
  assertEquals(Step.COL_LOAD,plan(phase(BaCall.RED_EGG,11)).getStep());
 }
 @Test public void lowReserveStillWinsAfterAnInterruptedTrip() {
  Instruction trip=plan(phase(BaCall.RED_EGG,11));
  Snapshot red=phase(BaCall.RED_EGG,2).toBuilder().tasks(trip.getTasks()).lastTask(trip.getTask()).lastTargetTile(trip.getTarget().getTile()).build();
  assertEquals(Step.COL_COLLECT,plan(red).getStep());
 }
 @Test public void loadingDoesNotLeadStraightIntoShootingAndReloading() {
  Snapshot stocked=phase(BaCall.RED_EGG,11);
  Instruction load=plan(stocked);assertEquals(Step.COL_LOAD,load.getStep());
  Snapshot delivered=stocked.toBuilder().inventory(Map.of(BaIds.EGG_RED,6)).eggsRed(5).playerTile(ME.dy(-10))
   .tasks(load.getTasks()).lastTask(load.getTask()).lastTargetTile(load.getTarget().getTile()).build();
  // The recorded egg is now eleven tiles away; don't chase it or fire the cannon ourselves.
  assertNotEquals(Step.COL_FIRE,plan(delivered).getStep());
  assertNotEquals(Step.COL_LOAD,plan(delivered).getStep());
  assertEquals(Step.COL_COLLECT,plan(delivered.toBuilder().groundItems(List.of(
   new GroundItemView(BaIds.EGG_RED,delivered.getPlayerTile().dx(1),1,false,990))).build()).getStep());
 }
 @Test public void smallAndDistantLoadsLoseValueWithoutAHardBatchCutoff() {
  Snapshot one=phase(BaCall.BLUE_EGG,1).toBuilder().penance(List.of()).build();
  assertTrue(loadScore(plan(one.toBuilder().inventory(Map.of(BaIds.EGG_RED,5)).build()))>loadScore(plan(one)));
  assertEquals(Step.COL_COLLECT,plan(one).getStep());
 }
 @Test public void fullInventoryCanMakeRoomAndFullHoppersCannotBeLoaded() {
  Snapshot full=phase(BaCall.RED_EGG,4).toBuilder().freeSlots(0).penance(List.of()).build();
  assertEquals(Step.COL_LOAD,plan(full).getStep());
  assertEquals(Step.COL_DISCARD,plan(full.toBuilder().inventory(Map.of(BaIds.EGG_RED,4,BaIds.EGG_BLUE,1)).build()).getStep());
  assertFalse(plan(full.toBuilder().eggsRed(5).eggsGreen(5).build()).getPriorities().stream().anyMatch(p->p.getHeadline().startsWith("Load")));
 }
 @Test public void greenCollectionWinsBeforeBlueAndGainsPressureNearCapacity() {
  Snapshot green=phase(BaCall.GREEN_EGG,5).toBuilder().penance(List.of()).build();
  assertEquals(Step.COL_COLLECT,plan(green).getStep());
  assertTrue(loadScore(plan(green.toBuilder().freeSlots(1).build()))>loadScore(plan(green.toBuilder().freeSlots(4).build())));
  assertEquals(Step.COL_LOAD,plan(green.toBuilder().freeSlots(0).build()).getStep());
 }
 @Test public void waitingForOpeningDropsBeatsPointlessCannonTrips() {
  Snapshot s=phase(BaCall.RED_EGG,2).toBuilder().groundItems(List.of()).penance(List.of())
   .inventory(Map.of(BaIds.EGG_RED,2,BaIds.EGG_BLUE,1)).build();
  assertEquals(Step.COL_FOLLOW,plan(s).getStep());
 }
 @Test public void scoreTermsAreFiniteAndExplainTheTotal() {
  Instruction decision=plan(phase(BaCall.RED_EGG,11));
  for(PriorityLine line:decision.getPriorities()) {
   assertTrue(Double.isFinite(line.getScore()));assertEquals(line.getScore(),line.getScoreTerms().values().stream().mapToDouble(Double::doubleValue).sum(),1e-8);
  }
  assertEquals(decision.getScore(),decision.getPriorities().stream().mapToDouble(PriorityLine::getScore).max().getAsDouble(),1e-8);
 }
}
