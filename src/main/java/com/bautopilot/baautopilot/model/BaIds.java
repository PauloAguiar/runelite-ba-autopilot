package com.bautopilot.baautopilot.model;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/** Object and item ids the planner and tracker share. */
public final class BaIds
{
	public static final int ATTACKER_MACHINE = ObjectID.BARASSAULT_VENDING_MACHINE_ATTACKER;
	public static final int DEFENDER_MACHINE = ObjectID.BARASSAULT_VENDING_MACHINE_DEFENDER;
	public static final int HEALER_MACHINE = ObjectID.BARASSAULT_VENDING_MACHINE_HEALER;
	public static final int COLLECTOR_CONVERTER = ObjectID.BARASSAULT_VENDING_MACHINE_COLLECTOR;
	public static final int SPRING = ObjectID.BARBASSAULT_NATURAL_SPRING;

	public static final int TRAP_FULL = ObjectID.BARBASSAULT_TRAP;
	public static final int TRAP_ONE = ObjectID.BARBASSAULT_TRAP_1USE;
	public static final int TRAP_BROKEN = ObjectID.BARBASSAULT_TRAP_BROKEN;

	public static final int[] HOPPERS = {
		ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_EMPTY, ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_HALF_FULL,
		ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_FULL, ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_EMPTY_N,
		ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_HALF_FULL_N, ObjectID.BARBASSAULT_EGG_LAUNCHER_BOX_FULL_N,
		ObjectID.BARBASSAULT_EGG_HOPPER,
	};
	/**
	 * The clickable egg launcher ("Shoot") is the turret bottom, 20133: recorded Shoot clicks
	 * (2026-10-15) all targeted it, and the launcher base ids never appeared in the scene.
	 */
	public static final int[] LAUNCHERS = {
		ObjectID.BARBASSAULT_TURRET_BOTTOM,
		ObjectID.BARBASSAULT_EGG_LAUNCHER_BASE, ObjectID.BARBASSAULT_EGG_LAUNCHER_BASE_N,
	};
	/**
	 * Mushrooms with "Get-Spikes": only 20250. Each one has a decorative 20252 ("mushroom group")
	 * on the next tile, which has no option (recorded wave 10, 2026-10-15).
	 */
	public static final int[] MUSHROOMS = {
		ObjectID.BARBASSAULT_PETRIFIED_MUSHROOM,
	};
	public static final int[] POISON_CRATERS = {
		ObjectID.BARBASSAULT_CRATER_POISION, ObjectID.BARBASSAULT_CRATER_POISION_BIG,
	};
	public static final int[] LAVA_CRATERS = {
		ObjectID.BARBASSAULT_CRATER_LAVA, ObjectID.BARBASSAULT_CRATER_LAVA_BIG,
	};

	/** Every object the tracker keeps a location for. */
	public static final Set<Integer> TRACKED_OBJECTS;

	static
	{
		ImmutableSet.Builder<Integer> b = ImmutableSet.builder();
		b.add(ATTACKER_MACHINE, DEFENDER_MACHINE, HEALER_MACHINE, COLLECTOR_CONVERTER, SPRING,
			TRAP_FULL, TRAP_ONE, TRAP_BROKEN);
		for (int[] ids : new int[][]{HOPPERS, LAUNCHERS, MUSHROOMS, POISON_CRATERS, LAVA_CRATERS})
		{
			for (int id : ids)
			{
				b.add(id);
			}
		}
		TRACKED_OBJECTS = b.build();
	}

	public static final int EGG_GREEN = ItemID.BARBASSAULT_EGG_01;
	public static final int EGG_RED = ItemID.BARBASSAULT_EGG_02;
	public static final int EGG_BLUE = ItemID.BARBASSAULT_EGG_03;
	public static final int EGG_YELLOW = ItemID.BARBASSAULT_EGG_04;
	public static final int EGG_POISONED = ItemID.BARBASSAULT_EGG_POISON;
	public static final int EGG_SPIKED = ItemID.BARBASSAULT_EGG_POISON_SPIKES;
	public static final int EGG_OMEGA = ItemID.BARBASSAULT_EGG_HEATED_POISON_SPIKES;
	public static final int SPIKES = ItemID.BARBASSAULT_SPIKES;

