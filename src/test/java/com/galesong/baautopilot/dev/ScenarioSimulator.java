package com.galesong.baautopilot.dev;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Local developer UI, excluded from the plugin and recorder jars. */
public final class ScenarioSimulator
{
	private ScenarioSimulator() { }

	public static void main(String[] args) throws Exception
	{
		Path selected = args.length == 0 || args[0].isBlank() ? null : Path.of(args[0]);
		List<Path> directories = new ArrayList<>();
		directories.add(Path.of("src/test/resources/recordings"));
		directories.add(Path.of(System.getProperty("user.home"), ".runelite", "ba-autopilot", "recordings"));
		if (selected != null && selected.toAbsolutePath().getParent() != null) directories.add(selected.toAbsolutePath().getParent());
		if (args.length > 2 && !args[2].isBlank()) directories.add(Path.of(args[2]));
		ScenarioCatalog catalog = new ScenarioCatalog(selected, directories);
		CollectorPreferences preferences = new CollectorPreferences(Path.of("docs/review-evidence/collector-preferences.json"));
		int port = args.length > 1 && !args[1].isBlank() ? Integer.parseInt(args[1]) : 0;
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		byte[] page;
		try (InputStream in = ScenarioSimulator.class.getResourceAsStream("/scenario-simulator.html"))
		{
			if (in == null) throw new IllegalStateException("Missing scenario UI resource");
			page = in.readAllBytes();
		}
		byte[] mapScript;
		try (InputStream in = ScenarioSimulator.class.getResourceAsStream("/scenario-map.js"))
		{
			if (in == null) throw new IllegalStateException("Missing scenario map resource");
			mapScript = in.readAllBytes();
		}
		server.createContext("/", exchange ->
		{
			try
			{
				String path = exchange.getRequestURI().getPath();
				if ("/api/captures".equals(path) && "GET".equals(exchange.getRequestMethod()))
				{
					if ("true".equals(query(exchange, "refresh"))) catalog.refresh();
					respond(exchange, 200, "application/json", ScenarioEngine.JSON.toJson(catalog.listing()).getBytes(StandardCharsets.UTF_8));
				}
				else if ("/api/meta".equals(path) && "GET".equals(exchange.getRequestMethod()))
					respond(exchange, 200, "application/json", ScenarioEngine.JSON.toJson(catalog.engine(query(exchange, "capture")).metadata()).getBytes(StandardCharsets.UTF_8));
				else if ("/api/preferences".equals(path) && "GET".equals(exchange.getRequestMethod()))
					respond(exchange, 200, "application/json", ScenarioEngine.JSON.toJson(preferences.read()).getBytes(StandardCharsets.UTF_8));
				else if (("/api/decision".equals(path) || "/api/preferences".equals(path)) && "POST".equals(exchange.getRequestMethod()))
				{
					// No CORS: only this loopback page may submit scenario inputs.
					String origin = exchange.getRequestHeaders().getFirst("Origin");
					if (origin != null && !origin.equals("http://127.0.0.1:" + server.getAddress().getPort()))
						throw new IllegalArgumentException("Origin is outside this simulator");
					byte[] body = exchange.getRequestBody().readNBytes(1_000_001);
					if (body.length > 1_000_000) throw new IllegalArgumentException("Scenario is too large");
					JsonObject input = ScenarioEngine.JSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
					ScenarioEngine engine = catalog.engine(input.has("capture") ? input.get("capture").getAsString() : null);
					JsonObject result;
					if ("/api/preferences".equals(path))
					{
						int tick = input.get("tick").getAsInt();
						if (!"COLLECTOR".equals(engine.metadata().get("capturedRole").getAsString())
							|| !engine.metadata().getAsJsonArray("ticks").contains(new com.google.gson.JsonPrimitive(tick)))
							throw new IllegalArgumentException("Choose a captured Collector tick");
						result = preferences.save(input.get("capture").getAsString(), tick, input.get("choice").getAsString());
					}
					else result = engine.evaluate(input.get("index").getAsInt(), input.get("fromIndex").getAsInt(),
						input.getAsJsonObject("world"), input.getAsJsonObject("roles"));
					respond(exchange, 200, "application/json", ScenarioEngine.JSON.toJson(result).getBytes(StandardCharsets.UTF_8));
				}
				else if ("/scenario-map.js".equals(path) && "GET".equals(exchange.getRequestMethod())) respond(exchange, 200, "text/javascript", mapScript);
				else if ("/".equals(path) && "GET".equals(exchange.getRequestMethod())) respond(exchange, 200, "text/html", page);
				else respond(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
			}
			catch (RuntimeException | java.io.IOException e)
			{
				JsonObject error = new JsonObject();
				error.addProperty("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
				respond(exchange, 400, "application/json", ScenarioEngine.JSON.toJson(error).getBytes(StandardCharsets.UTF_8));
			}
		});
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
		System.out.println("Scenario simulator: http://127.0.0.1:" + server.getAddress().getPort() + "/");
		System.out.println("Capture library with recorded-role decisions. Stop with Ctrl+C.");
	}

	private static String query(HttpExchange exchange, String key)
	{
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null) return null;
		for (String part : query.split("&"))
		{
			String[] pair = part.split("=", 2);
			if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(key))
				return pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
		}
		return null;
	}

	private static void respond(HttpExchange exchange, int status, String type, byte[] body) throws java.io.IOException
	{
		exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, body.length);
		try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
	}
}
