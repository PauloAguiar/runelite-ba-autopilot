package com.galesong.baautopilot.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.Role;
import lombok.Getter;
import net.runelite.api.gameval.AnimationID;

/** Confirmed Queen hand-offs and ammunition use, including omegas loaded and fired between ticks. */
public final class QueenProgress
{
	static final int LOAD_SETTLE_TICKS = 2;
	private final Deque<Integer> pendingLoads = new ArrayDeque<>();
	@Getter private int yellowPassed;
	@Getter private int spikedPassed;
	@Getter private int omegaHandoffTick = -1;
	@Getter private int omegaFired;
	@Getter private int lastShotTick = -1;
	@Getter private boolean dead;
	private int observedSpent;
	private int settledLoads;
	private int lastAmmo = -1;

	public void reset()
	{
		pendingLoads.clear();
		yellowPassed = spikedPassed = omegaFired = observedSpent = settledLoads = 0;
		omegaHandoffTick = -1;
		lastShotTick = lastAmmo = -1;
		dead = false;
	}

	public void message(String text, boolean passingYellow, int tick)
	{
		if (passingYellow && "You passed the egg to the player.".equals(text)) yellowPassed++;
		if ("The omega egg has been loaded into the hopper.".equals(text)) pendingLoads.addLast(tick);
	}

	public void ammo(int tick, int loaded)
	{
		if (lastAmmo >= 0 && loaded < lastAmmo) observedSpent += lastAmmo - loaded;
		lastAmmo = loaded;
		while (!pendingLoads.isEmpty() && tick - pendingLoads.peekFirst() >= LOAD_SETTLE_TICKS)
		{
			pendingLoads.removeFirst();
			settledLoads++;
		}
		// Independent lower bounds: adding them would count the same egg twice.
		int fired = Math.max(observedSpent, Math.max(0, settledLoads - loaded));
		if (fired > omegaFired) { omegaFired = fired; lastShotTick = tick; }
	}

	public void died() { dead = true; }

	public void passedSpiked() { spikedPassed++; }

	/** A directed use animation is a readiness cue, not proof of the Collector's egg count. */
	public void players(int tick, List<PlayerView> players)
	{
		for (PlayerView giver : players)
		{
			if (giver.getRole() != Role.DEFENDER || giver.getAnimation() != AnimationID.HUMAN_PICKUPTABLE
				|| giver.getInteracting() == null || giver.getTile() == null) continue;
			for (PlayerView receiver : players)
				if (receiver.getRole() == Role.COLLECTOR && giver.getInteracting().equals(receiver.getName())
					&& receiver.getTile() != null && ArenaTiles.distance(giver.getTile(), receiver.getTile()) <= 1)
					omegaHandoffTick = tick;
		}
	}
}
