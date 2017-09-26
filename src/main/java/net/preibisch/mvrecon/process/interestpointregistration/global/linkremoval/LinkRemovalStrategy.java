package net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval;

import java.util.HashMap;

import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;

public interface LinkRemovalStrategy
{
	public boolean removeLink( TileConfiguration tc, HashMap< ViewId, ? extends Tile< ? > > map );
}
