package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.WaveInfo;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** One live penance NPC, as the planner sees it (template coordinates, no client references). */
@Value
@Builder(toBuilder = true)
public class PenanceView
{
	int npcIndex;
	PenanceType type;
	WorldPoint tile;
	/** Previous consecutive observation, for diagnostic motion rather than an inferred AI timer. */
	WorldPoint previousTile;
	int observedTick;
	@Builder.Default
	int animation = -1;
	String overhead;
	/** Health bar ratio / scale; scale -1 when no bar has been seen yet (full health). */
	@Builder.Default
	int healthRatio = -1;
	@Builder.Default
	int healthScale = -1;
	/** Healers only: 1-based spawn order (NPC index order), 0 otherwise. */
	int order;
	/** Healers only: correct poisoned food this plugin saw land on it. */
	int foods;
	int foodsThisPeriod;
	@Builder.Default
	int lastFoodTick = -1;
	/** Latest observed poison damage; food counts remain confirmed own inventory consumption. */
	@Builder.Default
	int lastPoisonDamage = -1;
	@Builder.Default
	int lastPoisonTick = -1;
	int spawnTick;
	boolean dying;
	/** Walked out of view and presumed alive; {@link #tile} is where it was last seen. */
	boolean unseen;
	/** Ticks since it walked out of view (0 while in view). */
	int unseenTicks;
	/** Hitsplat damage this wave: anyone's, yours, poison. */
	int damageTaken;
	int damageMine;
	int damagePoison;
	/** Damage per second over the last {@link DamageLog#WINDOW_TICKS}. */
	double dps;

	/**
	 * HP left, estimated from the damage taken and the health bar (no HP table needed): -1 before
	 * both are known.
	 */
	public int estimatedHpLeft()
	{
		double f = hpFraction();
		if (damageTaken <= 0 || healthScale <= 0 || f >= 1)
		{
			return -1;
		}
		return (int) Math.round(damageTaken * f / (1 - f));
	}

	/** Seconds to kill at the current damage per second, or -1 when unknown or nothing is hitting it. */
	public double secondsToKill()
	{
		int left = estimatedHpLeft();
		return left < 0 || dps <= 0 ? -1 : left / dps;
	}

	public double hpFraction()
	{
		if (healthScale <= 0 || healthRatio < 0)
		{
			return 1.0;
		}
		return (double) healthRatio / healthScale;
	}

	public boolean poisoned()
	{
		return lastFoodTick >= 0 || lastPoisonTick >= 0;
	}

	/**
	 * Use an observed hit over an older food estimate, including poison applied by a partner.
	 * Outside view, estimate up to five remaining hits at the last observed strength.
	 * A newer confirmed food immediately restores the estimate to 4 until another hit is seen.
	 */
	private int poisonHitsDone(int tick)
	{
		// A confirmed feed wins a tie with an older poison hit in the same game tick.
		if (lastPoisonTick >= 0 && lastPoisonTick > lastFoodTick)
		{
			return (WaveInfo.FOOD_DAMAGE - lastPoisonDamage) * 5
				+ Math.max(0, (tick - lastPoisonTick) / WaveInfo.POISON_INTERVAL_TICKS);
		}
		return Math.max(0, (tick - lastFoodTick) / WaveInfo.POISON_INTERVAL_TICKS);
	}

	/** Poison damage still to come from the latest food at {@code tick}. */
	public int poisonRemaining(int tick)
	{
		if (!poisoned())
		{
			return 0;
		}
		int hitsDone = poisonHitsDone(tick);
		int remaining = 0;
		for (int n = hitsDone + 1; n <= 20; n++)
		{
			remaining += WaveInfo.poisonHit(n);
		}
		return remaining;
	}

	/** Damage of the next poison hit (4 right after feeding, decaying to 1, then 0). */
	public int nextPoisonHit(int tick)
	{
		if (!poisoned())
		{
			return 0;
		}
		int hitsDone = poisonHitsDone(tick);
		return WaveInfo.poisonHit(hitsDone + 1);
	}
}
