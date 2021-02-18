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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;

public class CenterOfMassPairwise< I extends InterestPoint > implements MatcherPairwise< I >
{
	final CenterOfMassParameters params;

	public CenterOfMassPairwise( final CenterOfMassParameters params )
	{
		this.params = params;
	}

	@Override
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn )
	{
		final PairwiseResult< I > result = new PairwiseResult< I >( false ); // these are bogus correspondences

		if ( listAIn == null || listBIn == null || listAIn.size() < 1 || listBIn.size() < 1 )
		{
			result.setCandidates( new ArrayList< PointMatchGeneric< I > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< I > >(), Double.NaN );

			result.setResult(
				System.currentTimeMillis(),
				"Not enough detections to match (1  required per list, |listA|= " + listAIn.size() + ", |listB|= " + listBIn.size() + ")" );
			return result;
		}

		final double[] centerA, centerB;

		if ( params.getCenterType() == 0 )
		{
			centerA = average( listAIn );
			centerB = average( listBIn );
		}
		else
		{
			centerA = median( listAIn );
			centerB = median( listBIn );
		}

		final ArrayList< PointMatchGeneric< I > > inliers = new ArrayList< PointMatchGeneric< I > >();

		// TODO: is this good?
		inliers.add( new PointMatchGeneric< I >( (I)listAIn.get( 0 ).newInstance( 0, centerA ), (I)listBIn.get( 0 ).newInstance( 0, centerB ) ) );

		result.setCandidates( inliers );
		result.setInliers( inliers, 0 );

		result.setResult(
				System.currentTimeMillis(),
				"Center of Mass, Center A: " + Util.printCoordinates( centerA ) + ", Center B: " + Util.printCoordinates( centerB ) );

		return result;
	}

	private static final double[] average( final List< ? extends InterestPoint > list )
	{
		final int n = list.get( 0 ).getL().length;
		final RealSum[] sum = new RealSum[ n ];

		for ( int d = 0; d < n; ++d )
			sum[ d ] = new RealSum();

		for ( final InterestPoint i : list )
		{
			final double[] l = i.getL();

			for ( int d = 0; d < n; ++d )
				sum[ d ].add( l[ d ] );
		}

		final double[] center = new double[ n ];

		for ( int d = 0; d < n; ++d )
			center[ d ] = sum[ d ].getSum() / (double)list.size();

		return center;
	}

	private static final double[] median( final List< ? extends InterestPoint > list )
	{
		final int n = list.get( 0 ).getL().length;
		final double[][] values = new double[ n ][ list.size() ];

		for ( int j = 0; j < list.size(); ++j )
		{
			final double[] l = list.get( j ).getL();

			for ( int d = 0; d < n; ++d )
				values[ d ][ j ] = l[ d ];
		}

		final double[] center = new double[ n ];

		for ( int d = 0; d < n; ++d )
			center[ d ] = Util.median( values[ d ] );

		return center;
	}

	/**
	 * We only read l[]
	 */
	@Override
	public boolean requiresInterestPointDuplication() { return false; }
}
