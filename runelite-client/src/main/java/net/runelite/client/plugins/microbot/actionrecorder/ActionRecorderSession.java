package net.runelite.client.plugins.microbot.actionrecorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionPayloads;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecord;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecordType;
import net.runelite.client.plugins.microbot.actionrecorder.model.CaptureSettingsSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.LocationSnapshot;

@Slf4j
final class ActionRecorderSession
{
	private static final int SCHEMA_VERSION = 4;
	private static final int MAX_PENDING_RECORDS = 8192;
	private static final DateTimeFormatter DIRECTORY_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson JSONL_GSON = new Gson();

	private final String sessionId = UUID.randomUUID().toString();
	private final String name;
	private final String notes;
	private final CaptureSettingsSnapshot captureSettings;
	private final long startedAtEpochMs = System.currentTimeMillis();
	private final Path outputDirectory;
	private final Path eventsPath;
	private final ArrayBlockingQueue<ActionRecord> queue = new ArrayBlockingQueue<>(MAX_PENDING_RECORDS);
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicLong acceptedCount = new AtomicLong();
	private final AtomicLong droppedCount = new AtomicLong();
	private final Map<ActionRecordType, Long> eventCounts = Collections.synchronizedMap(new EnumMap<>(ActionRecordType.class));
	private final List<String> markers = Collections.synchronizedList(new ArrayList<>());
	private final Thread writerThread;

	private volatile boolean accepting = true;
	private volatile boolean stopped;
	private volatile boolean failed;
	private volatile String stopReason = "operator_stop";
	private volatile long stoppedAtEpochMs;

	ActionRecorderSession(Path recordingsRoot, String name, String notes, CaptureSettingsSnapshot captureSettings) throws IOException
	{
		this.name = name;
		this.notes = notes;
		this.captureSettings = captureSettings;
		String directoryName = DIRECTORY_TIME.format(Instant.ofEpochMilli(startedAtEpochMs).atZone(ZoneId.systemDefault()))
			+ "-" + ActionRecorderFiles.safeName(name) + "-" + sessionId.substring(0, 8);
		this.outputDirectory = recordingsRoot.resolve(directoryName);
		this.eventsPath = outputDirectory.resolve("events.jsonl");
		Files.createDirectories(outputDirectory);

		writerThread = new Thread(this::writeLoop, "ActionRecorder-Writer");
		writerThread.setDaemon(true);
		writerThread.start();

		offer(ActionRecordType.SESSION_START, -1, null,
			new ActionPayloads.SessionStart(name, notes, startedAtEpochMs,
				outputDirectory.getFileName().toString(), captureSettings));
	}

	synchronized boolean offer(ActionRecordType type, int gameTick, LocationSnapshot location, Object payload)
	{
		if (!accepting)
		{
			return false;
		}
		if (queue.remainingCapacity() == 0)
		{
			droppedCount.incrementAndGet();
			return false;
		}

		long now = System.currentTimeMillis();
		ActionRecord record = new ActionRecord(
			SCHEMA_VERSION,
			sessionId,
			sequence.incrementAndGet(),
			type,
			now,
			now - startedAtEpochMs,
			gameTick,
			location,
			payload);
		if (!queue.offer(record))
		{
			sequence.decrementAndGet();
			droppedCount.incrementAndGet();
			return false;
		}

		acceptedCount.incrementAndGet();
		eventCounts.merge(type, 1L, Long::sum);
		if (type == ActionRecordType.OPERATOR_MARKER && payload instanceof ActionPayloads.OperatorMarker)
		{
			markers.add(((ActionPayloads.OperatorMarker) payload).getLabel());
		}
		return true;
	}

	synchronized void requestStop(String reason)
	{
		if (!accepting)
		{
			return;
		}
		stopReason = reason == null || reason.trim().isEmpty() ? "operator_stop" : reason.trim();
		accepting = false;
	}

