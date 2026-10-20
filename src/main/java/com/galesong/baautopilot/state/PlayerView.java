package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.Role;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** Public player observations only; teammate inventory contents are never inferred as readable. */
@Value
public class PlayerView
{
	String name;
	Role role;
	WorldPoint tile;
	int animation;
	String interacting;
}
