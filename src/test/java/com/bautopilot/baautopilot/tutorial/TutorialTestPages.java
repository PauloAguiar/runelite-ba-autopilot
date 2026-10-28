package com.bautopilot.baautopilot.tutorial;

import com.google.gson.Gson;
import java.util.List;

/** The bundled pages, as the plugin loads them. */
final class TutorialTestPages
{
	private static final TutorialLibrary LIBRARY = new TutorialLibrary(new Gson());

	static List<TutorialSteps.Page> of(Tutorial tutorial)
	{
		return LIBRARY.pages(tutorial);
	}

	private TutorialTestPages()
	{
	}
}