	boolean awaitStopped(long timeoutMs)
	{
		try
		{
			writerThread.join(timeoutMs);
			return stopped;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void writeLoop()
	{
		try (BufferedWriter writer = Files.newBufferedWriter(eventsPath, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
		{
			int recordsSinceFlush = 0;
			while (accepting || !queue.isEmpty())
			{
				ActionRecord record = queue.poll(250, TimeUnit.MILLISECONDS);
				if (record == null)
				{
					continue;
				}
				writer.write(JSONL_GSON.toJson(record));
				writer.newLine();
				recordsSinceFlush++;
				if (recordsSinceFlush >= captureSettings.getFlushEveryRecords()
					|| record.getType() == ActionRecordType.SESSION_START
					|| record.getType() == ActionRecordType.OPERATOR_MARKER)
				{
					writer.flush();
					recordsSinceFlush = 0;
				}
			}

			stoppedAtEpochMs = System.currentTimeMillis();
			long writtenRecordCount = acceptedCount.get() + 1;
			ActionRecord endRecord = new ActionRecord(
				SCHEMA_VERSION,
				sessionId,
				sequence.incrementAndGet(),
				ActionRecordType.SESSION_END,
				stoppedAtEpochMs,
				stoppedAtEpochMs - startedAtEpochMs,
				-1,
				null,
				new ActionPayloads.SessionEnd(stopReason, stoppedAtEpochMs, acceptedCount.get(),
					writtenRecordCount, droppedCount.get()));
			writer.write(JSONL_GSON.toJson(endRecord));
			writer.newLine();
			writer.flush();
			eventCounts.merge(ActionRecordType.SESSION_END, 1L, Long::sum);
			writeHandoffFiles(writtenRecordCount);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			failed = true;
			log.warn("Action Recorder writer interrupted");
		}
		catch (IOException | RuntimeException e)
		{
			failed = true;
			log.warn("Action Recorder failed to write session artifacts", e);
		}
		finally
		{
			accepting = false;
			stopped = true;
		}
	}

	private void writeHandoffFiles(long writtenRecordCount) throws IOException
	{
		Map<ActionRecordType, Long> counts;
		synchronized (eventCounts)
		{
			counts = new EnumMap<>(eventCounts);
		}
		List<String> markerCopy;
		synchronized (markers)
		{
			markerCopy = new ArrayList<>(markers);
		}

		ActionPayloads.Manifest manifest = new ActionPayloads.Manifest(
			SCHEMA_VERSION,
			sessionId,
			name,
			notes,
			startedAtEpochMs,
			stoppedAtEpochMs,
			acceptedCount.get(),
			writtenRecordCount,
			droppedCount.get(),
			counts,
			markerCopy,
			captureSettings,
			"events.jsonl",
			"handoff.md");
		Files.write(outputDirectory.resolve("manifest.json"), GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		Files.write(outputDirectory.resolve("handoff.md"), buildHandoff(markerCopy).getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	private String buildHandoff(List<String> markerCopy)
	{
		StringBuilder out = new StringBuilder();
		out.append("# Microbot Action Recorder handoff\n\n")
			.append("Session: ").append(name).append("\n\n")
			.append("This folder is an observation bundle for Microbot Hub script development. ")
			.append("Treat interactions as demonstrated intent and container, animation, widget, varbit, object, ground-item, and location records as observed effects.\n\n")
			.append("## Recording integrity\n\n")
			.append("- Accepted observations: ").append(acceptedCount.get()).append('\n')
			.append("- Generated session-end records: 1\n")
			.append("- Total written records: ").append(acceptedCount.get() + 1).append('\n')
			.append("- Dropped observations: ").append(droppedCount.get()).append("\n\n")
			.append("## Candidate flow boundaries\n\n");
		if (markerCopy.isEmpty())
		{
			out.append("No operator markers were recorded. Derive candidate phases from location clusters and interactions, then review them with the operator.\n\n");
		}
		else
		{
			for (String marker : markerCopy)
			{
				out.append("- ").append(marker).append('\n');
			}
			out.append('\n');
		}
		out.append("## Hub review guidance\n\n")
			.append("- Correlate each `INTERACTION` with the following ticks and observed deltas; do not replay canvas coordinates.\n")
			.append("- Join `WALK_DESTINATION` and `OBJECT_TARGET_STATE` to their source interaction by `interactionId`.\n")
			.append("- Treat keyboard and camera records as operator context, not mandatory script inputs.\n")
			.append("- Use item IDs plus noted/placeholder links, object IDs, widget IDs, and world locations as primary evidence.\n")
			.append("- Mark accidental or redundant operator actions as discarded observations rather than script states.\n")
			.append("- Implement the reviewed phases as a Hub state machine with explicit entry conditions, success conditions, timeouts, and recovery behavior.\n")
			.append("- Re-query Microbot caches before acting and use condition-based waits; the recording is evidence, not live entity state.\n");
		return out.toString();
	}

	String getSessionId()
	{
		return sessionId;
	}

	String getName()
	{
		return name;
	}

	Path getOutputDirectory()
	{
		return outputDirectory;
	}

	long getAcceptedCount()
	{
		return acceptedCount.get();
	}

	long getDroppedCount()
	{
		return droppedCount.get();
	}

	int getPendingCount()
	{
		return queue.size();
	}

	boolean isAccepting()
	{
		return accepting;
	}

	boolean isStopped()
	{
		return stopped;
	}

	boolean isFailed()
	{
		return failed;
	}
}
