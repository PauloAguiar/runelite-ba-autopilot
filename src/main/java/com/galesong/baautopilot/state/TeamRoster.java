package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.Role;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID.BarbassaultOverRecruitPlayerNames;
import net.runelite.client.util.Text;

/**
 * Who plays which role, read from the team list shown in the lobby (names in "Leader: x" /
 * "Player n: x" rows, role icons as models 20561/20563/20566/20569; mapping from BA Healer Order).
 * Kept until the team list shows a different team, so the Queen fight can say "pass to <name>".
 */
public class TeamRoster
{
	private static final int SLOTS = 5;
	private static final int ATTACKER_MODEL = 20561;
	private static final int COLLECTOR_MODEL = 20563;
	private static final int DEFENDER_MODEL = 20566;
	private static final int HEALER_MODEL = 20569;

	private Map<String, Role> roles = ImmutableMap.of();

	public void reset()
	{
		roles = ImmutableMap.of();
	}

	public void read(RoundLogic.Hud hud)
	{
		if (!hud.visible(BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME))
		{
			return;
		}
		Map<String, Role> seen = new LinkedHashMap<>();
		for (int i = 0; i < SLOTS; i++)
		{
			String name = name(i, hud.text(BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME + i));
			Role role = role(hud.model(BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_ICON + i));
			if (name != null && role != null)
			{
				seen.put(name, role);
			}
		}
		if (!seen.isEmpty())
		{
			roles = ImmutableMap.copyOf(seen);
		}
	}

	public Map<String, Role> roles()
	{
		return roles;
	}

	/** Your role on the team list, matching names the way the game shows them; null when absent. */
	public Role roleOf(String player)
	{
		if (player == null)
		{
			return null;
		}
		String key = Text.standardize(player);
		return roles.entrySet().stream()
			.filter(e -> Text.standardize(e.getKey()).equals(key))
			.map(Map.Entry::getValue)
			.findFirst().orElse(null);
	}

	/** "Leader: Zezima" / "Player 2: Lynx" / "-----" to the name, or null. */
	static String name(int slot, String text)
	{
		if (text == null)
		{
			return null;
		}
		String t = Text.removeTags(text).replace(' ', ' ').trim();
		String prefix = slot == 0 ? "leader:" : "player " + slot + ":";
		if (t.toLowerCase().startsWith(prefix))
		{
			t = t.substring(prefix.length()).trim();
		}
		return t.isEmpty() || t.startsWith("-") ? null : t;
	}

	static Role role(int model)
	{
		switch (model)
		{
			case ATTACKER_MODEL:
				return Role.ATTACKER;
			case COLLECTOR_MODEL:
				return Role.COLLECTOR;
			case DEFENDER_MODEL:
				return Role.DEFENDER;
			case HEALER_MODEL:
				return Role.HEALER;
			default:
				return null;
		}
	}
}
