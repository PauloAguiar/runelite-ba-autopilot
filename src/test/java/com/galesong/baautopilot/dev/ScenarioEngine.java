package com.galesong.baautopilot.dev;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Planner;
import com.galesong.baautopilot.plan.Settings;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.plan.TaskMemory;
import com.galesong.baautopilot.state.Snapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replays decisions against fixed observations; alternate actions never mutate the recorded world. */
public final class ScenarioEngine
{
	static final Gson JSON = new GsonBuilder().enableComplexMapKeySerialization().serializeNulls().create();
	private static final Set<String> WORLD = Set.of("penance", "unseenPenance", "uncertainPenance", "groundItems", "objects",
		"eggsRed", "eggsGreen", "eggsBlue", "eggsOmega", "trapCharges", "queenAlive", "cleared",
		"goodBaitLanded", "badBaitLanded", "launcherShotsPerTick", "lastLauncherShotTick", "teamDps",
		"fighterRangerKills", "killsByType", "runnersKilled", "uncertainRunners", "healersKilled", "playerTiles", "queenOmegaFired", "lastQueenOmegaShotTick", "queenDead", "queenOmegaHandoffTick");
	private static final Set<String> PRIVATE = Set.of("playerTile", "inventory", "freeSlots", "ammoId",
		"hasHammer", "hp", "maxHp", "listenCall", "listenFresh", "shoutCall", "shoutDone", "shoutWrong",
		"shoutCoveredBy", "shoutLost", "shoutSettled", "teammates", "bagRed", "bagGreen", "bagBlue",
		"roleLevels", "rolePoints", "logsTaken", "myDps", "myDamage", "interactingNpc", "selfDamagePerTick", "teammateDamagePerTick", "queenYellowPassed", "queenSpikedPassed", "attackerOpeningComplete");
	private final RecordedRound round;
	private final Settings settings;
	private final List<RoundReplay.Frame> frames;
	private final Role capturedRole;

	public ScenarioEngine(RecordedRound round)
	{
		this.round = round;
		settings = round.header.settings == null ? Settings.builder().build() : round.header.settings;
		frames = new ArrayList<>();
		for (RoundReplay.Frame frame : RoundReplay.run(round))
		{
			Snapshot s = frame.snapshot;
			if (!s.isInArena() || s.getRole() == null) continue;
			frames.add(frame);
		}
		if (frames.isEmpty()) throw new IllegalArgumentException("Capture has no arena decisions");
		capturedRole = frames.get(0).snapshot.getRole();
	}

	public JsonObject metadata()
	{
		JsonObject out = new JsonObject();
		out.addProperty("recording", round.name());
		out.addProperty("capturedRole", capturedRole.name());
		out.addProperty("wave", frames.get(0).snapshot.getWave());
		out.addProperty("capturedLevel", frames.get(0).snapshot.getRoleLevels().getOrDefault(capturedRole, 0));
		out.add("ticks", JSON.toJsonTree(frames.stream().map(f -> f.snapshot.getTick()).toArray()));
		out.add("settings", JSON.toJsonTree(settings));
		Map<Role, List<BaCall>> calls = new EnumMap<>(Role.class);
		Map<Role, JsonObject> stocked = new EnumMap<>(Role.class);
		for (Role role : Role.values())
		{
			List<BaCall> options = new ArrayList<>();
			for (BaCall call : BaCall.values()) if (call.getReceiver() == role) options.add(call);
			calls.put(role, options);
		}
		stocked.put(capturedRole, stocked(capturedRole));
		out.add("calls", JSON.toJsonTree(calls));
		out.add("stocked", JSON.toJsonTree(stocked));
		out.add("worldFields", JSON.toJsonTree(WORLD.stream().sorted().toArray()));
		out.add("roleFields", JSON.toJsonTree(PRIVATE.stream().sorted().toArray()));
		JsonArray review = new JsonArray();
		int lastReview = Integer.MIN_VALUE / 2;
		if (capturedRole == Role.COLLECTOR) for (int i = 0; i < frames.size(); i++)
		{
			RoundReplay.Frame frame = frames.get(i);
			com.galesong.baautopilot.plan.PriorityLine egg = frame.instruction.getPriorities().stream()
				.filter(p -> "col.collect".equals(p.getTask())).findFirst().orElse(null);
			com.galesong.baautopilot.plan.PriorityLine load = frame.instruction.getPriorities().stream()
				.filter(p -> "col.load".equals(p.getTask()) || "col.support".equals(p.getTask())).findFirst().orElse(null);
			if (egg == null || load == null || egg.getScore() == null || load.getScore() == null
				|| Math.abs(egg.getScore() - load.getScore()) > 15 || frame.snapshot.getTick() - lastReview < 10) continue;
			JsonObject choice = new JsonObject();
			choice.addProperty("index", i);
			choice.addProperty("tick", frame.snapshot.getTick());
			choice.addProperty("eggScore", egg.getScore());
			choice.addProperty("loadScore", load.getScore());
			review.add(choice);
			lastReview = frame.snapshot.getTick();
		}
		out.add("reviewChoices", review);
		return out;
	}

