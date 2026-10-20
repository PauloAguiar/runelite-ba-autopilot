package com.galesong.baautopilot.state;

import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.PenanceType;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.model.WaveInfo;
import com.galesong.baautopilot.plan.RunnerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;

/** Observations and conditional forecasts. Healer finishing also uses the bounded HP estimate. */
public final class PenanceDiagnostics
{
	public static final int ZONE_SIZE = 8;
	public enum PoisonResult { UNKNOWN, NO_POISON, KILLS, SURVIVES, UNCERTAIN, DYING }

	@Value
	public static class Label
	{
		int npcIndex;
		WorldPoint tile;
		String title;
		String detail;
		String tone;
	}

	@Value
	public static class HealerForecast
	{
		int maxHp;
		int hpMin;
		int hpMax;
		int poison;
		int damageMin;
		int damageMax;
		int earliestDeathTicks;
		int latestDeathTicks;
		PoisonResult result;
	}

	private PenanceDiagnostics() { }

	/** Fixed world zones, not a square centred on the runner. Zero-based rank: NE, E, SE, N... */
	public static int zoneRank(WorldPoint runner, WorldPoint bait)
	{
		if (runner == null || bait == null || runner.getPlane() != bait.getPlane()) return -1;
		int dx = Math.floorDiv(bait.getX(), ZONE_SIZE) - Math.floorDiv(runner.getX(), ZONE_SIZE);
		int dy = Math.floorDiv(bait.getY(), ZONE_SIZE) - Math.floorDiv(runner.getY(), ZONE_SIZE);
		return Math.abs(dx) > 1 || Math.abs(dy) > 1 ? -1 : (1 - dx) * 3 + 1 - dy;
	}

	/** Candidate only: captured clients do not expose the runner's selected food or its sight test. */
	public static GroundItemView candidateBait(Snapshot s, PenanceView runner)
	{
		if (s.getRole() != Role.DEFENDER || runner.getTile() == null) return null;
		boolean trigger = s.getGroundItems().stream().anyMatch(b -> bait(b) && distance(runner.getTile(), b.getTile()) <= s.baitRange());
		if (!trigger) return null;
		return s.getGroundItems().stream().filter(PenanceDiagnostics::bait)
			.filter(b -> zoneRank(runner.getTile(), b.getTile()) >= 0)
			.min(Comparator.comparingInt((GroundItemView b) -> zoneRank(runner.getTile(), b.getTile()))
				.thenComparing(Comparator.comparingInt(GroundItemView::getSpawnTick).reversed()))
			.orElse(null);
	}

