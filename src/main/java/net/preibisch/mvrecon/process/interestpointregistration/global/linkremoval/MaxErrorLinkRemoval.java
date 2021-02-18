/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.QualityPointMatch;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;

public class MaxErrorLinkRemoval implements LinkRemovalStrategy
{
	@Override
	public Pair< Group< ViewId >, Group< ViewId > > removeLink( final TileConfiguration tc, final HashMap< ViewId, ? extends Tile< ? > > map )
	{
		double worstInvScore = -Double.MAX_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for ( final Tile<?> t : tc.getTiles())
		{
			//System.out.println( "Inspecting group: " + findGroup( t, map ) );

			final int connected = t.getConnectedTiles().size();

			// we mustn't disconnect a tile entirely
			if ( connected <= 1 )
				continue;

			for ( final PointMatch pm : t.getMatches() )
			{
				final Tile<?> connectedTile = t.findConnectedTile( pm );

				// make sure that pm is not the only connection of the connected tile either 
				if ( connectedTile.getConnectedTiles().size() <= 1 )
					continue;

				double quality = 0.01; // between [0.01, 1.00]

				if ( QualityPointMatch.class.isInstance( pm ) )
					quality = ( (QualityPointMatch)pm ).getQuality(); // most likely cross correlation

				quality = Math.min( 1.0, quality );
				quality = Math.max( 0.01, quality );

				final double invScore = ( 1.01 - quality ) * Math.sqrt( pm.getDistance() );// * Math.log10( connected );

				//System.out.println( "invScore=" + invScore + " [dist=" + pm.getDistance() + ", quality=" + quality + ", connected=" + connected + "] to " + findGroup( t.findConnectedTile( pm ), map ) );

				if ( invScore > worstInvScore )
				{
					worstInvScore = invScore;

					worstTile1 = t;
					worstTile2 = connectedTile;

					//System.out.println( "NEW WORST: " + worstInvScore + " between " + findGroup( worstTile1, map ) + " and " + findGroup( worstTile2, map ) );
				}
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
