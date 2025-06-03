/*-
 * #%L
 * TrakEM2 plugin for ImageJ.
 * %%
 * Copyright (C) 2005 - 2021 Albert Cardona, Stephan Saalfeld and others.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;

/**
 * @author Stephan Saalfeld saalfelds@janelia.hhmi.org
 *
 */
public class RansacRegressionReduceFilter
{
	private final AffineModel1D model;

	private final int iterations;
	private final double  maxEpsilon;
	private final double minInlierRatio;
	private final int minNumInliers;
	private final double maxTrust;

	public RansacRegressionReduceFilter(
			final AffineModel1D model
	) {
		this( model, 1000, 0.1, 0.1, 10, 3.0 );
	}
	public RansacRegressionReduceFilter(
		final AffineModel1D model,
		final int iterations,
		final double  maxEpsilon,
		final double minInlierRatio,
		final int minNumInliers,
		final double maxTrust
	) {
		this.iterations = iterations;
		this.maxEpsilon = maxEpsilon;
		this.minInlierRatio = minInlierRatio;
		this.minNumInliers = minNumInliers;
		this.maxTrust = maxTrust;
		this.model = model;
	}

	public void filter( final List< PointMatch > candidates, final Collection< PointMatch > inliers )
	{
		boolean inliersAreValid;
		try
		{
			if ( model instanceof FastAffineModel1D )
				inliersAreValid = ( ( FastAffineModel1D ) model ).fastFilterRansac( candidates, inliers, iterations, maxEpsilon, minInlierRatio, minNumInliers, maxTrust );
			else
				inliersAreValid = model.filterRansac( candidates, inliers, iterations, maxEpsilon, minInlierRatio, minNumInliers, maxTrust );
			if ( inliersAreValid )
				model.fit( inliers );
		}
		catch ( final NotEnoughDataPointsException | IllDefinedDataPointsException e )
		{
			inliersAreValid = false;
		}

		if ( !inliersAreValid )
		{
			inliers.clear();
			return;
		}

		final double[] minMax = minMax( inliers );
		final double weight = 2.0 / model.getMinNumMatches();
		final List< Point > points = evenlySpacedPoints( minMax, model.getMinNumMatches() );
		inliers.clear();

		for ( final Point point : points )
		{
			point.apply( model );
			inliers.add( new PointMatch( point, new Point( point.getW().clone() ), weight ) );
		}
	}

	private static double[] minMax( final Iterable< PointMatch > matches )
	{
		final Iterator< PointMatch > iter = matches.iterator();
		PointMatch m = iter.next();
		double min = m.getP1().getL()[ 0 ];
		double max = min;
		while ( iter.hasNext() )
		{
			m = iter.next();
			final double x = m.getP1().getL()[ 0 ];
			if ( x < min )
				min = x;
			else if ( x > max )
				max = x;
		}
		return new double[] { min, max };
	}

	private static List< Point > evenlySpacedPoints( final double[] interval, final int n )
	{
		if ( n == 1 )
			return Collections.singletonList( new Point( new double[] { ( interval[ 0 ] + interval[ 1 ] ) / 2 } ) );

		final double min = interval[ 0 ];
		final double delta = ( interval[ 1 ] - interval[ 0 ] ) / ( n - 1 );
		final List< Point > points = new ArrayList<>();

		for ( int k = 0; k < n; k++ )
		{
			points.add( new Point( new double[] { min + k * delta } ) );
		}

		return points;
	}
}
