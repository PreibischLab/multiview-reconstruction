package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.util.ConstantRandomAccessible;
import ij.ImageJ;
import mpicbg.models.AffineModel1D;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.intensityadjust.IntensityAdjuster;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.fusion.transformed.fusion.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.fusion.NewFusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.images.TransformedInputRandomAccessible;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestNewFusion
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		// generate 4 views with 1000 corresponding beads, single timepoint
		// SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );
		SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );;

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testNewFusion( spimData );
	}

	public static void testNewFusion( final SpimData2 spimData )
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

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = FusionTools.fuseVirtual( spimData, viewIds, bb, downsampling ).getA();
		DisplayImage.getImagePlusInstance( virtual, true, "Fused, Virtual", 0, 255 ).show();
	}

	public static < T extends RealType< T > > Pair< RandomAccessibleInterval< FloatType >, Interval > transformView(
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D transform,
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

		final TransformedInputRandomAccessible< T > virtual = new TransformedInputRandomAccessible< T >( input, transform, false, 0.0f, new FloatType( outsideValue ), boundingBox );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		transform.estimateBounds( interval )

		return new ValuePair<>( Views.interval( virtual, new FinalInterval( size ) ), ;
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final BasicImgLoader imgloader,
			final Map< ViewId, AffineTransform3D > registrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final Collection< ? extends ViewId > views,
			final boolean useBlending,
			final boolean useContentBased,
			final int interpolation,
			final Interval boundingBox,
			final double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{

		Interval bBox2d = null;
		// go through the images and check if they are all 2-dimensional
		boolean is_2d = false;
		for ( final ViewId vid: views )
		{
			if ( viewDescriptions.get(vid).getViewSetup().hasSize() )
				if (viewDescriptions.get(vid).getViewSetup().getSize().dimension(2) == 1)
					is_2d = true;
				else
				{
					// TODO: maybe warn that 2d images will be lost during fusion if we have a 2d/3d mixup
					is_2d = false; // we found a non-2d image
					break;
				}
		}

		if (is_2d)
		{
			// set the translational part of the registrations to 0
			for ( AffineTransform3D transform : registrations.values())
			{
				// check if we have just scaling in 3d
				boolean justScale3d = true;
				for (int d1 = 0; d1<3; d1++)
					for (int d2 = 0; d2<3; d2++)
						if ((d1 > 1 || d2>1) && d1 != d2 && transform.get(d1,d2) != 0)
							justScale3d = false;
				if (justScale3d)
					transform.set(0,2,3);
				else
					IOFunctions.println("WARNING: You are trying to fuse 2d images with 3d registrations.");
			}
			// create a virtual 2-d bounding box
			long[] bbMin = new long[3];
			long[] bbMax = new long[3];
			boundingBox.min(bbMin);
			boundingBox.max(bbMax);
			bbMin[2] = bbMax[2] = 0;
			bBox2d = new FinalInterval(bbMin, bbMax);
		}

		final Pair< Interval, AffineTransform3D > scaledBB = FusionTools.createDownsampledBoundingBox( is_2d ? bBox2d : boundingBox, downsampling );
		final Interval bb = scaledBB.getA();
		final AffineTransform3D bbTransform = scaledBB.getB();

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final ViewId viewId : views )
		{
			AffineTransform3D model = registrations.get( viewId );

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model );

			if ( intensityAdjustments != null && intensityAdjustments.containsKey( viewId ) )
				inputImg = new ConvertedRandomAccessibleInterval< FloatType, FloatType >(
						FusionTools.convertInput( inputImg ),
						new IntensityAdjuster( intensityAdjustments.get( viewId ) ),
						new FloatType() );

			images.add( TransformView.transformView( inputImg, model, bb, 0, interpolation ) );

			// add all (or no) weighting schemes
			if ( useBlending || useContentBased )
			{
				RandomAccessibleInterval< FloatType > transformedBlending = null, transformedContentBased = null;

				// instantiate blending if necessary
				if ( useBlending )
				{
					final float[] blending = Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
					final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					FusionTools.adjustBlending( viewDescriptions.get( viewId ), blending, border, model );
	
					transformedBlending = TransformWeight.transformBlending( inputImg, border, blending, model, bb );
				}
	
				// instantiate content based if necessary
				if ( useContentBased )
				{
					final double[] sigma1 = Util.getArrayFromValue( FusionTools.defaultContentBasedSigma1, 3 );
					final double[] sigma2 = Util.getArrayFromValue( FusionTools.defaultContentBasedSigma2, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					FusionTools.adjustContentBased( viewDescriptions.get( viewId ), sigma1, sigma2, model );

					transformedContentBased = TransformWeight.transformContentBased( inputImg, new CellImgFactory< ComplexFloatType >(), sigma1, sigma2, model, bb );
				}

				if ( useContentBased && useBlending )
				{
					weights.add( new CombineWeightsRandomAccessibleInterval(
									new FinalInterval( transformedBlending ),
									transformedBlending,
									transformedContentBased,
									CombineType.MUL ) );
				}
				else if ( useBlending )
				{
					weights.add( transformedBlending );
				}
				else if ( useContentBased )
				{
					weights.add( transformedContentBased );
				}
			}
			else
			{
				final RandomAccessibleInterval< FloatType > imageArea =
						Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( inputImg ) );

				weights.add( TransformView.transformView( imageArea, model, bb, 0, 0 ) );
			}
		}

		return new ValuePair<>( new NewFusedRandomAccessibleInterval( new FinalInterval( FusionTools.getFusedZeroMinInterval( bb ) ), images, weights, 50 ), bbTransform );
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > viewIds,
			Interval bb,
			double downsampling )
	{
		return fuseVirtual( spimData, viewIds, bb, downsampling, null );
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > viewIds,
			Interval bb,
			double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{
		return fuseVirtual( spimData, viewIds, true, false, 1, bb, downsampling, intensityAdjustments );
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > views,
			final boolean useBlending,
			final boolean useContentBased,
			final int interpolation,
			final Interval boundingBox,
			final double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();

		final HashMap< ViewId, AffineTransform3D > registrations = new HashMap<>();

		for ( final ViewId viewId : views )
		{
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			registrations.put( viewId, vr.getModel().copy() );
		}

		final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions = spimData.getSequenceDescription().getViewDescriptions();

		return fuseVirtual( imgLoader, registrations, viewDescriptions, views, useBlending, useContentBased, interpolation, boundingBox, downsampling, intensityAdjustments );
	}

}
