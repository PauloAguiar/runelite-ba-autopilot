package com.bautopilot.baautopilot.model;

/**
 * Per-wave penance counts and healer hit points (OSRS Wiki, Barbarian Assault/Strategies and
 * Advanced strategies). Index 0 is unused so a wave number indexes directly.
 */
public final class WaveInfo
{
	public static final int TICKS_PER_CALL = 50;
	public static final int POISON_INTERVAL_TICKS = 5;
	public static final int FOOD_DAMAGE = 4;

	private static final int[] HEALER_HP = {0, 27, 32, 37, 43, 49, 55, 60, 67, 76, 60};
	private static final int[] HEALERS_INITIAL = {0, 2, 3, 2, 3, 4, 4, 4, 5, 6, 4};
	private static final int[] HEALERS_TOTAL = {0, 2, 3, 3, 4, 5, 6, 7, 7, 8, 7};
	private static final int[] RUNNERS_INITIAL = {0, 2, 2, 2, 3, 4, 4, 5, 5, 5, 5};
	private static final int[] RUNNERS_TOTAL = {0, 2, 3, 4, 4, 5, 6, 6, 7, 9, 6};
	/** Out at once (OSRS Wiki wave table "initial+reserves"); the reserves come only after kills. */
	private static final int[] FIGHTERS_INITIAL = {0, 2, 2, 5, 5, 3, 5, 5, 7, 6, 5};
	private static final int[] RANGERS_INITIAL = {0, 2, 3, 3, 3, 5, 5, 6, 5, 7, 6};
	private static final int[] FIGHTERS_TOTAL = {0, 4, 5, 5, 6, 6, 6, 7, 7, 8, 7};
	private static final int[] RANGERS_TOTAL = {0, 4, 4, 6, 6, 6, 7, 7, 8, 8, 7};
	/** Logs the defender should carry: 2 until wave 4, then 4. */
	private static final int[] DEFENDER_LOGS = {0, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4};

	private WaveInfo()
	{
	}

	public static boolean valid(int wave)
	{
		return wave >= 1 && wave <= 10;
	}

	public static int healerHp(int wave)
	{
		return valid(wave) ? HEALER_HP[wave] : 0;
	}

	public static int healersInitial(int wave)
	{
		return valid(wave) ? HEALERS_INITIAL[wave] : 0;
	}

	public static int healersTotal(int wave)
	{
		return valid(wave) ? HEALERS_TOTAL[wave] : 0;
	}

	public static int runnersInitial(int wave)
	{
		return valid(wave) ? RUNNERS_INITIAL[wave] : 0;
	}

	public static int runnersTotal(int wave)
	{
		return valid(wave) ? RUNNERS_TOTAL[wave] : 0;
	}

	public static int fightersTotal(int wave)
	{
		return valid(wave) ? FIGHTERS_TOTAL[wave] : 0;
	}

	public static int rangersTotal(int wave)
	{
		return valid(wave) ? RANGERS_TOTAL[wave] : 0;
	}

	/** Out at once for a penance type this wave (the rest are reserves, out only after kills). */
	public static int initial(PenanceType type, int wave)
	{
		if (!valid(wave))
		{
			return 0;
		}
		switch (type)
		{
			case FIGHTER:
				return FIGHTERS_INITIAL[wave];
			case RANGER:
				return RANGERS_INITIAL[wave];
			case RUNNER:
				return RUNNERS_INITIAL[wave];
			default:
				return HEALERS_INITIAL[wave];
		}
	}

	public static int total(PenanceType type, int wave)
	{
		switch (type)
		{
			case FIGHTER:
				return fightersTotal(wave);
			case RANGER:
				return rangersTotal(wave);
			case RUNNER:
				return runnersTotal(wave);
			default:
				return healersTotal(wave);
		}
	}

	public static int defenderLogs(int wave)
	{
		return valid(wave) ? DEFENDER_LOGS[wave] : 2;
	}

	/** Gau Cho's log counts (User:Gau Cho/Defender): 0, 1 x3, 2 x3, 3, 4, then 2 on wave 10. */
	private static final int[] GAU_CHO_LOGS = {0, 0, 1, 1, 1, 2, 2, 2, 3, 4, 2};

	public static int defenderLogs(int wave, DefenderGuide guide)
	{
		if (guide.isGauCho())
		{
			return valid(wave) ? GAU_CHO_LOGS[wave] : 1;
		}
		return defenderLogs(wave);
	}

	/**
	 * Total poison damage one food deals when it is never topped up: 4 on use, then 20 hits
	 * every 5 ticks of 4,4,4,4,4,3,3,3,3,3,2,...,1 = 50.
	 */
	public static int fullPoisonDamage()
	{
		return FOOD_DAMAGE + 5 * (4 + 3 + 2 + 1);
	}

	/** Damage of the n-th (1-based) poison hit after the last food. */
	public static int poisonHit(int n)
	{
		if (n < 1 || n > 20)
		{
			return 0;
		}
		return 4 - (n - 1) / 5;
	}

	/**
	 * Advanced-strategies kill order for attackers: which type to finish next given how many
	 * fighters and rangers have died so far. Wave 1 alternates starting with a fighter; wave 2
	 * kills two fighters then alternates ranger-fighter; wave 3 alternates ranger-fighter; wave 4+
	 * alternates fighter-ranger.
	 */
	public static PenanceType attackerNext(int wave, int kills)
	{
		switch (wave)
		{
			case 1:
				return kills % 2 == 0 ? PenanceType.FIGHTER : PenanceType.RANGER;
			case 2:
				if (kills < 2)
				{
					return PenanceType.FIGHTER;
				}
				return (kills - 2) % 2 == 0 ? PenanceType.RANGER : PenanceType.FIGHTER;
			case 3:
				return kills % 2 == 0 ? PenanceType.RANGER : PenanceType.FIGHTER;
			default:
				return kills % 2 == 0 ? PenanceType.FIGHTER : PenanceType.RANGER;
		}
	}
}
