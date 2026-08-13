package net.runelite.client.plugins.gpu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import net.runelite.client.RuneLite;

final class GpuFboDiagnostics
{
	private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;
	private static final Path LOG_PATH = RuneLite.LOGS_DIR.toPath().resolve("gpu-fbo-debug.log");
	private static final Path BACKUP_PATH = RuneLite.LOGS_DIR.toPath().resolve("gpu-fbo-debug.log.1");

	private GpuFboDiagnostics()
	{
	}

	static void write(String format, Object... args)
	{
		try
		{
			Files.createDirectories(LOG_PATH.getParent());
			rotateIfNeeded();
			String line = String.format(Locale.ROOT, format, args);
			Files.writeString(LOG_PATH,
				String.format(Locale.ROOT, "%s [%s] %s%n", Instant.now(), Thread.currentThread().getName(), line),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		}
		catch (IOException ignored)
		{
			// Diagnostics must never disrupt rendering or add noise to the normal client log.
		}
	}

	private static void rotateIfNeeded() throws IOException
	{
		if (Files.exists(LOG_PATH) && Files.size(LOG_PATH) >= MAX_LOG_BYTES)
		{
			Files.move(LOG_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
