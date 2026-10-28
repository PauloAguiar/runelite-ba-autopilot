package com.bautopilot.baautopilot.tutorial;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The tutorials the panel offers, in order; Next on a tutorial's last page goes on to the next.
 * Their pages are in tutorial-pages.json (see TutorialLibrary).
 */
@Getter
@RequiredArgsConstructor
public enum Tutorial
{
	INTRODUCTION("Introduction"),
	BASICS("Basics"),
	COLLECTOR("Collector"),
	HEALER("Healer"),
	ATTACKER("Attacker"),
	DEFENDER("Defender");

	private final String label;

	/** The tutorial after this one; null for the last. */
	public Tutorial next()
	{
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	/** The tutorial before this one; null for the first. */
	public Tutorial previous()
	{
		return ordinal() > 0 ? values()[ordinal() - 1] : null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
