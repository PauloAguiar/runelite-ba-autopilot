package com.bautopilot.baautopilot.tutorial;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The tutorials' pages, read once from tutorial-pages.json (tutorial name to its pages); each
 * page's autopilot box comes from TutorialSituations.
 */
@Singleton
public class TutorialLibrary
{
	private final Gson gson;
	private volatile Map<Tutorial, List<TutorialSteps.Page>> pages;

	@Inject
	public TutorialLibrary(Gson gson)
	{
		this.gson = gson.newBuilder().registerTypeAdapter(Color.class, new ColorAdapter().nullSafe()).create();
	}

	public List<TutorialSteps.Page> pages(Tutorial tutorial)
	{
		if (pages == null)
		{
			pages = load();
		}
		return pages.get(tutorial);
	}

	private Map<Tutorial, List<TutorialSteps.Page>> load()
	{
		Map<String, List<TutorialSteps.Page>> read;
		try (Reader in = new InputStreamReader(TutorialLibrary.class.getResourceAsStream("tutorial-pages.json"),
			StandardCharsets.UTF_8))
		{
			read = gson.fromJson(in, new TypeToken<Map<String, List<TutorialSteps.Page>>>()
			{
			}.getType());
		}
		catch (IOException e)
		{
			throw new IllegalStateException("tutorial-pages.json", e);
		}
		Map<Tutorial, List<TutorialSteps.Page>> out = new EnumMap<>(Tutorial.class);
		for (Tutorial tutorial : Tutorial.values())
		{
			List<TutorialSteps.Page> list = new ArrayList<>();
			for (TutorialSteps.Page page : read.get(tutorial.name()))
			{
				list.add(page.toBuilder().pilot(TutorialSituations.forPage(tutorial, list.size())).build());
			}
			out.put(tutorial, Collections.unmodifiableList(list));
		}
		return out;
	}

	/** Colours as "#AARRGGBB". */
	static final class ColorAdapter extends TypeAdapter<Color>
	{
		@Override
		public void write(JsonWriter out, Color c) throws IOException
		{
			if (c == null)
			{
				out.nullValue();
				return;
			}
			out.value(String.format("#%08X", c.getRGB()));
		}

		@Override
		public Color read(JsonReader in) throws IOException
		{
			String s = in.nextString();
			return new Color((int) Long.parseLong(s.substring(1), 16), true);
		}
	}
}
