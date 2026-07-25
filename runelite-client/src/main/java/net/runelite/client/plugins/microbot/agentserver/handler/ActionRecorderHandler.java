package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.actionrecorder.ActionRecorderPlugin;

/** Operator session controls for the structured Action Recorder plugin. */
public class ActionRecorderHandler extends AgentHandler
{
	private static final String BASE_PATH = "/action-recorder";

	public ActionRecorderHandler(Gson gson)
	{
		super(gson);
	}

	@Override
	public String getPath()
	{
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException
	{
		String subPath = getSubPath(exchange, BASE_PATH);
		switch (subPath)
		{
			case "":
			case "/":
			case "/status":
				handleStatus(exchange);
				break;
			case "/start":
				handleStart(exchange);
				break;
			case "/marker":
				handleMarker(exchange);
				break;
			case "/stop":
				handleStop(exchange);
				break;
			case "/sessions":
				handleSessions(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + subPath));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET"))
		{
			return;
		}
		ActionRecorderPlugin plugin = findPlugin();
		if (plugin == null)
		{
			sendJson(exchange, 404, errorResponse("Action Recorder plugin not found"));
			return;
		}
		sendJson(exchange, 200, plugin.getRecorderStatus());
	}

	private void handleStart(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "POST"))
		{
			return;
		}
		ActionRecorderPlugin plugin = requireActivePlugin(exchange);
		if (plugin == null)
		{
			return;
		}
		Map<String, Object> body = readOptionalJsonBody(exchange);
		String name = stringValue(body.get("name"));
		String notes = stringValue(body.get("notes"));
		ActionRecorderPlugin.RecorderStatus status = plugin.startSession(name, notes);
		sendJson(exchange, status.isRecording() ? 200 : 409, status);
	}

	private void handleMarker(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "POST"))
		{
			return;
		}
		ActionRecorderPlugin plugin = requireActivePlugin(exchange);
		if (plugin == null)
		{
			return;
		}
		Map<String, Object> body = readOptionalJsonBody(exchange);
		String label = stringValue(body.get("label"));
		if (label == null || label.trim().isEmpty())
		{
			sendJson(exchange, 400, errorResponse("label is required"));
			return;
		}
		boolean accepted = plugin.addMarker(label, stringValue(body.get("notes")));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("accepted", accepted);
		result.put("status", plugin.getRecorderStatus());
		sendJson(exchange, accepted ? 200 : 409, result);
	}

	private void handleStop(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "POST"))
		{
			return;
		}
		ActionRecorderPlugin plugin = requireActivePlugin(exchange);
		if (plugin == null)
		{
			return;
		}
		ActionRecorderPlugin.RecorderStatus before = plugin.getRecorderStatus();
		if (!before.isRecording() && !before.isStopping())
		{
			sendJson(exchange, 409, errorResponse("No Action Recorder session is active"));
			return;
		}
		Map<String, Object> body = readOptionalJsonBody(exchange);
		String reason = stringValue(body.get("reason"));
		sendJson(exchange, 202, plugin.requestStop(reason));
	}

	private void handleSessions(HttpExchange exchange) throws IOException
	{
		if (!requireMethod(exchange, "GET"))
		{
			return;
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("sessions", ActionRecorderPlugin.listSessionDirectories());
		sendJson(exchange, 200, result);
	}

	private ActionRecorderPlugin requireActivePlugin(HttpExchange exchange) throws IOException
	{
		ActionRecorderPlugin plugin = findPlugin();
		if (plugin == null)
		{
			sendJson(exchange, 404, errorResponse("Action Recorder plugin not found"));
			return null;
		}
		if (!plugin.getRecorderStatus().isPluginActive())
		{
			sendJson(exchange, 409, errorResponse("Enable the Action Recorder plugin before controlling a session"));
			return null;
		}
		return plugin;
	}

	private static ActionRecorderPlugin findPlugin()
	{
		return Microbot.getPlugin(ActionRecorderPlugin.class);
	}

	private boolean requireMethod(HttpExchange exchange, String method) throws IOException
	{
		if (method.equalsIgnoreCase(exchange.getRequestMethod()))
		{
			return true;
		}
		sendJson(exchange, 405, errorResponse("Method not allowed; expected " + method));
		return false;
	}

	private static String stringValue(Object value)
	{
		return value instanceof String ? (String) value : null;
	}

	private Map<String, Object> readOptionalJsonBody(HttpExchange exchange) throws IOException
	{
		Map<String, Object> body = readJsonBody(exchange);
		return body == null ? Collections.emptyMap() : body;
	}
}
