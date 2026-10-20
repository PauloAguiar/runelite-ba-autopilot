package com.bautopilot.baautopilot;

import static org.junit.Assert.assertEquals;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.BaIds;
import com.bautopilot.baautopilot.state.EggBag;
import java.util.Map;
import org.junit.Test;

public class EggBagTest
{
	@Test
	public void eggsTakenThenBaggedAreCountedByColour()
	{
		EggBag bag = new EggBag();
		bag.menu("Take", "Red egg", 10);
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 12);
		bag.menu("Take", "Blue egg", 20);
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 21);
		assertEquals(1, bag.count(BaIds.EGG_RED));
		assertEquals(1, bag.count(BaIds.EGG_BLUE));

		// No recent Take: credit the current call's colour
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 60);
		assertEquals(1, bag.count(BaIds.EGG_GREEN));
	}

	@Test
	public void loadingEmptiesTheBagUpToEachHoppersRoom()
	{
		EggBag bag = new EggBag();
		bag.menu("Take", "Red egg", 1);
		bag.message("You put the egg in the bag.", null, 1);
		bag.menu("Take", "Red egg", 3);
		bag.message("You put the egg in the bag.", null, 3);
		bag.hoppers(0, 4, 0);
		bag.message("You load the hopper.", null, 5);
		// Only one red fits (4/5 loaded)
		assertEquals(1, bag.count(BaIds.EGG_RED));
		bag.hoppers(0, 0, 0);
		bag.message("You load the hopper.", null, 8);
		assertEquals(0, bag.count(BaIds.EGG_RED));
	}

	@Test
	public void emptyingTheBagWaitsForTheInventoryTransfer()
	{
		EggBag bag = new EggBag();
		bag.menu("Take", "Green egg", 1);
		bag.message("You put the egg in the bag.", null, 2);
		bag.menu("Empty", "Collection bag", 4);
		assertEquals(1, bag.count(BaIds.EGG_GREEN));
		bag.inventory(Map.of(BaIds.EGG_GREEN, 1), 5);
		assertEquals(0, bag.count(BaIds.EGG_GREEN));
	}

	@Test
	public void fullInventoryRetainsBagContentsAndPartialEmptyOnlyRemovesTransferredColours()
	{
		EggBag bag = new EggBag();
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 10);
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 11);
		bag.message("You put the egg in the bag.", BaCall.RED_EGG, 12);
		bag.inventory(Map.of(BaIds.EGG_GREEN, 20), 13);
		bag.menu("Empty", "Collection bag", 14);
		bag.inventory(Map.of(BaIds.EGG_GREEN, 20), 15);
		bag.inventory(Map.of(BaIds.EGG_GREEN, 20), 21);
		assertEquals(2, bag.count(BaIds.EGG_GREEN));
		assertEquals(1, bag.count(BaIds.EGG_RED));
		bag.menu("Empty", "Collection bag", 22);
		bag.inventory(Map.of(BaIds.EGG_GREEN, 21), 23);
		assertEquals(1, bag.count(BaIds.EGG_GREEN));
		assertEquals(1, bag.count(BaIds.EGG_RED));
		bag.inventory(Map.of(BaIds.EGG_GREEN, 21), 24);
		assertEquals(1, bag.count(BaIds.EGG_GREEN));
	}

	@Test
	public void takingAnEggCancelsAnUnconfirmedEmpty()
	{
		EggBag bag = new EggBag();
		bag.message("You put the egg in the bag.", BaCall.GREEN_EGG, 10);
		bag.menu("Empty", "Collection bag", 11);
		bag.menu("Take", "Green egg", 12);
		bag.inventory(Map.of(BaIds.EGG_GREEN, 1), 13);
		assertEquals(1, bag.count(BaIds.EGG_GREEN));
	}
}
