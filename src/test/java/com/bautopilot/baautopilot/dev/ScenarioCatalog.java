package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.Role;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Lightweight capture index. Full round reconstruction happens only when a capture is selected. */
final class ScenarioCatalog
{
	static final class Entry
	{
		final String id;
		final String name;
		final Role role;
		final int wave;
		final int level;
		final String source;
		final transient Path path;

		Entry(Path path, Role role, int wave, int level, String source)
		{
			this.id = path.getFileName().toString();
			this.name = id;
			this.path = path;
			this.role = role;
			this.wave = wave;
			this.level = level;
			this.source = source;
		}
	}

	private final Path selected;
	private final List<Path> directories;
	private Map<String, Entry> entries = new LinkedHashMap<>();
	private final Map<String, ScenarioEngine> engines = new LinkedHashMap<>(4, .75f, true);
	private String defaultId;

	ScenarioCatalog(Path selected, List<Path> directories) throws IOException
	{
		this.selected = selected;
		this.directories = directories;
		refresh();
	}

	synchronized void refresh() throws IOException
	{
		Map<String, Entry> found = new LinkedHashMap<>();
		if (selected != null) add(found, selected, "Selected capture");
		for (Path directory : directories)
		{
			if (!Files.isDirectory(directory)) continue;
			try (Stream<Path> files = Files.list(directory))
			{
				for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
					.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).collect(Collectors.toList()))
					add(found, file, directory.endsWith("src/test/resources/recordings") ? "Saved example" : "Local recording");
			}
		}
		if (found.isEmpty()) throw new IOException("No playable BA captures found");
		entries = found;
		engines.clear();
		defaultId = selected != null && found.containsKey(selected.getFileName().toString())
			? selected.getFileName().toString() : found.keySet().stream().max(String::compareTo).get();
	}

	private static void add(Map<String, Entry> found, Path file, String source) throws IOException
	{
		if (!Files.isRegularFile(file) || found.containsKey(file.getFileName().toString())) return;
		Entry entry = inspect(file, source);
		if (entry != null) found.put(entry.id, entry);
	}

	/** Read only metadata fields, skipping bulky NPC/world payloads and incomplete trailing lines. */
	static Entry inspect(Path file, String source) throws IOException
	{
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				Rec.Tick tick = new Rec.Tick();
				try (JsonReader reader = new JsonReader(new StringReader(line)))
				{
					reader.beginObject();
					while (reader.hasNext())
					{
						String key = reader.nextName();
						if (reader.peek() == JsonToken.NULL) { reader.nextNull(); continue; }
						switch (key)
						{
							case "instance": tick.instance = reader.nextBoolean(); break;
							case "tile": tick.tile = Rec.GSON.fromJson(reader, Rec.Pos.class); break;
							case "live": tick.live = Rec.GSON.fromJson(reader, Rec.Live.class); break;
							case "vb":
								tick.vb = new LinkedHashMap<>();
								reader.beginObject();
								while (reader.hasNext()) tick.vb.put(Integer.parseInt(reader.nextName()), reader.nextInt());
								reader.endObject();
								break;
							default: reader.skipValue();
						}
					}
					reader.endObject();
					if (!tick.instance || tick.tile == null || tick.live == null || tick.live.wave < 1 || tick.live.wave > 10
						|| !ArenaTiles.isArenaRegion(RoundReplay.point(tick.tile).getRegionID()) || tick.live.role == null) continue;
					Role role = Role.valueOf(tick.live.role);
					return new Entry(file, role, tick.live.wave, RoundReplay.roleLevels(tick).getOrDefault(role, 0), source);
				}
				catch (RuntimeException | IOException malformedLine)
				{
					// A recording may still be open, or have been interrupted mid-line.
				}
			}
		}
		return null;
	}

	synchronized JsonObject listing()
	{
		JsonObject out = new JsonObject();
		out.addProperty("defaultId", defaultId);
		List<Entry> sorted = new ArrayList<>(entries.values());
		sorted.sort(Comparator.comparing((Entry entry) -> entry.name).reversed());
		out.add("captures", ScenarioEngine.JSON.toJsonTree(sorted));
		return out;
	}

	synchronized ScenarioEngine engine(String id) throws IOException
	{
		String chosen = id == null || id.isBlank() ? defaultId : id;
		Entry entry = entries.get(chosen);
		if (entry == null) throw new IllegalArgumentException("Capture is not in this library");
		ScenarioEngine engine = engines.get(chosen);
		if (engine == null)
		{
			engine = new ScenarioEngine(RecordedRound.read(entry.path));
			engines.put(chosen, engine);
			if (engines.size() > 2) engines.remove(engines.keySet().iterator().next());
		}
		return engine;
	}
}
