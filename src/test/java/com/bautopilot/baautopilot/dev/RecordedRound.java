package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.HealCodeSet;
import com.bautopilot.baautopilot.plan.Settings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A recording made by {@link BaRecorderPlugin}, read back from its JSONL file. */
public final class RecordedRound
{
	final Path source;
	final Rec.Header header;
	final List<Rec.Tick> ticks;

	RecordedRound(Path source, Rec.Header header, List<Rec.Tick> ticks)
	{
		this.source = source;
		this.header = header;
		this.ticks = Collections.unmodifiableList(ticks);
	}

	public static RecordedRound read(Path file) throws IOException
	{
		Rec.Header header = null;
		List<Rec.Tick> ticks = new ArrayList<>();
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			String line;
			int n = 0;
			while ((line = in.readLine()) != null)
			{
				n++;
				if (line.trim().isEmpty())
				{
					continue;
				}
				JsonObject o;
				try
				{
					o = new JsonParser().parse(line).getAsJsonObject();
				}
				catch (RuntimeException e)
				{
					// The last line of a recording cut short by a crash may be partial
					continue;
				}
				String type = o.has("type") ? o.get("type").getAsString() : "";
				if ("header".equals(type))
				{
					header = Rec.GSON.fromJson(o, Rec.Header.class);
					if (o.has("settings") && o.get("settings").isJsonObject())
					{
						header.settings = settings(o.getAsJsonObject("settings"));
					}
				}
				else if ("tick".equals(type))
				{
					ticks.add(Rec.GSON.fromJson(o, Rec.Tick.class));
				}
				else
				{
					throw new IOException(file + ":" + n + ": unknown line type '" + type + "'");
				}
			}
		}
		if (header == null)
		{
			header = new Rec.Header();
		}
		if (header.version > Rec.VERSION)
		{
			throw new IOException(file + ": recording version " + header.version + " is newer than this reader (" + Rec.VERSION + ")");
		}
		return new RecordedRound(file, header, ticks);
	}

	/**
	 * Recorded settings laid over today's defaults: Gson skips Lombok's builder defaults, so a
	 * setting added after the recording would otherwise replay as false/null (safety bait off,
	 * no heal code). The old {@code healCodes} flag maps to the Regular codes or Off.
	 */
	static Settings settings(JsonObject recorded)
	{
		JsonObject merged = Rec.GSON.toJsonTree(Settings.builder().build()).getAsJsonObject();
		for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : recorded.entrySet())
		{
			merged.add(e.getKey(), e.getValue());
		}
		if (recorded.has("healCodes") && !recorded.has("healCodeSet"))
		{
			merged.addProperty("healCodeSet", recorded.get("healCodes").getAsBoolean()
				? HealCodeSet.REGULAR.name() : HealCodeSet.OFF.name());
		}
		return Rec.GSON.fromJson(merged, Settings.class);
	}

	public String name()
	{
		return source == null ? "(in memory)" : source.getFileName().toString();
	}
}
