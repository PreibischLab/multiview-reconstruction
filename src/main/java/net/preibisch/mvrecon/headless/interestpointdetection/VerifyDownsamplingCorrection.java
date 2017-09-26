package net.preibisch.mvrecon.headless.interestpointdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoG;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGParameters;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class VerifyDownsamplingCorrection
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();
		IOFunctions.printIJLog = true;

		final SpimData2 sdTiff = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Grants and CV/BIMSB/Projects/Big Data Sticher/TestDownsampling/TIF/dataset.xml" );
		final SpimData2 sdHdf5 = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Grants and CV/BIMSB/Projects/Big Data Sticher/TestDownsampling/HDF5/dataset.xml" );

		//String label = "beads4x";
		//testLoad( sdTiff, sdHdf5, label );
		testFresh( sdTiff, sdHdf5 );
	}

	public static void testFresh( final SpimData2 sdTiff, final SpimData2 sdHdf5 )
	{
		final DoGParameters dog = new DoGParameters();

		/*
		dog.downsampleXY = 2;
		dog.downsampleZ = 2;
		dog.sigma = 1.8014999628067017;
		dog.threshold = 0.007973356172442436;
		*/

		dog.downsampleXY = 8;
		dog.downsampleZ = 8;
		dog.sigma = 1.1500000476837158;
		dog.threshold = 0.007973356172442436;

		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( sdTiff.getSequenceDescription().getViewDescriptions().values() );

		dog.imgloader = sdTiff.getSequenceDescription().getImgLoader();
		final HashMap< ViewId, List< InterestPoint > > pointsTiff = DoG.findInterestPoints( dog );

		dog.imgloader = sdHdf5.getSequenceDescription().getImgLoader();
		final HashMap< ViewId, List< InterestPoint > > pointsHdf5 = DoG.findInterestPoints( dog );

		for ( final ViewId viewId : sdTiff.getSequenceDescription().getViewDescriptions().values() )
		{
			statistics( viewId, pointsTiff.get( viewId ), pointsHdf5.get( viewId ) );
		}
	}


	public static void testLoad( final SpimData2 sdTiff, final SpimData2 sdHdf5, final String label )
	{
		for ( final ViewId viewId : sdTiff.getSequenceDescription().getViewDescriptions().values() )
		{
			final List< InterestPoint > ipListTiff =
					sdTiff.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label ).getInterestPointsCopy();

			final List< InterestPoint > ipListHdf5 =
					sdHdf5.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label ).getInterestPointsCopy();

			statistics( viewId, ipListTiff, ipListHdf5 );
		}
	}

	public static void statistics( final ViewId viewId, final List< InterestPoint > ipListTiff, final List< InterestPoint > ipListHdf5 )
	{
		IOFunctions.println( "View: " + Group.pvid( viewId ) );

		final KDTree< InterestPoint > kdTreeTiff = new KDTree<>( ipListTiff, ipListTiff );
		final NearestNeighborSearchOnKDTree< InterestPoint > search = new NearestNeighborSearchOnKDTree<>( kdTreeTiff );

		final ArrayList< Pair< InterestPoint, InterestPoint > > pairs = new ArrayList<>();

		double dist = 0;
		double maxDist = 0;
		ArrayList< Double > median = new ArrayList<>();

		for ( final InterestPoint ipHdf5 : ipListHdf5 )
		{
			search.search( ipHdf5 );

			if ( search.getDistance() < 4 )
			{
				pairs.add( new ValuePair<>( search.getSampler().get(), ipHdf5 ) );
				dist += search.getDistance();
				maxDist = Math.max( search.getDistance(), maxDist );
				median.add( search.getDistance() );
			}
		}

		double[] medianList = new double[ median.size() ];
		for ( int i = 0; i < median.size(); ++i )
			medianList[ i ] = median.get( i );

		IOFunctions.println( "Found " + pairs.size() + " matching detections, avg dist = " + ( dist / pairs.size() ) + ", median dist = " + Util.median( medianList ) + ", maxDist = " + maxDist );

		IOFunctions.println();
	}
}