	public static final int LOGS = ItemID.LOGS;
	public static final int HAMMER = ItemID.HAMMER;

	public static final Set<Integer> BAIT = ImmutableSet.of(
		ItemID.BARBASSAULT_RUNNER_FOOD, ItemID.BARBASSAULT_RUNNER_FOOD2, ItemID.BARBASSAULT_RUNNER_FOOD3);
	public static final Set<Integer> POISON_FOOD = ImmutableSet.of(
		ItemID.BARBASSAULT_POISION_01, ItemID.BARBASSAULT_POISION_02, ItemID.BARBASSAULT_POISION_03);
	public static final Set<Integer> VIALS_WITH_WATER = ImmutableSet.of(
		ItemID.BARBASSAULT_VIAL_04_FULL, ItemID.BARBASSAULT_VIAL_04_3Q, ItemID.BARBASSAULT_VIAL_04_HALF,
		ItemID.BARBASSAULT_VIAL_04_1Q);
	public static final int VIAL_EMPTY = ItemID.BARBASSAULT_VIAL_04_EMPTY;
	public static final int VIAL_FULL = ItemID.BARBASSAULT_VIAL_04_FULL;

	/** Healer vial doses from its item (4 full, 3, 2, 1, 0 empty), -1 when you carry none. */
	public static int vialCharges(java.util.function.IntPredicate has)
	{
		if (has.test(ItemID.BARBASSAULT_VIAL_04_FULL))
		{
			return 4;
		}
		if (has.test(ItemID.BARBASSAULT_VIAL_04_3Q))
		{
			return 3;
		}
		if (has.test(ItemID.BARBASSAULT_VIAL_04_HALF))
		{
			return 2;
		}
		if (has.test(ItemID.BARBASSAULT_VIAL_04_1Q))
		{
			return 1;
		}
		return has.test(ItemID.BARBASSAULT_VIAL_04_EMPTY) ? 0 : -1;
	}

	/** The vial item you carry (for its icon), -1 for none. */
	public static int vialItem(java.util.function.IntPredicate has)
	{
		for (int id : new int[]{ItemID.BARBASSAULT_VIAL_04_FULL, ItemID.BARBASSAULT_VIAL_04_3Q, ItemID.BARBASSAULT_VIAL_04_HALF,
			ItemID.BARBASSAULT_VIAL_04_1Q, ItemID.BARBASSAULT_VIAL_04_EMPTY})
		{
			if (has.test(id))
			{
				return id;
			}
		}
		return -1;
	}
	public static final Set<Integer> ARROWS = ImmutableSet.of(
		ItemID.BARBASSAULT_BULLET_ARROW, ItemID.BARBASSAULT_FIELD_ARROW, ItemID.BARBASSAULT_BLUNT_ARROW,
		ItemID.BARBASSAULT_BARBED_ARROW);
	public static final Set<Integer> HORNS = ImmutableSet.of(
		ItemID.BARBASSAULT_ATT_HORN_01, ItemID.BARBASSAULT_ATT_HORN_02, ItemID.BARBASSAULT_ATT_HORN_03,
		ItemID.BARBASSAULT_ATT_HORN_04, ItemID.BARBASSAULT_ATT_HORN_05,
		ItemID.BARBASSAULT_HEAL_HORN_01, ItemID.BARBASSAULT_HEAL_HORN_02, ItemID.BARBASSAULT_HEAL_HORN_03,
		ItemID.BARBASSAULT_HEAL_HORN_04, ItemID.BARBASSAULT_HEAL_HORN_05,
		ItemID.BARBASSAULT_DEFENDER_HORN, ItemID.BARBASSAULT_HORN_COLLECTOR);

	private BaIds()
	{
	}

	public static boolean contains(int[] ids, int id)
	{
		for (int i : ids)
		{
			if (i == id)
			{
				return true;
			}
		}
		return false;
	}
}
