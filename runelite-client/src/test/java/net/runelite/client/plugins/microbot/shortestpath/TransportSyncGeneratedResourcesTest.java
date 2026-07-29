package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Fail-fast validation for transport-sync staging output using the real Transport constructor.
 */
public class TransportSyncGeneratedResourcesTest
{
	private static final Map<String, TransportType> CATEGORIES = new LinkedHashMap<>();
	private static final Set<String> ACCEPTED_HEADERS = new HashSet<>(Arrays.asList(
		"Origin", "Destination", "menuOption menuTarget objectID", "Currency", "Skills",
		"Items", "Item IDs", "Quests", "Duration", "Display info", "Display Info",
		"Consumable", "Wilderness level", "isMembers", "Varbits", "Varplayers",
		"VarPlayers", "Region override"));

	static
	{
		CATEGORIES.put("agility_shortcuts.tsv", TransportType.AGILITY_SHORTCUT);
		CATEGORIES.put("boats.tsv", TransportType.BOAT);
		CATEGORIES.put("canoes.tsv", TransportType.CANOE);
		CATEGORIES.put("charter_ships.tsv", TransportType.CHARTER_SHIP);
		CATEGORIES.put("fairy_rings.tsv", TransportType.FAIRY_RING);
		CATEGORIES.put("gnome_gliders.tsv", TransportType.GNOME_GLIDER);
		CATEGORIES.put("hot_air_balloons.tsv", TransportType.HOT_AIR_BALLOON);
		CATEGORIES.put("magic_carpets.tsv", TransportType.MAGIC_CARPET);
		CATEGORIES.put("magic_mushtrees.tsv", TransportType.MAGIC_MUSHTREE);
		CATEGORIES.put("minecarts.tsv", TransportType.MINECART);
		CATEGORIES.put("quetzal_whistle.tsv", TransportType.TELEPORTATION_ITEM);
		CATEGORIES.put("quetzals.tsv", TransportType.QUETZAL);
		CATEGORIES.put("seasonal_transports.tsv", TransportType.SEASONAL_TRANSPORT);
		CATEGORIES.put("ships.tsv", TransportType.SHIP);
		CATEGORIES.put("spirit_trees.tsv", TransportType.SPIRIT_TREE);
		CATEGORIES.put("teleportation_boxes.tsv", TransportType.TELEPORTATION_PORTAL);
		CATEGORIES.put("teleportation_items.tsv", TransportType.TELEPORTATION_ITEM);
		CATEGORIES.put("teleportation_levers.tsv", TransportType.TELEPORTATION_LEVER);
		CATEGORIES.put("teleportation_minigames.tsv", TransportType.TELEPORTATION_MINIGAME);
		CATEGORIES.put("teleportation_portals.tsv", TransportType.TELEPORTATION_PORTAL);
		CATEGORIES.put("teleportation_portals_poh.tsv", TransportType.TELEPORTATION_PORTAL);
		CATEGORIES.put("teleportation_spells.tsv", TransportType.TELEPORTATION_SPELL);
		CATEGORIES.put("teleportation_spells_home.tsv", TransportType.TELEPORTATION_SPELL);
		CATEGORIES.put("transports.tsv", TransportType.TRANSPORT);
		CATEGORIES.put("wilderness_obelisks.tsv", TransportType.WILDERNESS_OBELISK);
	}

	@Test
	public void generatedCatalogParsesWithoutCapabilityCollapse() throws IOException
	{
		String generatedProperty = System.getProperty("microbot.transport.generated.dir");
		// Staged output only exists after running the converter; generic test sweeps
		// (runUnitTests etc.) must skip this, not fail. validateTransportSync sets the property.
		Assume.assumeTrue("skipped: run via :client:validateTransportSync", generatedProperty != null);
		Path generatedRoot = Paths.get(generatedProperty);
		assertTrue("generated transport directory is missing: " + generatedRoot, Files.isDirectory(generatedRoot));

		Properties provenance = loadProvenance(generatedRoot);
		assertEquals("staged payload hash does not match provenance",
			provenance.getProperty("generated_payload_sha256"), payloadSha256(generatedRoot));
		assertEquals("staged collision map hash does not match provenance",
			provenance.getProperty("candidate_collision_map_sha256"),
			sha256(Files.readAllBytes(generatedRoot.resolve("collision-map.zip"))));
		CollisionMap candidateCollisionMap;
		try (InputStream stream = Files.newInputStream(generatedRoot.resolve("collision-map.zip")))
		{
			candidateCollisionMap = new CollisionMap(SplitFlagMap.fromZip(stream));
		}
		int totalRows = 0;
		for (Map.Entry<String, TransportType> category : CATEGORIES.entrySet())
		{
			String filename = category.getKey();
			List<String> generated = Files.readAllLines(generatedRoot.resolve(filename), StandardCharsets.UTF_8);
			List<String> baseline = readBaseline(filename);
			int generatedRows = validateRows(filename, generated, category.getValue());
			int baselineRows = countDataRows(baseline);
			assertEquals(filename + " changed the number of parser-inert interactions",
				countParserInertInteractions(baseline), countParserInertInteractions(generated));
			assertTrue(filename + " suspicious row reduction: generated=" + generatedRows +
				" baseline=" + baselineRows, generatedRows >= Math.floor(baselineRows * 0.9));
			// Ratchet against the shipped collision map: a sync must not introduce transports
			// whose endpoints land on fully blocked tiles (door-into-wall = incompatible
			// transport/collision revisions). Existing offenders are tolerated but not added to.
			int generatedBlocked = countBlockedEndpoints(generated, category.getValue(), candidateCollisionMap);
			int baselineBlocked = countBlockedEndpoints(baseline, category.getValue(), candidateCollisionMap);
			assertTrue(filename + " added transports with collision-blocked endpoints: generated=" +
				generatedBlocked + " baseline=" + baselineBlocked, generatedBlocked <= baselineBlocked);
			totalRows += generatedRows;
		}
		validateLocalOnlyResources(generatedRoot);
		assertTrue("expected the full transport catalog, parsed only " + totalRows + " rows", totalRows > 7_000);
	}

