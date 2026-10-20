package com.galesong.baautopilot.state;

import org.junit.Test;
import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.Role;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import static org.junit.Assert.*;

public class QueenProgressTest
{
	private static final String LOAD = "The omega egg has been loaded into the hopper.";

	@Test public void handoffCueRequiresKnownRolesDirectedUseAnimationAndAdjacency()
	{
		QueenProgress progress = new QueenProgress();
		WorldPoint tile = ArenaTiles.queenCollectorWatch();
		PlayerView collector = new PlayerView("Collector", Role.COLLECTOR, tile, -1, null);
		for (PlayerView wrong : List.of(new PlayerView("Defender", null, tile.dx(1), 832, "Collector"),
			new PlayerView("Defender", Role.DEFENDER, tile.dx(1), -1, "Collector"),
			new PlayerView("Defender", Role.DEFENDER, tile.dx(1), 832, null),
			new PlayerView("Defender", Role.DEFENDER, tile.dx(4), 832, "Collector")))
			progress.players(10, List.of(wrong, collector));
		assertEquals(-1, progress.getOmegaHandoffTick());
		progress.players(11, List.of(new PlayerView("Defender", Role.DEFENDER, tile.dx(1), AnimationID.HUMAN_PICKUPTABLE, "Collector"), collector));
		assertEquals(11, progress.getOmegaHandoffTick());
		progress.players(12, List.of());
		assertEquals(11, progress.getOmegaHandoffTick());
		progress.passedSpiked();
		assertEquals(1, progress.getSpikedPassed());
		progress.reset();
		assertEquals(-1, progress.getOmegaHandoffTick());
		assertEquals(0, progress.getSpikedPassed());
	}

	@Test public void firedEggsAreCountedOnceAcrossLoadMessagesAndAmmoDrops()
	{
		QueenProgress progress = new QueenProgress();
		progress.ammo(10, 0);
		progress.message(LOAD, false, 11);
		progress.ammo(11, 1);
		assertEquals(0, progress.getOmegaFired());
		progress.ammo(12, 0);
		progress.ammo(13, 0);
		assertEquals(1, progress.getOmegaFired());
		assertEquals(12, progress.getLastShotTick());
		progress.ammo(14, 1);
		progress.message(LOAD, false, 15);
		progress.ammo(15, 1);
		progress.ammo(16, 0);
		progress.ammo(17, 0);
		assertEquals(2, progress.getOmegaFired());
	}

	@Test public void instantLoadAndShotBetweenTicksIsCountedAfterSettling()
	{
		QueenProgress progress = new QueenProgress();
		progress.ammo(10, 0);
		for (int tick = 11; tick < 16; tick++)
		{
			progress.message(LOAD, false, tick);
			progress.ammo(tick, 0);
		}
		assertEquals(3, progress.getOmegaFired());
		progress.ammo(17, 0);
		assertEquals(5, progress.getOmegaFired());
		assertEquals(17, progress.getLastShotTick());
	}

	@Test public void delayedAmmoIncreaseDoesNotCreateAPrematureShotAndResetClearsProgress()
	{
		QueenProgress progress = new QueenProgress();
		progress.ammo(10, 0);
		progress.message(LOAD, false, 11);
		progress.ammo(11, 0);
		progress.ammo(12, 1);
		progress.ammo(13, 1);
		assertEquals(0, progress.getOmegaFired());
		progress.message("You passed the egg to the player.", false, 14);
		progress.message("You passed the egg to the player.", true, 15);
		assertEquals(1, progress.getYellowPassed());
		progress.died();
		progress.reset();
		assertFalse(progress.isDead());
		assertEquals(0, progress.getYellowPassed());
		assertEquals(0, progress.getOmegaFired());
		progress.ammo(20, 0);
		assertEquals(0, progress.getOmegaFired());
	}
}
