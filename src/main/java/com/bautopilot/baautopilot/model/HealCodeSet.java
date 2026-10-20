package com.bautopilot.baautopilot.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which community code breaks ties between equally urgent targets. Missing variants use Regular.
 * The OS variants are the community codes shipped by the BA Heal Codes plugin
 * (github.com/evaan/ba-heal-codes, BSD 2-Clause, (c) 2021 Evan).
 */
@Getter
@RequiredArgsConstructor
public enum HealCodeSet
{
	REGULAR("Regular"),
	ONE_OS("1x OS"),
	TWO_OS("2x OS"),
	OFF("Off: by priority");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
