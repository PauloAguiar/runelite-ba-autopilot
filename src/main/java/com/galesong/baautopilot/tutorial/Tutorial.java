package com.galesong.baautopilot.tutorial;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The tutorials the panel offers, in order; Next on a tutorial's last page goes on to the next.
 * Planned: one per role and an advanced one; each is a list of pages like the introduction's.
 */
@Getter
@RequiredArgsConstructor
public enum Tutorial
{
	INTRODUCTION("Introduction", IntroductionTutorial.PAGES),
	BASICS("Basics", BasicsTutorial.PAGES),
	COLLECTOR("Collector", CollectorTutorial.PAGES),
	HEALER("Healer", HealerTutorial.PAGES),
	ATTACKER("Attacker", AttackerTutorial.PAGES),
	DEFENDER("Defender", DefenderTutorial.PAGES);

	private final String label;
	private final List<TutorialSteps.Page> pages;

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
