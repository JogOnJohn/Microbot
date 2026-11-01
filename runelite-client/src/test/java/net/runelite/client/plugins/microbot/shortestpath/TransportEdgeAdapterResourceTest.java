package net.runelite.client.plugins.microbot.shortestpath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.transport.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.TransportEdgeAdapter;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransportEdgeAdapterResourceTest
{
	@Test
	public void shippedCatalogKeepsExactSourcesAndEveryParsedBranch()
	{
		int count = 0;
		for (Set<Transport> transports : Transport.loadAllFromResources().values())
		{
			for (Transport source : transports)
			{
				assertFaithful(source);
				count++;
			}
		}
		assertTrue("expected the shipped transport catalog", count > 6000);
	}

	@Test
	public void generatedConverterRowsUseUnchangedParserAndKeepRequirements() throws Exception
	{
		String root = System.getProperty("microbot.transport.generated.dir");
		Assume.assumeTrue("optional converter output is not configured", root != null);
		Map<String, TransportType> categories = Map.of(
			"transports.tsv", TransportType.TRANSPORT,
			"teleportation_items.tsv", TransportType.TELEPORTATION_ITEM,
			"teleportation_spells.tsv", TransportType.TELEPORTATION_SPELL,
			"canoes.tsv", TransportType.CANOE);
		int count = 0;
		for (Map.Entry<String, TransportType> category : categories.entrySet())
		{
			Path path = Paths.get(root).resolve(category.getKey());
			assertTrue("missing generated resource: " + path, Files.isRegularFile(path));
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			assertFalse(lines.isEmpty());
			String[] headers = lines.get(0).replaceFirst("^# ?", "").split("\t", -1);
			for (String line : lines.subList(1, lines.size()))
			{
				if (line.isBlank() || line.startsWith("#")) continue;
				String[] values = line.split("\t", -1);
				Map<String, String> fields = new HashMap<>();
				for (int i = 0; i < headers.length && i < values.length; i++)
				{
					if (!values[i].isEmpty()) fields.put(headers[i], values[i]);
				}
				Transport source = new Transport(fields, category.getValue());
				// Origin/destination half-rows are expanded by the loader, not the adapter.
				if (source.getDestination() == null)
				{
					try
					{
						TransportEdgeAdapter.toEdge(source);
						fail("incomplete row must not become an executable edge");
					}
					catch (NullPointerException expected) { }
					continue;
				}
				assertFaithful(source);
				count++;
			}
		}
		assertTrue("expected generated rows", count > 0);
	}

	private static void assertFaithful(Transport source)
	{
		Rs2TransportEdge edge = TransportEdgeAdapter.toEdge(source);
		assertSame(source, TransportEdgeAdapter.toLegacy(edge));
		assertEquals(source.getOrigin(), edge.getOrigin());
		assertEquals(source.getDestination(), edge.getDestination());
		assertEquals(source.getDisplayInfo(), edge.getDisplayInfo());
		assertEquals(source.getAction(), edge.getAction());
		assertEquals(source.getName(), edge.getTarget());
		assertEquals(source.getDuration(), edge.getDuration());
		assertEquals(source.getCurrencyName(), edge.getCurrencyName());
		assertEquals(source.getCurrencyAmount(), edge.getCurrencyAmount());
		if (source.getParsedItemRequirements() == null)
		{
			assertEquals(source.getItemIdRequirements().size(), edge.getItemRequirements().size());
			return;
		}
		List<ItemRequirement> groups = source.getParsedItemRequirements().getRequirements();
		assertEquals(groups.size(), edge.getItemRequirements().size());
		for (int i = 0; i < groups.size(); i++)
		{
			List<ItemRequirement.Branch> expected = groups.get(i).getBranches();
			List<Rs2TransportItemRequirement.Branch> actual = edge.getItemRequirements().get(i).getBranches();
			assertEquals(expected.size(), actual.size());
			for (int j = 0; j < expected.size(); j++)
			{
				assertEquals(ids(expected.get(j).getItemIds()), actual.get(j).getItemIds());
				assertEquals(ids(expected.get(j).getStaffIds()), actual.get(j).getStaffIds());
				assertEquals(ids(expected.get(j).getOffhandIds()), actual.get(j).getOffhandIds());
				assertEquals(expected.get(j).getQuantity(), actual.get(j).getQuantity());
				assertEquals(expected.get(j).getUnlock() == null ? null : expected.get(j).getUnlock().name(),
					actual.get(j).getUnlock());
			}
		}
	}

	private static Set<Integer> ids(int[] ids)
	{
		Set<Integer> result = new HashSet<>();
		if (ids != null) Arrays.stream(ids).forEach(result::add);
		return result;
	}
}
