package net.runelite.client.plugins.microbot.actionrecorder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionPayloads;
import net.runelite.client.plugins.microbot.actionrecorder.model.ActionRecordType;
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
		ActionRecorderSession session = new ActionRecorderSession(root, "Farm run", "normal route");
		assertTrue(session.offer(ActionRecordType.OPERATOR_MARKER, 10, null,
			new ActionPayloads.OperatorMarker("BANK_PREP", "withdraw supplies")));

		session.requestStop("demonstration_complete");

		assertTrue(session.awaitStopped(5_000));
		Path output = session.getOutputDirectory();
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
		assertEquals(2, session.getAcceptedCount());
	}
}
