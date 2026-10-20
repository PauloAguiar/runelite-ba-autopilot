package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ScenarioCatalogTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	private static final Path FIXTURES = Path.of("src/test/resources/recordings");
	private static final String COLLECTOR = "2026-10-17_20-10-05-wave10.jsonl";
	private static final String HEALER = "2026-10-17_17-52-14-wave10.jsonl";

	@Test
	public void indexesCapturedRolesWavesAndLevelsAndDeduplicatesCopies() throws IOException
	{
		Path copies = folder.newFolder("captures").toPath();
		Files.copy(FIXTURES.resolve(COLLECTOR), copies.resolve(COLLECTOR));
		Files.copy(FIXTURES.resolve(HEALER), copies.resolve(HEALER));
		ScenarioCatalog catalog = new ScenarioCatalog(FIXTURES.resolve(COLLECTOR), List.of(copies));
		JsonObject listing = catalog.listing();
		assertEquals(COLLECTOR, listing.get("defaultId").getAsString());
		JsonArray captures = listing.getAsJsonArray("captures");
		assertEquals(2, captures.size());
		ScenarioCatalog.Entry entry = ScenarioCatalog.inspect(copies.resolve(COLLECTOR), "Test");
		assertEquals(Role.COLLECTOR, entry.role);
		assertEquals(10, entry.wave);
		assertEquals(2, entry.level);
		ScenarioEngine collector = catalog.engine(COLLECTOR);
		ScenarioEngine healer = catalog.engine(HEALER);
		assertEquals("COLLECTOR", collector.metadata().get("capturedRole").getAsString());
		assertEquals("HEALER", healer.metadata().get("capturedRole").getAsString());
		assertEquals(1, collector.evaluate(0, 0, new JsonObject(), new JsonObject()).getAsJsonObject("roles").size());
		assertEquals(1, healer.evaluate(0, 0, new JsonObject(), new JsonObject()).getAsJsonObject("roles").size());
		assertSame(collector, catalog.engine(COLLECTOR));
	}

	@Test
	public void refreshFindsNewCapturesAndKeepsMissingLevelsUnknown() throws IOException
	{
		Path copies = folder.newFolder("captures").toPath();
		Files.copy(FIXTURES.resolve(COLLECTOR), copies.resolve(COLLECTOR));
		Files.writeString(copies.resolve("lobby.jsonl"), "{\"type\":\"tick\",\"instance\":false}\n{broken");
		ScenarioCatalog catalog = new ScenarioCatalog(null, List.of(copies));
		assertEquals(1, catalog.listing().getAsJsonArray("captures").size());
		Files.copy(FIXTURES.resolve(HEALER), copies.resolve(HEALER));
		catalog.refresh();
		assertEquals(2, catalog.listing().getAsJsonArray("captures").size());
		Path old = copies.resolve("old.jsonl");
		Files.writeString(old, "{\"type\":\"tick\",\"instance\":true,\"tile\":{\"x\":1877,\"y\":5483,\"p\":0},\"live\":{\"role\":\"ATTACKER\",\"wave\":3}}\n");
		ScenarioCatalog.Entry entry = ScenarioCatalog.inspect(old, "Test");
		assertNotNull(entry);
		assertEquals(0, entry.level);
		assertEquals(Role.ATTACKER, entry.role);
	}

	@Test(expected = IllegalArgumentException.class)
	public void arbitraryPathsCannotBeSelectedThroughTheApi() throws IOException
	{
		new ScenarioCatalog(FIXTURES.resolve(COLLECTOR), List.of()).engine("../../outside.jsonl");
	}
}
