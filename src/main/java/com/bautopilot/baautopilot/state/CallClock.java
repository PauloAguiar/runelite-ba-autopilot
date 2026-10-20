package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.WaveInfo;

/**
 * The 30-second (50-tick) call cycle. Started by the wave message, then re-anchored whenever the
 * HUD's call-to-give flips, so drift from a missed message or a leader stall corrects itself.
 */
public class CallClock
{
	/**
	 * The first call runs from one tick after the wave message: in 8 recorded waves the HUD's call
	 * flipped at exactly wave message + 1 + 50k (waves 2-8; wave 1's message came late).
	 */
	public static final int START_OFFSET = 1;
	/** A flip this close to a boundary re-anchors the clock instead of being ignored. */
	public static final int RESYNC_WINDOW = 10;

	private int periodStart = -1;
	private int period;

	public void reset()
	{
		periodStart = -1;
		period = 0;
	}

	public void start(int tick)
	{
		periodStart = tick + START_OFFSET;
		period = 0;
	}

	public boolean running()
	{
		return periodStart >= 0;
	}

	/** Advances through any boundaries reached by {@code tick}; returns true if the period changed. */
	public boolean tick(int tick)
	{
		if (periodStart < 0)
		{
			return false;
		}
		boolean changed = false;
		while (tick - periodStart >= WaveInfo.TICKS_PER_CALL)
		{
			periodStart += WaveInfo.TICKS_PER_CALL;
			period++;
			changed = true;
		}
		return changed;
	}

	/** The call to give changed on the HUD at {@code tick}; returns true if that started a new period. */
	public boolean callFlipped(int tick)
	{
		if (periodStart < 0)
		{
			periodStart = tick;
			return true;
		}
		int into = tick - periodStart;
		if (into >= WaveInfo.TICKS_PER_CALL - RESYNC_WINDOW)
		{
			period++;
			periodStart = tick;
			return true;
		}
		if (into <= RESYNC_WINDOW)
		{
			periodStart = tick;
		}
		return false;
	}

	/** Tick the current call period began, or -1 when the clock isn't running. */
	public int periodStartTick()
	{
		return periodStart;
	}

	public int period()
	{
		return period;
	}

	public int ticksInto(int tick)
	{
		return periodStart < 0 ? -1 : Math.max(0, tick - periodStart);
	}

	public int ticksToChange(int tick)
	{
		return periodStart < 0 ? -1 : WaveInfo.TICKS_PER_CALL - Math.max(0, tick - periodStart);
	}
}
