package com.bautopilot.baautopilot.state;

import lombok.Value;

/** A teammate's hit points from the healer HUD (only the healer sees these). */
@Value
public class TeammateView
{
	String name;
	int hp;
	int maxHp;

	public double fraction()
	{
		return maxHp <= 0 ? 1.0 : (double) hp / maxHp;
	}
}
