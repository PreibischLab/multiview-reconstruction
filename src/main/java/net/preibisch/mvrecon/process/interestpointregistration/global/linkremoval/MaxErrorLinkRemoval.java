package net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class MaxErrorLinkRemoval implements LinkRemovalStrategy
{
	@Override
	public boolean removeLink( final TileConfiguration tc, final HashMap< ViewId, ? extends Tile< ? > > map )
	{
		double worstDistance = -Double.MAX_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for (Tile<?> t : tc.getTiles())
		{
			// we mustn't disconnect a tile entirely
			if (t.getConnectedTiles().size() <= 1)
				continue;
			
			for (PointMatch pm : t.getMatches())
			{
				
				if (/*worstTile1 == null || */ pm.getDistance() > worstDistance)
				{
					worstDistance = pm.getDistance();
					
					
					worstTile1 = t;
					worstTile2 = t.findConnectedTile( pm );
				}
				
				//System.out.println( pm.getDistance() + " " + worstDistance + " " + worstTile1 );
			}
		}

		if (worstTile1 == null)
		{
			System.err.println( "WARNING: can not remove any more links without disconnecting components" );
			return false;
		}

		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );

		IOFunctions.println( new Date( System.currentTimeMillis() ) +  ": Removed link from " + findGroup( worstTile1, map ) + " to " + findGroup( worstTile2, map ) );

		return true;
	}

	public static Group< ViewId > findGroup( final Tile< ? > tile, final HashMap< ViewId, ? extends Tile< ? > > map )
	{
		final HashSet< ViewId > views = new HashSet<>();

		for ( final ViewId viewId : map.keySet() )
			if ( map.get( viewId ) == tile )
				views.add( viewId );

		return new Group<>( views );
	}
}