	private static Properties loadProvenance(Path generatedRoot) throws IOException
	{
		Properties properties = new Properties();
		try (InputStream stream = Files.newInputStream(generatedRoot.resolve("sync-provenance.properties")))
		{
			properties.load(stream);
		}
		for (String key : Arrays.asList("tooling_commit", "data_commit", "manifest_sha256",
			"baseline_collision_map_sha256", "candidate_collision_map_sha256", "generated_payload_sha256"))
		{
			assertTrue("missing provenance property " + key, !properties.getProperty(key, "").isBlank());
		}
		return properties;
	}

	private static String payloadSha256(Path root) throws IOException
	{
		List<String> filenames = new java.util.ArrayList<>(CATEGORIES.keySet());
		filenames.addAll(Arrays.asList("npcs.tsv", "restrictions.tsv", "blocked_edges.tsv",
			"dangerous_tiles.tsv", "collision-map.zip"));
		java.util.Collections.sort(filenames);
		MessageDigest digest = sha256Digest();
		for (String filename : filenames)
		{
			digest.update(filename.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(Files.readAllBytes(root.resolve(filename)));
			digest.update((byte) 0);
		}
		return hex(digest.digest());
	}

	private static String sha256(byte[] bytes)
	{
		MessageDigest digest = sha256Digest();
		return hex(digest.digest(bytes));
	}

	private static MessageDigest sha256Digest()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes)
		{
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}

	private static void validateLocalOnlyResources(Path root) throws IOException
	{
		validateRows("npcs.tsv", Files.readAllLines(root.resolve("npcs.tsv"), StandardCharsets.UTF_8),
			TransportType.NPC);
		for (Map<String, String> row : parseFieldMaps(root.resolve("restrictions.tsv")))
		{
			assertNotNull("restriction did not parse", new Restriction(row));
		}
		validateCoordinateTable(root.resolve("blocked_edges.tsv"), 4, true);
		validateCoordinateTable(root.resolve("dangerous_tiles.tsv"), 2, false);
	}