	/** Only the captured role has a decision. Rebuild its history on every request, including rewinds. */
	public JsonObject evaluate(int index, int fromIndex, JsonObject world, JsonObject roles)
	{
		if (index < 0 || index >= frames.size() || fromIndex < 0 || fromIndex >= frames.size())
			throw new IllegalArgumentException("Frame index is outside this capture");
		validate(world, WORLD);
		for (Map.Entry<String, JsonElement> entry : roles.entrySet())
		{
			if (!capturedRole.name().equals(entry.getKey()))
				throw new IllegalArgumentException("This capture contains " + capturedRole.name()
					+ " inputs; select a " + entry.getKey() + " capture to inspect that role");
			validate(entry.getValue().getAsJsonObject(), PRIVATE);
		}
		Planner planner = new Planner();
		Instruction previous = null;
		int lastNpc = -1;
		JsonObject cards = new JsonObject();
		for (int i = 0; i <= index; i++)
		{
			Snapshot input = sourceSnapshot(i);
			Role role = input.getRole();
			if (previous != null) input = input.toBuilder().lastTask(previous.getTask())
				.lastStep(previous.getStep()).lastActionTarget(previous.getTarget())
				.lastTargetTile(previous.getTarget().getTile())
				.lastTargetNpc(lastNpc)
				.tasks(previous.getTasks()).build();
			JsonObject rolePatch = roles.has(role.name()) ? roles.getAsJsonObject(role.name()) : new JsonObject();
			if (i >= fromIndex) input = patch(input, world, rolePatch);
			validateCalls(input);
			Instruction decision = planner.plan(input, settings);
			previous = decision;
			if (decision.getTarget().getKind() == Target.Kind.NPC) lastNpc = decision.getTarget().getId();
			if (i == index)
			{
				JsonObject card = new JsonObject();
				card.add("input", JSON.toJsonTree(input));
				card.add("decision", JSON.toJsonTree(decision));
				card.add("npcDebug", JSON.toJsonTree(com.galesong.baautopilot.state.PenanceDiagnostics.labels(input)));
				card.add("mapItems", mapItems(input));
				card.add("mapObjects", mapObjects(input));
				card.addProperty("captured", true);
				card.addProperty("positionSource", "Captured player");
				// Avoid asking Gson to reflect into JDK-private collection implementations on rewinds.
				JsonArray overrides = new JsonArray();
				if (i >= fromIndex) rolePatch.keySet().forEach(overrides::add);
				card.add("overrides", overrides);
				cards.add(role.name(), card);
			}
		}
		JsonObject result = new JsonObject();
		result.addProperty("index", index);
		result.addProperty("tick", frames.get(index).snapshot.getTick());
		result.addProperty("wave", frames.get(index).snapshot.getWave());
		result.add("roles", cards);
		result.add("recordedLive", JSON.toJsonTree(frames.get(index).raw.live));
		JsonArray players = new JsonArray();
		RoundReplay.Frame current = frames.get(index);
		if (current.raw.players != null) for (Rec.Plr player : current.raw.players)
		{
			if (player.tile == null || "Me".equals(player.n)) continue;
			JsonObject observed = new JsonObject();
			observed.addProperty("name", player.n);
			Role knownRole = player.role != null ? player.role : current.snapshot.getTeam().get(player.n);
			observed.addProperty("role", knownRole == null ? null : knownRole.name());
			observed.add("tile", JSON.toJsonTree(RoundReplay.point(player.tile)));
			players.add(observed);
		}
		result.add("observedPlayers", players);
		return result;
	}

