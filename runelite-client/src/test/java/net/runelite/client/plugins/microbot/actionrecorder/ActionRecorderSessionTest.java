package net.runelite.client.plugins.microbot.actionrecorder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionPayloads;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecordType;
import net.runelite.client.plugins.microbot.actionrecorder.model.CaptureSettingsSnapshot;
import net.runelite.client.plugins.microbot.actionrecorder.model.KeyboardCaptureMode;
import net.runelite.client.plugins.microbot.actionrecorder.model.ObjectCaptureMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionRecorderSessionTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void stopDrainsEventsAndCreatesHubHandoffBundle() throws Exception
	{
		Path root = temporaryFolder.newFolder("recordings").toPath();
		ActionRecorderSession session = new ActionRecorderSession(root, "Farm run", "normal route", captureSettings());
		Path output = session.getOutputDirectory();
		awaitNonEmpty(output.resolve("events.jsonl"));
		String started = new String(Files.readAllBytes(output.resolve("events.jsonl")), StandardCharsets.UTF_8);
		assertTrue(started.contains("SESSION_START"));
		assertTrue(started.contains("\"schemaVersion\":4"));
		assertTrue(started.contains("captureSettings"));
		assertTrue(session.offer(ActionRecordType.OPERATOR_MARKER, 10, null,
			new ActionPayloads.OperatorMarker("BANK_PREP", "withdraw supplies")));

		session.requestStop("demonstration_complete");

		assertTrue(session.awaitStopped(5_000));
		assertTrue(Files.isRegularFile(output.resolve("events.jsonl")));
		assertTrue(Files.isRegularFile(output.resolve("manifest.json")));
		assertTrue(Files.isRegularFile(output.resolve("handoff.md")));

		String events = new String(Files.readAllBytes(output.resolve("events.jsonl")), StandardCharsets.UTF_8);
		assertTrue(events.contains("SESSION_START"));
		assertTrue(events.contains("OPERATOR_MARKER"));
		assertTrue(events.contains("SESSION_END"));
		String handoff = new String(Files.readAllBytes(output.resolve("handoff.md")), StandardCharsets.UTF_8);
		assertTrue(handoff.contains("BANK_PREP"));
		assertTrue(handoff.contains("Microbot Hub"));
		assertTrue(handoff.contains("Accepted observations: 2"));
		assertTrue(handoff.contains("Total written records: 3"));
		assertEquals(2, session.getAcceptedCount());
		String manifest = new String(Files.readAllBytes(output.resolve("manifest.json")), StandardCharsets.UTF_8);
		assertTrue(manifest.contains("\"flushEveryRecords\": 25"));
		assertTrue(manifest.contains("\"acceptedObservationCount\": 2"));
		assertTrue(manifest.contains("\"writtenRecordCount\": 3"));
		assertTrue(manifest.contains("\"droppedObservationCount\": 0"));
	}

	private static void awaitNonEmpty(Path path) throws Exception
	{
		long deadline = System.currentTimeMillis() + 2_000;
		while ((!Files.isRegularFile(path) || Files.size(path) == 0) && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
		}
		assertTrue("Session start record was not flushed promptly", Files.isRegularFile(path) && Files.size(path) > 0);
	}

	private static CaptureSettingsSnapshot captureSettings()
	{
		return new CaptureSettingsSnapshot(
			true, true, 1, true, true, true, true, true,
			true, true, true, true, true, false, ObjectCaptureMode.ACTIONABLE_NEARBY,
			true, true, 16, false, KeyboardCaptureMode.ALLOWLIST, "W,A,S,D", true, 25);
	}
}
