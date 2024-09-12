package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NEARESTNEIGHBOR;
import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NLINEAR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.models.AffineModel1D;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.blocks.BlockAlgoUtils;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.ClampType;
import net.imglib2.algorithm.blocks.convert.Convert;
import net.imglib2.algorithm.blocks.transform.Transform;
import net.imglib2.algorithm.blocks.transform.Transform.Interpolation;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.process.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.lazy.LazyAffineFusion;
import net.preibisch.mvrecon.process.fusion.lazy.LazyFusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class BlkAffineFusion
{
	public static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T > init(
			final Converter< FloatType, T > converter,
			final BasicImgLoader imgloader,
			final Collection< ? extends ViewId > viewIds,
			final Map< ViewId, ? extends AffineTransform3D > viewRegistrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final FusionGUI.FusionType fusionType,
			final int interpolationMethod,
			final Map< ViewId, AffineModel1D > intensityAdjustments,
			final Interval fusionInterval,
			final T type,
			final int[] blockSize )
	{
		// go through the views and check if they are all 2-dimensional
		final boolean is2d = viewIds.stream()
				.map( viewDescriptions::get )
				.map( BasicViewDescription::getViewSetup )
				.filter( BasicViewSetup::hasSize )
				.allMatch( vs -> vs.getSize().dimension( 2 ) == 1 );


		System.out.println( "BlkAffineFusion.init" );

		if ( !supports( is2d, fusionType, intensityAdjustments ) )
		{
			IOFunctions.println( "BlkAffineFusion: Fusion method not supported (yet). Falling back to LazyAffineFusion." );
			return LazyAffineFusion.init( converter, imgloader, viewIds, viewRegistrations, viewDescriptions, fusionType, interpolationMethod, intensityAdjustments, fusionInterval, type, blockSize );
		}

		System.out.println( "  --> let's go!" );

		final HashMap< ViewId, Dimensions > viewDimensions = LazyFusionTools.assembleDimensions( viewIds, viewDescriptions );
		final Interpolation interpolation = ( interpolationMethod == 1 ) ? NLINEAR : NEARESTNEIGHBOR;

		// final Interval bb = boundingBox;
		// boundingBox is targetBlock from accept()
		// maybe we don't need that.
		// LazyAffineFusion also has long[] globalMin
		// sounds like global bounding box
		// we need that, where does it come from
		// ==>
		long[] globalMin = fusionInterval.minAsLongArray();


		// to be able to use the "lowest ViewId" wins strategy
		final List< ? extends ViewId > sortedViewIds = new ArrayList<>( viewIds );
		Collections.sort( sortedViewIds );

		// Which views to process (use un-altered bounding box and registrations).
		// Final filtering happens per Cell.
		// Here we just pre-filter everything outside the fusionInterval.
		final Overlap overlap = new Overlap(
				sortedViewIds,
				viewRegistrations,
				viewDimensions,
				LazyFusionTools.defaultAffineExpansion,
				is2d ? 2 : 3 )
				.filter( fusionInterval )
				.offset( fusionInterval.minAsLongArray() );

		final List< BlockSupplier< FloatType > > images = new ArrayList<>( overlap.numViews() );
		final List< BlockSupplier< FloatType > > weights = new ArrayList<>( overlap.numViews() );

		for ( final ViewId viewId : overlap.getViewIds() )
		{
			final AffineTransform3D model = viewRegistrations.get( viewId ).copy();

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final double[] usedDownsampleFactors = new double[ 3 ];
			RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model, usedDownsampleFactors );

			final AffineTransform3D transform = concatenateBoundingBoxOffset( model, fusionInterval );

			final BlockSupplier< FloatType > viewBlocks = transformedBlocks(
					Cast.unchecked( inputImg ),
					transform, interpolation );
			images.add( viewBlocks );

			// instantiate blending if necessary
			final float[] blending = Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			FusionTools.adjustBlending( viewDimensions.get( viewId ), Group.pvid( viewId ), blending, border, model );

			final BlockSupplier< FloatType > blend = new BlendingBlockSupplier( inputImg, border, blending, transform );
			weights.add( blend );
		}

		final BlockSupplier< T > blocks = convertToOutputType(
				WeightedAverage.of( images, weights, overlap ),
				converter, type )
				.tile( 32 );
		return BlockAlgoUtils.cellImg( blocks, fusionInterval.dimensionsAsLongArray(), blockSize );
	}

	private static < T extends NativeType< T > > BlockSupplier< T > convertToOutputType(
			final BlockSupplier< FloatType > floatBlocks,
			final Converter< FloatType, T > converter,
			final T type )
	{
		if ( converter == null )
		{
			return floatBlocks.andThen( Convert.convert( type, ClampType.CLAMP ) );
		}
		else if ( converter instanceof net.imglib2.display.LinearRange )
		{
			final net.imglib2.display.LinearRange range = ( net.imglib2.display.LinearRange ) converter;
			return floatBlocks
					.andThen( LinearRange.linearRange( range.getMin(), range.getMax() ) )
					.andThen( Convert.convert( type, ClampType.CLAMP ) );
		}
		else
		{
			return floatBlocks.andThen( Convert.convert( type, () -> converter ) );
		}
	}


	private static < T extends NativeType< T > > BlockSupplier< FloatType > transformedBlocks(
			final RandomAccessibleInterval< T > inputImg,
			final AffineTransform3D transform,
			final Interpolation interpolation )
	{
		return BlockSupplier.of( Views.extendBorder( ( inputImg ) ) )
				.andThen( Convert.convert( new FloatType() ) )
				.andThen( Transform.affine( transform, interpolation ) );
	}


	private static AffineTransform3D concatenateBoundingBoxOffset(
			final AffineTransform3D transformFromSource,
			final Interval boundingBoxInTarget )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.setTranslation(
				-boundingBoxInTarget.min( 0 ),
				-boundingBoxInTarget.min( 1 ),
				-boundingBoxInTarget.min( 2 ) );
		t.concatenate( transformFromSource );
		return t;
	}


	private static < T extends RealType< T > & NativeType< T > > boolean supports(
			final boolean is2d,
			final FusionGUI.FusionType fusionType,
			final Map< ViewId, AffineModel1D > intensityAdjustments )
	{
		if ( is2d )
			return false; // TODO

		switch ( fusionType )
		{
		case AVG_BLEND:
			break;
		case AVG:
		case AVG_CONTENT:
		case AVG_BLEND_CONTENT:
		case MAX:
		case FIRST:
			return false; // TODO
		}

		if ( intensityAdjustments != null )
			return false; // TODO

		return true;
	}
}