	private static boolean bait(GroundItemView b) { return b.getTile() != null && b.getQuantity() > 0 && BaIds.BAIT.contains(b.getItemId()); }
	private static int distance(WorldPoint a, WorldPoint b) { return a.getPlane() == b.getPlane() ? Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) : Integer.MAX_VALUE; }

	public static Label runner(Snapshot s, PenanceView p)
	{
		GroundItemView bait = candidateBait(s, p);
		String motion = direction(p.getPreviousTile(), p.getTile());
		String speech = p.getOverhead() == null ? "" : p.getOverhead().toLowerCase(Locale.ROOT);
		String state;
		if (p.isDying()) state = "DYING";
		else if (p.getAnimation() == AnimationID.BARBASSAULT_PENANCE_RUNNER_EATING)
		{
			state = speech.contains("chomp") ? "EAT GOOD" : speech.contains("blug") ? "EAT BAD" : "EAT";
		}
		else if (p.getPreviousTile() == null) state = "STATE?";
		else if (speech.contains("blug") && !motion.isEmpty()) state = "POST-BAD? " + motion;
		else if (bait != null) state = "HUNT?" + (motion.isEmpty() ? "" : " " + motion);
		else state = motion.isEmpty() ? "IDLE" : "WALK? " + motion;
		String detail = s.getRole() == Role.DEFENDER ? RunnerState.of(s, p).name() + "?" : "bait context?";
		if (bait != null) detail += " " + (bait.isGood() ? "G" : "B") + "@" + (bait.getTile().getX() & 63) + "," + (bait.getTile().getY() & 63);
		String tone = s.getRole() == Role.DEFENDER && RunnerState.of(s, p) == RunnerState.ESCAPING ? "bad" : "muted";
		return new Label(p.getNpcIndex(), p.getTile(), "R " + state, detail, tone);
	}

	private static String direction(WorldPoint previous, WorldPoint current)
	{
		if (previous == null || current == null || previous.getPlane() != current.getPlane()) return "";
		int dx = Integer.signum(current.getX() - previous.getX()), dy = Integer.signum(current.getY() - previous.getY());
		return (dy > 0 ? "N" : dy < 0 ? "S" : "") + (dx > 0 ? "E" : dx < 0 ? "W" : "");
	}

	/**
	 * Bounds use wave HP and the quantised health bar. A known own food supplies a time anchor;
	 * contradictory/partner damage supplies a range of positions within that poison strength.
	 * Forecast assumes no more food, cannon damage, healing, or stun, and no missed poison hits.
	 */
	public static HealerForecast healer(Snapshot s, PenanceView p)
	{
		int max = WaveInfo.healerHp(s.getWave());
		int hpMin = -1, hpMax = -1;
		if (max > 0 && p.getHealthScale() > 0 && p.getHealthRatio() >= 0)
		{
			int ratio = Math.min(p.getHealthRatio(), p.getHealthScale());
			// Invert the server's 1 + (scale - 1) * hp / max for nonzero HP,
			// as RuneLite's OpponentInfoOverlay does, retaining the quantisation interval.
			int scale = p.getHealthScale();
			hpMin = ratio == 0 ? 0 : ratio == 1 || scale <= 1 ? 1 : (max * (ratio - 1) + scale - 2) / (scale - 1);
			hpMax = ratio == 0 ? 0 : scale <= 1 ? max : Math.min(max, (max * ratio - 1) / (scale - 1));
		}
		else if (max > 0 && p.getDamageTaken() > 0)
		{
			hpMin = hpMax = Math.max(1, max - p.getDamageTaken());
		}
		if (p.isDying()) return new HealerForecast(max, 0, 0, 0, 0, 0, 0, 0, PoisonResult.DYING);
		int strength = p.nextPoisonHit(s.getTick());
		if (max <= 0 || hpMin < 0) return new HealerForecast(max, hpMin, hpMax, strength, 0, 0, -1, -1, PoisonResult.UNKNOWN);
		if (strength == 0) return new HealerForecast(max, hpMin, hpMax, 0, 0, 0, -1, -1, PoisonResult.NO_POISON);
		int low, high;
		int food = p.getLastFoodTick(), observed = p.getLastPoisonTick(), damage = p.getLastPoisonDamage();
		boolean anchored = food >= 0 && (observed <= food
			|| damage == WaveInfo.poisonHit(Math.max(0, (observed - food) / WaveInfo.POISON_INTERVAL_TICKS) + 1));
		// A one-damage hit without food history cannot identify the poison's source/duration.
		if (!anchored && damage == 1) return new HealerForecast(max, hpMin, hpMax, strength, 0, 0, -1, -1, PoisonResult.UNKNOWN);
		if (anchored)
		{
			int age = Math.max(0, s.getTick() - food);
			low = age / WaveInfo.POISON_INTERVAL_TICKS;
			high = (age + WaveInfo.POISON_INTERVAL_TICKS - 1) / WaveInfo.POISON_INTERVAL_TICKS;
		}
		else if (observed >= 0 && damage >= 1 && damage <= 4)
		{
			// A current hit may be any of the five at that strength. A 4 may be immediate food.
			int elapsed = Math.max(0, s.getTick() - observed) / WaveInfo.POISON_INTERVAL_TICKS;
			low = (4 - damage) * 5 + (damage == 4 ? 0 : 1) + elapsed;
			high = (5 - damage) * 5 + elapsed;
		}
		else return new HealerForecast(max, hpMin, hpMax, strength, 0, 0, -1, -1, PoisonResult.UNKNOWN);
		low = Math.min(20, low);
		high = Math.min(20, high);
		int min = remaining(high), most = remaining(low);
		int earliest = ticksToKill(low, hpMin, 1), latest = ticksToKill(high, hpMax, WaveInfo.POISON_INTERVAL_TICKS);
		PoisonResult result = min >= hpMax ? PoisonResult.KILLS : most < hpMin ? PoisonResult.SURVIVES : PoisonResult.UNCERTAIN;
		return new HealerForecast(max, hpMin, hpMax, strength, min, most, earliest, latest, result);
	}

	private static int remaining(int done)
	{
		int total = 0;
		for (int n = done + 1; n <= 20; n++) total += WaveInfo.poisonHit(n);
		return total;
	}

	private static int ticksToKill(int done, int hp, int delay)
	{
		if (hp <= 0) return 0;
		for (int n = done + 1; n <= 20; n++)
		{
			hp -= WaveInfo.poisonHit(n);
			if (hp <= 0) return delay + (n - done - 1) * WaveInfo.POISON_INTERVAL_TICKS;
		}
		return -1;
	}

	public static List<Label> labels(Snapshot s)
	{
		List<Label> labels = new ArrayList<>();
		for (PenanceView p : s.getPenance())
		{
			if (p.getTile() == null || p.isUnseen()) continue;
			if (p.getType() == PenanceType.RUNNER) labels.add(runner(s, p));
			if (p.getType() == PenanceType.HEALER)
			{
				HealerForecast f = healer(s, p);
				String hp = f.hpMin < 0 ? "?" : "~" + (f.hpMin + f.hpMax + 1) / 2;
				String detail;
				switch (f.result)
				{
					case DYING: detail = "DYING"; break;
					case NO_POISON: detail = "no active poison"; break;
					case KILLS: detail = "dies ~" + f.earliestDeathTicks + "-" + f.latestDeathTicks + "t"; break;
					case SURVIVES: detail = "survives: short >=" + (f.hpMin - f.damageMax) + " HP"; break;
					case UNCERTAIN: detail = "kill?" + (f.earliestDeathTicks >= 0 ? " earliest ~" + f.earliestDeathTicks + "t" : ""); break;
					default: detail = "poison outcome unknown";
				}
				labels.add(new Label(p.getNpcIndex(), p.getTile(), "H" + p.getOrder() + " HP" + hp + "/" + f.maxHp + " P" + f.poison,
					detail, f.result == PoisonResult.KILLS ? "good" : f.result == PoisonResult.SURVIVES || f.result == PoisonResult.NO_POISON ? "bad" : "muted"));
			}
		}
		return labels;
	}
}
