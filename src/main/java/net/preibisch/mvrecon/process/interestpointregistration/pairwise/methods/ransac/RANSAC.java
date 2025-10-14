/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.pointcloud.pointdescriptor.LinkedPoint;

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
			final int minNumMatches,
			final int numIterations )
	{
		final int numCorrespondences = correspondenceCandidates.size();
		final int minNumCorrespondences = Math.max( model.getMinNumMatches(), minNumMatches );

		IOFunctions.println( "Too few minNumMatches specified " + minNumMatches + ", model requires at least " + model.getMinNumMatches() + ". Adjusting to " + model.getMinNumMatches() );

		// if there are not enough correspondences for the used model
		if ( numCorrespondences < minNumCorrespondences )
			return new ValuePair< String, Double >( "Not enough correspondences found " + numCorrespondences + ", should be at least " + minNumCorrespondences, Double.NaN );

		/**
		 * The ArrayList that stores the inliers after RANSAC, contains PointMatches of LinkedPoints
		 * so that MultiThreading is possible
		 */
		ArrayList< PointMatch > candidates = new ArrayList< PointMatch >();
		ArrayList< PointMatch > inliers = new ArrayList< PointMatch >();
		
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
			// remove the inconsistent inliers
			final int numCorr = inliers.size();
			inliers = removeInconsistentMatches( inliers );

			if ( inliers.size() < minNumCorrespondences )
			{
				final int numRemoved = numCorr - inliers.size();

				// and try again with cleaned correspondences
				candidates = removeInconsistentMatches( candidates );
				inliers.clear();

				try
				{
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

				if ( !modelFound || inliers.size() < minNumCorrespondences )
					return new ValuePair< String, Double >( "NO Model found after removing " + numRemoved + " inconsistent matches and re-running RANSAC using " + candidates.size(), Double.NaN );
			}

			for ( final PointMatch pointMatch : inliers )
			{
				@SuppressWarnings("unchecked")
				final PointMatchGeneric<LinkedPoint< I > > pm = (PointMatchGeneric< LinkedPoint< I > >) pointMatch;

				final I detectionA = pm.getPoint1().getLinkedObject();
				final I detectionB = pm.getPoint2().getLinkedObject();

				inlierList.add( new PointMatchGeneric< I >( detectionA, detectionB ) );
			}

			String inconsistent = "";
			if ( numCorr != inliers.size() )
				inconsistent = " [removed " + (numCorr - inliers.size() ) + " inconsistent inliers]";

			return new ValuePair< String, Double >( "Remaining inliers after RANSAC: " + inliers.size() + " of " + candidates.size() + " (" + nf.format(ratio) + ") with average error " + model.getCost() + "" + inconsistent, model.getCost() );
		}
		else
		{
			if ( modelFound )
				return new ValuePair< String, Double >( "Model found but not enough remaining inliers (" + inliers.size() + "/" + minNumCorrespondences + ") after RANSAC of " + candidates.size(), Double.NaN );
			else
				return new ValuePair< String, Double >( "NO Model found after RANSAC of " + candidates.size(), Double.NaN );
		}
	}

	/**
	 * a class that computes hash and equals only using the coordinates of a double[] array
	 */
	private static class HashableDoubleArray
	{
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(l);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			HashableDoubleArray other = (HashableDoubleArray) obj;
			return Arrays.equals(l, other.l);
		}

		final double[] l;

		public HashableDoubleArray( final double[] l ) { this.l = l; }
	}

	public static < P extends PointMatch > ArrayList< P > removeInconsistentMatches( final List< P > matches )
	{
		final HashMap< HashableDoubleArray, ArrayList< Integer > > p1 = new HashMap<>();
		final HashMap< HashableDoubleArray, ArrayList< Integer > > p2 = new HashMap<>();

		for ( int i = 0; i < matches.size(); ++i )
		{
			final P pm = matches.get( i );

			// only the underlying detections are the same objects
			final HashableDoubleArray detectionA = new HashableDoubleArray( pm.getP1().getL() );
			final HashableDoubleArray detectionB = new HashableDoubleArray( pm.getP2().getL() );

			//System.out.println( Arrays.toString( detectionA.l ) + " " + Arrays.toString( detectionB.l ) + " " + detectionA.hashCode() + " = " + detectionB.hashCode() );

			if ( p1.containsKey( detectionA ) )
			{
				p1.get( detectionA ).add( i );
			}
			else
			{
				final ArrayList<Integer> list = new ArrayList<>();
				list.add( i );
				p1.put( detectionA, list );
			}

			if ( p2.containsKey( detectionB ) )
			{
				p2.get( detectionB ).add( i );
			}
			else
			{
				final ArrayList<Integer> list = new ArrayList<>();
				list.add( i );
				p2.put( detectionB, list );
			}
		}

		// build a HashSet of all indicies that collide
		final HashSet< Integer > toRemove = new HashSet<>();

		for ( final Entry< HashableDoubleArray, ArrayList< Integer > > entry : p1.entrySet() )
			if (entry.getValue().size() > 1 )
				toRemove.addAll( entry.getValue() );

		for ( final Entry< HashableDoubleArray, ArrayList< Integer > > entry : p2.entrySet() )
			if (entry.getValue().size() > 1 )
				toRemove.addAll( entry.getValue() );

		//System.out.println( "Removing " + toRemove.size() + " matches." );

		final ArrayList< P > newList = new ArrayList<>();
		for ( int i = 0; i < matches.size(); ++i )
		{
			if ( !toRemove.contains( i ) )
				newList.add( matches.get( i ) );
		}

		return newList;
	}
}
