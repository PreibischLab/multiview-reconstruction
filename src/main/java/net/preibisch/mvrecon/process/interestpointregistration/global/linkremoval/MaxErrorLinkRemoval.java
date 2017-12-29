/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class MaxErrorLinkRemoval implements LinkRemovalStrategy
{
	@Override
	public Pair< Group< ViewId >, Group< ViewId > > removeLink( final TileConfiguration tc, final HashMap< ViewId, ? extends Tile< ? > > map )
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
			return null;
		}

		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );

		final Group<ViewId> groupA = findGroup( worstTile1, map );
		final Group<ViewId> groupB = findGroup( worstTile2, map );

		IOFunctions.println( new Date( System.currentTimeMillis() ) +  ": Removed link from " + groupA + " to " + groupB );

		return new ValuePair< Group<ViewId>, Group<ViewId> >( groupA, groupB );
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
