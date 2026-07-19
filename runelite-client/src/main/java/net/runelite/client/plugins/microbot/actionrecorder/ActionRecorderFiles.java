package net.runelite.client.plugins.microbot.actionrecorder;

import java.text.Normalizer;
import java.util.Locale;

final class ActionRecorderFiles
{
	private ActionRecorderFiles()
	{
	}

	static String safeName(String value)
	{
		String normalized = Normalizer.normalize(value == null ? "session" : value, Normalizer.Form.NFKD)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-+|-+$)", "");
		if (normalized.isEmpty())
		{
			return "session";
		}
		return normalized.length() <= 48 ? normalized : normalized.substring(0, 48).replaceAll("-+$", "");
	}
}
