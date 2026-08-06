package shortestpath.pathfinder;

import java.util.Set;
import org.junit.Test;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;

import static org.junit.Assert.assertEquals;

public class PathfinderConfigBlockedTransportKeyTest
{
	@Test
	public void transportKeyMatchesMinigameTeleportDisplayName()
	{
		Transport nightmareZone = findMinigameTeleport("Nightmare Zone Minigame Teleport");

		assertEquals("teleportation_minigames:nightmare_zone", PathfinderConfig.transportKey(nightmareZone));
	}

	@Test
	public void transportKeyKeepsRatPitsDestinationSuffix()
	{
		Transport ratPitsArdougne = findMinigameTeleport("Rat Pits Minigame Teleport: 1. Ardougne");

		assertEquals("teleportation_minigames:rat_pits_ardougne", PathfinderConfig.transportKey(ratPitsArdougne));
	}

	private static Transport findMinigameTeleport(String displayInfo)
	{
		return TransportLoader.loadAllFromResources().values().stream()
			.flatMap(Set::stream)
			.filter(transport -> TransportType.TELEPORTATION_MINIGAME.equals(transport.getType()))
			.filter(transport -> displayInfo.equals(transport.getDisplayInfo()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing minigame teleport: " + displayInfo));
	}
}
