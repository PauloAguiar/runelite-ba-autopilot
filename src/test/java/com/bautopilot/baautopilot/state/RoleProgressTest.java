package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.Role;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarbitComposition;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleProgressTest
{
	private static final class Harness
	{
		GameState state = GameState.LOGGED_IN;
		int reads, definitions;
		boolean definitionsAvailable = true;
		final Map<Integer, Integer> values = new HashMap<>();
		final RoleProgress progress = new RoleProgress();
		final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (self, method, args) ->
		{
			switch (method.getName())
			{
				case "getGameState": return state;
				case "getVarbitValue": reads++; return values.getOrDefault((int) args[0], 0);
				case "getVarbit":
					definitions++;
					int id = (int) args[0];
					return !definitionsAvailable ? null : Proxy.newProxyInstance(VarbitComposition.class.getClassLoader(),
						new Class<?>[]{VarbitComposition.class}, (definition, field, ignored) ->
						{
							if (field.getName().equals("getIndex")) return backing(id);
							throw new AssertionError("Unexpected definition read: " + field.getName());
						});
				default: throw new AssertionError("Progress scanned unrelated client state: " + method.getName());
			}
		});
		Snapshot read() { return progress.read(client); }
		void varbit(int id) { progress.changed(event(id, -1)); }
		void varp(int id) { progress.changed(event(-1, id)); }
	}

	private static int backing(int varbit) { return 10_000 + varbit / 4; }
	private static VarbitChanged event(int varbit, int varp)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(varbit);
		event.setVarpId(varp);
		return event;
	}

	@Test public void readsAllRolesWithoutAnArenaAndIncludesPointMultipliers()
	{
		Harness h = new Harness();
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_ATT, 1);
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_DEF, 2);
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_COL, 4);
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_HEAL, 5);
		h.values.put(VarbitID.BARBASSAULT_POINTS_ATTACKER_BASE, 23);
		h.values.put(VarbitID.BARBASSAULT_POINTS_DEFENDER_BASE, 100);
		h.values.put(VarbitID.BARBASSAULT_POINTS_COLLECTOR_BASE, 200);
		h.values.put(VarbitID.BARBASSAULT_POINTS_HEALER_BASE, 300);
		h.values.put(VarbitID.BARBASSAULT_POINTS_DEFENDER_EXTRA, 1);
		h.values.put(VarbitID.BARBASSAULT_POINTS_COLLECTOR_EXTRA, 2);
		h.values.put(VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA, 3);
		Snapshot progress = h.read();
		assertFalse(progress.isInArena());
		assertEquals(Map.of(Role.ATTACKER, 1, Role.DEFENDER, 2, Role.COLLECTOR, 4, Role.HEALER, 5), progress.getRoleLevels());
		assertEquals(Map.of(Role.ATTACKER, 23, Role.DEFENDER, 612, Role.COLLECTOR, 1224, Role.HEALER, 1836), progress.getRolePoints());
		assertEquals(12, h.reads);
		assertEquals(12, h.definitions);
	}

	@Test public void aThousandIdleReadsAndUnrelatedEventsDoNotReadVariablesAgain()
	{
		Harness h = new Harness();
		Snapshot first = h.read();
		for (int i = 0; i < 1000; i++)
		{
			h.varbit(1);
			h.varp(1);
			assertSame(first, h.read());
		}
		assertEquals(12, h.reads);
		assertEquals(12, h.definitions);
	}

	@Test public void coalescesPointRolloverAndLevelChangesFromBackingVarpEvents()
	{
		Harness h = new Harness();
		Snapshot first = h.read();
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_DEF, 4);
		h.values.put(VarbitID.BARBASSAULT_POINTS_DEFENDER_BASE, 20);
		h.values.put(VarbitID.BARBASSAULT_POINTS_DEFENDER_EXTRA, 2);
		h.varp(backing(VarbitID.BARBASSULT_ROLELEVEL_DEF));
		h.varp(backing(VarbitID.BARBASSAULT_POINTS_DEFENDER_BASE));
		h.varp(backing(VarbitID.BARBASSAULT_POINTS_DEFENDER_EXTRA));
		assertEquals(12, h.reads);
		Snapshot updated = h.read();
		assertNotSame(first, updated);
		assertEquals(Integer.valueOf(4), updated.getRoleLevels().get(Role.DEFENDER));
		assertEquals(Integer.valueOf(1044), updated.getRolePoints().get(Role.DEFENDER));
		assertEquals(24, h.reads);
		assertSame(updated, h.read());
	}

	@Test public void directVarbitChangesHandleSpendingPointsAndKeepIdenticalResultsCached()
	{
		Harness h = new Harness();
		h.values.put(VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA, 2);
		h.read();
		h.values.put(VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA, 0);
		h.varbit(VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA);
		Snapshot spent = h.read();
		assertEquals(Integer.valueOf(0), spent.getRolePoints().get(Role.HEALER));
		h.varbit(VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA);
		assertSame(spent, h.read());
	}

	@Test public void loginInitializesProgressLoadingPreservesItAndLogoutClearsTheOldAccount()
	{
		Harness h = new Harness();
		h.state = GameState.LOGIN_SCREEN;
		assertTrue(h.read().getRolePoints().isEmpty());
		assertEquals(0, h.reads);
		h.state = GameState.LOGGED_IN;
		h.values.put(VarbitID.BARBASSAULT_POINTS_ATTACKER_BASE, 100);
		Snapshot firstAccount = h.read();
		h.state = GameState.LOADING;
		h.progress.invalidate();
		assertSame(firstAccount, h.read());
		assertEquals(12, h.reads);
		h.state = GameState.LOGIN_SCREEN;
		h.progress.reset();
		assertTrue(h.read().getRolePoints().isEmpty());
		h.state = GameState.LOGGED_IN;
		h.values.clear();
		assertEquals(Integer.valueOf(0), h.read().getRolePoints().get(Role.ATTACKER));
	}

	@Test public void missingDefinitionsCanRecoverWithoutMissingServerVarpUpdates()
	{
		Harness h = new Harness();
		h.definitionsAvailable = false;
		h.read();
		h.definitionsAvailable = true;
		h.values.put(VarbitID.BARBASSULT_ROLELEVEL_HEAL, 5);
		h.varp(backing(VarbitID.BARBASSULT_ROLELEVEL_HEAL));
		assertEquals(Integer.valueOf(5), h.read().getRoleLevels().get(Role.HEALER));
	}
}
