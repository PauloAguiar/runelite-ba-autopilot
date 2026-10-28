package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.Role;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarbitComposition;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;

/** Account progress stays available outside the arena without waking the round tracker. */
final class RoleProgress
{
	private static final Role[] ROLES = {Role.ATTACKER, Role.DEFENDER, Role.COLLECTOR, Role.HEALER};
	/** Four levels, four point remainders, four point multiples of 512, in ROLES order. */
	private static final int[] VARBITS = {
		VarbitID.BARBASSULT_ROLELEVEL_ATT, VarbitID.BARBASSULT_ROLELEVEL_DEF,
		VarbitID.BARBASSULT_ROLELEVEL_COL, VarbitID.BARBASSULT_ROLELEVEL_HEAL,
		VarbitID.BARBASSAULT_POINTS_ATTACKER_BASE, VarbitID.BARBASSAULT_POINTS_DEFENDER_BASE,
		VarbitID.BARBASSAULT_POINTS_COLLECTOR_BASE, VarbitID.BARBASSAULT_POINTS_HEALER_BASE,
		VarbitID.BARBASSAULT_POINTS_ATTACKER_EXTRA, VarbitID.BARBASSAULT_POINTS_DEFENDER_EXTRA,
		VarbitID.BARBASSAULT_POINTS_COLLECTOR_EXTRA, VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA
	};
	private static final Snapshot UNKNOWN = Snapshot.builder().build();
	private final Set<Integer> backingVarps = new HashSet<>();
	private boolean mapped;
	private boolean dirty = true;
	private int[] values;
	private Snapshot current = UNKNOWN;

	void reset()
	{
		current = UNKNOWN;
		values = null;
		backingVarps.clear();
		mapped = false;
		dirty = true;
	}

	void invalidate()
	{
		dirty = true;
	}

	void changed(VarbitChanged event)
	{
		if (event.getVarbitId() >= 0)
		{
			for (int id : VARBITS)
				if (id == event.getVarbitId()) { dirty = true; break; }
		}
		// Server updates often name the backing varp, rather than an individual varbit.
		else if (!mapped || backingVarps.contains(event.getVarpId())) dirty = true;
	}

	Snapshot read(Client client)
	{
		if (!dirty || client.getGameState() != GameState.LOGGED_IN) return current;
		if (!mapped)
		{
			mapped = true;
			for (int id : VARBITS)
			{
				VarbitComposition definition = client.getVarbit(id);
				if (definition == null) mapped = false;
				else backingVarps.add(definition.getIndex());
			}
		}
		int[] next = new int[VARBITS.length];
		for (int i = 0; i < VARBITS.length; i++) next[i] = client.getVarbitValue(VARBITS[i]);
		dirty = false;
		if (Arrays.equals(values, next)) return current;
		values = next;
		ImmutableMap.Builder<Role, Integer> levels = ImmutableMap.builder(), points = ImmutableMap.builder();
		for (int i = 0; i < ROLES.length; i++)
		{
			levels.put(ROLES[i], next[i]);
			points.put(ROLES[i], next[4 + i] + 512 * next[8 + i]);
		}
		current = Snapshot.builder().roleLevels(levels.build()).rolePoints(points.build()).build();
		return current;
	}
}
