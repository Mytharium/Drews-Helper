package shortestpath;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShortestPathPluginOverrideTest
{
	private Map<String, Object> configOverride;

	@Before
	@SuppressWarnings("unchecked")
	public void before() throws Exception
	{
		Field field = ShortestPathPlugin.class.getDeclaredField("configOverride");
		field.setAccessible(true);
		configOverride = (Map<String, Object>) field.get(null);
		configOverride.clear();
	}

	@After
	public void after()
	{
		configOverride.clear();
	}

	@Test
	public void overrideStringSetAcceptsCollection()
	{
		configOverride.put("blockedTransportKeys", Arrays.asList(
			"teleportation_minigames:nightmare_zone",
			"",
			" teleportation_minigames:bounty_hunter "));

		assertEquals(
			Set.of("teleportation_minigames:nightmare_zone", "teleportation_minigames:bounty_hunter"),
			ShortestPathPlugin.overrideStringSet("blockedTransportKeys"));
	}

	@Test
	public void overrideStringSetAcceptsSingleString()
	{
		configOverride.put("blockedTransportKeys", "teleportation_minigames:nightmare_zone");

		assertEquals(
			Set.of("teleportation_minigames:nightmare_zone"),
			ShortestPathPlugin.overrideStringSet("blockedTransportKeys"));
	}

	@Test
	public void overrideStringSetRejectsUnsupportedValues()
	{
		configOverride.put("blockedTransportKeys", new HashMap<>());

		assertTrue(ShortestPathPlugin.overrideStringSet("blockedTransportKeys").isEmpty());
	}

	@Test
	public void drewsInternalConfigPostsTransportTelemetryByDefault()
	{
		assertTrue(new DrewShortestPathInternalConfig().postTransports());
	}
}
