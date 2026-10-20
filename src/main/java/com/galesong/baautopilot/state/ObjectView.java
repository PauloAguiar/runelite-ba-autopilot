package com.galesong.baautopilot.state;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
public class ObjectView
{
	int id;
	WorldPoint tile;
}
