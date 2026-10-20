package com.bautopilot.baautopilot.model;

import net.runelite.api.gameval.NpcID;

public enum PenanceType
{
	FIGHTER,
	RANGER,
	RUNNER,
	HEALER,
	QUEEN;

	/** Penance NPC ids run in blocks of nine per type, one id per wave level. */
	public static PenanceType forNpcId(int id)
	{
		if (id >= NpcID.BARBASSAULT_PEN_FIGHTER_LV1 && id <= NpcID.BARBASSAULT_PEN_FIGHTER_LV9)
		{
			return FIGHTER;
		}
		if (id >= NpcID.BARBASSAULT_PEN_RUNNER_LV1 && id <= NpcID.BARBASSAULT_PEN_RUNNER_LV9)
		{
			return RUNNER;
		}
		if (id >= NpcID.BARBASSAULT_PEN_RANGER_LV1 && id <= NpcID.BARBASSAULT_PEN_RANGER_LV9)
		{
			return RANGER;
		}
		if (id >= NpcID.BARBASSAULT_PEN_HEALER_LV1 && id <= NpcID.BARBASSAULT_PEN_HEALER_LV9)
		{
			return HEALER;
		}
		if (id == NpcID.BARBASSAULT_PEN_QUEEN_NEW)
		{
			return QUEEN;
		}
		return null;
	}
}
