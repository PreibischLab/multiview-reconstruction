package net.preibisch.mvrecon.headless.quality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.quality.FRCRealRandomAccessible;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

public class TestQuality
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;
		
		// generate 4 views with 1000 corresponding beads, single timepoint
		// spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testQuality( spimData );
	}

	public static void testQuality( final SpimData2 spimData )
	{
		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();

		viewIds.add( new ViewId( 0, 0 ) );
		viewIds.add( new ViewId( 0, 1 ) );

		//viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		Interval bb = new BoundingBoxMaximal( viewIds, spimData ).estimate( "Full Bounding Box" );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": bounding box = " + bb );

		// img loading and registrations
		final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		final ViewRegistrations registrations = spimData.getViewRegistrations();

		final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > data = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading view " +  Group.pvid( viewId ) + " ..." );

			final RandomAccessibleInterval input = imgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );
			//DisplayImage.getImagePlusInstance( input, true, "Fused, Virtual", 0, 255 ).show();

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Computing FRC for " +  Group.pvid( viewId ) + " ..." );

			final FRCRealRandomAccessible< FloatType > frc = FRCRealRandomAccessible.distributeGridFRC( input, 0.1, 5, 256, true, null );
			//DisplayImage.getImagePlusInstance( frc.getRandomAccessibleInterval(), true, "Fused, Virtual", Double.NaN, Double.NaN ).show();

			final ViewRegistration vr = registrations.getViewRegistration( viewId );
			vr.updateModel();

			data.add( new ValuePair<>( frc.getRandomAccessibleInterval(), vr.getModel() ) );
		}

		// downsampling
		double downsampling = 4; //Double.NaN;

		//
		// display virtually fused
		//

		final RandomAccessibleInterval< FloatType > virtual = fuseRAIs( data, downsampling, bb, 1 );
		DisplayImage.getImagePlusInstance( virtual, false, "Fused, Virtual", Double.NaN, Double.NaN ).show();

	}

	public static RandomAccessibleInterval< FloatType > fuseRAIs(
			final Collection< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > data,
			final double downsampling,
			final Interval boundingBox,
			final int interpolation )
	{
		final Interval bb;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		else
			bb = boundingBox;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();

		for ( final Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > d : data )
		{
			AffineTransform3D model = d.getB();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			images.add( TransformView.transformView( d.getA(), model, bb, 0, interpolation ) );
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images );
	}
}
