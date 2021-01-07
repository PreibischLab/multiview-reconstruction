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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac;

import java.text.NumberFormat;
import java.util.ArrayList;

import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.LinkedPoint;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RANSAC
{
	public static < I extends InterestPoint > Pair< String, Double > computeRANSAC( 
			final ArrayList< PointMatchGeneric < I > > correspondenceCandidates, 
			final ArrayList< PointMatchGeneric < I > > inlierList, 
			final Model<?> model, 
			final double maxEpsilon, 
			final double minInlierRatio, 
			final double minNumberInlierFactor, 
			final int numIterations )
	{
		final int numCorrespondences = correspondenceCandidates.size();
		final int minNumCorrespondences = Math.max( model.getMinNumMatches(), (int)Math.round( model.getMinNumMatches() * minNumberInlierFactor ) );
		
		/*
		 * First remove the inconsistent correspondences
		 */
		// I do not think anymore that this is required
		// removeInconsistentCorrespondences( correspondenceCandidates );

		// if there are not enough correspondences for the used model
		if ( numCorrespondences < minNumCorrespondences )
			return new ValuePair< String, Double >( "Not enough correspondences found " + numCorrespondences + ", should be at least " + minNumCorrespondences, Double.NaN );

		/**
		 * The ArrayList that stores the inliers after RANSAC, contains PointMatches of LinkedPoints
		 * so that MultiThreading is possible
		 */
		//final ArrayList< PointMatchGeneric<LinkedPoint<T>> > candidates = new ArrayList<PointMatchGeneric<LinkedPoint<T>>>();		
		final ArrayList< PointMatch > candidates = new ArrayList< PointMatch >();
		final ArrayList< PointMatch > inliers = new ArrayList< PointMatch >();
		
		// clone the beads for the RANSAC as we are working multithreaded and they will be modified
		for ( final PointMatchGeneric< I > correspondence : correspondenceCandidates )
		{
			final I detectionA = correspondence.getPoint1();
			final I detectionB = correspondence.getPoint2();

			// the LinkedPoint always clones the location array
			final LinkedPoint< I > pA = new LinkedPoint< I >( detectionA.getL(), detectionA.getW(), detectionA );
			final LinkedPoint< I > pB = new LinkedPoint< I >( detectionB.getL(), detectionB.getW(), detectionB );
			final double weight = correspondence.getWeight(); 

			candidates.add( new PointMatchGeneric< LinkedPoint< I > >( pA, pB, weight ) );
		}
		
		boolean modelFound = false;
		
		try
		{
			/*modelFound = m.ransac(
  					candidates,
					inliers,
					numIterations,
					maxEpsilon, minInlierRatio );*/
		
			modelFound = model.filterRansac(
					candidates,
					inliers,
					numIterations,
					maxEpsilon, minInlierRatio ); 
		}
		catch ( NotEnoughDataPointsException e )
		{
			return new ValuePair< String, Double >( e.toString(), Double.NaN );
		}
			
		final NumberFormat nf = NumberFormat.getPercentInstance();
		final double ratio = ( (double)inliers.size() / (double)candidates.size() );
		
		if ( modelFound && inliers.size() >= minNumCorrespondences )
		{			
			for ( final PointMatch pointMatch : inliers )
			{
				@SuppressWarnings("unchecked")
				final PointMatchGeneric<LinkedPoint< I > > pm = (PointMatchGeneric< LinkedPoint< I > >) pointMatch;
				
				final I detectionA = pm.getPoint1().getLinkedObject();
				final I detectionB = pm.getPoint2().getLinkedObject();
				
				inlierList.add( new PointMatchGeneric< I >( detectionA, detectionB ) );
			}

			return new ValuePair< String, Double >( "Remaining inliers after RANSAC: " + inliers.size() + " of " + candidates.size() + " (" + nf.format(ratio) + ") with average error " + model.getCost(), model.getCost() );
		}
		else
		{
			if ( modelFound )
				return new ValuePair< String, Double >( "Model found but not enough remaining inliers (" + inliers.size() + "/" + minNumCorrespondences + ") after RANSAC of " + candidates.size(), Double.NaN );
			else
				return new ValuePair< String, Double >( "NO Model found after RANSAC of " + candidates.size(), Double.NaN );
		}
	}
}
