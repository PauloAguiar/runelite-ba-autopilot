package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.tutorial.TutorialLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.runelite.client.RuneLite;

/**
 * Development only: the tutorial layout from ~/.runelite/ba-autopilot/tutorial-layout.json, created
 * from the bundled copy the first time, so props can be moved without rebuilding. The tutorial
 * places them again whenever the file is saved. The plugin itself never touches files.
 */
final class TutorialLayoutFile implements TutorialLayout.DevSource
{
	static final File FILE = new File(new File(RuneLite.RUNELITE_DIR, "ba-autopilot"), TutorialLayout.RESOURCE);

	TutorialLayoutFile()
	{
		if (FILE.exists())
		{
			return;
		}
		FILE.getParentFile().mkdirs();
		try (InputStream in = TutorialLayout.class.getResourceAsStream(TutorialLayout.RESOURCE))
		{
			Files.copy(in, FILE.toPath());
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public String json()
	{
		try
		{
			return new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public long version()
	{
		return FILE.lastModified();
	}
}
