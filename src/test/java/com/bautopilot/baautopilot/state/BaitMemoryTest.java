package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.ArenaTiles;
import com.bautopilot.baautopilot.model.BaCall;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaitMemoryTest
{
	private static final WorldPoint TILE = ArenaTiles.mainstack(6);
	private static final int WORMS = BaCall.BAIT_WORMS.getItemId();

	@Test public void sameTickReplacementPreservesEachDropsQualityAndAgeWithoutLandingAgain()
	{
		BaitMemory memory = new BaitMemory();
		for (boolean good : new boolean[]{true, false})
		{
			memory.despawn(new GroundItemView(WORMS, TILE, 1, good, 100), 200);
			GroundItemView restored = memory.spawn(WORMS, TILE, 1, 200, TILE, () -> {
				fail("A scene replacement is not a new drop"); return !good;
			});
			assertEquals(good, restored.isGood());
			assertEquals(100, restored.getSpawnTick());
		}
	}

	@Test public void aRealPickupAndRedropUsesTheNewCallEvenWithinTheSameTick()
	{
		BaitMemory memory = new BaitMemory();
		memory.despawn(new GroundItemView(WORMS, TILE, 1, true, 100), 200);
		memory.menu("Drop", "Worms", 200);
		GroundItemView redropped = memory.spawn(WORMS, TILE, 1, 200, TILE, () -> false);
		assertFalse(redropped.isGood());
		assertEquals(200, redropped.getSpawnTick());
	}

	@Test public void removedFoodIsNotResurrectedAcrossTicksTilesOrWaves()
	{
		BaitMemory memory = new BaitMemory();
		memory.despawn(new GroundItemView(WORMS, TILE, 1, true, 100), 200);
		assertFalse(memory.spawn(WORMS, TILE.dx(1), 1, 200, TILE, () -> false).isGood());
		assertFalse(memory.spawn(WORMS, TILE, 1, 201, TILE, () -> false).isGood());
		memory.despawn(new GroundItemView(WORMS, TILE, 1, true, 100), 202);
		memory.reset();
		assertFalse(memory.spawn(WORMS, TILE, 1, 202, TILE, () -> false).isGood());
	}
}
