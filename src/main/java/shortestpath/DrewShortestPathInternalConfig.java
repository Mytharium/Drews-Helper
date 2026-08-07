package shortestpath;

import javax.inject.Singleton;

@Singleton
final class DrewShortestPathInternalConfig implements ShortestPathConfig
{
	@Override
	public boolean postTransports()
	{
		return true;
	}

	@Override
	public void setBuiltTeleportationBoxes(String content)
	{
	}

	@Override
	public void setBuiltTeleportationPortalsPoh(String content)
	{
	}
}
