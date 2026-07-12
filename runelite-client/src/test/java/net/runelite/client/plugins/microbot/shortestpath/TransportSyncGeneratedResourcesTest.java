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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

		CollisionMap collisionMap = new CollisionMap(SplitFlagMap.fromResources());
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
			int generatedBlocked = countBlockedEndpoints(generated, category.getValue(), collisionMap);
			int baselineBlocked = countBlockedEndpoints(baseline, category.getValue(), collisionMap);
			assertTrue(filename + " added transports with collision-blocked endpoints: generated=" +
				generatedBlocked + " baseline=" + baselineBlocked, generatedBlocked <= baselineBlocked);
			totalRows += generatedRows;
		}
		assertTrue("expected the full transport catalog, parsed only " + totalRows + " rows", totalRows > 7_000);
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