	Snapshot sourceSnapshot(int index)
	{
		return frames.get(index).snapshot.toBuilder().lastTask(null).lastStep(null).lastActionTarget(null).lastTargetTile(null).lastTargetNpc(-1)
			.tasks(TaskMemory.empty()).build();
	}

	private static JsonArray mapItems(Snapshot s)
	{
		JsonArray out = new JsonArray();
		for (com.galesong.baautopilot.state.GroundItemView item : s.getGroundItems())
		{
			JsonObject entry = new JsonObject();
			entry.add("tile", JSON.toJsonTree(item.getTile()));
			entry.addProperty("quantity", item.getQuantity());
			entry.addProperty("id", item.getItemId());
			BaCall call = BaCall.forItem(Role.DEFENDER, item.getItemId());
			if (call == null) call = BaCall.forItem(Role.COLLECTOR, item.getItemId());
			if (call == null) call = BaCall.forItem(Role.HEALER, item.getItemId());
			boolean queenEgg = item.getItemId() == BaIds.EGG_YELLOW || item.getItemId() == BaIds.EGG_POISONED
				|| item.getItemId() == BaIds.EGG_SPIKED || item.getItemId() == BaIds.EGG_OMEGA;
			String category = BaIds.BAIT.contains(item.getItemId()) || BaIds.POISON_FOOD.contains(item.getItemId()) ? "bait"
				: queenEgg || call != null ? "egg"
				: item.getItemId() == BaIds.LOGS ? "logs" : item.getItemId() == BaIds.HAMMER ? "hammer" : "item";
			entry.addProperty("category", category);
			String name = call != null ? call.getItemName() : item.getItemId() == BaIds.EGG_YELLOW ? "Yellow egg"
				: item.getItemId() == BaIds.EGG_POISONED ? "Poisoned egg" : item.getItemId() == BaIds.EGG_SPIKED ? "Spiked egg"
				: item.getItemId() == BaIds.EGG_OMEGA ? "Omega egg" : category.equals("logs") ? "Logs"
				: category.equals("hammer") ? "Hammer" : "Item " + item.getItemId();
			entry.addProperty("name", name);
			entry.addProperty("variant", call == null ? queenEgg ? "yellow" : "" : call.getKeyword().toLowerCase(java.util.Locale.ROOT));
			entry.addProperty("baitStatus", !BaIds.BAIT.contains(item.getItemId()) || s.getRole() != Role.DEFENDER ? "unknown" : item.isGood() ? "good" : "bad");
			out.add(entry);
		}
		return out;
	}

