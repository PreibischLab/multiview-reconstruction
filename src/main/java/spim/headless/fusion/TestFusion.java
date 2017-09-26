package spim.headless.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.headless.boundingbox.TestBoundingBox;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionTools;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.interestpointdetection.methods.downsampling.DownsampleTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestFusion
{
	public static void main( String[] args )
	{
		new ImageJ();

		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testFusion( spimData );
	}

	public static void testFusion( final SpimData2 spimData )
	{
		Interval bb = TestBoundingBox.testBoundingBox( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		double downsampling = Double.NaN;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();
		
		for ( final ViewId viewId : viewIds )
		{
			final ImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model );

			final float[] blending =  Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border, model );

			images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );
			weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );

			//images.add( TransformWeight.transformBlending( inputImg, border, blending, vr.getModel(), bb ) );
			//weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
		}

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
		DisplayImage.getImagePlusInstance( virtual, true, "Fused, Virtual", 0, 255 ).show();

		//
		// actually fuse into an image multithreaded
		//
		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image and copying, size = " + Util.printCoordinates( size ) );

		final RandomAccessibleInterval< FloatType > fusedImg = FusionTools.copyImg( virtual, new ImagePlusImgFactory<>(), new FloatType(), true );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Finished fusion process." );

		DisplayImage.getImagePlusInstance( fusedImg, false, "Fused", 0, 255 ).show();
	}
}
