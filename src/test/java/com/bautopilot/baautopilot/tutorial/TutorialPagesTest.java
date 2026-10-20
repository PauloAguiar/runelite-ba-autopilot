package com.bautopilot.baautopilot.tutorial;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Tutorial pages' menus open on items the page shows. (No length limit: reported 2026-10-19, a
 * tutorial must be explicit about what to do and where, never saving words.)
 */
public class TutorialPagesTest
{
	@Test
	public void menusOpenOnShownItems()
	{
		List<String> wrong = new ArrayList<>();
		for (Tutorial t : Tutorial.values())
		{
			for (TutorialSteps.Page page : t.getPages())
			{
				TutorialSteps.Menu menu = page.getMenu();
				if (menu == null)
				{
					continue;
				}
				boolean held = page.getPilot() != null
					? menu.getItemId() == Tutorials.horn(page.getPilot().getSituation().getRole())
						|| page.getPilot().getSituation().getInventory().containsKey(menu.getItemId())
					: page.getItems().containsKey(menu.getItemId());
				if (!held || menu.getPick() < 0 || menu.getPick() >= menu.getOptions().size())
				{
					wrong.add(t.getLabel() + " / " + page.getTitle());
				}
			}
		}
		assertTrue("Menus on items the page doesn't hold, or picking no option:\n" + String.join("\n", wrong), wrong.isEmpty());
	}
}
