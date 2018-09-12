package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import bdv.util.ConstantRandomAccessible;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.nonrigid.CorrespondingIP;
import net.preibisch.mvrecon.process.fusion.nonrigid.InterpolatingNonRigidRandomAccessible;
import net.preibisch.mvrecon.process.fusion.nonrigid.NonRigidRandomAccessible;
import net.preibisch.mvrecon.process.fusion.nonrigid.NonRigidTools;
import net.preibisch.mvrecon.process.fusion.nonrigid.NonrigidIP;
import net.preibisch.mvrecon.process.fusion.nonrigid.SimpleReferenceIP;
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
		final List< ViewId > viewsToFuse = new ArrayList< ViewId >(); // fuse
		final List< ViewId > viewsToUse = new ArrayList< ViewId >(); // used to compute the non-rigid transform

		viewsToUse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		viewsToFuse.add( new ViewId( 0, 0 ) );
		//viewsToFuse.add( new ViewId( 0, 2 ) );
		//viewsToFuse.add( new ViewId( 0, 3 ) );

		// filter not present ViewIds
		List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewsToUse );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		removed = SpimData2.filterMissingViews( spimData, viewsToFuse );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		double downsampling = Double.NaN;

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = fuseVirtual( spimData, viewsToFuse, viewsToUse, "beads13", 0, boundingBox, downsampling );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Displaying " );

		final ImagePlus imp = ImageJFunctions.wrapFloat( virtual, "virtual" );
		imp.setSlice( 100 );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": showing " );

		imp.show();
		//DisplayImage.getImagePlusInstance( virtual, true, "Fused, Virtual", 0, 255 ).show();

	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final RandomAccessibleInterval< T > input,
			final Collection< ? extends NonrigidIP > ips,
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

		//final NonRigidRandomAccessible< T > virtual = new NonRigidRandomAccessible< T >( input, ips, false, 0.0f, new FloatType( outsideValue ), offset );

		final long[] controlPointDistance = new long[] { 100, 100, 100 };
		final InterpolatingNonRigidRandomAccessible< T > virtual = new InterpolatingNonRigidRandomAccessible< T >( input, ips, controlPointDistance, false, 0.0f, new FloatType( outsideValue ), boundingBox );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static RandomAccessibleInterval< FloatType > fuseVirtual(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewsToFuse,
			final Collection< ? extends ViewId > viewsToUse,
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

		for ( final ViewId viewId : viewsToUse )
		{
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();

			final AffineTransform3D model = vr.getModel().copy();

			if ( !Double.isNaN( downsampling ) )
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );

			registrations.put( viewId, model );
		}

		// new loop for interestpoints that need the registrations
		final HashMap< ViewId, ArrayList< CorrespondingIP > > annotatedIps = new HashMap<>();

		for ( final ViewId viewId : viewsToUse )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading corresponding interest points for " + Group.pvid( viewId ) + ", label '" + label + "'" );

			if ( spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).contains( label ) )
			{
				final InterestPointList ipList = spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
				final Map< ViewId, ViewInterestPointLists > interestPointLists = spimData.getViewInterestPoints().getViewInterestPoints();

				final List< CorrespondingInterestPoints > cipList = ipList.getCorrespondingInterestPointsCopy();
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": There are " + cipList.size() + " corresponding interest points in total (to all views)." );

				final ArrayList< CorrespondingIP > aips = NonRigidTools.assembleAllCorrespondingPoints( viewId, ipList, cipList, viewsToUse, interestPointLists );

				if ( aips == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to assemble pairs of corresponding interest points." );
					return null;
				}

				// TODO: what if there were no corresponding interest points???
				//			- add image corners?
				//			- just use the "old" fusion?

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loaded " + aips.size() + " pairs of corresponding interest points." );

				final double dist = NonRigidTools.transformAnnotatedIPs( aips, registrations );

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Average distance = " + dist );

				annotatedIps.put( viewId, aips );
			}
			else
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Label '" + label + "' does not exist. Stopping." );
				return null;
			}

		}

		// compute an average location of each unique interest point that is defined by many (2...n) corresponding interest points
		// this location in world coordinates defines where each individual point should be "warped" to
		final HashMap< ViewId, ArrayList< SimpleReferenceIP > > uniquePoints = NonRigidTools.computeReferencePoints( annotatedIps );

		
		//SimpleMultiThreading.threadHaltUnClean();

		for ( final ViewId viewId : viewsToFuse )
		{
			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			// TODO: RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( spimData.getSequenceDescription().getImgLoader(), viewId, registrations.get( viewId ) );

			RandomAccessibleInterval inputImg = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

			// TODO: use standard fusion if uniquePoints.get( viewId ).size() == 0
			images.add( transformView( inputImg, uniquePoints.get( viewId ), bb, 0, interpolation ) );

			//final RandomAccessibleInterval< FloatType > imageArea =
			//		Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( inputImg ) );

			//weights.add( transformView( imageArea, uniquePoints.get( viewId ), bb, 0, 0 ) );

			weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( bb ) ) );
		}

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Returning " );

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}






}
