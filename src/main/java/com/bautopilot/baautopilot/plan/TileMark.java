package com.bautopilot.baautopilot.plan;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** A secondary tile label, e.g. the defender food pattern with each spot's progress. */
@Value
public class TileMark
{
	WorldPoint tile;
	String label;
	boolean done;
	boolean good;
}
