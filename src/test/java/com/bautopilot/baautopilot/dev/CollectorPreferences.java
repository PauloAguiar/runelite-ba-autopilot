package com.bautopilot.baautopilot.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Explicit human labels on captured baseline states; never interpreted as simulated outcomes. */
final class CollectorPreferences
{
	private final Path file;
	CollectorPreferences(Path file) { this.file = file; }

	synchronized JsonArray read() throws IOException
	{
		return Files.exists(file) ? ScenarioEngine.JSON.fromJson(Files.readString(file), JsonArray.class) : new JsonArray();
	}

	synchronized JsonObject save(String capture, int tick, String choice) throws IOException
	{
		if (!Set.of("EGGS", "CANNON", "UNSURE").contains(choice)) throw new IllegalArgumentException("Invalid preference");
		JsonArray labels = read(), next = new JsonArray();
		for (JsonElement label : labels)
		{
			JsonObject old = label.getAsJsonObject();
			if (!capture.equals(old.get("capture").getAsString()) || tick != old.get("tick").getAsInt()) next.add(old);
		}
		JsonObject label = new JsonObject();
		label.addProperty("capture", capture);
		label.addProperty("tick", tick);
		label.addProperty("choice", choice);
		next.add(label);
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "collector-labels-", ".tmp");
		try
		{
			Files.writeString(temporary, ScenarioEngine.JSON.toJson(next), StandardCharsets.UTF_8);
			Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		finally { Files.deleteIfExists(temporary); }
		return label;
	}
}