	private static JsonArray mapObjects(Snapshot s)
	{
		JsonArray out = new JsonArray();
		for (com.galesong.baautopilot.state.ObjectView object : s.getObjects())
		{
			int id = object.getId();
			String name = id == BaIds.TRAP_FULL ? "Trap: 2 charges" : id == BaIds.TRAP_ONE ? "Trap: 1 charge"
				: id == BaIds.TRAP_BROKEN ? "Trap: broken" : id == BaIds.HEALER_MACHINE ? "Healer food machine"
				: id == BaIds.ATTACKER_MACHINE ? "Attacker arrow machine" : id == BaIds.DEFENDER_MACHINE ? "Defender bait machine"
				: id == BaIds.COLLECTOR_CONVERTER ? "Egg converter" : id == BaIds.SPRING ? "Healer spring"
				: BaIds.contains(BaIds.HOPPERS, id) ? "Egg hopper" : BaIds.contains(BaIds.LAUNCHERS, id) ? "Egg cannon"
				: BaIds.contains(BaIds.MUSHROOMS, id) ? "Spike mushroom" : BaIds.contains(BaIds.POISON_CRATERS, id) ? "Poison crater"
				: BaIds.contains(BaIds.LAVA_CRATERS, id) ? "Lava crater" : "Object " + id;
			JsonObject entry = new JsonObject();
			entry.add("tile", JSON.toJsonTree(object.getTile()));
			entry.addProperty("id", id);
			entry.addProperty("name", name);
			out.add(entry);
		}
		return out;
	}

	private static Snapshot patch(Snapshot source, JsonObject world, JsonObject role)
	{
		if (world.size() == 0 && role.size() == 0) return source;
		JsonObject json = JSON.toJsonTree(source).getAsJsonObject();
		world.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
		role.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
		return JSON.fromJson(json, Snapshot.class);
	}

	private static void validate(JsonObject patch, Set<String> fields)
	{
		for (Map.Entry<String, JsonElement> e : patch.entrySet())
		{
			if (!fields.contains(e.getKey())) throw new IllegalArgumentException("Unsupported override: " + e.getKey());
			if (Set.of("listenCall", "shoutCall", "shoutWrong").contains(e.getKey()) && !e.getValue().isJsonNull())
				BaCall.valueOf(e.getValue().getAsString());
			if (e.getValue().isJsonNull() && !Set.of("listenCall", "shoutCall", "shoutWrong", "shoutCoveredBy").contains(e.getKey()))
				throw new IllegalArgumentException(e.getKey() + " cannot be null");
		}
	}

	private static void validateCalls(Snapshot s)
	{
		if (s.getListenCall() != null && s.getListenCall().getReceiver() != s.getRole())
			throw new IllegalArgumentException("Incoming call must belong to " + s.getRole());
		if (s.getShoutCall() != null && s.getShoutCall().getReceiver() != s.getRole().partner())
			throw new IllegalArgumentException("Outgoing call must belong to " + s.getRole().partner());
		if (s.getFreeSlots() < 0 || s.getFreeSlots() > 28 || s.getMaxHp() <= 0 || s.getHp() < 0 || s.getHp() > s.getMaxHp())
			throw new IllegalArgumentException("Invalid inventory capacity or hit points");
	}

	private static JsonObject stocked(Role role)
	{
		Map<Integer, Integer> inventory = new LinkedHashMap<>();
		if (role == Role.ATTACKER) BaIds.ARROWS.forEach(id -> inventory.put(id, 100));
		if (role == Role.DEFENDER)
		{
			BaIds.BAIT.forEach(id -> inventory.put(id, 6));
			inventory.put(BaIds.LOGS, 2);
			inventory.put(BaIds.HAMMER, 1);
		}
		if (role == Role.HEALER)
		{
			BaIds.POISON_FOOD.forEach(id -> inventory.put(id, 8));
			inventory.put(BaIds.VIAL_FULL, 1);
		}
		if (role == Role.COLLECTOR)
		{
			inventory.put(BaIds.EGG_RED, 15);
			inventory.put(BaIds.EGG_GREEN, 5);
		}
		JsonObject patch = new JsonObject();
		patch.add("inventory", JSON.toJsonTree(inventory));
		patch.addProperty("freeSlots", role == Role.ATTACKER ? 22 : role == Role.DEFENDER ? 5 : role == Role.HEALER ? 1 : 6);
		patch.addProperty("hasHammer", role == Role.DEFENDER);
		return patch;
	}
}
