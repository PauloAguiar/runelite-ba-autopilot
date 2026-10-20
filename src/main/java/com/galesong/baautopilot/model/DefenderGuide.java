package com.galesong.baautopilot.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Which defender guide the bait pattern and log counts follow. */
@Getter
@RequiredArgsConstructor
public enum DefenderGuide
{
	/**
	 * OSRS Wiki, Barbarian Assault/Strategies: 1-4-1-4-1 (waves 1-9) / 1-4-4-1 (wave 10), logs 2 for
	 * waves 1-4 and 4 from wave 5.
	 */
	WIKI("OSRS Wiki (1-4-1-4-1)"),
	/**
	 * User:Gau Cho/Defender (OSRS Wiki): 4 good on the trap, 1 any on the trail, 4 good on the
	 * mainstack, then logs and hammer; logs 0 / 1 (2-4) / 2 (5-7) / 3 (8) / 4 (9) / 2 (10).
	 */
	GAU_CHO("Gau Cho (trap, trail, mainstack)");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
