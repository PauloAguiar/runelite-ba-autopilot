package com.galesong.baautopilot.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.InterfaceID;

/**
 * The four Barbarian Assault roles, with the HUD widgets each one reads its calls from.
 * Attacker and Collector call each other; Defender and Healer call each other.
 */
@Getter
@RequiredArgsConstructor
public enum Role
{
	ATTACKER("Attacker", InterfaceID.BARBASSAULT_OVER_ATT,
		InterfaceID.BarbassaultHorn.BARBASSAULT_HORN_COL,
		InterfaceID.BarbassaultOverAtt.BARBASSAULT_ATTACKER_HEAR_TEXT,
		InterfaceID.BarbassaultOverAtt.BARBASSAULT_ATTACKER_HEAR_DETAIL_TEXT,
		InterfaceID.BarbassaultOverAtt.BARBASSAULT_ATTACKER_SHOUT_TEXT),
	DEFENDER("Defender", InterfaceID.BARBASSAULT_OVER_DEF,
		InterfaceID.BarbassaultHorn.BARBASSAULT_HORN_HEAL,
		-1,
		InterfaceID.BarbassaultOverDef.BARBASSAULT_DEFENDER_HEAR_DETAIL_TEXT,
		InterfaceID.BarbassaultOverDef.BARBASSAULT_DEFENDER_SHOUT_TEXT),
	COLLECTOR("Collector", InterfaceID.BARBASSAULT_OVER_COL,
		InterfaceID.BarbassaultHorn.BARBASSAULT_HORN_ATT,
		-1,
		InterfaceID.BarbassaultOverCol.BARBASSAULT_COLLECTOR_HEAR_DETAIL_TEXT,
		InterfaceID.BarbassaultOverCol.BARBASSAULT_COLLECTOR_SHOUT_TEXT),
	HEALER("Healer", InterfaceID.BARBASSAULT_OVER_HEAL,
		InterfaceID.BarbassaultHorn.BARBASSUALT_HORN_DEF,
		-1,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_HEAR_DETAIL_TEXT,
		InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_SHOUT_TEXT);

	private final String label;
	private final int hudGroup;
	/** Your partner's line on the Horn of Glory interface (reward horn owners only). */
	private final int hornListenComponent;
	/** Attacker's "Controlled/" line above the detail line; -1 for the other roles. */
	private final int listenPrefixComponent;
	private final int listenComponent;
	private final int shoutComponent;

	/** The role whose horn you hear, and who hears yours. */
	public Role partner()
	{
		switch (this)
		{
			case ATTACKER:
				return COLLECTOR;
			case COLLECTOR:
				return ATTACKER;
			case DEFENDER:
				return HEALER;
			default:
				return DEFENDER;
		}
	}

	public static Role forHudGroup(int group)
	{
		for (Role r : values())
		{
			if (r.hudGroup == group)
			{
				return r;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