	private static List<Map<String, String>> parseFieldMaps(Path path) throws IOException
	{
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		assertTrue(path.getFileName() + " is empty", !lines.isEmpty());
		String[] headers = lines.get(0).replaceFirst("^#\\s?", "").split("\\t", -1);
		List<Map<String, String>> rows = new java.util.ArrayList<>();
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++)
		{
			String line = lines.get(lineNumber - 1);
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			// Match Restriction.loadAllFromResources(): Java's default split drops trailing empties,
			// so absent optional values are not inserted into the field map.
			String[] fields = line.split("\\t");
			assertTrue(path.getFileName() + ":" + lineNumber + " has excess fields", fields.length <= headers.length);
			Map<String, String> row = new HashMap<>();
			for (int i = 0; i < headers.length; i++)
			{
				if (i < fields.length)
				{
					row.put(headers[i], fields[i]);
				}
			}
			rows.add(row);
		}
		return rows;
	}

	private static void validateCoordinateTable(Path path, int expectedColumns, boolean validateEdge)
		throws IOException
	{
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		assertTrue(path.getFileName() + " is empty", !lines.isEmpty());
		Set<String> rows = new HashSet<>();
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++)
		{
			String line = lines.get(lineNumber - 1);
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] fields = line.split("\\t", -1);
			assertTrue(path.getFileName() + ":" + lineNumber + " missing columns", fields.length >= expectedColumns);
			parseWorldPoint(fields[0], path, lineNumber);
			if (validateEdge)
			{
				WorldPoint origin = parseWorldPoint(fields[0], path, lineNumber);
				WorldPoint destination = parseWorldPoint(fields[1], path, lineNumber);
				assertTrue(path.getFileName() + ":" + lineNumber + " edge is not adjacent",
					origin.getPlane() == destination.getPlane() && origin.distanceTo2D(destination) == 1);
				assertTrue(path.getFileName() + ":" + lineNumber + " invalid boolean", fields[2].matches("(?i:true|false)"));
			}
			assertTrue(path.getFileName() + ":" + lineNumber + " duplicate row", rows.add(line));
		}
	}

	private static WorldPoint parseWorldPoint(String value, Path path, int lineNumber)
	{
		String[] coordinates = value.trim().split("\\s+");
		assertEquals(path.getFileName() + ":" + lineNumber + " invalid coordinate", 3, coordinates.length);
		return new WorldPoint(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
			Integer.parseInt(coordinates[2]));
	}

	private static int countBlockedEndpoints(List<String> lines, TransportType type, CollisionMap collisionMap)
	{
		int blocked = 0;
		for (Transport transport : parseRows(lines, type))
		{
			blocked += blockedEndpoint(transport.getOrigin(), collisionMap);
			blocked += blockedEndpoint(transport.getDestination(), collisionMap);
		}
		return blocked;
	}

	private static int blockedEndpoint(WorldPoint point, CollisionMap collisionMap)
	{
		// Null = originless teleport; negative coordinates = permutation sentinel.
		if (point == null || point.getX() < 0)
		{
			return 0;
		}
		return collisionMap.isBlocked(point.getX(), point.getY(), point.getPlane()) ? 1 : 0;
	}

	private static List<Transport> parseRows(List<String> lines, TransportType type)
	{
		List<Transport> transports = new java.util.ArrayList<>();
		if (lines.isEmpty())
		{
			return transports;
		}
		String[] headers = lines.get(0).replaceFirst("^#\\s?", "").split("\\t", -1);
		for (int i = 1; i < lines.size(); i++)
		{
			String line = lines.get(i);
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] fields = line.split("\\t", -1);
			Map<String, String> fieldMap = new HashMap<>();
			for (int j = 0; j < headers.length; j++)
			{
				fieldMap.put(headers[j], j < fields.length ? fields[j] : "");
			}
			transports.add(new Transport(fieldMap, type));
		}
		return transports;
	}

	private static int validateRows(String filename, List<String> lines, TransportType expectedType)
	{
		assertTrue(filename + " is empty", !lines.isEmpty());
		String headerLine = lines.get(0);
		assertTrue(filename + " header must start with #", headerLine.startsWith("#"));
		String[] headers = headerLine.replaceFirst("^#\\s?", "").split("\\t", -1);
		for (String header : headers)
		{
			assertTrue(filename + " has unknown header: " + header, ACCEPTED_HEADERS.contains(header));
		}

		Set<String> exactRows = new HashSet<>();
		int count = 0;
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++)
		{
			String line = lines.get(lineNumber - 1);
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] fields = line.split("\\t", -1);
			assertTrue(filename + ":" + lineNumber + " has excess fields", fields.length <= headers.length);
			Map<String, String> fieldMap = new HashMap<>();
			for (int i = 0; i < headers.length; i++)
			{
				fieldMap.put(headers[i], i < fields.length ? fields[i] : "");
			}
			Transport transport = new Transport(fieldMap, expectedType);
			assertNotNull(filename + ":" + lineNumber + " produced no type", transport.getType());
			assertTrue(filename + ":" + lineNumber + " changed handler classification",
				transport.getType() == expectedType ||
				(expectedType == TransportType.AGILITY_SHORTCUT && transport.getType() == TransportType.GRAPPLE_SHORTCUT));
			String interaction = fieldMap.getOrDefault("menuOption menuTarget objectID", "").trim();
			if (!interaction.isEmpty() && hasObjectId(interaction))
			{
				assertTrue(filename + ":" + lineNumber + " interaction did not parse: " + interaction,
					transport.getObjectId() > 0 && transport.getAction() != null && transport.getName() != null);
			}
			String exactKey = expectedType + "\t" + line;
			assertTrue(filename + ":" + lineNumber + " exact duplicate row", exactRows.add(exactKey));
			count++;
		}
		return count;
	}

	private static List<String> readBaseline(String filename) throws IOException
	{
		try (InputStream stream = Transport.class.getResourceAsStream(filename))
		{
			assertNotNull("missing baseline resource " + filename, stream);
			return Arrays.asList(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R", -1));
		}
	}

	private static int countDataRows(List<String> lines)
	{
		int count = 0;
		for (int i = 1; i < lines.size(); i++)
		{
			if (!lines.get(i).isBlank() && !lines.get(i).startsWith("#"))
			{
				count++;
			}
		}
		return count;
	}

	private static int countParserInertInteractions(List<String> lines)
	{
		if (lines.isEmpty())
		{
			return 0;
		}
		String[] headers = lines.get(0).replaceFirst("^#\\s?", "").split("\\t", -1);
		int interactionIndex = Arrays.asList(headers).indexOf("menuOption menuTarget objectID");
		if (interactionIndex < 0)
		{
			return 0;
		}
		int count = 0;
		for (int i = 1; i < lines.size(); i++)
		{
			String line = lines.get(i);
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] fields = line.split("\\t", -1);
			if (fields.length > interactionIndex)
			{
				String interaction = fields[interactionIndex].trim();
				if (!interaction.isEmpty() && !hasObjectId(interaction))
				{
					count++;
				}
			}
		}
		return count;
	}

	private static boolean hasObjectId(String interaction)
	{
		return interaction.matches(".*(?:\\s|;)\\d+$");
	}
}
