package com.bautopilot.baautopilot.dev;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.plan.PriorityLine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fits a small, regularised weight proposal to explicit human preferences; does not alter runtime. */
public final class CollectorWeightCalibration
{
	private static final double[] GRID = {.5, .75, 1, 1.25, 1.5, 2};
	private static final class Example
	{
		String capture, preference;
		int tick;
		boolean red;
		List<PriorityLine> choices;
	}
	public static void main(String[] args) throws Exception
	{
		Path directory = Path.of(args[0]).toAbsolutePath().normalize();
		Path labels = Path.of(args[1]), output = Path.of(args[2]);
		List<Example> examples = new ArrayList<>();
		Map<String, List<RoundReplay.Frame>> rounds = new HashMap<>();
		for (JsonElement value : new CollectorPreferences(labels).read())
		{
			JsonObject label = value.getAsJsonObject();
			String preference = label.get("choice").getAsString();
			if (!preference.equals("EGGS") && !preference.equals("CANNON")) continue;
			String capture = label.get("capture").getAsString();
			Path file = directory.resolve(capture).normalize();
			if (!directory.equals(file.getParent()) || !Files.isRegularFile(file)) continue;
			if (!rounds.containsKey(capture)) rounds.put(capture, RoundReplay.run(RecordedRound.read(file)));
			int tick = label.get("tick").getAsInt();
			RoundReplay.Frame frame = rounds.get(capture).stream().filter(f -> f.raw.t == tick).findFirst().orElse(null);
			if (frame == null) continue;
			List<PriorityLine> choices = frame.instruction.getPriorities();
			if (choices.stream().noneMatch(p -> "col.collect".equals(p.getTask()))
				|| choices.stream().noneMatch(CollectorWeightCalibration::load)) continue;
			Example example = new Example();
			example.capture = capture; example.tick = tick; example.preference = preference;
			example.red = frame.snapshot.getListenCall() == BaCall.RED_EGG; example.choices = choices;
			examples.add(example);
		}
		double bestLoss = Double.POSITIVE_INFINITY;
		double[] best = {1, 1, 1};
		for (double red : GRID) for (double escape : GRID) for (double batch : GRID)
		{
			double loss = Math.pow(red - 1, 2) + Math.pow(escape - 1, 2) + Math.pow(batch - 1, 2);
			for (Example e : examples)
			{
				double margin = margin(e, red, escape, batch) * (e.preference.equals("EGGS") ? 1 : -1);
				loss += Math.max(0, 5 - margin);
			}
			if (loss < bestLoss) { bestLoss = loss; best = new double[]{red, escape, batch}; }
		}
		JsonObject result = new JsonObject();
		result.addProperty("labelledTicks", examples.size());
		result.addProperty("redReserveMultiplier", best[0]);
		result.addProperty("escapingRunnerMultiplier", best[1]);
		result.addProperty("loadingBatchMultiplier", best[2]);
		result.addProperty("objective", bestLoss);
		result.addProperty("appliedToRuntime", false);
		com.google.gson.JsonArray comparisons = new com.google.gson.JsonArray();
		for (Example e : examples)
		{
			JsonObject comparison = new JsonObject();
			comparison.addProperty("capture", e.capture); comparison.addProperty("tick", e.tick);
			comparison.addProperty("preference", e.preference);
			comparison.addProperty("currentEggMinusLoad", margin(e, 1, 1, 1));
			comparison.addProperty("proposedEggMinusLoad", margin(e, best[0], best[1], best[2]));
			comparisons.add(comparison);
		}
		result.add("comparisons", comparisons);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, ScenarioEngine.JSON.toJson(result));
		System.out.println("Calibration: " + examples.size() + " labelled ticks; proposal " + output);
		System.out.println("Multipliers red/escape/batch: " + best[0] + "/" + best[1] + "/" + best[2]);
		System.out.println("Fit uses labelled captured states only; no held-out outcome validation and no automatic runtime changes.");
	}

	private static boolean load(PriorityLine p) { return "col.load".equals(p.getTask()) || "col.support".equals(p.getTask()); }
	private static double margin(Example e, double red, double escape, double batch)
	{
		double eggs = -Double.MAX_VALUE, cannon = -Double.MAX_VALUE;
		for (PriorityLine p : e.choices)
		{
			if (p.getScore() == null) continue;
			double score = 0;
			for (Map.Entry<String, Double> term : p.getScoreTerms().entrySet())
			{
				double factor = term.getKey().equals("escaping runners") ? escape
					: term.getKey().equals("batch") || term.getKey().equals("blue window") ? batch
					: e.red && (term.getKey().equals("next egg / reserve") || term.getKey().equals("expiry")
						|| term.getKey().equals("collection opportunity")) ? red : 1;
				score += term.getValue() * factor;
			}
			if ("col.collect".equals(p.getTask())) eggs = Math.max(eggs, score);
			if (load(p)) cannon = Math.max(cannon, score);
		}
		return eggs - cannon;
	}
}
