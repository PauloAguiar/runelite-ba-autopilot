package com.galesong.baautopilot.dev;

import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CollectorPreferencesTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	@Test public void labelsSurviveRestartAndUpdatingOneDoesNotEraseOtherTicks() throws Exception
	{
		Path file = temporary.getRoot().toPath().resolve("labels.json");
		CollectorPreferences preferences = new CollectorPreferences(file);
		preferences.save("wave2.jsonl", 569, "EGGS");
		preferences.save("wave2.jsonl", 643, "UNSURE");
		new CollectorPreferences(file).save("wave2.jsonl", 643, "CANNON");
		assertEquals(2, preferences.read().size());
		assertEquals("CANNON", preferences.read().get(1).getAsJsonObject().get("choice").getAsString());
	}
	@Test(expected = IllegalArgumentException.class) public void unknownChoicesCannotBecomeTrainingLabels() throws Exception
	{
		new CollectorPreferences(temporary.getRoot().toPath().resolve("labels.json")).save("wave2.jsonl", 569, "INVENTED");
	}
	@Test public void calibrationUsesExplicitLabelsAndLeavesAnAlreadyMatchingModelAlone() throws Exception
	{
		Path labels = temporary.getRoot().toPath().resolve("labels.json"), proposal = temporary.getRoot().toPath().resolve("proposal.json");
		CollectorPreferences preferences = new CollectorPreferences(labels);
		preferences.save("2026-10-18_11-46-01-wave2.jsonl", 569, "EGGS");
		preferences.save("2026-10-18_11-46-01-wave2.jsonl", 682, "UNSURE");
		CollectorWeightCalibration.main(new String[]{"src/test/resources/recordings", labels.toString(), proposal.toString()});
		com.google.gson.JsonObject result = ScenarioEngine.JSON.fromJson(java.nio.file.Files.readString(proposal), com.google.gson.JsonObject.class);
		assertEquals(1, result.get("labelledTicks").getAsInt());
		assertEquals(1, result.get("redReserveMultiplier").getAsDouble(), .001);
		assertEquals(1, result.get("loadingBatchMultiplier").getAsDouble(), .001);
		assertFalse(result.get("appliedToRuntime").getAsBoolean());
		assertTrue(result.getAsJsonArray("comparisons").get(0).getAsJsonObject().get("currentEggMinusLoad").getAsDouble() > 5);
	}
}
