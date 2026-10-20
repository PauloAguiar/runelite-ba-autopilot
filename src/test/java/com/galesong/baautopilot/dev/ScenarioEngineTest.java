package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.state.Snapshot;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioEngineTest
{
	@Test public void unresolvedRunnerHistorySurvivesTimeoutAndClearsOnReturnOrGameConfirmation() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(RecordedRound.read(Path.of("src/test/resources/recordings/2026-10-18_18-15-53-wave9.jsonl")));
		com.google.gson.JsonArray ticks = engine.metadata().getAsJsonArray("ticks");
		for (int tick : new int[]{5069, 5082, 5138})
		{
			int index = -1;
			for (int j = 0; j < ticks.size(); j++) if (ticks.get(j).getAsInt() == tick) { index = j; break; }
			assertTrue(index >= 0);
			JsonObject input = engine.evaluate(index, index, new JsonObject(), new JsonObject()).getAsJsonObject("roles")
				.getAsJsonObject("DEFENDER").getAsJsonObject("input");
			int expected = tick == 5069 ? 3 : tick == 5082 ? 2 : 0;
			assertEquals(expected, input.get("uncertainRunners").getAsInt());
			assertEquals(expected, input.getAsJsonArray("uncertainPenance").size());
			for (com.google.gson.JsonElement npc : input.getAsJsonArray("uncertainPenance"))
			{
				assertTrue(npc.getAsJsonObject().get("unseen").getAsBoolean());
				assertTrue(npc.getAsJsonObject().get("unseenTicks").getAsInt() >= 60);
				assertNotEquals(5082 == tick ? 63595 : -1, npc.getAsJsonObject().get("npcIndex").getAsInt());
			}
		}
	}
	private static RecordedRound latest() throws IOException
	{
		return RecordedRound.read(Path.of("src/test/resources/recordings/2026-10-17_20-10-05-wave10.jsonl"));
	}

	@Test
	public void capturedRoleMatchesOrdinaryReplayAndRewindsAreIndependent() throws IOException
	{
		RecordedRound round = latest();
		ScenarioEngine engine = new ScenarioEngine(round);
		List<RoundReplay.Frame> arena = RoundReplay.run(round).stream()
			.filter(f -> f.snapshot.isInArena() && f.snapshot.getRole() != null).collect(Collectors.toList());
		int index = 0;
		while (arena.get(index).snapshot.getTick() != 3226) index++;
		JsonObject baseline = engine.evaluate(index, index, new JsonObject(), new JsonObject());
		assertEquals(ScenarioEngine.JSON.toJsonTree(arena.get(index).instruction),
			baseline.getAsJsonObject("roles").getAsJsonObject("COLLECTOR").get("decision"));
		JsonObject roles = ScenarioEngine.JSON.fromJson("{COLLECTOR:{hp:1}}", JsonObject.class);
		assertNotEquals(baseline, engine.evaluate(index + 1, index, new JsonObject(), roles));
		assertEquals(baseline, engine.evaluate(index, index, new JsonObject(), new JsonObject()));
		assertEquals(1, baseline.getAsJsonObject("roles").size());
		// A captured Healer exercises NPC targeting and food history as well as Collector tasks.
		RecordedRound healer = RecordedRound.read(Path.of("src/test/resources/recordings/2026-10-17_17-52-14-wave10.jsonl"));
		List<RoundReplay.Frame> healerFrames = RoundReplay.run(healer).stream()
			.filter(f -> f.snapshot.isInArena() && f.snapshot.getRole() != null).collect(Collectors.toList());
		int healIndex = 0;
		while (healerFrames.get(healIndex).snapshot.getTick() != 3502) healIndex++;
		assertEquals(ScenarioEngine.JSON.toJsonTree(healerFrames.get(healIndex).instruction),
			new ScenarioEngine(healer).evaluate(healIndex, healIndex, new JsonObject(), new JsonObject())
				.getAsJsonObject("roles").getAsJsonObject("HEALER").get("decision"));
	}

	@Test
	public void latestHealerScenarioUsesDamageEvidenceAndCoversTheHealthyNewcomer() throws IOException
	{
		RecordedRound round = RecordedRound.read(Path.of("src/test/resources/recordings/2026-10-17_21-54-13-wave8.jsonl"));
		ScenarioEngine engine = new ScenarioEngine(round);
		com.google.gson.JsonArray ticks = engine.metadata().getAsJsonArray("ticks");
		int index = 0;
		while (ticks.get(index).getAsInt() != 3158) index++;
		JsonObject result = engine.evaluate(index, index, new JsonObject(), new JsonObject());
		JsonObject decision = result.getAsJsonObject("roles").getAsJsonObject("HEALER").getAsJsonObject("decision");
		assertEquals("heal.cover", decision.get("task").getAsString());
		assertTrue(decision.get("headline").getAsString().contains("healer #6"));
		assertTrue(result.getAsJsonObject("recordedLive").get("headline").getAsString().contains("healer #5"));
	}

	@Test
	public void canRewindBeforeScenarioEditsStart() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(latest());
		JsonObject roles = ScenarioEngine.JSON.fromJson("{COLLECTOR:{hp:1}}", JsonObject.class);
		JsonObject baseline = engine.evaluate(60, 60, new JsonObject(), new JsonObject());
		JsonObject rewound = engine.evaluate(60, 61, new JsonObject(), roles);
		assertEquals(baseline, rewound);
		assertEquals(0, rewound.getAsJsonObject("roles").getAsJsonObject("COLLECTOR").getAsJsonArray("overrides").size());
	}

	@Test
	public void onlyTheRecordedRoleGetsADecisionAndOtherPlayersStayObservations() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(latest());
		Snapshot captured = engine.sourceSnapshot(60);
		assertFalse(captured.getInventory().isEmpty());
		assertEquals(2, (int) captured.getRoleLevels().get(Role.COLLECTOR));
		assertEquals(2, engine.metadata().get("capturedLevel").getAsInt());
		JsonObject result = engine.evaluate(60, 60, new JsonObject(), new JsonObject());
		assertEquals(1, result.getAsJsonObject("roles").size());
		assertTrue(result.getAsJsonObject("roles").has("COLLECTOR"));
		assertFalse(result.getAsJsonObject("roles").has("HEALER"));
		assertTrue(result.getAsJsonArray("observedPlayers").size() > 0);
	}

	@Test
	public void changingPositionChangesTheRealPlannersEggTarget() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(latest());
		Snapshot s = engine.sourceSnapshot(60);
		int regionX = s.getPlayerTile().getX() & ~63, regionY = s.getPlayerTile().getY() & ~63;
		JsonObject world = new JsonObject();
		world.addProperty("queenAlive", false);
		world.add("penance", ScenarioEngine.JSON.toJsonTree(List.of()));
		world.add("unseenPenance", ScenarioEngine.JSON.toJsonTree(List.of()));
		world.add("groundItems", ScenarioEngine.JSON.fromJson("[{itemId:" + BaIds.EGG_RED + ",quantity:1,spawnTick:" + s.getTick() + ",tile:{x:" + (regionX + 15) + ",y:" + (regionY + 40) + ",plane:0}},"
			+ "{itemId:" + BaIds.EGG_RED + ",quantity:1,spawnTick:" + s.getTick() + ",tile:{x:" + (regionX + 35) + ",y:" + (regionY + 40) + ",plane:0}}]", com.google.gson.JsonArray.class));
		JsonObject role = ScenarioEngine.JSON.fromJson("{inventory:{},bagRed:0,bagGreen:0,bagBlue:0,freeSlots:26,listenCall:RED_EGG,listenFresh:true,shoutDone:true}", JsonObject.class);
		JsonObject roles = new JsonObject();
		roles.add("COLLECTOR", role);
		role.add("playerTile", ScenarioEngine.JSON.toJsonTree(new net.runelite.api.coords.WorldPoint(regionX + 15, regionY + 40, 0)));
		JsonObject left = engine.evaluate(60, 60, world, roles).getAsJsonObject("roles").getAsJsonObject("COLLECTOR").getAsJsonObject("decision");
		role.add("playerTile", ScenarioEngine.JSON.toJsonTree(new net.runelite.api.coords.WorldPoint(regionX + 35, regionY + 40, 0)));
		JsonObject right = engine.evaluate(60, 60, world, roles).getAsJsonObject("roles").getAsJsonObject("COLLECTOR").getAsJsonObject("decision");
		assertEquals(Step.COL_COLLECT.name(), left.get("step").getAsString());
		assertEquals(Step.COL_COLLECT.name(), right.get("step").getAsString());
		assertNotEquals(left.get("target"), right.get("target"));
	}

	@Test
	public void stockedFoodCannotInventAnUnknownCall() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(RecordedRound.read(Path.of("src/test/resources/recordings/2026-10-17_17-52-14-wave10.jsonl")));
		JsonObject role = engine.metadata().getAsJsonObject("stocked").getAsJsonObject("HEALER").deepCopy();
		role.add("listenCall", com.google.gson.JsonNull.INSTANCE);
		role.addProperty("listenFresh", false);
		JsonObject roles = new JsonObject();
		roles.add("HEALER", role);
		JsonObject card = engine.evaluate(60, 60, new JsonObject(), roles).getAsJsonObject("roles").getAsJsonObject("HEALER");
		assertNotEquals(Step.HEAL_FEED.name(), card.getAsJsonObject("decision").get("step").getAsString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void wrongRoleCallIsRejected() throws IOException
	{
		new ScenarioEngine(latest()).evaluate(0, 0, new JsonObject(),
			ScenarioEngine.JSON.fromJson("{COLLECTOR:{listenCall:" + BaCall.POISON_WORMS.name() + "}}", JsonObject.class));
	}

	@Test
	public void mapDescriptionsRetainStackQuantitiesAndUseFriendlyNames() throws IOException
	{
		ScenarioEngine engine = new ScenarioEngine(latest());
		net.runelite.api.coords.WorldPoint tile = engine.sourceSnapshot(60).getPlayerTile();
		List<com.galesong.baautopilot.state.GroundItemView> items = List.of(
			new com.galesong.baautopilot.state.GroundItemView(BaIds.EGG_RED, tile, 12, false, 1),
			new com.galesong.baautopilot.state.GroundItemView(BaCall.BAIT_WORMS.getItemId(), tile, 7, true, 1),
			new com.galesong.baautopilot.state.GroundItemView(BaIds.EGG_OMEGA, tile, 1, false, 1));
		JsonObject world = new JsonObject();
		world.add("groundItems", ScenarioEngine.JSON.toJsonTree(items));
		JsonObject card = engine.evaluate(60, 60, world, new JsonObject()).getAsJsonObject("roles").getAsJsonObject("COLLECTOR");
		com.google.gson.JsonArray described = card.getAsJsonArray("mapItems");
		assertEquals(3, described.size());
		assertEquals("Red eggs", described.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("red", described.get(0).getAsJsonObject().get("variant").getAsString());
		assertEquals(12, described.get(0).getAsJsonObject().get("quantity").getAsInt());
		assertEquals("bait", described.get(1).getAsJsonObject().get("category").getAsString());
		assertEquals("unknown", described.get(1).getAsJsonObject().get("baitStatus").getAsString());
		assertEquals("Omega egg", described.get(2).getAsJsonObject().get("name").getAsString());
		boolean trapNamed = false;
		for (com.google.gson.JsonElement object : card.getAsJsonArray("mapObjects"))
			trapNamed |= object.getAsJsonObject().get("name").getAsString().startsWith("Trap:");
		assertTrue(trapNamed);
	}

	@Test(expected = IllegalArgumentException.class)
	public void cannotRequestAnUncapturedRolesDecision() throws IOException
	{
		new ScenarioEngine(latest()).evaluate(0, 0, new JsonObject(),
			ScenarioEngine.JSON.fromJson("{HEALER:{hp:99}}", JsonObject.class));
	}
}
