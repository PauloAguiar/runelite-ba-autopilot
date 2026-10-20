package com.galesong.baautopilot.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Fixed arena landmarks in instance-template coordinates (region 7509 for waves 1-9, 7508 for
 * wave 10). Convert live tiles with {@code WorldPoint.fromLocalInstance} before comparing.
 *
 * <p>The defender food pattern is the wiki's 1-4-1-4-1 (waves 1-9) and 1-4-4-1 (wave 10), on
 * the community "beginner tiles" (safety / trap / trail / mainstack / west relure).
 */
public final class ArenaTiles
{
	public static final int REGION_WAVES_1_9 = 7509;
	public static final int REGION_WAVE_10 = 7508;
	public static final int REGION_LOBBY = 10322;

	@Value
	public static class FoodSpot
	{
		String name;
		WorldPoint tile;
		int count;
		boolean good;

		public String label()
		{
			return count + (good ? " good" : " bad");
		}
	}

	private static final List<FoodSpot> PATTERN_1_9 = Collections.unmodifiableList(Arrays.asList(
		new FoodSpot("Safety", p(REGION_WAVES_1_9, 45, 29), 1, false),
		new FoodSpot("Trap", p(REGION_WAVES_1_9, 45, 35), 4, true),
		new FoodSpot("Trail", p(REGION_WAVES_1_9, 39, 39), 1, false),
		new FoodSpot("Mainstack", p(REGION_WAVES_1_9, 35, 43), 4, true),
		new FoodSpot("West relure", p(REGION_WAVES_1_9, 29, 46), 1, false)));

	/**
	 * OSRS Wiki 1-4-4-1: good food on the tile west of the trap, the last bad food where the logs
	 * respawn on the north wall. Recorded 2026-10-16 (wave 10): east trap at (45, 34), logs at
	 * (29, 47) / (30, 46).
	 */
	private static final List<FoodSpot> PATTERN_10 = Collections.unmodifiableList(Arrays.asList(
		new FoodSpot("Safety", p(REGION_WAVE_10, 39, 32), 1, false),
		new FoodSpot("Trap", p(REGION_WAVE_10, 44, 34), 4, true),
		new FoodSpot("Mainstack", p(REGION_WAVE_10, 38, 46), 4, true),
		new FoodSpot("West relure", p(REGION_WAVE_10, 30, 46), 1, false)));

	private ArenaTiles()
	{
	}

	public static WorldPoint p(int region, int x, int y)
	{
		return WorldPoint.fromRegion(region, x, y, 0);
	}

	public static List<FoodSpot> defenderPattern(int wave)
	{
		return wave == 10 ? PATTERN_10 : PATTERN_1_9;
	}

	/**
	 * The bait pattern a guide uses, on the wiki's tiles. Gau Cho: trap 4 good, trail 1 any,
	 * mainstack 4 good (no log-spawn relure; his wave 10 is not written up, so the wiki's wave 10
	 * minus its relure). Without {@code safety} the safety bait is left out.
	 */
	public static List<FoodSpot> defenderPattern(int wave, DefenderGuide guide, boolean safety)
	{
		List<FoodSpot> out = new java.util.ArrayList<>();
		for (FoodSpot f : defenderPattern(wave))
		{
			if (!safety && f.getName().equals("Safety"))
			{
				continue;
			}
			if (guide == DefenderGuide.GAU_CHO && f.getName().equals("West relure"))
			{
				continue;
			}
			out.add(f);
		}
		return Collections.unmodifiableList(out);
	}

	/** Where the defender stands to stack runners: on the trap food. */
	public static WorldPoint trapFood(int wave)
	{
		return defenderPattern(wave).get(1).getTile();
	}

	/**
	 * Where attackers wait for the next fighter or ranger: 3 tiles south of the gap between their
	 * caves. Recorded spawns (2026-10-15/16, every recording): fighters (24, 47), rangers (18, 45)
	 * on waves 1-9 and (24, 47) / (18, 46) on wave 10.
	 */
	public static WorldPoint attackerWait(int wave)
	{
		return wave == 10 ? p(REGION_WAVE_10, 21, 43) : p(REGION_WAVES_1_9, 21, 43);
	}

	/** Western corridor tiles observed in recorded openings, outside the cannon hill. */
	public static WorldPoint collectorWestApproach(int wave)
	{
		return p(wave == 10 ? REGION_WAVE_10 : REGION_WAVES_1_9, 18, wave == 10 ? 31 : 29);
	}

	public static WorldPoint collectorBehindHill(int wave)
	{
		return p(wave == 10 ? REGION_WAVE_10 : REGION_WAVES_1_9, 18, 37);
	}

	/** Collector waiting position for Queen yellow drops, north of the southern pool. */
	public static WorldPoint queenCollectorWatch()
	{
		return p(REGION_WAVE_10, 28, 25);
	}

	/**
	 * Where a healer waits for the next penance healer: 3 tiles south of its cave. Recorded spawns:
	 * (42, 45) on waves 1-9, (36, 47) on wave 10.
	 */
	public static WorldPoint healerWait(int wave)
	{
		return wave == 10 ? p(REGION_WAVE_10, 36, 44) : p(REGION_WAVES_1_9, 42, 42);
	}

	/** Good food a few tiles south of the runner spawn cave. */
	public static WorldPoint mainstack(int wave)
	{
		return spot(wave, "Mainstack");
	}

	/** The bad food several tiles south of the trap: past it, a runner is heading for the exit. */
	public static WorldPoint safety(int wave)
	{
		return spot(wave, "Safety");
	}

	private static WorldPoint spot(int wave, String name)
	{
		return defenderPattern(wave).stream().filter(f -> f.getName().equals(name)).findFirst().get().getTile();
	}

	/** One tile south of the trap food: step here to let a gathered stack eat in turn. */
	/** The log spawn on the West relure tile (the other log lies diagonally next to it). */
	public static WorldPoint logSpawn(int wave)
	{
		return wave == 10 ? p(REGION_WAVE_10, 30, 46) : p(REGION_WAVES_1_9, 29, 46);
	}

	public static WorldPoint trapStep(int wave)
	{
		return trapFood(wave).dy(-1);
	}

	/** Close enough to service the trap while leaving the food tile free for runners. */
	public static WorldPoint defenderService(int wave)
	{
		return trapFood(wave).dy(1);
	}

	public static int patternGood(int wave)
	{
		return defenderPattern(wave).stream().filter(FoodSpot::isGood).mapToInt(FoodSpot::getCount).sum();
	}

	public static int patternBad(int wave)
	{
		return defenderPattern(wave).stream().filter(s -> !s.isGood()).mapToInt(FoodSpot::getCount).sum();
	}

	/**
	 * The BA lobby: the underground region, or the outpost above it (the BA Minigame plugin's box,
	 * x 2522-2538, y 3560-3579).
	 */
	public static boolean isLobby(WorldPoint p)
	{
		return p != null && (p.getRegionID() == REGION_LOBBY
			|| (p.getX() >= 2522 && p.getX() <= 2538 && p.getY() >= 3560 && p.getY() <= 3579));
	}

	public static boolean isArenaRegion(int region)
	{
		return region == REGION_WAVES_1_9 || region == REGION_WAVE_10;
	}

	/** Chebyshev distance between two template tiles; MAX_VALUE across planes or nulls. */
	public static int distance(WorldPoint a, WorldPoint b)
	{
		if (a == null || b == null || a.getPlane() != b.getPlane())
		{
			return Integer.MAX_VALUE;
		}
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}
}
