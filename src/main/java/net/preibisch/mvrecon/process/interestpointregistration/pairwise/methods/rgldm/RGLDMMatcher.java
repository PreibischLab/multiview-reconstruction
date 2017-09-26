package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm;

import java.util.ArrayList;

import mpicbg.pointdescriptor.AbstractPointDescriptor;
import mpicbg.pointdescriptor.SimplePointDescriptor;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.pointdescriptor.matcher.Matcher;
import mpicbg.pointdescriptor.matcher.SubsetMatcher;
import mpicbg.pointdescriptor.similarity.SimilarityMeasure;
import mpicbg.pointdescriptor.similarity.SquareDistance;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class RGLDMMatcher< I extends InterestPoint >
{
	public ArrayList< PointMatchGeneric< I > > extractCorrespondenceCandidates( 
			final ArrayList< I > nodeListA,
			final ArrayList< I > nodeListB,
			final int numNeighbors,
			final int redundancy,
			final double ratioOfDistance,
			final double differenceThreshold ) 
	{
		/* create KDTrees */	
		final KDTree< I > treeA = new KDTree< I >( nodeListA, nodeListA );
		final KDTree< I > treeB = new KDTree< I >( nodeListB, nodeListB );
		
		/* extract point descriptors */
		final Matcher matcher = new SubsetMatcher( numNeighbors, numNeighbors + redundancy );
		final int numRequiredNeighbors = matcher.getRequiredNumNeighbors();
		
		final SimilarityMeasure similarityMeasure = new SquareDistance();
		
		final ArrayList< SimplePointDescriptor< I > > descriptorsA = createSimplePointDescriptors( treeA, nodeListA, numRequiredNeighbors, matcher, similarityMeasure );
		final ArrayList< SimplePointDescriptor< I > > descriptorsB = createSimplePointDescriptors( treeB, nodeListB, numRequiredNeighbors, matcher, similarityMeasure );

		return findCorrespondingDescriptors( descriptorsA, descriptorsB, ratioOfDistance, differenceThreshold );
	}
	
	protected static final < I extends InterestPoint, D extends AbstractPointDescriptor< I , D > > ArrayList< PointMatchGeneric< I > > findCorrespondingDescriptors(
			final ArrayList< D > descriptorsA,
			final ArrayList< D > descriptorsB,
			final double nTimesBetter,
			final double differenceThreshold )
	{
		final ArrayList< PointMatchGeneric< I > > correspondenceCandidates = new ArrayList<>();
		
		for ( final D descriptorA : descriptorsA )
		{
			double bestDifference = Double.MAX_VALUE;
			double secondBestDifference = Double.MAX_VALUE;

			D bestMatch = null;
			D secondBestMatch = null;

			for ( final D descriptorB : descriptorsB )
			{
				final double difference = descriptorA.descriptorDistance( descriptorB );

				if ( difference < secondBestDifference )
				{
					secondBestDifference = difference;
					secondBestMatch = descriptorB;
					
					if ( secondBestDifference < bestDifference )
					{
						double tmpDiff = secondBestDifference;
						D tmpMatch = secondBestMatch;
						
						secondBestDifference = bestDifference;
						secondBestMatch = bestMatch;
						
						bestDifference = tmpDiff;
						bestMatch = tmpMatch;
					}
				}				
			}
			
			if ( bestDifference < differenceThreshold && bestDifference * nTimesBetter < secondBestDifference )
			{	
				// add correspondence for the two basis points of the descriptor
				I detectionA = descriptorA.getBasisPoint();
				I detectionB = bestMatch.getBasisPoint();
				
				// for RANSAC
				correspondenceCandidates.add( new PointMatchGeneric< I >( detectionA, detectionB ) );
			}
		}

		return correspondenceCandidates;
	}

	protected static < I extends InterestPoint > ArrayList< SimplePointDescriptor< I > > createSimplePointDescriptors(
			final KDTree< I > tree,
			final ArrayList< I > basisPoints,
			final int numNeighbors,
			final Matcher matcher,
			final SimilarityMeasure similarityMeasure )
	{
		final KNearestNeighborSearchOnKDTree< I > nnsearch = new KNearestNeighborSearchOnKDTree<>( tree, numNeighbors + 1 );
		final ArrayList< SimplePointDescriptor< I > > descriptors = new ArrayList<> ( );

		for ( final I p : basisPoints )
		{
			final ArrayList< I > neighbors = new ArrayList<>();
			nnsearch.search( p );

			// the first hit is always the point itself
			for ( int n = 1; n < numNeighbors + 1; ++n )
				neighbors.add( nnsearch.getSampler( n ).get() );

			try
			{
				descriptors.add( new SimplePointDescriptor< I >( p, neighbors, similarityMeasure, matcher ) );
			}
			catch ( NoSuitablePointsException e )
			{
				e.printStackTrace();
			}
		}

		return descriptors;
	}

}
