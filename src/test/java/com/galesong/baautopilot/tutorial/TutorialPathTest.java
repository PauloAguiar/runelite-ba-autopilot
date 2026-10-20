package com.galesong.baautopilot.tutorial;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

public class TutorialPathTest
{
	/** Two legs, 100 east then 300 north: the glide covers them by distance, not leg by leg. */
	private final List<LocalPoint> line = Arrays.asList(
		new LocalPoint(0, 0, 0), new LocalPoint(100, 0, 0), new LocalPoint(100, 300, 0));

	@Test
	public void movesByDistance()
	{
		assertEquals(new LocalPoint(0, 0, 0), TutorialPath.along(line, 0));
		assertEquals(new LocalPoint(100, 0, 0), TutorialPath.along(line, 0.25));
		assertEquals(new LocalPoint(100, 100, 0), TutorialPath.along(line, 0.5));
		assertEquals(new LocalPoint(100, 300, 0), TutorialPath.along(line, 1));
	}

	@Test
	public void staysPutOnOnePoint()
	{
		assertEquals(new LocalPoint(5, 5, 0), TutorialPath.along(Arrays.asList(new LocalPoint(5, 5, 0)), 0.7));
	}
}
