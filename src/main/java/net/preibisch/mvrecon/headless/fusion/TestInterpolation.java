package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import bdv.util.ConstantRandomAccessible;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxReorientation;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformedInputRandomAccessible;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestInterpolation
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );

		testInterpolation( spimData, "My Bounding Box" );
		// for bounding box1111 test 128,128,128 vs 256,256,256 (no blocks), there are differences at the edges
		// 
	}

	public static void testInterpolation(
			final SpimData2 spimData,
			final String bbTitle )
	{
		// one common ExecutorService for all
		final ExecutorService service = DeconViews.createExecutorService();

		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return;
		}

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		//viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		viewIds.add( new ViewId( 0, 1 ) );
		viewIds.add( new ViewId( 0, 2 ) );
		viewIds.add( new ViewId( 0, 3 ) );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		double downsampling = Double.NaN;

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = fuseVirtual( spimData, viewIds, "beads13", 1, boundingBox, downsampling );
		DisplayImage.getImagePlusInstance( virtual, true, "Fused, Virtual", 0, 255 ).show();

	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D transform,
			final HashMap< ViewId, AffineTransform3D > registrations,
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final long[] offset = new long[ input.numDimensions() ];
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		final TransformedInputRandomAccessible< T > virtual = new TransformedInputRandomAccessible< T >( input, transform, false, 0.0f, new FloatType( outsideValue ), offset );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static RandomAccessibleInterval< FloatType > fuseVirtual(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > views,
			final String label,
			final int interpolation,
			final Interval boundingBox,
			final double downsampling )
	{
		final Interval bb;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		else
			bb = boundingBox;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		// create final registrations for all views and a list of corresponding interest points
		final HashMap< ViewId, AffineTransform3D > registrations = new HashMap<>();

		for ( final ViewId viewId : views )
		{
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();

			final AffineTransform3D model = vr.getModel().copy();

			if ( !Double.isNaN( downsampling ) )
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );

			registrations.put( viewId, model );
		}

		// new loop for interestpoints that need the registrations
		final HashMap< ViewId, ArrayList< AnnotatedIP > > annotatedIps = new HashMap<>();

		for ( final ViewId viewId : views )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading corresponding interest points for " + Group.pvid( viewId ) + ", label '" + label + "'" );

			if ( spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).contains( label ) )
			{
				final InterestPointList ipList = spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
				final Map< ViewId, ViewInterestPointLists > interestPointLists = spimData.getViewInterestPoints().getViewInterestPoints();

				final List< CorrespondingInterestPoints > cipList = ipList.getCorrespondingInterestPointsCopy();
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": There are " + cipList.size() + " corresponding interest points in total (to all views)." );

				final ArrayList< AnnotatedIP > aips = assembleAllCorrespondingPoints( viewId, ipList, cipList, views, interestPointLists );

				if ( aips == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to assemble pairs of corresponding interest points." );
					return null;
				}

				// TODO: what if there were no corresponding interest points???
				//			- add image corners?
				//			- just use the "old" fusion?

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loaded " + aips.size() + " pairs of corresponding interest points." );

				final double dist = transformAnnotatedIPs( aips, registrations );

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Average distance = " + dist );

				annotatedIps.put( viewId, aips );
			}
			else
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Label '" + label + "' does not exist. Stopping." );
				return null;
			}

		}

		computeReferencePoints( annotatedIps );

		SimpleMultiThreading.threadHaltUnClean();

		for ( final ViewId viewId : views )
		{
			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( spimData.getSequenceDescription().getImgLoader(), viewId, registrations.get( viewId ) );

			images.add( transformView( inputImg, registrations.get( viewId ), registrations, bb, 0, interpolation ) );

			final RandomAccessibleInterval< FloatType > imageArea =
					Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( inputImg ) );

			weights.add( transformView( imageArea, registrations.get( viewId ), registrations, bb, 0, 0 ) );
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}

	public static void computeReferencePoints( final HashMap< ViewId, ArrayList< AnnotatedIP > > annotatedIps )
	{
		final ArrayList< AnnotatedIP > aips = new ArrayList<>();

		for ( final ArrayList< AnnotatedIP > aipl : annotatedIps.values() )
			aips.addAll( aipl );

		// find unique interest points in the pairs of images
		final ArrayList< HashSet< AnnotatedIP > > uniqueIPs = findUniqueInterestPoints( aips );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Total number of pairs are " + aips.size() + ", which are " + uniqueIPs.size() + " unique interest points." );

		// some statistics
		final int[] count = uniqueInterestPointCounts( uniqueIPs );

		System.out.println( "Structure: " );
		for ( int i = 0; i < count.length; ++i )
			if ( count[ i ] > 0 )
				System.out.println( i + ": " + count[ i ] );

		final RealSum sum = new RealSum();
		int countDist = 0;
		double maxDist = 0;

		// compute the centers
		for ( final HashSet< AnnotatedIP > uniqueIP : uniqueIPs )
		{
			final double[] avgPosW = new double[ 3 ];

			for ( final AnnotatedIP aip : uniqueIP )
			{
				for ( int d = 0; d < avgPosW.length; ++d )
					avgPosW[ d ] += aip.w[ d ];
			}

			for ( int d = 0; d < avgPosW.length; ++d )
				avgPosW[ d ] /= (double)uniqueIP.size();
	
			for ( final AnnotatedIP aip : uniqueIP )
			{
				aip.setAvgPosW( avgPosW );

				final double dist = Math.sqrt( BoundingBoxReorientation.squareDistance( aip.w[ 0 ], aip.w[ 1 ], aip.w[ 2 ], aip.avgPosW[ 0 ], aip.avgPosW[ 1 ], aip.avgPosW[ 2 ] ) );
				sum.add( dist );
				maxDist = Math.max( maxDist, dist );
				++countDist;
			}
		}

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Average distance from unique interest point: " + ( sum.getSum() / (double)countDist) );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Max distance from unique interest point: " + maxDist );
	}

	public static int[] uniqueInterestPointCounts( final List< HashSet< AnnotatedIP > > groups )
	{
		if ( groups == null || groups.size() == 0 )
			return new int[ 1 ];

		int maxSize = 0;

		for ( final HashSet< ? > group : groups )
			maxSize = Math.max( group.size(), maxSize );

		final int[] counts = new int[ maxSize + 1 ];

		for ( final HashSet< ? > group : groups )
			++counts[ group.size() ];

		return counts;
	}

	public static ArrayList< HashSet< AnnotatedIP > > findUniqueInterestPoints( final Collection< AnnotatedIP > pairs)
	{
		final ArrayList< HashSet< AnnotatedIP > > groups = new ArrayList<>();
		final HashSet< AnnotatedIP > unassignedCorr = new HashSet<>( pairs );

		while ( unassignedCorr.size() > 0 )
		{
			final HashSet< AnnotatedIP > group = new HashSet<AnnotatedIP>();
			final AnnotatedIP start = unassignedCorr.iterator().next();
			group.add( start );
			unassignedCorr.remove( start );

			final LinkedList< AnnotatedIP > potentialCorrespondences = new LinkedList<>();
			potentialCorrespondences.add( start );

			final HashSet< AnnotatedIP > visited = new HashSet<>();

			while ( potentialCorrespondences.size() > 0 )
			{
				final AnnotatedIP pc = potentialCorrespondences.remove();
				visited.add( pc );

				final ArrayList< AnnotatedIP > toRemove = new ArrayList<>();

				for ( final AnnotatedIP newCorr : unassignedCorr )
				{
					if ( pc.ip.equals( newCorr.ip ) || pc.ip.equals( newCorr.corrIp ) || pc.corrIp.equals( newCorr.ip ) || pc.corrIp.equals( newCorr.corrIp ))
					{
						group.add( newCorr );
						toRemove.add( newCorr );
						potentialCorrespondences.add( newCorr );
					}
				}

				unassignedCorr.removeAll( toRemove );
			}

			groups.add( group );

		}
		return groups;
	}

	public static double transformAnnotatedIPs( final Collection< AnnotatedIP > aips, final Map< ViewId, AffineTransform3D > models )
	{
		final RealSum sum = new RealSum( aips.size() );

		for ( final AnnotatedIP aip : aips )
		{
			aip.transform( models.get( aip.viewId ), models.get( aip.corrViewId ) );

			sum.add( Math.sqrt( BoundingBoxReorientation.squareDistance( aip.w[ 0 ], aip.w[ 1 ], aip.w[ 2 ], aip.corrW[ 0 ], aip.corrW[ 1 ], aip.corrW[ 2 ] ) ) );
			//RealPoint.wrap( aip.w );
		}

		return sum.getSum() / (double)aips.size();
	}

	public static ArrayList< AnnotatedIP > assembleAllCorrespondingPoints(
			final ViewId viewId,
			final InterestPointList ipList,
			final List< ? extends CorrespondingInterestPoints > cipList,
			final Collection< ? extends ViewId > viewsToProcess,
			final Map< ? extends ViewId, ? extends ViewInterestPointLists > interestPointLists )
	{
		// result
		final ArrayList< AnnotatedIP > ipPairs = new ArrayList<>();

		// sort all ViewIds into a set
		final HashSet< ViewId > views = new HashSet<>( viewsToProcess );

		// sort all interest points into a HashMap
		final HashMap< Integer, InterestPoint > ips = new HashMap<>();

		for ( final InterestPoint ip : ipList.getInterestPointsCopy() )
			ips.put( ip.getId(), ip );

		// sort all corresponding interest points into a HashMap
		final HashMap< ViewId, List< IPL > > loadedIps = new HashMap<>();

		for ( final CorrespondingInterestPoints cip : cipList )
		{
			// local interest point
			final InterestPoint ip = ips.get( cip.getDetectionId() );

			if ( ip == null )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Instance for id=" + ip + " of local interest point does not exist. Stopping." );
				return null;
			}

			// get corresponding interest point instance without reloading all the time
			final ViewId corrViewId = cip.getCorrespondingViewId();
			final String corrLabel = cip.getCorrespodingLabel();

			// only processing those views that are requested
			if ( !views.contains( corrViewId ) )
				continue;

			final List< IPL > ipls;

			// were there ever any interest points for this corresponding ViewId loaded?
			if ( loadedIps.containsKey( corrViewId )  )
			{
				ipls = loadedIps.get( corrViewId );
			}
			else
			{
				ipls = new ArrayList<>();
				loadedIps.put( corrViewId, ipls );
			}

			// were the interest points for this corresponding label of this corresponding ViewId loaded?
			IPL ipl = getIPL( ipls, corrLabel );

			if ( ipl == null )
			{
				// load corresponding interest points
				final ViewInterestPointLists vipl = interestPointLists.get( corrViewId );

				if ( vipl == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": No interestpoints for " +  Group.pvid( corrViewId ) + " exist. Stopping." );
					return null;
				}

				final InterestPointList corrIpList = vipl.getInterestPointList( corrLabel );

				if ( corrIpList == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Corresponding Label '" + corrLabel + "' does not exist for " +  Group.pvid( corrViewId ) + ". Stopping." );
					return null;
				}

				final HashMap< Integer, InterestPoint > corrIps = new HashMap<>();

				// sort all corresponding interest points into a HashMap
				for ( final InterestPoint corrIp : corrIpList.getInterestPointsCopy() )
					corrIps.put( corrIp.getId(), corrIp );

				ipl = new IPL( corrLabel, corrIps );

				ipls.add( ipl );
			}

			final int corrId = cip.getCorrespondingDetectionId();
			final InterestPoint corrIp = ipl.map.get( corrId );

			if ( corrIp == null )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Instance for id=" + corrId + " of corresponding Label '" + corrLabel + "' does not exist for " +  Group.pvid( corrViewId ) + ". Stopping." );
				return null;
			}
	
			ipPairs.add( new AnnotatedIP( ip, viewId, corrIp, corrViewId ) );
		}

		return ipPairs;
	}

	public static class IPL
	{
		final String label;
		final Map< Integer, InterestPoint > map;

		public IPL( final String label, final Map< Integer, InterestPoint > map )
		{
			this.label = label;
			this.map = map;
		}
	}

	public static class AnnotatedIP
	{
		final double[] l, corrL;
		final double[] w, corrW;
		final InterestPoint ip, corrIp;
		final ViewId viewId, corrViewId;

		public double[] avgPosW;

		public AnnotatedIP( final InterestPoint ip, final ViewId viewId, final InterestPoint corrIp, final ViewId corrViewId )
		{
			this.l = ip.getL().clone();
			this.w = ip.getL().clone();
			this.corrL = corrIp.getL().clone();
			this.corrW = corrIp.getL().clone();
			this.ip = ip;
			this.corrIp = corrIp;
			this.viewId = viewId;
			this.corrViewId = corrViewId;

			this.avgPosW = w;
		}

		protected void setAvgPosW( final double[] avgPosW ) { this.avgPosW = avgPosW; }
		public double[] getAvgPos() { return avgPosW; }

		public void transform( final AffineTransform3D t, final AffineTransform3D corrT )
		{
			t.apply( l, w );
			corrT.apply( corrL, corrW );
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ( ( corrIp == null ) ? 0 : corrIp.hashCode() );
			result = prime * result + Arrays.hashCode( corrL );
			result = prime * result
					+ ( ( corrViewId == null ) ? 0 : corrViewId.hashCode() );
			result = prime * result + ( ( ip == null ) ? 0 : ip.hashCode() );
			result = prime * result + Arrays.hashCode( l );
			result = prime * result
					+ ( ( viewId == null ) ? 0 : viewId.hashCode() );
			return result;
		}

		@Override
		public boolean equals( Object obj )
		{
			if ( this == obj )
				return true;
			if ( obj == null )
				return false;
			if ( getClass() != obj.getClass() )
				return false;
			AnnotatedIP other = (AnnotatedIP) obj;
			if ( corrIp == null )
			{
				if ( other.corrIp != null )
					return false;
			} else if ( !corrIp.equals( other.corrIp ) )
				return false;
			if ( !Arrays.equals( corrL, other.corrL ) )
				return false;
			if ( corrViewId == null )
			{
				if ( other.corrViewId != null )
					return false;
			} else if ( !corrViewId.equals( other.corrViewId ) )
				return false;
			if ( ip == null )
			{
				if ( other.ip != null )
					return false;
			} else if ( !ip.equals( other.ip ) )
				return false;
			if ( !Arrays.equals( l, other.l ) )
				return false;
			if ( viewId == null )
			{
				if ( other.viewId != null )
					return false;
			} else if ( !viewId.equals( other.viewId ) )
				return false;
			return true;
		}
	}

	private static IPL getIPL( final Collection< IPL > ipls, final String label )
	{
		for ( final IPL ipl : ipls )
			if ( ipl.label.equals( label ) )
				return ipl;

		return null;
	}
}
