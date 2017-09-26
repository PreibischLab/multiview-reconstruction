package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.geometrichashing;

import java.util.ArrayList;
import java.util.HashSet;

import mpicbg.pointdescriptor.LocalCoordinateSystemPointDescriptor;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.pointdescriptor.matcher.SubsetMatcher;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

/**
 * Class that actually computes the geometric hashing
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class GeometricHasher< I extends InterestPoint >
{
	public ArrayList< PointMatchGeneric< I > > extractCorrespondenceCandidates( 
			final ArrayList< I > nodeListA,
			final ArrayList< I > nodeListB,
			final double differenceThreshold,
			final int redundancy,
			final double ratioOfDistance )
	{
		final KDTree< I > tree1 = new KDTree<>( nodeListA, nodeListA );
		final KDTree< I > tree2 = new KDTree<>( nodeListB, nodeListB );

		final ArrayList< LocalCoordinateSystemPointDescriptor< I > > descriptors1 =
			createLocalCoordinateSystemPointDescriptors( tree1, nodeListA, redundancy, false );
		
		final ArrayList< LocalCoordinateSystemPointDescriptor< I > > descriptors2 =
			createLocalCoordinateSystemPointDescriptors( tree2, nodeListB, redundancy, false );
		
		// create lookup tree for descriptors2
		final KDTree< LocalCoordinateSystemPointDescriptor< I > > lookUpTree2 = new KDTree<>( descriptors2, descriptors2 );
		final KNearestNeighborSearchOnKDTree< LocalCoordinateSystemPointDescriptor< I > > nnsearch = new KNearestNeighborSearchOnKDTree<>( lookUpTree2, 2 );

		// store the candidates for corresponding beads
		final ArrayList< PointMatchGeneric< I > > correspondences = new ArrayList<>();
		
		/* compute matching */
		computeMatching( descriptors1, nnsearch, correspondences, differenceThreshold, ratioOfDistance );
		
		return correspondences;
	}
	
	protected void computeMatching( 
			final ArrayList< LocalCoordinateSystemPointDescriptor< I > > descriptors1,
			final KNearestNeighborSearchOnKDTree< LocalCoordinateSystemPointDescriptor< I > > nnsearch2,
			final ArrayList< PointMatchGeneric< I > > correspondences,
			final double differenceThreshold, 
			final double ratioOfDistance )
	{
		final HashSet< Pair< I, I > > pairs = new HashSet<>();

		//System.out.println( "BeadA" + "\t" + "BeadB1" + "\t" + "BeadB2" + "\t" + "Diff1" + "\t" + "Diff2" );

		for ( final LocalCoordinateSystemPointDescriptor< I > descriptorA : descriptors1 )
		{
			nnsearch2.search( descriptorA );

			double best = descriptorA.descriptorDistance( nnsearch2.getSampler( 0 ).get() );
			double secondBest = descriptorA.descriptorDistance( nnsearch2.getSampler( 1 ).get() );

			if ( best < differenceThreshold && best * ratioOfDistance <= secondBest )
			{
				final I detectionA = descriptorA.getBasisPoint();
				final I detectionB = nnsearch2.getSampler( 0 ).get().getBasisPoint();

				//System.out.println( beadA.getID() + "\t" + matches[ 0 ].getBasisPoint().getID() + "\t" + matches[ 1 ].getBasisPoint().getID() + "\t" + best + "\t" + secondBest );

				//detectionA.addPointDescriptorCorrespondence( detectionB, 1 );
				//detectionB.addPointDescriptorCorrespondence( detectionA, 1 );

				// twice the same pair could potentially show up due to redundancy
				pairs.add( new ValuePair<>( detectionA, detectionB ) );

				//correspondences.add( new PointMatchGeneric< I >( detectionA, detectionB, 1 ) );
			}
		}

		for ( final Pair< I, I > pair : pairs )
			correspondences.add( new PointMatchGeneric< I >( pair.getA(), pair.getB(), 1 ) );
	}

	public static < I extends InterestPoint > ArrayList< LocalCoordinateSystemPointDescriptor< I > > createLocalCoordinateSystemPointDescriptors( 
			final KDTree< I > tree,
			final ArrayList< I > basisPoints,
			final int redundancy,
			final boolean normalize )
	{
		final int numNeighbors = 3;

		final KNearestNeighborSearchOnKDTree< I > nnsearch = new KNearestNeighborSearchOnKDTree<>( tree, numNeighbors + redundancy + 1 );
		final ArrayList< LocalCoordinateSystemPointDescriptor< I > > descriptors = new ArrayList<> ( );

		final int[][] neighborIndicies = SubsetMatcher.computePD( numNeighbors + redundancy, numNeighbors, 1 );

		for ( final I p : basisPoints )
		{
			nnsearch.search( p );

			for ( final int[] neighbors : neighborIndicies )
			{
				final ArrayList< I > neighborPoints = new ArrayList<>();

				// the first hit is always the point itself
				for ( int n = 0; n < numNeighbors; ++n )
					neighborPoints.add( nnsearch.getSampler( neighbors[ n ] ).get() );
	
				try
				{
					descriptors.add( new LocalCoordinateSystemPointDescriptor< I >( p, neighborPoints, normalize ) );
				}
				catch ( NoSuitablePointsException e )
				{
					e.printStackTrace();
				}
			}
		}

		return descriptors;
	}
}
