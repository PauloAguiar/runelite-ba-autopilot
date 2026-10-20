package com.galesong.baautopilot.tutorial;

import com.galesong.baautopilot.model.ArenaTiles;
import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.state.GroundItemView;
import com.galesong.baautopilot.state.ObjectView;
import com.galesong.baautopilot.state.PenanceView;
import com.galesong.baautopilot.state.Snapshot;
import com.google.common.collect.ImmutableList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Arena moments for the tutorial's autopilot box: a page describes the game as it would be, and
 * the plugin's own planner and box show what it says then. Objects stand where recordings put
 * them (the same tiles as arena-maps.json).
 */
final class Situations
{
	/** Any tick: what matters is the time into the wave and the call. */
	static final int TICK = 1000;

	private Situations()
	{
	}

	/** You, in a role, standing on a tile of a wave's arena; mid-wave with a fresh call made both ways. */
	static Snapshot.SnapshotBuilder arena(Role role, int wave, int x, int y)
	{
		return Snapshot.builder()
			.tick(TICK)
			.inArena(true)
			.wave(wave)
			.role(role)
			.waveTick(120)
			.callPeriod(2)
			.ticksToCallChange(30)
			.listenFresh(true)
			.shoutCall(madeCall(role))
			.shoutDone(true)
			.collectorOpeningComplete(true)
			.attackerOpeningComplete(true)
			.hp(90)
			.maxHp(99)
			.freeSlots(20)
			.playerTile(at(wave, x, y))
			.objects(objects(wave));
	}

	/** The first seconds of a wave: no calls yet, nothing done. */
	static Snapshot.SnapshotBuilder opening(Role role, int wave, int x, int y)
	{
		return arena(role, wave, x, y)
			.waveTick(3)
			.callPeriod(0)
			.ticksToCallChange(47)
			.listenFresh(false)
			.collectorOpeningComplete(false)
			.attackerOpeningComplete(false);
	}

	/** The call this role gives its partner, already made (call pages override it as owed). */
	private static BaCall madeCall(Role role)
	{
		switch (role)
		{
			case ATTACKER:
				return BaCall.RED_EGG;
			case COLLECTOR:
				return BaCall.ACCURATE;
			case DEFENDER:
				return BaCall.POISON_WORMS;
			default:
				return BaCall.BAIT_CRACKERS;
		}
	}

	static WorldPoint at(int wave, int x, int y)
	{
		return ArenaTiles.p(wave == 10 ? ArenaTiles.REGION_WAVE_10 : ArenaTiles.REGION_WAVES_1_9, x, y);
	}

	static PenanceView penance(int index, PenanceType type, int wave, int x, int y)
	{
		return PenanceView.builder()
			.npcIndex(index)
			.type(type)
			.tile(at(wave, x, y))
			.observedTick(TICK)
			.spawnTick(TICK - 60)
			.order(index)
			.build();
	}

	static GroundItemView item(int itemId, int wave, int x, int y, int quantity)
	{
		return new GroundItemView(itemId, at(wave, x, y), quantity, false, TICK - 20);
	}

	/** Dispensers, the spring, launchers, hoppers and traps; wave 10 has one launcher and the Queen's craters. */
	static List<ObjectView> objects(int wave)
	{
		ImmutableList.Builder<ObjectView> b = ImmutableList.builder();
		b.add(new ObjectView(BaIds.ATTACKER_MACHINE, at(wave, 33, 14)),
			new ObjectView(BaIds.DEFENDER_MACHINE, at(wave, 34, 14)),
			new ObjectView(BaIds.HEALER_MACHINE, at(wave, 35, 14)),
			new ObjectView(BaIds.COLLECTOR_CONVERTER, at(wave, 36, 14)),
			new ObjectView(BaIds.SPRING, at(wave, 26, 14)),
			new ObjectView(BaIds.LAUNCHERS[0], at(wave, 21, 34)),
			new ObjectView(BaIds.HOPPERS[0], at(wave, 22, 34)),
			new ObjectView(BaIds.TRAP_FULL, at(wave, 45, 34)),
			new ObjectView(BaIds.TRAP_FULL, at(wave, 15, 33)));
		if (wave == 10)
		{
			b.add(new ObjectView(BaIds.POISON_CRATERS[0], at(wave, 18, 17)),
				new ObjectView(BaIds.POISON_CRATERS[0], at(wave, 41, 17)),
				new ObjectView(BaIds.LAVA_CRATERS[0], at(wave, 16, 19)),
				new ObjectView(BaIds.LAVA_CRATERS[0], at(wave, 39, 18)),
				new ObjectView(BaIds.MUSHROOMS[0], at(wave, 23, 17)),
				new ObjectView(BaIds.MUSHROOMS[0], at(wave, 36, 17)));
		}
		else
		{
			b.add(new ObjectView(BaIds.LAUNCHERS[0], at(wave, 40, 34)),
				new ObjectView(BaIds.HOPPERS[0], at(wave, 41, 34)));
		}
		return b.build();
	}
}
