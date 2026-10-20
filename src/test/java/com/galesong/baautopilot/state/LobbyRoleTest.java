package com.galesong.baautopilot.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.galesong.baautopilot.model.Role;
import java.util.Collections;
import net.runelite.api.gameval.InterfaceID.BarbassaultOverRecruitPlayerNames;
import org.junit.Test;

/** Reported 2026-10-16: on a team in the lobby, the panel showed no role (only the arena HUD set it). */
public class LobbyRoleTest
{
	/** The lobby's team list: leader attacker, you (with a non-breaking space) defender. */
	private static final RoundLogic.Hud TEAM_LIST = new RoundLogic.Hud()
	{
		@Override
		public boolean visible(int component)
		{
			return component == BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME;
		}

		@Override
		public String text(int component)
		{
			if (component == BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_NAME)
			{
				return "Leader: Zezima";
			}
			if (component == BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER_1_NAME)
			{
				return "Player 1: Gale Song";
			}
			return "-----";
		}

		@Override
		public int model(int component)
		{
			if (component == BarbassaultOverRecruitPlayerNames.BARBASSAULT_LEADER_ICON)
			{
				return 20561;
			}
			return component == BarbassaultOverRecruitPlayerNames.BARBASSAULT_PLAYER1_ICON ? 20566 : -1;
		}
	};

	@Test
	public void lobbyTeamListGivesYourRole()
	{
		RoundLogic r = new RoundLogic();
		r.setSelfName("Gale Song");
		r.tick(1, false, TEAM_LIST, Collections.emptyMap(), 0, 0, 0);
		assertEquals(Role.DEFENDER, r.snapshot().build().getRole());
	}

	@Test
	public void notOnTheListNoRole()
	{
		RoundLogic r = new RoundLogic();
		r.setSelfName("Someone Else");
		r.tick(1, false, TEAM_LIST, Collections.emptyMap(), 0, 0, 0);
		assertNull(r.snapshot().build().getRole());
	}
}
